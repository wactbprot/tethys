(ns tethys.cli
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [tethys.model.core :as model]
            [tethys.core.scheduler :as sched]
            [tethys.system :as sys]
            [portal.api :as p])
   (:gen-class))

;; # open portal

;; See https://github.com/djblue/portal

(comment
  (def p (p/open))
  (add-tap #'p/submit)
  (tap> @(c-agent :mpd-ref 0))
  (p/close))

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
;;* `d-`... documents
;;* `r-`... response

;; The following functions are intended
;; for [REPL](https://clojure.org/guides/repl/introduction) usage.

;; ## Start, stop and restart system
;;
;; In order to get an overview of the active mpds use `(mpds)`.
(defn mpds [] (-> (sys/mpds) keys))

(defn start
  ([] (start nil))
  ([id-set]
   (sys/init id-set)
  (mpds)))

(defn stop [] (sys/stop))
(defn images [] (sys/images))
(defn image [mpd]
  {:pre  [(keyword? mpd)]}
  (-> (images) mpd))

;; ## Ctrl-interface

;; Get the `agent` of a certain container by `(c-agent mpd ndx)`
(defn c-agent [mpd ndx]
  {:pre  [(keyword? mpd)
          (int? ndx)]}
  (model/images->cont-agent (images) (struct model/state mpd :conts ndx)))


;; ## Start, stop container

(defn ctrl!
  "Set `op` at position `mpd` `ndx` by `sendìng it to the ctrl scheduler."
  [[mpd ndx] op]
  {:pre  [(keyword? mpd)
          (int? ndx)
          (keyword? op)]}
  (sched/ctrl! (model/images->state-agent (images) (struct model/state mpd :conts ndx)) op))

;; Sets the ctrl at the `pos`ition defined by `ndx` `mpd`

(comment
  (c-run :mpd-ref 0)
  (map :is (:state @(c-agent :mpd-ref 0))))

(defn c-run [& pos] (ctrl! pos :run))
(defn c-mon [& pos] (ctrl! pos :mon))
(defn c-stop [& pos] (ctrl! pos :stop))

;; ## Set container state

(defn- state!
  "Set `op` at position `mpd` `ndx` `sdx` `pdx` by `sendìng it to the
  state scheduler."
  [[mpd ndx sdx pdx] op]
  {:pre  [(keyword? mpd)
          (int? ndx)
          (int? sdx)
          (int? pdx)
          (keyword? op)]}
  (sched/state! (images) (struct model/state mpd :conts ndx sdx pdx) op))

;; Sets the state at the `pos`ition defined by `ndx`,`sdx`, `pdx`  of `mpd`

(comment
  (s-ready :mpd-ref 0 0 0))

(defn s-ready [& pos] (state! pos :ready))
(defn s-exec  [& pos] (state! pos :executed))
(defn s-work [& pos] (state! pos :working))
(defn s-error [& pos] (state! pos :error))

;; ## Tasks
(comment
  (def task {:TaskName "Common-wait"})
  (def build-task-fn (:mpd-ref (:model/task @sys/system))) 
  (build-task-fn task))

;; ## Worker
(comment
  (def task {:TaskName "Common-wait"
             :id :mpd-ref
             :ndx 0 :sdx 0 :pdx 0 :group :conts})
  (def spawn-work-fn (:mpd-ref (:model/worker @sys/system)))
  (spawn-work-fn (images) task))


;; ## Exchange interface
;;
;; Get the `agent` of a mpd by `(e-agent mpd)`
(defn e-agent [mpd] (model/images->exch-agent (images) (struct model/state mpd)))
(defn e-interface [mpd] @(e-agent mpd))
(defn e-error [mpd] (agent-error (e-agent mpd)))
(defn e-restart [mpd] (restart-agent (e-agent mpd)))

;; ## Documents
;;
;; Only the id of the documents (calibration docs, measurement docs)
;; are stored in a set (see `model`).
(defn d-add [mpd id] (model/add-doc-id (images) (struct model/state mpd) id))
(defn d-rm [mpd id] (model/rm-doc-id (images) (struct model/state mpd) id))
(defn d-rm-all [mpd] (model/rm-all-doc-ids (images) (struct model/state mpd)))
(defn d-refresh [mpd id-coll] (model/refresh-doc-ids (images) mpd id-coll))
(defn d-show [mpd] (model/doc-ids (images) (struct model/state mpd)))

(defn -main [] (start))
