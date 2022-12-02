(ns tethys.exchange
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [clojure.string :as string]))

;; The function `e-value` returns the value of `@e-agt` under the path
;; `p`.
(defn e-value [e-agt p]
  (let [v (mapv keyword (string/split p #"\."))]
    (get-in @e-agt v)))
    
;; Checks a certain exchange endpoint to evaluate to true.
(defn ok? [a p] (contains? #{"ok" :ok "true" true "yo!"} (e-value a p)))

(defn exists? [a p] (some? (e-value a p)))

;; Checks if the exchange `p`ath given with `:StopIf` evaluates to true.
(defn stop-if [e-agt {p :StopIf}] (if p (ok? e-agt p) true))

;; Checks if the exchange `p`ath given with `:RunIf` evaluates to true.
(defn run-if [e-agt {p :RunIf}] (if p (ok? e-agt p) true))

;; Runs the task `only-if-not` the exchange `p`ath given with
;; `:OnlyIfNot` evaluates to `true`.
(defn only-if-not [e-agt {p :OnlyIfNot}]
  (cond
    (nil? p) true
    (not (exists? e-agt p)) false
    (not (ok? e-agt p)) true
    (ok? e-agt p) false))


;; ## Read from Exchange
;;
;; Builds a map by replacing the values of the input map `FromExchange`. The
;; replacements are gathered from `e-agt` the complete exchange
;;
(defn from [e-agt {:keys [FromExchange] :as task}]
  (when (and (map? @e-agt) (map? FromExchange))
    (into {} (mapv (fn [[k p]] {k (e-value e-agt p)}) FromExchange))))

(comment
  (def a (agent {:A {:Type "ref" :Unit "Pa" :Value 100.0},
                 :B "token"}))

  (from a {:FromExchange {:%check "A"}})
  {:%check {:Type "ref", :Unit "Pa", :Value 100.0}}
  (from a {:FromExchange {:%check "A.Type"}})
  {:%check "ref"}
  (from a {:FromExchange {:%check "C"}})
  {:%check nil})

;; ## Write to Exchange
;;
;; There are two mechanisms which enable writing to the exchange interface:
;;
;; * `ToExchange` 
;; * `ExchangePath` and `Value`
;;
;; TODO: maybe the value has to be treated (e.g. ensure keyword keys)
(defn path-value->map [path value]
  (when (string? path)
    (let [v (mapv keyword (string/split path #"\."))]
      (assoc-in {} v value))))


(defn to [e-agt {:keys [ToExchange ExchangePath Value] :as task}]
  (send e-agt (fn [m]
                (-> m
                    (merge ToExchange)
                    (merge (path-value->map ExchangePath Value))))))

(comment
  (def t {:ToExchange {:Filling_Pressure_current {:Value 100 :Unit
                                                  "mbar"}
                       :Filling_Pressure_Dev {:Value 0.5
                                              :Unit "1"}
                       :Filling_Pressure_Ok {:Ready false}}}))
