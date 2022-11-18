(ns tethys.worker
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.exchange :as exch]
            [tethys.scheduler :as sched]))

(defn dispatch [conts e-agt task]
  (prn task))

(defn state-agent [{:keys [id ndx]} conts]
  (-> conts id (nth ndx)))

(defn check-precond-and-dispatch [conts e-agt task] 
  (let [stop-if-delay 1000
        s-agt (state-agent task conts)]
    (if (exch/run-if e-agt task)
      (if (exch/only-if-not e-agt task)
        (dispatch task conts e-agt)
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
  (let [a (agent [])
        w (fn [_ a _ v]
            (when (seq v)
              (future (check-precond-and-dispatch conts e-agt (first v)))
              (send a (fn [v] (-> v rest vec)))))]
    (set-error-handler! a (fn [a ex]
                            (µ/log ::error-handler :error (str "error occured: " ex))
                            (Thread/sleep 1000)
                            (restart-agent a @a)))
    (add-watch a :queqe w)))

(defn down [[_ a]]
  (µ/log ::down :message "shut down worker queqe agent")
  (remove-watch a :queqe)
  (send a (fn [_] [])))
