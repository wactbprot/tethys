(ns tethys.model
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
    (:require [clojure.string :as str]))


(defn- flattenv [v] (-> v flatten vec))

;; ## Model
;; The model `ns` transforms the *mpd*s as received from the databas
;; into a structure where different threads can work on. For the
;; moving parts, agents are used.

;; This ns knows how to pull it out of the `image`; like this:
(defn state-agent [image ndx group]
  (prn ndx)
  (prn group)
  (-> image group (nth ndx)))

(defn cont-agent [image ndx] (state-agent image ndx :conts ))

(defn defin-agent [image ndx] (state-agent image ndx :defins))

(defstruct state :id :group :ndx :sdx :pdx :is)

(defn state-struct [group id ndx group-kw]
  (flattenv (mapv (fn [s sdx] 
                    (mapv (fn [t pdx]
                            (merge t (struct state id group-kw ndx sdx pdx :ready)))
                          s (range)))
                  group (range))))

(defn up [id cont defins]
  {:conts (mapv (fn [{:keys [Ctrl Definition]} ndx]
                  (agent {:ctrl (or (keyword Ctrl) :ready)
                          :state (state-struct Definition id ndx :conts)})) 
                cont (range))
   :defins (mapv (fn [{:keys [Ctrl Definition DefinitionClass Condition]} ndx]
                   (agent {:cls DefinitionClass
                           :cond Condition
                           :ctrl (or (keyword Ctrl) :ready)
                           :state (state-struct Definition id ndx :defins)})) 
                 defins (range))})

(defn down [[id {:keys [conts defins]}]]
  (run! #(send % (fn [_] {})) conts)
  (run! #(send % (fn [_] {})) defins))
