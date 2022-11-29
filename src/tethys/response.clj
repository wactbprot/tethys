(ns tethys.response
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [clj-http.client :as http]
            [tethys.docs :as docs]
            [tethys.exchange :as exch]
            [tethys.model :as model]
            [tethys.scheduler :as sched]))

;; The `id` is the mpd id comming from the task; `ids` are the doc-ids
;; comming from the response (devproxy).
(defn refresh [images {:keys [id ids] :as resp}]
  (µ/log ::refresh :message "trigger ids refresh")
  (docs/refresh images id ids)
  resp)

(defn store [images resp]
  (µ/log ::refresh :message "trigger store results")
  (docs/store images resp)
  resp)

(defn retry [images resp]
  (µ/log ::refresh :message "trigger retry")
  (let [s-agt (model/images->state-agent images resp)]
    (sched/state-ready! s-agt resp)
    resp))


(defn to-exch [images resp]
  (µ/log ::refresh :message "write data to exchange interface")
  (let [e-agt (model/images->exch-agent images resp)]
    (exch/to e-agt resp)
    resp))

(defn err [images {:keys [error] :as resp}]
  (µ/log ::refresh :error (str error))
  (let [s-agt (model/images->state-agent images resp)]
    (sched/state-error! s-agt resp)
    resp))

;; Only happy path for the helper functions above.
(defn dispatch [images {:keys [error ids DocPath ToExchange Result Retry] :as task}]
  (cond->> task
    Result (store images)
    ids (refresh images)
    Retry (retry images)
    ToExchange (to-exch images)
    error (err images)))

(defn add [a m] (send a (fn [l] (conj l m))))

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
