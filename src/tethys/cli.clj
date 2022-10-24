(ns tethys.cli
  (:require [tethys.system :as sys]))

;; ## Start, stop and restart

;; In order to get an overview of the active mpds use `(mpds)`.
(defn mpds [] (keys (:model/cont @sys/system)))

;; The following functions are intended
;; for [REPL](https://clojure.org/guides/repl/introduction) usage.
(defn start [id-set]
  (sys/init id-set)
  (mpds))

(defn stop [] (sys/stop))

;; ## Start, stop container
(defn ctrl [a op] (send a (fn [m] (assoc m :ctrl op))))


;; Get the `agent` of a certain container by `(cont-agent mpd ndx)`
(defn cont-agent [mpd ndx] (sys/cont-agent mpd ndx))

;; This `agent` looks like this:
(comment
  {:ctrl :run
   :state
   [{:id :mpd-ppc-gas_dosing
     :group :cont
     :ndx 0
     :sdx 0
     :pdx 0
     :is :executed
     :task {:TaskName "PPC_MaxiGauge-ini"}}
    {:id :mpd-ppc-gas_dosing
     :group :cont
     :ndx 0
     :sdx 1
     :pdx 0
     :is :executed
     :task {:TaskName "PPC_DualGauge-ini"}}
    {:id :mpd-ppc-gas_dosing
     :group :cont
     :ndx 0
     :sdx 2
     :pdx 0
     :is :executed
     :task {:TaskName "PPC_VAT_DOSING_VALVE-ini"}}
    {:id :mpd-ppc-gas_dosing
     :group :cont
     :ndx 0
     :sdx 3
     :pdx 0
     :is :executed
     :task {:TaskName "PPC_Faulhaber_Servo-comp_ini"}}]})

;; Run `cont`ainer `ndx` of `mpd`
(defn run-cont [mpd ndx] (ctrl (cont-agent mpd ndx) :run))
(defn mon-cont [mpd ndx] (ctrl (cont-agent mpd ndx) :mon))
(defn stop-cont [mpd ndx] (ctrl (cont-agent mpd ndx) :stop))


;; ## Set container state
(defn state! [a sdx pdx op]
  (send a
        (fn [m] (assoc m
                      :state
                      (mapv (fn [{s :sdx p :pdx :as m}]
                              (if (and (= s sdx)
                                       (= p pdx))
                                (assoc m :is op)
                                m))
                            (:state m))))))


;; Sets the state at position `ndx`,`sdx`, `pdx`  of `mpd` 
(defn ready-state [mpd ndx sdx pdx]
  (state! (cont-agent mpd ndx) sdx pdx :ready))

(defn exec-state  [mpd ndx sdx pdx]
  (state! (cont-agent mpd ndx) sdx pdx :executed))

(defn work-state [mpd ndx sdx pdx]
  (state! (cont-agent mpd ndx) sdx pdx :working))

(defn error-state [mpd ndx sdx pdx]
  (state! (cont-agent mpd ndx) sdx pdx :error))
