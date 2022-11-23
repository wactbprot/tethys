(ns tethys.worker
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.exchange :as exch]
            [tethys.model :as model]
            [tethys.scheduler :as sched]))

(defn dispatch [images task]
  (prn task))

(defn images->image [images {:keys [id]}] (-> id keyword images))

(defn state-agent [images {:keys [id ndx group] :as task}]
  (model/state-agent (images->image images task) ndx group))

(defn exch-agent [images task]
  (model/exch-agent (images->image images task)))

(defn check-precond-and-dispatch [images task] 
  (let [stop-if-delay 1000
        s-agt (state-agent images task)
        e-agt (exch-agent images task)]    
    (if (exch/run-if e-agt task)
      (if (exch/only-if-not e-agt task)
        (dispatch images task)
        (do
          (Thread/sleep stop-if-delay)
          (µ/log ::check-precond-and-dispatch :message "state set by only-if-not")
          (send s-agt (fn [m] (sched/set-op-at-pos :executed m task)))))
      (do
        (Thread/sleep stop-if-delay)
        (µ/log ::check-precond-and-dispatch :message "state set by run-if")
        (send s-agt (fn [m] (sched/set-op-at-pos :ready m task)))))))

(defn up [{:keys [worker-queqe]} images]
  (µ/log ::up :message "start up worker queqe agent")
  (let [w (fn [_ wq _ _]
            (send wq (fn [l]
                          (when (seq l)
                            (check-precond-and-dispatch images (first l))
                            (-> l rest)))))]
    (set-error-handler! worker-queqe (fn [a ex]
                            (µ/log ::error-handler :error (str "error occured: " ex))
                            (Thread/sleep 1000)
                            (restart-agent a @a)))
    (add-watch worker-queqe :queqe w)))

(defn down [[_ wq]]
  (µ/log ::down :message "shut down worker queqe agent")
  (remove-watch wq :queqe)
  (send wq (fn [_] [])))

(comment
  ;; the futures should be kept with a hash key:
  (format "%x" (hash  {:TaskName "Common-wai",
                       :Replace {:%waittime 2000},
                       :id :mpd-ref,
                       :group :cont,
                       :ndx 0,
                       :sdx 2,
                       :pdx 0,
                       :is :ready}))
  ;; =>
  "6092736d")
