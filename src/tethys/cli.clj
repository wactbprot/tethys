(ns tethys.cli
  (:require [tethys.system :as sys]))

;; ## Start, stop and restart
;; The following functions are intended
;; for [REPL](https://clojure.org/guides/repl/introduction) usage.
(defn sys-start [id-set] (keys (sys/init id-set)))

(defn sys-stop [] (sys/stop))

(defn sys-restart []
  (stop)
  (Thread/sleep 500)
  (start))

;; In order to get an overview of the active mpds use `(mpds)`.
(defn mpds [] (keys (:model/cont @sys/system)))

;; ## Start, stop container

(defn cont-run [mpd ndx]
  (let [a (sys/cont-agent mpd ndx)  
        f (fn [m] (assoc m :ctrl :run))]
    (send a f)))
