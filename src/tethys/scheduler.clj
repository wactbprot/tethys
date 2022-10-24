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

(defn start-next! [a m]
  (when (seq m)
    (send a (fn [n] (assoc n :start m)))))

(defn whatch-fn! [_ a _ {:keys [ctrl state]}]
  (cond
    (and (not= :error ctrl) (error? state))  (ctrl-error! a)
    (all-exec? state)                        (all-ready! a)
    (or (= :run ctrl) (= :mon ctrl))         (start-next! a (find-next state))
    :nothing-todo-here true))

(defn whatch-up [as] (mapv #(add-watch % :sched whatch-fn!) as))
(defn whatch-down [[_ as]] (mapv #(remove-watch % :sched) as))

