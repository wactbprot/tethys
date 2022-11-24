(ns tethys.worker.wait
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.model :as model]
            [tethys.scheduler :as sched]))

(defn wait [images {:keys [WaitTime] :as task}]
  (Thread/sleep WaitTime)
  (sched/state-executed! (model/images->state-agent images task) task))
  
