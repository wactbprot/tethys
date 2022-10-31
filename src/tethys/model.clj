(ns tethys.model
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
    (:require [clojure.string :as str]))


;; ## Model
;; The model `ns` transforms the *mpd*s as received from the databas
;; into a a structure where different threads can work on. For the
;; moving parts, agents are used.

(defn- flattenv [v] (-> v flatten vec))

(defstruct state :id :group :ndx :sdx :pdx :is)

(defn agents-up [id group-kw m]
  (mapv (fn [{:keys [Ctrl Definition]} ndx]
          (agent {:task-queue []
                  :work-queue []
                  :ctrl (or (keyword Ctrl) :ready)
                  :state (flattenv
                          (mapv (fn [s sdx] 
                                  (mapv (fn [t pdx]
                                          (merge t (struct state
                                                           id group-kw ndx sdx pdx :ready)))
                                        s (range)))
                                Definition (range)))})) 
        m (range)))

(defn agents-down [[id group-agents]]
  (run! #(send % (fn [_] {})) group-agents))
