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

(defn url [{{:keys [prot host port] :as db} :db}]
  (str prot "://" host ":" port "/_replicate"))

(defn body [{:keys [SourceDB TargetDB] :as task}]
   {:source SourceDB
    :target TargetDB})

(defn req [{request :json-post-header {:keys [opt]} :db :as conf} task]
  (merge 
   (assoc request :body (json/write-str (body task)))
   opt))

(defn repli [images {:keys [pos-str] :as task}]
  (let [conf (model/images->conf images task)
        s-agt (model/images->state-agent images task)]
    (try
      (let [{:keys [status error]} (http/post (url conf) (req conf task))]
        (if (or (not error)
                (< status 400))
          (do
            (µ/log ::replicate :message "replication done" :pos-str pos-str)
            (sched/state-executed! s-agt task))
          (do
            (µ/log ::replicate :error (str "from dtatabase" (:error error)) :pos-str pos-str)
            (sched/state-error! s-agt task))))
      (catch Exception e
         (µ/log ::replicate :error (str "from clj-http" (.getMessage e)) :pos-str pos-str)
         (sched/state-error! s-agt task)))))
 
