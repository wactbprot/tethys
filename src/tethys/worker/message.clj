(ns tethys.worker.message
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The wait worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.model :as model]
            [tethys.core.scheduler :as sched]))

;; Example for a `message` task:
;; <pre>
;; {
;;  "Action": "message",
;;  "Comment": "Writes a message to the container message channel.",
;;  "TaskName": "message",
;;  "Message": "@message"
;;  }

(defn watch-fn [images {:keys [pos-str] :as task}]
  (fn [_ s-agt {o :message} {n :message}]
    (when (and (not= o n) (nil? n))
      (µ/log ::message :message "Message resolved" :pos-str pos-str)
      (sched/state-executed! images task)
      (remove-watch s-agt :message))))

(defn message [images {:keys [pos-str] :as task}]
  (let [s-agt (model/images->state-agent images task)]
    (model/add-message images task)
    (µ/log ::message :message "Message added" :pos-str pos-str)
    (add-watch s-agt :message (watch-fn images task))))

(comment
  (model/rm-message (images) {:group :conts,
                              :ndx 7,
                              :TaskName "Common-message",
                              :pdx 0,
                              :sdx 0,
                              :id :mpd-ref,
                              :pos-str ":mpd-ref.:conts.7.0.0"}))
