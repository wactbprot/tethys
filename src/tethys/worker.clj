(ns tethys.worker
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]))

(defn up []
  (µ/log ::up :message "start up worker agent queqe")
  (let [a (agent [])
        w (fn [_ _ _ v]
            (when (seq v)
              (prn v)
              (-> v rest vec)))]
    (add-watch a :queqe w)))

(defn down [[_ a]]
  (µ/log ::down :message "shut down worker agent queqe")
  (send a (fn [_] [])))
