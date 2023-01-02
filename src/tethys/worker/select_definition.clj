(ns tethys.worker.select-definition
  ^{:author "wactbprot"
    :doc "The select definition werger worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clj-http.client :as http]
            [tethys.core.exchange :as exch]
             [tethys.core.model :as model]
            [tethys.core.response :as resp]
            [tethys.core.scheduler :as sched]))

(defn defins-match-class [defins cls]
  )

    
;;  The `condition-match?`  Tests a single condition of the form defined in the `definitions`
;;  section.
;;
;;  Example:
;; <pre>
;;  (condition-match? 10 "gt" 1)
;;  ;; true
;; </pre>
(defn condition-match? [l m r]
  (condp = (keyword m)
    :eq (= l r)
    :le (<= (read-string (str l)) (read-string (str r)))
    :lt (<  (read-string (str l)) (read-string (str r)))
    :gt (>  (read-string (str l)) (read-string (str r)))
    :ge (>= (read-string (str l)) (read-string (str r)))))

(defn condition-ok? [e-agt {:keys [ExchangePath Methode Value] :as condition-map}]
  (condition-match? (exch/e-value e-agt ExchangePath) Methode Value))

(defn agent-match [defins e-agt cls]
  (reduce (fn [_ a]
            (when (every? true? (mapv (fn [m] (condition-ok? e-agt m)) (:cond @a)))
              (reduced a)))
          {} (filterv (fn [a] (= cls (:cls @a))) defins)))


(defn select [images {:keys [DefinitionClass id] :as task}]
  (let [s-agt (model/images->state-agent images task)
        e-agt (model/images->exch-agent images task)
        d-agt (agent-match (model/images->defins images task) e-agt DefinitionClass)]

    ;; add a watch fn that is called when selected definition is
    ;; completed
    
    (prn  d-agt)))
