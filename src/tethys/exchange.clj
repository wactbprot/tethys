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


;; Builds a map by replacing the values of the input map `m`. The
;; replacements are gathered from `e-agt` the complete exchange
;;
(defn from [e-agt m]
  (when (and (map? @e-agt) (map? m))
    (into {} (mapv (fn [[k p]] {k (e-value e-agt p)}) m))))

(comment
  (def a (agent {:A {:Type "ref" :Unit "Pa" :Value 100.0},
                 :B "token"}))

  (from a {:%check "A"})
  {:%check {:Type "ref", :Unit "Pa", :Value 100.0}}
  (from a {:%check "A.Type"})
  {:%check "ref"}
  (from a {:%check "C"})
  {:%check nil})
