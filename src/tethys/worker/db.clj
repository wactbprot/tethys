(ns tethys.worker.db
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.data.json :as json]
            [com.brunobonacci.mulog :as µ]
            [tethys.core.db :as db]
            [tethys.core.docs :as docs]
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

(defn replicate-db [images {:keys [pos-str SourceDB TargetDB] :as task}]
  (let [conf (model/images->conf images task)
        s-agt (model/images->state-agent images task)
        {error :error} (db/replicate-db {:source SourceDB :target TargetDB} (-> conf :db))]
    (if-not error
      (do
        (µ/log ::replicate :message "replication done" :pos-str pos-str)
        (sched/state-executed! s-agt task)
        {:ok true})
      (do
        (µ/log ::replicate :error (str "from database"  error) :pos-str pos-str)
        (sched/state-error! s-agt task)
        {:error true}))))


;; Example for a `gen-doc` task:
;; <pre>
;;{
;; "Action": "genDbDoc",
;; "Comment": "Generates a state document in which the results are stored.",
;; "TaskName": "gen_state_doc",
;; "Value": {"_id": "state-se3_@year@month@day",
;;           "State": {"Measurement": {"Date": [{
;;                                               "Type": "generated",
;;                                               "Value": "@year-@month-@day @hour:@minute:@second"
;;                                               }],
;;                                     "AuxValues": {},
;;                                     "Values": {}
;;                                     }
;;                     }
;;           }
;; }
;; </pre>

(defn gen-doc [images {:keys [Value pos-str] :as task}]
  (let [conf (model/images->conf images task)
        db (-> conf :db)
        id (-> Value :_id)
        s-agt (model/images->state-agent images task)]
    (when-not (db/doc-exist? id db)
      (db/put-doc Value db))
    (docs/add images task id)
    (µ/log ::gen-doc :message "document added" :pos-str pos-str)
    (sched/state-executed! s-agt task)
    {:ok true}))

(defn rm-docs [images {:keys [pos-str] :as task}]
  (let [s-agt (model/images->state-agent images task)]
    (docs/rm-all images task)
    (µ/log ::rm-docs :message "all document ids removed form ids interface" :pos-str pos-str)
    (sched/state-executed! s-agt task)))
