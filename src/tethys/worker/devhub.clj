(ns tethys.worker.devhub
  ^{:author "wactbprot"
    :doc "The devhub worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.model :as model]))

(defn url [{u :dev-hub-url}] u)
(defn req [{header :json-post-header} task] (assoc header :body (json/write-str task)))

(defn devhub [images {:keys [id] :as task}]
  (let [image (model/images->state-agent images task)
        conf  (model/image->conf image)]
    (try
      (prn (http/post (url conf) (req conf task)))
      (catch Exception e
        (µ/log ::devhub :message (.getMessage e))))))
