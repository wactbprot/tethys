(ns tethys.model.core
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.string :as str]
            [com.brunobonacci.mulog :as µ]))

;; # Model

;; The model `ns` transforms the *mpd*s as received from the database
;; into a structure where different threads can work on. For the
;; moving parts, agents are used.
;;
;; This `ns` knows how to pull out thedifferent parts of the mpd `image`. 
(defn images->image [images id]
  (let [id (keyword id)]
    (-> images id)))

(defn images->state-agent [images {:keys [id ndx group]}]
  (let [group-kw (keyword group)
        ndx (Integer. ndx)]
    (-> images
        (images->image id)
        group-kw
        (nth ndx))))

(defn images->cont-agent [images task]
  (images->state-agent images (assoc task :group :conts)))

(defn images->conts [images {:keys [id]}]
  (-> images
      (images->image id)
      :conts))

(defn images->defins [images {:keys [id]}]
  (-> images
      (images->image id)
      :defins))

(defn images->exch-agent [images {:keys [id]}]
  (-> images
      (images->image id)
      :exch))

(defn images->conf [images {:keys [id]}]
  (-> images
      (images->image id)
      :conf))

(defn images->ids-agent [images {:keys [id]}]
  (-> images
      (images->image id)
      :ids))

;; ## doc ids

;; TODO: move all `send` calls to model; the docs ns should not need
;; to know how to add; done

(defn doc-ids [images task] @(images->ids-agent images task)) 

(defn add-doc-id [images task doc-id]
  (µ/log ::add-doc-id :message (str "add doc id " doc-id))
  (let [i-agt (images->ids-agent images task)]
    (send i-agt (fn [coll] (conj coll doc-id)))))

(defn rm-doc-id [images task doc-id]
  (µ/log ::rm-doc-id :message (str "rm doc id " doc-id))
  (let [i-agt (images->ids-agent images task)] 
    (send i-agt (fn [coll] (disj coll doc-id)))))

(defn rm-all-doc-ids [images task]
  (µ/log ::rm-all-doc-ids :message  "rm all doc ids")
  (let [i-agt (images->ids-agent images task)]
    (run! (fn [id] (rm-doc-id images task id)) @i-agt)))

(defn refresh-doc-ids [images task id-coll]
  (µ/log ::refresh-doc-ids :message (str "refresh doc ids" id-coll))
  (rm-all-doc-ids images task)
  (run! (fn [id] (add-doc-id images task id)) id-coll))

;; ## message

(defn add-message [images {:keys [Message] :as task}]
  (let [s-agt (images->cont-agent images task)]
    (send s-agt (fn [m] (assoc m :message Message)))))

(defn rm-message [images task]
  (let [s-agt (images->cont-agent images task)]
    (send s-agt (fn [m] (dissoc m :message)))))

;; # Helper functions

(defn title->ndx [images task title]
  (reduce (fn [_ s-agt]
            (let [a @s-agt]
               (when (= title (-> a :title))
                (reduced (-> a :ndx)))))
          {} (images->conts images task)))

(defn- flattenv [v] (-> v flatten vec))

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

(defn up [{:keys [id cont defins exch conf]}]
  {:conf conf
   :exch (agent (or exch {}))
   :ids (agent #{})
   :conts (mapv (fn [{:keys [Ctrl Definition Title Element]} ndx]
                  (agent {:title Title
                          :element Element
                          :ndx ndx
                          :ctrl (or (keyword Ctrl) :ready)
                          :spawn {}
                          :state (state-struct Definition :conts id ndx )})) 
                cont (range))
   :defins (mapv (fn [{:keys [Ctrl Definition DefinitionClass Condition]} ndx]
                   (agent {:cls DefinitionClass
                           :cond Condition
                           :ndx ndx
                           :ctrl (or (keyword Ctrl) :ready)
                           :spawn {}
                           :state (state-struct Definition :defins id ndx)})) 
                 defins (range))})

;; The down methode sets all agents back to its initial value
(defn down [[_ {:keys [conts defins exch response-queqe ids]}]]
  (run! #(send % (fn [_] {})) conts)
  (run! #(send % (fn [_] {})) defins)
  (send exch (fn [_] {}))
  (send ids (fn [_] {})))
