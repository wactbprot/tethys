(ns tethys.core.model
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.string :as str]
            [clojure.pprint :as pp]
            [com.brunobonacci.mulog :as µ]))

(defn- flattenv [v] (-> v flatten vec))

;; # Model

;; The model `ns` transforms the *mpd*s as received from the database
;; into a structure where different threads can work on. For the
;; moving parts, agents are used.
;;
;; This `ns` knows how to pull out thedifferent parts of the mpd `image`. 
(defn images->image [images mpd]
  (let [mpd (keyword mpd)]
    (-> images mpd)))

(defn image->state-agent [image ndx group-kw]
  (let [group-kw (keyword group-kw)
        ndx (Integer. ndx)]
    (-> image group-kw (nth ndx))))

(defn image->cont-agent [image ndx] (image->state-agent image ndx :conts))
(defn image->defin-agent [image ndx] (image->state-agent image ndx :defins))
(defn image->task-agent [image] (-> image :task-queqe))
(defn image->worker-agent [image] (-> image :worker-queqe))
(defn image->worker-futures [image] (-> image :worker-futures))
(defn image->exch-agent [image] (-> image :exch))
(defn image->ids-agent [image] (-> image :ids))
(defn image->resp-agent [image] (-> image :response-queqe))
(defn image->conf [image] (-> image :conf))

(defn images->defins [images {:keys [id]}]
  (-> images
      (images->image id)
      (get :defins)))

(defn images->state-agent [images {:keys [id ndx group]}]
  (-> images
      (images->image id)
      (image->state-agent ndx group)))

(defn images->exch-agent [images {:keys [id]}]
  (-> images
      (images->image id)
      image->exch-agent))

(defn images->resp-agent [images {:keys [id]}]
  (-> images
      (images->image id)
      image->resp-agent))

(defn images->conf [images {:keys [id]}]
  (-> images
      (images->image id)
      image->conf))

(defn images->ids-agent [images {:keys [id]}]
  (-> images
      (images->image id)
      (image->ids-agent)))

;; ## message

(defn add-message [images {:keys [id ndx Message]}]
  (let [s-agt (-> images
                  (images->image id)
                  (image->cont-agent ndx))]
    (send s-agt (fn [m] (assoc m :message Message)))))

(defn rm-message [images {:keys [id ndx]}]
  (let [s-agt (-> images
                  (images->image id)
                  (image->cont-agent ndx))]
    (send s-agt (fn [m] (dissoc m :message)))))

;; ## worker

(defn spawn-work [images {:keys [id pos-str] :as task} f]
  (let [kw (keyword pos-str)
        w-atm (-> images
                  (images->image id)
                  (image->worker-futures))]
    (swap! w-atm assoc kw (future (f images task)))))

;; ## State

;; The `state` structure holds information of the position
;; 
;; * `:id` ... id of the mpd (as keyword)
;; * `:group` ... `:conts` or `:defins`
;; * `:ndx` ... number of the Container or nth Definitions element
;; * `:sdx` ... sequential step number
;; * `:pdx` ... parallel step number
;;
;; and information of the state. `:is` may be:
;;
;; * `ready`
;; * `working`
;; * `executed`
(defstruct state :id :group :ndx :sdx :pdx :is)

(defn assoc-position-string [{:keys [id group ndx sdx pdx] :as m}]
  (assoc m :pos-str (str/join "."  [id group ndx sdx pdx])))

(defn state-struct [v group-kw id ndx]
  (flattenv (mapv (fn [s sdx] 
                    (mapv (fn [t pdx]
                            (assoc-position-string
                             (merge t (struct state id group-kw ndx sdx pdx :ready))))
                          s (range)))
                  v (range))))

(defn up [id cont defins exch conf]
  {:conf conf
   :exch (agent (if (map? exch) exch {}))
   :ids (agent #{})
   :response-queqe (agent '())
   :worker-queqe (agent '())
   :worker-futures (atom {})
   :task-queqe (agent '())
   :conts (mapv (fn [{:keys [Ctrl Definition Title Element]} ndx]
                  (agent {:title Title
                          :element Element
                          :ndx ndx
                          :ctrl (or (keyword Ctrl) :ready)
                          :state (state-struct Definition :conts id ndx )})) 
                cont (range))
   :defins (mapv (fn [{:keys [Ctrl Definition DefinitionClass Condition]} ndx]
                   (agent {:cls DefinitionClass
                           :cond Condition
                           :ctrl (or (keyword Ctrl) :ready)
                           :state (state-struct Definition :defins id ndx)})) 
                 defins (range))})

;; The down methode sets all agents back to its initial value
(defn down [[_ {:keys [conts defins exch worker-queqe worker-futures task-queqe response-queqe ids]}]]
  (run! #(send % (fn [_] {})) conts)
  (run! #(send % (fn [_] {})) defins)
  (send exch (fn [_] {}))
  (send ids (fn [_] {}))
  (send worker-queqe (fn [_] '()))
  (reset! worker-futures {})
  (send response-queqe (fn [_] '()))
  (send task-queqe (fn [_] '())))

(defn write-edn [file data] (spit file (with-out-str (pp/pprint data))))

(defn suspend [[id {:keys [conts defins exch worker-queqe worker-futures task-queqe response-queqe ids]}] folder]
  (let [folder (str folder "/" (name id) "/")]
    (.mkdirs (java.io.File. folder))
    (write-edn (str folder "exch.edn") @exch)
    (write-edn (str folder "ids.edn") @ids)
    (write-edn (str folder "worker-queqe.edn") @worker-queqe)
    (write-edn (str folder "worker-futures.edn") @worker-futures)
    (write-edn (str folder "task-queqe.edn") @task-queqe)
    (write-edn (str folder "response-queqe.edn") @response-queqe)
    (write-edn (str folder "conts.edn") (mapv deref conts))
    (write-edn (str folder "defins.edn") (mapv deref defins))))
