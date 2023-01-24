(ns tethys.worker.ctrl-definition
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The select definition worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.exchange :as exch]
            [tethys.model.core :as model]
            [tethys.core.scheduler :as sched]))


(defn is? [f l r] (f (read-string (str l)) (read-string (str r))))

(defn condition-ok? [f l r]
  (condp = (keyword f)
    :eq (is? = l r)
    :le (is? <= l r)
    :lt (is? <  l r)
    :gt (is? >  l r)
    :ge (is? >= l r)))

(defn conditions-ok? [e-agt condition-maps]
  (mapv (fn [{:keys [ExchangePath Methode Value]}]
          (condition-ok? Methode (exch/e-value e-agt ExchangePath) Value))
        condition-maps))

(defn agent-match [defins e-agt cls]
  (reduce (fn [_ a]
            (when (every? true? (conditions-ok? e-agt (:cond @a)))
              (reduced a)))
          {} (filterv (fn [a] (= cls (:cls @a))) defins)))

(defn to-error? [o n] (and (= o :run) (= n :error)))
(defn to-ready? [o n] (and (= o :run) (= n :ready)))

(defn act-error! [agt-to-run images task]
  (µ/log ::select :error "selected definition returns with error")
  (sched/state-error! images task)
  (remove-watch agt-to-run :observer))

(defn act-ready! [agt-to-run images task]
  (µ/log ::select :message "selected definition executed")
  (sched/state-executed! images task)
  (remove-watch agt-to-run :observer))

(defn watch-fn [agt-to-run images task]
  (fn [_ _ {o :ctrl} {n :ctrl}]
    (cond
      (to-error? o n) (act-error! agt-to-run images task)
      (to-ready? o n) (act-ready! agt-to-run images task)
      :default :noop)))
  
(defn select [images {:keys [DefinitionClass id pos-str] :as task}]
  (let [e-agt (model/images->exch-agent images task)]
    (if-let [agt-to-run (agent-match (model/images->defins images task) e-agt DefinitionClass)]
      (do
        (µ/log ::select :message (str "found definition for class:" DefinitionClass) :pos-str pos-str)
        (add-watch agt-to-run :observer (watch-fn agt-to-run images task))
        (sched/ctrl-run! agt-to-run))
      (do
        (µ/log ::select :error (str "no matching definition for class:" DefinitionClass) :pos-str pos-str)
        (sched/state-error! images task)))))
