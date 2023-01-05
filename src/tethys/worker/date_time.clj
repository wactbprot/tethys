(ns tethys.worker.date-time
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The date and time worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [tethys.core.model :as model]
            [tethys.core.date-time :as dt]
            [tethys.core.response :as resp]
            [tethys.core.scheduler :as sched]))

(defn store-time [images {:keys [Type ExchangePath] :as task}]
  (let [r-agt (model/images->resp-agent images task)
        m {:Value (dt/get-time) :Type Type}]
    (resp/add r-agt (merge task {:Result [m]
                                 :ToExchange {ExchangePath m}}))))

(defn store-date [images {:keys [Type ExchangePath] :as task}]
  (let [r-agt (model/images->resp-agent images task)
        m {:Value (dt/get-date) :Type Type}]
    (resp/add r-agt (merge task {:Result [m]}))))
