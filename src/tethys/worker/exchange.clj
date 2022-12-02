(ns tethys.worker.exchange
  ^{:author "wactbprot"
    :doc "The exchange worker writes and reads from the exchange interface."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.exchange :as exch]
            [tethys.model :as model]
            [tethys.scheduler :as sched]))

(defn write [images task]
  (let [e-agt (model/images->exch-agent images task)
        s-agt (model/images->state-agent images task)]
    (prn (exch/to e-agt task))
    
    (if (agent-error e-agt)
      (sched/state-error! s-agt task)
      (sched/state-ready! s-agt task))))
