(ns tethys.task)

(defn whatch-fn! [_ a _ {:keys [task]}]
  (when (seq task)
    (prn task)))

(defn up [as] (mapv #(add-watch % :task whatch-fn!) as))
(defn down [[_ as]] (mapv #(remove-watch % :task) as))
