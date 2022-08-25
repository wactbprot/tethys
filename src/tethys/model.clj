(ns tethys.model
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
    (:require [clojure.string :as str]))

(defn- flattenv [v] (into [] (flatten v)))

(defn agents-up [id group-kw m]
  (mapv (fn [{:keys [Ctrl Definition]} ndx]
          (agent {:ctrl (or (keyword Ctrl) :ready)
                  :state (flattenv
                          (mapv (fn [s sdx] 
                                  (mapv (fn [t pdx] 
                                          {:id id
                                           :group group-kw
                                           :ndx ndx :sdx sdx :pdx pdx
                                           :is :ready
                                           :task t})
                                        s (range)))
                                Definition (range)))})) 
        m (range)))

(defn agents-down [[id group-agents]]
  (run! #(send % (fn [_] {})) group-agents))
