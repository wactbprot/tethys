(ns tethys.exchange
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
    (:require [clojure.string :as str]))

(defn agent-up [id exch] (agent (assoc exch :id id))) 
  
(defn agent-down [[id a]] (send a (fn [_] {})))
