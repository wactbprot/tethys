(ns tethys.scheduler
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]))

(defn is-eq [kw] (fn [m] (= (:is m) kw))) 

(defn all [kw v] (mapv (is-eq kw) v))

(defn error? [v] (not (every? false? (all :error v))))

(defn all-exec? [v] (every? true? (all :executed v)))


(defn whatch-fn! [key agt old-state new-state]
  (let [ctrl (:ctrl new-state)
        state (:state new-state)]
    (prn ".")
    (cond
      (error? state) (do
                       (µ/log ::whatch-fn! :error "state error")
                       (send agt (fn [m] (assoc m :ctrl :error)))))))

;; ## Scheduler
;; The *Tethys* scheduler is organized by means of **whatch** functions.
(defn whatch-up [group-agents]
  (mapv
   (fn [a]
     (add-watch a :sched whatch-fn!))
     group-agents))

(defn whatch-down [[id group-agents]]
  (mapv
   (fn [a]
     (remove-watch a :sched))
   group-agents))

