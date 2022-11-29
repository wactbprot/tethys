(ns tethys.docs
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.data.json :as json]
            [com.brunobonacci.mulog :as µ]
            [clj-http.client :as http]
            [tethys.model :as model]))

;; # Documents
;;
;; The `docs` namespace cares about the database documents used to
;; store the data which is gained by the different worker.

(defn ids [images mpd]
  (let [i-agt (model/images->ids-agent)]
    (await i-agt)
    (deref i-agt)))

(defn add [images mpd doc-id]
  (µ/log ::add :message (str "add " doc-id " to " mpd))
  (let [i-agt (model/images->ids-agent)] 
    (send i-agt (fn [coll] (conj coll doc-id)))
    (ids images mpd)))

(defn rm [images mpd doc-id]
  (µ/log ::rm :message (str "rm " doc-id " from " mpd))
  (let [i-agt (model/images->ids-agent)] 
    (send i-agt (fn [coll] (disj coll doc-id)))
    (ids images mpd)))

(defn rm-all [images mpd]
  (µ/log ::add :message  "rm all docs")
  (run! (fn [id] (rm images mpd id)) (ids images mpd))
  (ids images mpd))

(defn refresh [images mpd id-coll]
  (µ/log ::add :message  (str "refresh documents with collection: " id-coll))
  (rm-all images mpd)
  (run! (fn [id] (add images mpd id)) id-coll)
  (ids images mpd))

;; The data is stored via
;; the [vl-db-agent](https://github.com/wactbprot/vl-db-agent).
(defn url [{u :db-agent-url} doc-id] (str u "/" doc-id))

(defn req [{header :json-post-header} doc-path result]
  (assoc header :body (json/write-str {:Result result :DocPath doc-path})))

(defn store [images {:keys [id DocPath Result] :as task}]
  (µ/log ::store :message "initiate to store data")
  (let [conf (model/images->conf images task)
        doc-ids (ids images task)
        request (req conf DocPath Result)]
    (mapv #(http/post (url %) request) doc-ids)))
    
