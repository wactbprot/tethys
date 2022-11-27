(ns tethys.worker.devhub
  ^{:author "wactbprot"
    :doc "The devhub worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.model :as model]
            [tethys.scheduler :as sched]))

(defn url [{u :dev-hub-url}] u)
(defn req [{header :json-post-header} task] (assoc header :body (json/write-str task)))

;; ToDo: merge the result into the task in order to ensure that the
;; position gets not lost.
(defn devhub [images task]
  (let [conf (model/images->conf images task)]
    (try
      (prn (http/post (url conf) (req conf task)))
      (catch Exception e
        (µ/log ::devhub :message (.getMessage e))
        (sched/state-error! (model/images->state-agent images task) task)))))
