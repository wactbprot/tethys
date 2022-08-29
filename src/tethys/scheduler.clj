;; The tethys scheduler is organized by means of **whatch** functions.
(ns tethys.scheduler
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]))


(defn whatch-up [agts]
  (mapv
   (fn [a] (prn a))
   agts))

(defn whatch-down [agts]
  (mapv
   (fn [a] (prn a))
   agts))

