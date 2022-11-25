(ns tethys.response
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [clj-http.client :as http]))


(defn dispatch [images {:as resp}]
  (prn resp))

(defn up [{:keys [response-queqe]} images]
  (µ/log ::up :message "start up response queqe agent")
  (let [w (fn [_ rq _ _]
            (send rq (fn [l]
                       (when (seq l)
                         (dispatch images (first l))
                         (-> l rest)))))]
    (set-error-handler! response-queqe (fn [a ex]
                                       (µ/log ::error-handler :error (str "error occured: " ex))
                                       (Thread/sleep 1000)
                                       (µ/log ::error-handler :message "try to restart agent")
                                       (restart-agent a @a)))
    (add-watch response-queqe :queqe w)))

(defn down [[_ wq]]
  (µ/log ::down :message "shut down respons queqe agent")
  (remove-watch wq :queqe)
  (send wq (fn [_] [])))
