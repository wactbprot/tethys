(ns tethys.core.docs
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.data.json :as json]
            [com.brunobonacci.mulog :as µ]
            [clj-http.client :as http]
            [tethys.core.model :as model]))

;; The data is stored via
;; the [vl-db-agent](https://github.com/wactbprot/vl-db-agent).
(defn url [{u :db-agent-url} doc-id] (str u "/" doc-id))

(defn req [{header :json-post-header} doc-path result]
  (assoc header :body (json/write-str {:Result result :DocPath doc-path})))

(defn store [images {:keys [DocPath Result] :as task}]
  (when-let [doc-ids (model/doc-ids images task)]
    (µ/log ::store :message "initiate to store data")
    (let [conf (model/images->conf images task)
          request (req conf DocPath Result)]
      (mapv #(http/post (url conf %) request) doc-ids)))
  task)
    
