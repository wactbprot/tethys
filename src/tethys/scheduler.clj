(ns tethys.scheduler
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]))

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

;; The `:ctrl` interface of a container is set to `:error` if a task
;; turns to `:error`
(defn ctrl-error! [a]
  (µ/log ::ctrl-error! :error "state error")
  (send a (fn [m] (assoc m :ctrl :error))))

;; If all tasks in a container are executed, the states are set back
;; to `:ready`.  If `ctrl`was `:run` it becomes `:ready` in order to
;; stop execution of the container tasks.
(defn all-ready! [a]  
  (µ/log ::all-ready! :message "set all states to :ready")
  (send a (fn [{:keys [state ctrl] :as m}]
            (assoc (assoc m :ctrl (if (= ctrl :run) :ready ctrl))
                   :state (set-all-pos-ready state)))))

;; Start next if the [[next-ready]] is first or all predecessors are
;; executed.

(defn is-first? [{i :sdx}] (zero? i))

(defn find-next [v]
  (let [{sdx :sdx :as m} (first (filterv (is-eq :ready) v))]
    (when (seq m)
      (when (or (is-first? m)
                (predec-exec? sdx v))
        m))))

(defn set-op-at-pos [op {:keys [state] :as n} {:keys [sdx pdx]}]
  (assoc n :state (mapv (fn [{s :sdx p :pdx :as m}]
                          (if (and (= s sdx) (= p pdx))
                            (assoc m :is op)
                            m))
                        state)))

;; `start-next!` sets the state agent `s-agt` to working and `conj` the
;; task `m` to the task-queqe `t-agt`
(defn start-next! [s-agt t-agt task]
  (when (seq task)
    (send s-agt (fn [m] (set-op-at-pos :working m task)))
    (send t-agt (fn [v] (conj v task)))))

;; The `up` function is called with two agents: `conts` is a vector of
;; the container state agents `s-agt` of a certain mpd and `t-agt` is the
;; related task-queqe agent.
(defn up [conts t-agt]
  (mapv #(add-watch % :sched (fn [_ s-agt _ {:keys [ctrl state]}]
                               (cond
                                 (and (not= :error ctrl)
                                      (error? state))  (ctrl-error! s-agt)
                                 (all-exec? state)     (all-ready! s-agt)
                                 (or (= :run ctrl)
                                     (= :mon ctrl))    (start-next! s-agt t-agt (find-next state))
                                 :nothing-todo-here true)))

        conts))
;; The shut down function first removes the whatch function and second
;; sets a empty map to all container agents.
(defn down [[_ conts]]
  (mapv (fn [a]
          (remove-watch a :sched)
          (send a (fn [_] {})))
        conts))

