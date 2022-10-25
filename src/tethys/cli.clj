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

;; Get the `agent` of a mpd by `(exch-agent mpd)`
(defn exch-agent [mpd] (sys/exch-agent mpd))

;; ## Ctrl-interface setting Run, stop or mon a `cont`ainer `ndx` of
;; `mpd` with this functions.
(defn cont-run [mpd ndx] (ctrl (cont-agent mpd ndx) :run))
(defn cont-mon [mpd ndx] (ctrl (cont-agent mpd ndx) :mon))
(defn cont-stop [mpd ndx] (ctrl (cont-agent mpd ndx) :stop))

;; ## Set container state
;; The function `set-all-pos-ready` allows the direct setting of `:is`
;; verbs for a given state.
(defn- set-at-pos [state sdx pdx op]
  (mapv (fn [{s :sdx p :pdx :as m}]
          (if (and (= s sdx) (= p pdx))
            (assoc m :is op)
            m))
        state))


(defn- state! [a sdx pdx op]
  (send a (fn [{:keys [state] :as m}]
            (assoc m :state (set-at-pos state sdx pdx op)))))

;; Sets the state at position `ndx`,`sdx`, `pdx`  of `mpd` 
(defn state-ready [mpd ndx sdx pdx]
  (state! (cont-agent mpd ndx) sdx pdx :ready))

(defn state-exec  [mpd ndx sdx pdx]
  (state! (cont-agent mpd ndx) sdx pdx :executed))

(defn state-work [mpd ndx sdx pdx]
  (state! (cont-agent mpd ndx) sdx pdx :working))

(defn state-error [mpd ndx sdx pdx]
  (state! (cont-agent mpd ndx) sdx pdx :error))
