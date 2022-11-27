(ns tethys.worker
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.exchange :as exch]
            [tethys.model :as model]
            [tethys.scheduler :as sched]
            [tethys.worker.devhub :as devhub]
            [tethys.worker.wait :as wait]))

(defn dispatch [images {:keys [Action] :as task}]
  (future (case (keyword Action)
    :wait (wait/wait images task)
    :TCP (devhub/devhub images task)
    :VXI11 (devhub/devhub images task)
    (µ/log ::dispatch :error "no matching case"))))

(defn check [images task] 
  (let [stop-if-delay 1000
        s-agt (model/images->state-agent images task)
        e-agt (model/images->exch-agent images task)]    
    (if (exch/run-if e-agt task)
      (if (exch/only-if-not e-agt task)
        (dispatch images task)
        (do
          (Thread/sleep stop-if-delay)
          (µ/log ::check :message "state set by only-if-not")
          (sched/state-executed! s-agt task)))
      (do
        (Thread/sleep stop-if-delay)
        (µ/log ::check :message "state set by run-if")
        (sched/state-ready! s-agt task)))))

(defn up [{:keys [worker-queqe]} images]
  (µ/log ::up :message "start up worker queqe agent")
  (let [w (fn [_ wq _ _]
            (send wq (fn [l]
                       (when (seq l)
                         (check images (first l))
                         (-> l rest)))))]
    (set-error-handler! worker-queqe (fn [a ex]
                                       (µ/log ::error-handler :error (str "error occured: " ex))
                                       (Thread/sleep 1000)
                                       (µ/log ::error-handler :message "try to restart agent")
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
