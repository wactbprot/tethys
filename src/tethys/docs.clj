(ns tethys.docs
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.data.json :as json]
            [com.brunobonacci.mulog :as µ]
            [clj-http.client :as http]
            [tethys.model :as model]))

;; # Documents
;;
;; The `docs` namespace cares about the database documents used to
;; store the data, gained by the different worker.
(defn add [images mpd doc-id]
  (µ/log ::add :message (str "add " doc-id " to " mpd))
  (let [i-agt (-> images
                  (model/images->image mpd)
                  (model/image->ids-agent))] 
    (send i-agt (fn [coll] (conj coll doc-id)))))

(defn rm [images mpd doc-id]
  (µ/log ::rm :message (str "rm " doc-id " from " mpd))
  (let [i-agt (-> images
                  (model/images->image mpd)
                  (model/image->ids-agent))] 
    (send i-agt (fn [coll] (disj coll doc-id)))))

(defn ids [images mpd]
  (-> images
      (model/images->image mpd)
      (model/image->ids-agent)
      deref))

;; The data is stored via
;; the [vl-db-agent](https://github.com/wactbprot/vl-db-agent).
(defn url [{u :db-agent-url} doc-id] (str u "/" doc-id))

(defn store [images {:keys [id DocPath Result] :as task}]
  (µ/log ::store :message "initiate to store data")
  (let [conf (model/images->conf images task)
        doc-ids (ids images task)]))
