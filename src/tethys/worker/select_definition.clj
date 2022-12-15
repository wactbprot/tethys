(ns tethys.worker.select-definition
  ^{:author "wactbprot"
    :doc "The select definition werger worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.core.model :as model]
            [tethys.core.response :as resp]
            [tethys.core.scheduler :as sched]))

(defn select [images task]
  (let [r-agt (model/images->resp-agent images task)
        s-agt (model/images->state-agent images task)]

    (comment
      ;; add a watch fn that is called when selected definition is completed
      )))
