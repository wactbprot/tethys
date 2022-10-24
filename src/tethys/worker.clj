(ns tethys.worker)

;; idea: worker registers watch-fn for state agent
;; watching :start key
(defn whatch-fn! [_ a _ {:keys [start]}]
  (when (seq start)
    (prn start)))

(defn whatch-up [as] (mapv #(add-watch % :work whatch-fn!) as))
(defn whatch-down [[_ as]] (mapv #(remove-watch % :work) as))
