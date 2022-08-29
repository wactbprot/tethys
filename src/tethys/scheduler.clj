(ns tethys.scheduler
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]))

;; ## Scheduler
;; The *Tethys* scheduler is organized by means of **whatch** functions.
(defn whatch-up [group-agents]
  (mapv
   (fn [a]
     (add-watch a :sched (fn [key agt old-state new-state]
                           (prn new-state))))
     group-agents))

(defn whatch-down [[id group-agents]]
  (mapv
   (fn [a]
     (remove-watch a :sched))
   group-agents))

