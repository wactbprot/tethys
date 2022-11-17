(ns tethys.cli
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [tethys.db :as db]
            [tethys.system :as sys]
            [tethys.task :as task]))

;; # cli
;; This is the tethys **c**ommand **l**ine **i**nterface.
;; Some abbreviations:
;;
;;* `t-`... task
;;* `w-`... work
;;* `e-`... exchange
;;* `s-`... state
;;* `c-`... container

;; ## Start, stop and restart system
;;
;; In order to get an overview of the active mpds use `(mpds)`.
(defn mpds [] (keys (:db/mpds @sys/system)))

;; The following functions are intended
;; for [REPL](https://clojure.org/guides/repl/introduction) usage.
(defn start [id-set]
  (sys/init id-set)
  (mpds))

(defn stop [] (sys/stop))

;; ## Start, stop container
(defn ctrl [a op] (send a (fn [m] (assoc m :ctrl op))))


;; Get the `agent` of a certain container by `(c-agent mpd ndx)`
(defn c-agent [mpd ndx] (sys/cont-agent mpd ndx))

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

;; Get the `agent` of a mpd by `(e-agent mpd)`
(defn e-agent [mpd] (sys/exch-agent mpd))

;; The derefed e-agent looks like this (keys of the map are
;; keywords!):
(comment
  
  (deref (e-agent :mpd-ref))
  {:A {:Type "ref", :Unit "Pa", :Value 100.0},
   :B "token",
   :Target_pressure
   {:Selected 1,
    :Select [{:value 10.0} {:value 100.0} {:value 1000.0}],
    :Unit "Pa"},
   :Ref_gas
   {:Selected "N2",
    :Select
    [{:value "N2", :display "Stickstoff"}
     {:value "Ar", :display "Argon"}
     {:value "Ne", :display "Neon"}
     {:value "Kr", :display "Krypton"}
     {:value "Xe", :display "Xenon"}
     {:value "He", :display "Helium"}
     {:value "H2", :display "Wasserstoff"}],
    :Ready false},
   :id :mpd-ref})


;; ## Ctrl-interface
;;
;; setting Run, stop or mon a `cont`ainer `ndx` of
;; `mpd` with this functions.
(defn c-run [mpd ndx] (ctrl (c-agent mpd ndx) :run))
(defn c-mon [mpd ndx] (ctrl (c-agent mpd ndx) :mon))
(defn c-stop [mpd ndx] (ctrl (c-agent mpd ndx) :stop))

;; ## Set container state
;;
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
(defn s-ready [mpd ndx sdx pdx]
  (state! (c-agent mpd ndx) sdx pdx :ready))

(defn s-exec  [mpd ndx sdx pdx]
  (state! (c-agent mpd ndx) sdx pdx :executed))

(defn s-work [mpd ndx sdx pdx]
  (state! (c-agent mpd ndx) sdx pdx :working))

(defn s-error [mpd ndx sdx pdx]
  (state! (c-agent mpd ndx) sdx pdx :error))


;; ## Tasks
;; 
;; Occurring errors are detectaple with the `agent-error` function.
(defn t-agent [mpd] (mpd (:model/task @sys/system)))

(defn t-quequ [mpd] @(t-agent))

(defn t-error [mpd] (agent-error (t-agent mpd)))

(defn t-restart [mpd] (restart-agent (t-agent mpd) (t-queqe mpd)))

  ;; Get a task from the database and resole a `replace-map` by means
;; of [[t-resolve]]. An Example would be:
(comment
  (t-resolve "Common-wait" {"@waittime" 1000})
  {"Action" "wait",
   "Comment" "@waitfor  1000 ms",
   "TaskName" "Common-wait",
   "WaitTime" 1000}

  (t-resolve "PPC_MaxiGauge-ini" {"@CR" "\n"})
  {"TaskName" "PPC_MaxiGauge-ini",
   "Comment" "Initializes the safe gauge",
   "Action" "@acc",
   "Host" "@host",
   "Port" "@port",
   "Value" ["UNI,0\n" "" "PR1\n" ""]})

(defn t-resolve [task-name replace-map]
  (let [f (db/task-fn (:db/task @sys/system))]
    (task/assemble (f task-name) replace-map)))
  

;; ## Worker
(defn w-agent [mpd] (mpd (:model/worker @sys/system)))

(defn w-quequ [mpd] @(w-agent))

(defn w-error [mpd] (agent-error (w-agent mpd)))

(defn w-restart [mpd] (restart-agent (w-agent mpd) (w-queqe mpd)))
