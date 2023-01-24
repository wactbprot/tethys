(ns tethys.worker.exchange
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The exchange worker writes and reads from the exchange interface."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.exchange :as exch]
            [tethys.model.core :as model]
            [tethys.core.response :as resp]
            [tethys.core.scheduler :as sched]))


;; Example for a `writeExchange`task:

;; <pre>
;; {
;;  "Action": "writeExchange",
;;  "Comment": "Add a value to the Exchange api",
;;  "TaskName": "exchange_value",
;;  "ExchangePath": "@exchpath",
;;  "Value": "@value"
;;  }
;; </pre>

(defn write-exchange [images task]
  (let [e-agt (model/images->exch-agent images task)]
    (exch/to e-agt task)
    (sched/state-executed! images task)
    {:ok true}))

;; Example for a `readExchange`task:

;; <pre>
;; {
;; "Action": "readExchange",
;; "Comment": "Reads values under the given exchange path.",
;; "TaskName": "simple_read_element",
;; "DocPath": "@docpath",
;; "ExchangePath": "@exchpath"
;; }
;; </pre>

(defn read-exchange [images {:keys [ExchangePath] :as task}]
  (let [e-agt (model/images->exch-agent images task)
        r-agt (model/images->resp-agent images task)]
    (if-let [value (exch/e-value e-agt ExchangePath)]
      (do
        (resp/add r-agt (merge task {:Result [value]})))
      (do
        (µ/log ::read :error (str "No value found at exchange path " ExchangePath))
        (sched/state-error! images task)))))
        
