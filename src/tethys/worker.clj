(ns tethys.worker
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]))

(defn state-agent [{:keys [id ndx]} conts]
  (-> conts id (nth ndx)))


(defn up [conts]
  (µ/log ::up :message "start up worker agent queqe")
  (let [a (agent [])
        w (fn [_ a _ v]
            (when (seq v)
              (prn (state-agent (first v) conts))
              (send a (fn [v] (-> v rest vec)))))]
    (add-watch a :queqe w)))

(defn down [[_ a]]
  (µ/log ::down :message "shut down worker agent queqe")
  (remove-watch a :queqe)
  (send a (fn [_] [])))
