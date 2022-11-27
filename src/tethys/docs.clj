(ns tethys.docs
   ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.model :as model]))

;; # Documents
;;
;; The `docs` namespace cares about the database documents used to
;; store the data, gained by the different worker.
(defn add [images task doc-id]
  (send (model/images->ids-agent images task) (fn [coll] (conj coll doc-id))))

(defn rm [images task doc-id]
  (send (model/images->ids-agent images task) (fn [coll] (disj coll doc-id))))

(defn ids [images task] @(model/images->ids-agent images task))

;; The data is stored via
;; the [vl-db-agent](https://github.com/wactbprot/vl-db-agent).
(defn url [{u :db-agent-url} doc-id] (str u "/" doc-id))

(defn store [images {:keys [id DocPath Result]}]
  (let [conf (model/images->conf image task)
        doc-ids (ids images task)]))
