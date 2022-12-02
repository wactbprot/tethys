(ns tethys.core.model
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.string :as str]
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
(defn image->exch-agent [image] (-> image :exch))
(defn image->ids-agent [image] (-> image :ids))
(defn image->resp-agent [image] (-> image :response-queqe))
(defn image->conf [image] (-> image :conf))

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

(defn state-struct [v group-kw id ndx]
  (flattenv (mapv (fn [s sdx] 
                    (mapv (fn [t pdx]
                            (merge t (struct state id group-kw ndx sdx pdx :ready)))
                          s (range)))
                  v (range))))

(defn up [id cont defins exch conf]
  {:conf conf
   :exch (agent (if (map? exch) exch {}))
   :ids (agent #{})
   :response-queqe (agent '())
   :worker-queqe (agent '())
   :task-queqe (agent '())
   :conts (mapv (fn [{:keys [Ctrl Definition]} ndx]
                  (agent {:ctrl (or (keyword Ctrl) :ready)
                          :state (state-struct Definition :conts id ndx )})) 
                cont (range))
   :defins (mapv (fn [{:keys [Ctrl Definition DefinitionClass Condition]} ndx]
                   (agent {:cls DefinitionClass
                           :cond Condition
                           :ctrl (or (keyword Ctrl) :ready)
                           :state (state-struct Definition :defins id ndx)})) 
                 defins (range))})

;; The down methode sets all agents back to its initial value
(defn down [[_ {:keys [conts defins exch worker-queqe task-queqe response-queqe ids]}]]
  (run! #(send % (fn [_] {})) conts)
  (run! #(send % (fn [_] {})) defins)
  (send exch (fn [_] {}))
  (send ids (fn [_] {}))
  (send worker-queqe (fn [_] '()))
  (send response-queqe (fn [_] '()))
  (send task-queqe (fn [_] '())))
 
