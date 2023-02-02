(ns tethys.worker.devhub
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The devhub worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.model.core :as model]))

(defn url [{u :dev-hub-url}] u)
(defn req [{header :json-post-header} task] (assoc header :body (json/write-str task)))

;; ToDo: merge the result into the task in order to ensure that the
;; position gets not lost. Done: <*>
(defn devhub [images task continue-fn]
  (let [conf (model/images->conf images task)]
    (try
      (let [{:keys [status body error]} (http/post (url conf) (req conf task))]
        (if (or (not error)
                (< status 400))
          (try
            (continue-fn images (merge task (json/read-str body :key-fn keyword))) ; <*>
            (catch Exception e
              (µ/log ::devhub :error (.getMessage e))
              (continue-fn images (assoc task :error (.getMessage e)))))
          (do
            (µ/log ::devhub :error error)
            (continue-fn images (assoc task :error error)))))
      (catch Exception e
        (µ/log ::devhub :error (.getMessage e))
        (continue-fn images (assoc task :error (.getMessage e)))))))
