(ns tethys.scheduler
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.string :as string]))

(defn map->pos-str [{:keys [id group ndx sdx pdx]}]
  (string/join "/" [id group ndx sdx pdx]))
;; ## Scheduler
;;
;; The *Tethys* scheduler is driven by means
;; of [watch](https://clojuredocs.org/clojure.core/add-watch)
;; functions.

(defn is-eq [kw] (fn [m] (= (:is m) kw))) 

(defn all [kw v] (mapv (is-eq kw) v))

(defn next-ready [v] (first (filterv (is-eq :ready) v)))

(defn error? [v] (not (every? false? (all :error v))))

(defn all-exec? [v] (every? true? (all :executed v)))

(defn predec-exec? [n v] (all-exec? (filterv (fn [{s :sdx}] (< s n)) v)))

(defn reset [v] (mapv (fn [m] (assoc m :is :ready)) v))

;; The `:ctrl` interface of a container is set to `:error` if a task
;; turns to `:error`
(defn ctrl-error! [a]
  (µ/log ::ctrl-error! :error "state error")
  (send a (fn [m] (assoc m :ctrl :error))))

(defn all-ready! [a]  
  (µ/log ::all-ready! :message "set all states to :ready")
  (send a (fn [m] (assoc m :state (reset (:state m))))))

;; Start next if the [[next-ready]] is first or all predecessors are
;; executed.
(defn start-next! [a v]
  (let [m (when-let [{sdx :sdx :as m} (next-ready v)]
            (cond
              (zero? sdx) m
              (predec-exec? sdx v) m))]
    (µ/log ::start-next! :message (str "start task at: " (map->pos-str m)))))

  (defn whatch-fn! [key a os ns]
  (let [ctrl (:ctrl ns)
        state (:state ns)]
    (cond
      (and
       (not (= :error ctrl))
            (error? state))  (ctrl-error! a)
      (all-exec? state) (all-ready! a)
      (or 
       (= :run ctrl)
       (= :mon ctrl)) (start-next! a state)
      :nothing-todo-here true)))

(defn whatch-up [group-agents]
  (mapv
   (fn [a] (add-watch a :sched whatch-fn!))
   group-agents))

(defn whatch-down [[id group-agents]]
  (mapv
   (fn [a] (remove-watch a :sched))
   group-agents))

