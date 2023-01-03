(ns tethys.worker.wait
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The wait worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.model :as model]
            [tethys.core.scheduler :as sched]))

(defn wait [images {:keys [WaitTime] :as task}]
  (Thread/sleep (Integer. WaitTime))
  (µ/log ::wait :message "Waittime over")
  (sched/state-executed! (model/images->state-agent images task) task))
  
