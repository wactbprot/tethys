(ns tethys.worker.ctrl-container
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The worker for controlling mpds."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.exchange :as exch]
            [tethys.model.core :as model]
            [tethys.core.scheduler :as sched]))

(defn task->target [{:keys [Container Mp] :as task}]
  (struct model/state Mp :conts Container))

(defn run-by-ndx [images {:keys [Container Mp] :as task} continue-fn]
  (let [target (task->target task)
        agt-to-run (model/images->state-agent images target)]
    (add-watch agt-to-run :observer (fn [_ _ _ {ctrl :ctrl}]
                                      (condp = ctrl
                                        :error (let [error "mp-run returns with error"]
                                                 (µ/log ::run-mp :error error)
                                                 (remove-watch agt-to-run :observer)
                                                 (continue-fn images (assoc task :error error)))
                                        :ready (do
                                                 (µ/log ::run-mp :message "mp-run executed")
                                                 (remove-watch agt-to-run :observer)
                                                 (continue-fn images task))
                                        :noop)))
    (sched/ctrl-run! agt-to-run)))

(defn run-by-title [images {:keys [ContainerTitle Mp] :as task} continue-fn]
  (let [ndx (model/title->ndx images task ContainerTitle)]
    (run-by-ndx images (assoc task :Container ndx) continue-fn)))
  
(defn run-mp [images {:keys [ContainerTitle Container] :as task} continue-fn]
  (cond
    ContainerTitle (run-by-title images task continue-fn)
    Container (run-by-ndx images task continue-fn)
    :not-found (let [error "Neither ContainerTitle nor Container key given"]
                 (µ/log ::run-mp :error error)
                 (sched/state-error! images (assoc task :error error)))))


(defn stop-by-ndx [images {:keys [Container Mp ndx id] :as task }continue-fn]
  (sched/ctrl-stop! (model/images->state-agent images (task->target task)))
  (when-not (and (= (str ndx) (str Container))
                 (= (keyword Mp) (keyword id)))
    (continue-fn images task)))

(defn stop-by-title [images {:keys [ContainerTitle Mp] :as task} continue-fn]
  (let [ndx (model/title->ndx images task ContainerTitle)]
    (stop-by-ndx images (assoc task :Container ndx) continue-fn)))

(defn stop-mp [images {:keys [ContainerTitle Container] :as task} continue-fn]
  (cond
    ContainerTitle (stop-by-title images task continue-fn)
    Container (stop-by-ndx images task continue-fn)
    :not-found (let [error "Neither ContainerTitle nor Container key given"]
                 (µ/log ::stop-mp :error error)
                 (continue-fn images (assoc task :error error)))))
