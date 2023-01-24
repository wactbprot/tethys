(ns tethys.worker.wait
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The wait worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.scheduler :as sched]))

(defn wait [images {:keys [WaitTime pos-str] :as task}]
  (Thread/sleep (Integer. WaitTime))
  (µ/log ::wait :message "Waittime over" :pos-str pos-str)
  (sched/state-executed! images task))
  
