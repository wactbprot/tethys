(ns tethys.core.docs
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.data.json :as json]
            [com.brunobonacci.mulog :as µ]
            [clj-http.client :as http]
            [tethys.core.model :as model]))


;; # Documents

;; The `docs` namespace cares about the database documents used to
;; store the data which is gained by the different worker.

(defn ids [images task]
  @(model/images->ids-agent images task))

(defn add [images task doc-id]
  (µ/log ::add :message (str "add " doc-id))
  (let [i-agt (model/images->ids-agent images task)] 
    (send i-agt (fn [coll] (conj coll doc-id)))
    (ids images task)))

(defn rm [images task doc-id]
  (µ/log ::rm :message (str "rm " doc-id))
  (let [i-agt (model/images->ids-agent images task)] 
    (send i-agt (fn [coll] (disj coll doc-id)))
    (ids images task)))

(defn rm-all [images task]
  (µ/log ::add :message  "rm all docs")
  (run! (fn [id] (rm images task id)) (ids images task))
  (ids images task))

(defn refresh [images task id-coll]
  (µ/log ::add :message  (str "refresh documents with collection: " id-coll))
  (rm-all images task)
  (run! (fn [id] (add images task id)) id-coll)
  (ids images task))


;; The data is stored via
;; the [vl-db-agent](https://github.com/wactbprot/vl-db-agent).
(defn url [{u :db-agent-url} doc-id] (str u "/" doc-id))

(defn req [{header :json-post-header} doc-path result]
  (assoc header :body (json/write-str {:Result result :DocPath doc-path})))

(defn store [images {:keys [DocPath Result] :as task}]
  (when-let [doc-ids (ids images task)]
    (µ/log ::store :message "initiate to store data")
    (let [request (req (model/images->conf images task) DocPath Result)]
      (mapv #(http/post (url %) request) doc-ids)))
  task)
    
