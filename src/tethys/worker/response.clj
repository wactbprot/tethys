(ns tethys.worker.response
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.docs :as docs]
            [tethys.core.exchange :as exch]
            [tethys.model.core :as model]
            [tethys.core.scheduler :as sched]))

;; The `id` is the mpd id comming from the task; `ids` are the doc-ids
;; comming from the response (devproxy).
(defn refresh [images {:keys [id ids pos-str] :as task}]
  (µ/log ::refresh :message "trigger ids refresh" :pos-str pos-str)
  (model/refresh-doc-ids images task ids)
  task)

(defn store [images {:keys [pos-str] :as task}]
  (µ/log ::refresh :message "trigger store results" :pos-str pos-str)
  (docs/store images task)
  task)

(defn to-exch [images {:keys [pos-str] :as task}]
  (µ/log ::refresh :message "write data to exchange interface" :pos-str pos-str)
  (let [e-agt (model/images->exch-agent images task)]
    (exch/to e-agt task)
    task))

;; Only happy path for the helper functions above.
(defn dispatch [images {:keys [error ids DocPath ToExchange ExchangePath Result Retry] :as task}]
   (cond->> task
    Result (store images)
    ids (refresh images)
    ToExchange (to-exch images)
    ExchangePath (to-exch images))
  (let [e-agt (model/images->exch-agent images task)]
    (cond 
      Retry
      (sched/state-ready! images task)

      error
      (sched/state-error! images task)

      (not (exch/stop-if e-agt task))
      (sched/state-ready! images task)

      :default
      (sched/state-executed! images task))))
