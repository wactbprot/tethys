(ns tethys.worker.message
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The wait worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.model.core :as model]))

;; Example for a `message` task:
;; <pre>
;; {
;;  "Action": "message",
;;  "Comment": "Writes a message to the container message channel.",
;;  "TaskName": "message",
;;  "Message": "@message"
;;  }

(defn watch-fn [images {:keys [pos-str] :as task} continue-fn]
  (fn [_ s-agt {o :message} {n :message}]
    (when (and (not= o n) (nil? n))
      (µ/log ::message :message "Message resolved" :pos-str pos-str)
      (remove-watch s-agt :message)
      (continue-fn images task))))

(defn message [images {:keys [pos-str] :as task} continue-fn]
  (let [s-agt (model/images->state-agent images task)]
    (model/add-message images task)
    (µ/log ::message :message "Message added" :pos-str pos-str)
    (add-watch s-agt :message (watch-fn images task continue-fn))))

(comment
  (model/rm-message (images) {:group :conts,
                              :ndx 7,
                              :TaskName "Common-message",
                              :pdx 0,
                              :sdx 0,
                              :id :mpd-ref,
                              :pos-str ":mpd-ref.:conts.7.0.0"}))
