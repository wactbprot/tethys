(ns tethys.worker.devhub
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The devhub worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.core.model :as model]
            [tethys.core.response :as resp]
            [tethys.core.scheduler :as sched]))

(defn url [{u :dev-hub-url}] u)
(defn req [{header :json-post-header} task] (assoc header :body (json/write-str task)))

;; ToDo: merge the result into the task in order to ensure that the
;; position gets not lost. Done: <*>
(defn devhub [images task]
  (let [conf (model/images->conf images task)
        r-agt (model/images->resp-agent images task)
        s-agt (model/images->state-agent images task)]
    (try
      (let [{:keys [status body error]} (http/post (url conf) (req conf task))]
        (if (or (not error)
                (< status 400))
          (try
            (resp/add r-agt (merge task (json/read-str body :key-fn keyword))) ; <*>
            (catch Exception e
              (µ/log ::devhub :error (.getMessage e))
              (sched/state-error! s-agt task)))
          (do
            (µ/log ::devhub :error error)
            (sched/state-error! s-agt task))))
      (catch Exception e
        (µ/log ::devhub :error (.getMessage e))
        (sched/state-error! s-agt task)))))
