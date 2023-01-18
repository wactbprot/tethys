(ns tethys.worker.devproxy
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The devhub worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.core.model :as model]
            [tethys.core.response :as resp]
            [tethys.core.scheduler :as sched]))

;; Example for a `devproxy` (aka `anselm`) task:

;; <pre>
;; {
;;  "Action": "Anselm",
;;  "Comment": "Get maximum pressure for each dut branch in the current configuration.",
;;  "TaskName": "anselm_get_dut_max",
;;  "RequestPath": "dut_max",
;;  "FromExchange": {
;;                   "@target_pressure": "Target_pressure.Selected",
;;                   "@target_unit": "Target_pressure.Unit"
;;                   },
;;  "Value": {
;;            "DocPath": "Calibration.Measurement.Values.Position",
;;            "Target_pressure_value": "@target_pressure",
;;            "Target_pressure_unit": "@target_unit"
;;            }
;;  }
;; </pre>
(defn url [{u :dev-proxy-url} {p :RequestPath}] (str u "/" p))
(defn req [{header :json-post-header} {:keys [Value] :as task}] (assoc header :body (json/write-str Value)))

(defn devproxy [images {:keys [pos-str] :as task}]
  (let [conf (model/images->conf images task)
        r-agt (model/images->resp-agent images task)]
    (try
      (let [{:keys [status body error]} (http/post (url conf task) (req conf task))]
        (if (or (not error)
                (< status 400))
          (try
            (resp/add r-agt (merge task (json/read-str body :key-fn keyword)))
            (catch Exception e
              (µ/log ::devproxy :error (.getMessage e) :pos-str pos-str)
              (sched/state-error! images task)))
          (do
            (µ/log ::devproxy :error error :pos-str pos-str)
            (sched/state-error! images task))))
      (catch Exception e
        (µ/log ::devproxy :error (.getMessage e) :pos-str pos-str)
        (sched/state-error! images task)))))
