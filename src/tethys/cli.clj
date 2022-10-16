(ns tethys.cli
  (:require [tethys.system :as sys]))

;; ## Start, stop and restart
;; The following functions are intended
;; for [REPL](https://clojure.org/guides/repl/introduction) usage.
(defn start [id-set] (keys (sys/init id-set)))

(defn stop [] (sys/stop))

;; In order to get an overview of the active mpds use `(mpds)`.
(defn mpds [] (keys (:model/cont @sys/system)))

;; ## Start, stop container
(defn ctrl [a op] (send a (fn [m] (assoc m :ctrl op))))

;; Run `cont`ainer `ndx` of `mpd`
(defn run-cont [mpd ndx] (ctrl (sys/cont-agent mpd ndx) :run))
(defn mon-cont [mpd ndx] (ctrl (sys/cont-agent mpd ndx) :mon))
(defn stop-cont [mpd ndx] (ctrl (sys/cont-agent mpd ndx) :stop))
