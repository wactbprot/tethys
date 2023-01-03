(ns tethys.worker.exchange
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The exchange worker writes and reads from the exchange interface."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.core.exchange :as exch]
            [tethys.core.model :as model]
            [tethys.core.scheduler :as sched]))

(defn write [images task]
  (let [e-agt (model/images->exch-agent images task)
        s-agt (model/images->state-agent images task)]
    (exch/to e-agt task)
    (if-let[ex (agent-error e-agt)]
      (do
        (µ/log ::write :error (str "error occured: " ex))
        (sched/state-error! s-agt task))
      (sched/state-executed! s-agt task))))
