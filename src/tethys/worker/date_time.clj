(ns tethys.worker.date-time
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The date and time worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [tethys.model.core :as model]
            [tethys.core.date-time :as dt]
            [tethys.core.response :as resp]
            [tethys.model.scheduler :as sched]))

;; Example for a `getTime` and a `getDate` task:
;; <pre>
;;  {
;;   "TaskName": "get_time",
;;   "Action": "getTime",
;;   "Comment": "Saves a timestamp (path: @docpath).",
;;   "DocPath": "@docpath",
;;   "ExchangePath": "@timepath",
;;   "Type": "@type"
;;   }
;; {
;;  "TaskName": "get_date",
;;  "Action": "getDate",
;;  "Comment": "Saves a date string (path: @docpath)",
;;  "DocPath": "@docpath",
;;  "Type": "@type"
;;  }
;; </pre>

(defn store-time [images {:keys [Type ExchangePath] :as task}]
  (let [r-agt (model/images->resp-agent images task)
        m {:Value (dt/get-time) :Type Type}]
    (resp/add r-agt (merge task {:Result [m]
                                 :Value m}))))

(defn store-date [images {:keys [Type] :as task}]
  (let [r-agt (model/images->resp-agent images task)
        m {:Value (dt/get-date) :Type Type}]
    (resp/add r-agt (merge task {:Result [m]}))))
