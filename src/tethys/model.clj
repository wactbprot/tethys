(ns tethys.model
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
    (:require [clojure.string :as str]))

(defn- flattenv [v] (into [] (flatten v)))

(defstruct state :id :group :ndx :sdx :pdx :is :task)

(defn agents-up [id group-kw m]
  (mapv (fn [{:keys [Ctrl Definition]} ndx]
          (agent {:ctrl (or (keyword Ctrl) :ready)
                  :state (flattenv
                          (mapv (fn [s sdx] 
                                  (mapv (fn [t pdx]
                                          (struct state
                                                  id group-kw ndx sdx pdx :ready t))
                                        s (range)))
                                Definition (range)))})) 
        m (range)))

(defn agents-down [[id group-agents]]
  (run! #(send % (fn [_] {})) group-agents))
