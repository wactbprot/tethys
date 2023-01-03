(ns tethys.core.scheduler
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]))

;; ## State manipulation

(defn op-fn [op sdx pdx]
  (fn [{s :sdx p :pdx :as m}]
    (if (and (= s sdx) (= p pdx)) (assoc m :is op) m)))

(defn state! [a op {:keys [sdx pdx]}]
  (send a (fn [{:keys [state] :as n}] 
            (assoc n :state (mapv (op-fn op sdx pdx) state)))))

(defn state-executed! [a task] (state! a :executed task))
(defn state-ready! [a task] (state! a :ready task))
(defn state-error! [a task] (state! a :error task))
(defn state-working! [a task] (state! a :working task))

;; ## Scheduler
;;
;; The *Tethys* scheduler is driven by means
;; of [watch](https://clojuredocs.org/clojure.core/add-watch)
;; functions.

(defn is-eq [kw] (fn [m] (= (:is m) kw))) 

(defn all [kw v] (mapv (is-eq kw) v))

(defn error? [v] (not (every? false? (all :error v))))

(defn all-exec? [v] (every? true? (all :executed v)))

(defn predec-exec? [n v] (all-exec? (filterv (fn [{s :sdx}] (< s n)) v)))

(defn set-all-pos-ready [v] (mapv (fn [m] (assoc m :is :ready)) v))

(defn ctrl! [a op] (send a (fn [m] (assoc m :ctrl op))))
  
;; The `:ctrl` interface of a container is set to `:error` if a task
;; turns to `:error`
(defn ctrl-error! [a]
  (µ/log ::ctrl-error! :error "state error")
  (ctrl! a :error))

;; If all tasks in a container are executed, the states are set back
;; to `:ready`.  If `ctrl`was `:run` it becomes `:ready` in order to
;; stop execution of the container tasks.
(defn all-ready! [a]  
  (µ/log ::all-ready! :message "set all states to :ready")
  (send a (fn [{:keys [state ctrl] :as m}]
            (-> m
                (assoc :ctrl (if (= ctrl :run) :ready ctrl))
                (assoc :state (set-all-pos-ready state))))))

;; Start next if the next-ready:
;; `(first (filterv (is-eq :ready) v)`
;; is first or all predecessors are
;; executed.
(defn is-first? [{i :sdx}] (zero? i))

(defn find-next [v]
  (let [{sdx :sdx :as m} (first (filterv (is-eq :ready) v))]
    (when (seq m)
      (when (or (is-first? m) (predec-exec? sdx v))
        m))))

;; `start-next!` sets the state agent `s-agt` to working and `conj` the
;; task `m` to the task-queqe `tq`
(defn start-next! [s-agt tq task]
  (when (seq task)
    (send tq (fn [l]
               (state-working! s-agt task)
               (conj l task)))))

;; The `up` function is called with two agents: `conts` is a vector of
;; the container state agents `s-agt` of a certain mpd and `tq` is the
;; related task-queqe agent.
(defn up-whatch-fn [tq]
  (fn [a]
    (add-watch a :sched (fn [_ s-agt _ {:keys [ctrl state]}]
                          (cond
                            (and (not= :error ctrl)
                                 (error? state))  (ctrl-error! s-agt)
                            (all-exec? state)     (all-ready! s-agt)
                            (or (= :run ctrl)
                                (= :mon ctrl))    (start-next! s-agt tq (find-next state))
                            :nothing-todo-here true)))))

(defn up [{:keys [conts defins task-queqe]}]
  {:conts (mapv (up-whatch-fn task-queqe) conts)
   :defins (mapv (up-whatch-fn task-queqe) defins)})


;; The shut down function first removes the whatch function and second
;; sets a empty map to all container agents.
(defn down-whatch [a]
  (remove-watch a :sched)
  (send a (fn [_] {})))

(defn down [[_ {:keys [conts defins]}]]
  (mapv down-whatch conts)
  (mapv down-whatch defins))

