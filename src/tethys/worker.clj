(ns tethys.worker)

;; idea: worker registers watch-fn for state agent
;; watching :start key
(defn whatch-fn! [_ a _ {:keys [run]}]
  (when (seq run)
    (prn run)))

(defn up [as] (mapv #(add-watch % :work whatch-fn!) as))
(defn down [[_ as]] (mapv #(remove-watch % :work) as))
