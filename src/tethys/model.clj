(ns tethys.model
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
    (:require [clojure.string :as str]))


(defn- flattenv [v] (-> v flatten vec))

;; ## Model
;; The model `ns` transforms the *mpd*s as received from the database
;; into a structure where different threads can work on. For the
;; moving parts, agents are used.
;; This `ns` knows how to pull it out of the `image`; like this:
(defn state-agent [image ndx group-kw]
  (-> image group-kw (nth ndx)))

(defn cont-agent [image ndx] (state-agent image ndx :conts ))

(defn defin-agent [image ndx] (state-agent image ndx :defins))

(defn exch-agent [image] (-> image :exch))


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

(defn up [id cont defins exch]
  {:exch (agent (if (map? exch) exch {}))
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

(defn down [[id {:keys [conts defins]}]]
  (run! #(send % (fn [_] {})) conts)
  (run! #(send % (fn [_] {})) defins))
