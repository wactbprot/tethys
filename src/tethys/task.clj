(ns tethys.task
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.db :as db]))


;; The `whatch-fn!` checks if the task vector contains any
;; elements. Returns m (the agent) if not. If `:task` is not empty it
;; should contain a map like this:
(comment
  )

(defn up [db as]
  (let [f (db/task-fn db)
        w (fn [_ a _ {:keys [task] :as m}]
             (when (seq task)
               (prn (f (-> task first :TaskName)))
               (send a (fn [m] (assoc m :task (vec (rest task)))))))]
    (mapv #(add-watch % :task w) as)))

(defn down [[_ as]] (mapv #(remove-watch % :task) as))

