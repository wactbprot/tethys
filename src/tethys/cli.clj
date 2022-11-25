(ns tethys.cli
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [tethys.db :as db]
            [tethys.model :as model]
            [tethys.system :as sys]
            [tethys.scheduler :as sched]
            [tethys.task :as task]))

;; # cli
;;
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

(defn image [mpd] (sys/mpd-image mpd))

;; ## Ctrl-interface
;;
;; setting Run, stop or mon a `cont`ainer `ndx` of
;; `mpd` with this functions.

;; Get the `agent` of a certain container by `(c-agent mpd ndx)`
(defn c-agent [mpd ndx] (model/image->cont-agent (sys/mpd-image mpd) ndx))

;; ## Start, stop container
(defn ctrl [a op] (sched/ctrl! a op))
(defn c-run [mpd ndx] (ctrl (c-agent mpd ndx) :run))
(defn c-mon [mpd ndx] (ctrl (c-agent mpd ndx) :mon))
(defn c-stop [mpd ndx] (ctrl (c-agent mpd ndx) :stop))

;; ## Set container state
;;
(defn- state! [a op sdx pdx]
  (sched/state! a op (struct model/state nil nil nil sdx pdx op)))

;; Sets the state at position `ndx`,`sdx`, `pdx`  of `mpd`
(defn s-ready [mpd ndx sdx pdx]
  (state! (c-agent mpd ndx) :ready sdx pdx))

(defn s-exec  [mpd ndx sdx pdx]
  (state! (c-agent mpd ndx) :executed sdx pdx))

(defn s-work [mpd ndx sdx pdx]
  (state! (c-agent mpd ndx) :working sdx pdx))

(defn s-error [mpd ndx sdx pdx]
  (state! (c-agent mpd ndx) :error sdx pdx))

;; ## Tasks
;; 
;; Occurring errors are detectaple with the `agent-error` function.
(defn t-agent [mpd] (model/image->task-agent (sys/mpd-image mpd)))
(defn t-queqe [mpd] @(t-agent mpd))
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

(defn t-resolve [task-name replace-map use-map]
  (let [f (db/task-fn (:db/task @sys/system))]
    (task/assemble (f task-name) replace-map use-map)))
  
;; ## Worker
(defn w-agent [mpd] (model/image->worker-agent (sys/mpd-image mpd)))
(defn w-queqe [mpd] @(w-agent mpd))
(defn w-error [mpd] (agent-error (w-agent mpd)))
(defn w-restart [mpd] (restart-agent (w-agent mpd) (w-queqe mpd)))


;; ## Exchange interface
;;
;; Get the `agent` of a mpd by `(e-agent mpd)`
(defn e-agent [mpd] (model/image->exch-agent (sys/mpd-image mpd)))
