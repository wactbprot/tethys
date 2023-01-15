(ns tethys.cli
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [tethys.core.docs :as docs]
            [tethys.core.model :as model]
            [tethys.core.scheduler :as sched]
            [tethys.system :as sys]
            [portal.api :as p]))

;; # open portal

;; See https://github.com/djblue/portal
(def p (p/open))

(comment
   (tap> @(c-agent :mpd-ref 0)))

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

;; ## Start, stop and restart system
;;
;; In order to get an overview of the active mpds use `(mpds)`.
(defn mpds [] (-> @sys/system :db/mpds keys))

;; The following functions are intended
;; for [REPL](https://clojure.org/guides/repl/introduction) usage.
(defn start
  ([] (start nil))
  ([id-set]
   (add-tap #'p/submit)
   (sys/init id-set)
  (mpds)))

(defn stop []
  (p/close)
  (sys/stop))

(defn images [] (-> @sys/system :model/images))
(defn image [mpd] (-> (images) mpd))

;; ## Ctrl-interface

;; Get the `agent` of a certain container by `(c-agent mpd ndx)`
(defn c-agent [mpd ndx] (model/image->cont-agent (image mpd) ndx))


;; ## Start, stop container

(defn ctrl!
  "Set `op` at position `mpd` `ndx` by `sendìng it to the ctrl scheduler."
  [[mpd ndx] op]
  {:pre  [(keyword? mpd)
          (int? ndx)
          (keyword? op)]}
  (sched/ctrl! (c-agent mpd ndx) op))

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
  (let [a (c-agent mpd ndx)
        s (struct model/state mpd :cont ndx sdx pdx op)]
    (sched/state! a op s)))

;; Sets the state at the `pos`ition defined by `ndx`,`sdx`, `pdx`  of `mpd`

(comment
  (s-ready :mpd-ref 0 0 0))

(defn s-ready [& pos] (state! pos :ready))
(defn s-exec  [& pos] (state! pos :executed))
(defn s-work [& pos] (state! pos :working))
(defn s-error [& pos] (state! pos :error))

;; ## Tasks
;; 
;; Occurring errors are detectaple with the `agent-error` function.
(defn t-agent [mpd] (model/image->task-agent (image mpd)))
(defn t-queqe [mpd] @(t-agent mpd))
(defn t-error [mpd] (agent-error (t-agent mpd)))
(defn t-restart [mpd] (restart-agent (t-agent mpd) (t-queqe mpd)))

;; ## Worker
(defn w-agent [mpd] (model/image->worker-agent (image mpd)))
(defn w-future [mpd] (model/image->worker-futures (image mpd)))

(defn w-queqe [mpd] @(w-agent mpd))
(defn w-error [mpd] (agent-error (w-agent mpd)))
(defn w-restart [mpd] (restart-agent (w-agent mpd) (w-queqe mpd)))


;; ## Exchange interface
;;
;; Get the `agent` of a mpd by `(e-agent mpd)`
(defn e-agent [mpd] (model/image->exch-agent (image mpd)))
(defn e-interface [mpd] @(e-agent mpd))
(defn e-error [mpd] (agent-error (e-agent mpd)))
(defn e-restart [mpd] (restart-agent (e-agent mpd)))

;; ## Documents
;;
;; Only the id of the documents (calibration docs, measurement docs)
;; are stored in a set (see `model`and `docs`namespace).
(defn d-add [mpd id] (docs/add (images) {:id mpd} id))
(defn d-rm [mpd id] (docs/rm (images) {:id mpd} id))
(defn d-rm-all [mpd] (docs/rm-all (images) {:id mpd}))
(defn d-show [mpd] (docs/ids (images) {:id mpd}))
(defn d-refresh [mpd id-coll] (docs/refresh (images) mpd id-coll))

;; ## response queqe
;;
;; Get the `agent` of a mpd by `(r-agent mpd)`
(defn r-agent [mpd] (model/image->resp-agent (image mpd)))
(defn r-queqe [mpd] @(r-agent mpd))
(defn r-error [mpd] (agent-error (r-agent mpd)))

