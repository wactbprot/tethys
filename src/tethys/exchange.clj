(ns tethys.exchange
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
    (:require [clojure.string :as str]))

(defn up [id exch] (agent (assoc exch :id id))) 
  
(defn down [[_ a]] (send a (fn [_] {})))
