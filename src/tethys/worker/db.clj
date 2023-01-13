(ns tethys.worker.db
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.data.json :as json]
            [com.brunobonacci.mulog :as µ]
            [tethys.core.db :as db]
            [clj-http.client :as http]
            [tethys.core.model :as model]
            [tethys.core.scheduler :as sched]))


;; Example for a `replicate` task:
;; <pre>
;; {
;;  "Action": "replicateDB",
;;  "Comment": "Replicates from the source db: @sourcedb to the target db: @targetdb.",
;;  "SourceDB": "vl_db_work",
;;  "TargetDB": "vl_db",
;;  "TaskName": "db_replicate",
;;  "ExchangePath": "Replication_Result"
;;  }
;; </pre>

(defn repli [images {:keys [pos-str SourceDB TargetDB] :as task}]
  (let [conf (model/images->conf images task)
        s-agt (model/images->state-agent images task)
        {error :error} (db/replicate-db {:source SourceDB :target TargetDB} (:db conf))]
    (if-not error
      (do
        (µ/log ::replicate :message "replication done" :pos-str pos-str)
        (sched/state-executed! s-agt task))
      (do
        (µ/log ::replicate :error (str "from dtatabase" (:error error)) :pos-str pos-str)
        (sched/state-error! s-agt task))))) 
