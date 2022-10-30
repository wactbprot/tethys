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
  (prn db)
  (let [f (db/task-fn db)
        w (fn [_ a _ {:keys [task]}]
            (when (seq task)
              (prn (f (-> task first :task :TaskName)))))]
    (mapv #(add-watch % :task w) as)))
  
(defn down [[_ as]] (mapv #(remove-watch % :task) as))

