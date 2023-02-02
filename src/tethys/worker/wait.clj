(ns tethys.worker.wait
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The wait worker."}
  (:require [com.brunobonacci.mulog :as µ]))

(defn wait [images {:keys [WaitTime pos-str] :as task} continue-fn]
  (Thread/sleep (Integer. WaitTime))
  (µ/log ::wait :message "Waittime over" :pos-str pos-str)
  (continue-fn images task))
  
