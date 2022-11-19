(ns tethys.worker
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.exchange :as exch]
            [tethys.scheduler :as sched]))

(defn dispatch [conts e-agt task]
  (prn "...."))

(defn state-agent [conts {:keys [id ndx] :as task}]
  (-> conts id (nth ndx)))

(defn check-precond-and-dispatch [conts e-agt task] 
  (let [stop-if-delay 1000
        s-agt (state-agent conts task)]
    (prn task)
    (if (exch/run-if e-agt task)
      (if (exch/only-if-not e-agt task)
        (dispatch conts e-agt task)
        (do
          (Thread/sleep stop-if-delay)
          (µ/log ::check-precond-and-dispatch :message "state set by only-if-not")
          (send s-agt (fn [m] (sched/set-op-at-pos :executed m task)))))
      (do
        (Thread/sleep stop-if-delay)
        (µ/log ::check-precond-and-dispatch :message "state set by run-if")
        (send s-agt (fn [m] (sched/set-op-at-pos :ready m task)))))))

(defn up [conts e-agt]
  (µ/log ::up :message "start up worker queqe agent")
  (let [a (agent '())
        w (fn [_ w-agt _ v]
            (when (seq v)
              (send w-agt (fn [v]
                            (future (check-precond-and-dispatch conts e-agt (first v)))
                            (-> v rest)))))]
    (set-error-handler! a (fn [a ex]
                                (µ/log ::error-handler :error (str "error occured: " ex))
                                (Thread/sleep 1000)
                                (restart-agent a @a)))
    (add-watch a :queqe w)))

(defn down [[_ w-agt]]
  (µ/log ::down :message "shut down worker queqe agent")
  (remove-watch w-agt :queqe)
  (send w-agt (fn [_] [])))

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
