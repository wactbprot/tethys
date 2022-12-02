(ns tethys.worker
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.exchange :as exch]
            [tethys.model :as model]
            [tethys.scheduler :as sched]
            [tethys.worker.devhub :as devhub]
            [tethys.worker.exchange :as exchange]
            [tethys.worker.wait :as wait]))

(defn dispatch [images {:keys [Action] :as task}]
  (case (keyword Action)
     :wait (wait/wait images task)
     :TCP (devhub/devhub images task)
     :VXI11 (devhub/devhub images task)
     :writeExchange (exchange/write images task)
    (µ/log ::dispatch :error "no matching case")))

(defn check [images task] 
  (let [stop-if-delay 1000
        s-agt (model/images->state-agent images task)
        e-agt (model/images->exch-agent images task)]    
    (if (exch/run-if e-agt task)
      (if (exch/only-if-not e-agt task)
        (do
          (dispatch images task))
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
  (let [w (fn [_ w-agt _ wq]
            (when (seq wq)
              (future (check images (first wq)))
              (send w-agt (fn [l] (-> l rest)))))]
    (add-watch worker-queqe :queqe w))
  (set-error-handler! worker-queqe model/error))

(defn down [[_ w-agt]]
  (µ/log ::down :message "shut down worker queqe agent")
  (remove-watch w-agt :queqe)
  (send w-agt (fn [_] '())))

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
