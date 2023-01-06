(ns tethys.core.response
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [clj-http.client :as http]
            [tethys.core.docs :as docs]
            [tethys.core.exchange :as exch]
            [tethys.core.model :as model]
            [tethys.core.scheduler :as sched]))

;; The `id` is the mpd id comming from the task; `ids` are the doc-ids
;; comming from the response (devproxy).
(defn refresh [images {:keys [id ids] :as task}]
  (µ/log ::refresh :message "trigger ids refresh")
  (docs/refresh images id ids)
  task)

(defn store [images task]
  (µ/log ::refresh :message "trigger store results")
  (docs/store images task)
  task)

(defn to-exch [images task]
  (µ/log ::refresh :message "write data to exchange interface")
  (let [e-agt (model/images->exch-agent images task)]
    (exch/to e-agt task)
    task))

;; Only happy path for the helper functions above.
(defn dispatch [images {:keys [error ids DocPath ToExchange ExchangePath Result Retry] :as task}]
  (let [s-agt (model/images->state-agent images task)]
    (cond->> task
       Result (store images)
       ids (refresh images)
       ToExchange (to-exch images)
       ExchangePath (to-exch images))
     (cond 
       Retry (sched/state-ready! s-agt task)
       error (sched/state-error! s-agt task)
       :default (sched/state-executed! s-agt task))))

(defn add [a m] (send a (fn [l] (conj l m))))

(defn error [a ex]
  (µ/log ::error-handler :error (str "error occured: " ex))
  (Thread/sleep 1000)
  (µ/log ::error-handler :error "try restart agent")
  (restart-agent a @a))

(defn up [{:keys [response-queqe]} images]
  (µ/log ::up :message "start up response queqe agent")
  (set-error-handler! response-queqe error)
  (add-watch response-queqe :queqe (fn [_ r-agt _ r-queqe]
                                     (when (seq r-queqe)
                                       (send r-agt (fn [l]
                                                     (dispatch images (first l))
                                                     (-> l rest)))))))

(defn down [[_ r-agt]]
  (µ/log ::down :message "shut down respons queqe agent")
  (remove-watch r-agt :queqe)
  (send r-agt (fn [_] [])))
