(ns tethys.worker.select-definition
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The select definition worker."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.exchange :as exch]
            [tethys.core.model :as model]
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

(defn select [images {:keys [DefinitionClass id] :as task}]
  (let [s-agt (model/images->state-agent images task)
        e-agt (model/images->exch-agent images task)
        agt-to-run (agent-match (model/images->defins images task) e-agt DefinitionClass)]
    (add-watch agt-to-run :observer (fn [_ _ _ m]
                                      (condp = (:ctrl m)
                                        :error (do
                                                 (µ/log ::select :error "selected definition returns with error")
                                                 (sched/state-error! s-agt task)
                                                 (remove-watch agt-to-run :observer))
                                        :ready (do
                                                 (µ/log ::select :message "selected definition executed")
                                                 (sched/state-executed! s-agt task)
                                                 (remove-watch agt-to-run :observer))
                                        :noop)))
    (sched/ctrl-run! agt-to-run)))
