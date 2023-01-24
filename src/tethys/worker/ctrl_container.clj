(ns tethys.worker.ctrl-container
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The worker for controlling mpds."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.exchange :as exch]
            [tethys.model.core :as model]
            [tethys.core.scheduler :as sched]))

(defn task->target [{:keys [Container Mp] :as task}]
  (struct model/state Mp :conts Container))

(defn run-by-ndx [images {:keys [Container Mp] :as task}]
  (let [target (task->target task)
        agt-to-run (model/images->state-agent images target)]
    (add-watch agt-to-run :observer (fn [_ _ _ {ctrl :ctrl}]
                                      (condp = ctrl
                                        :error (do
                                                 (µ/log ::run-mp :error "mp-run returns with error")
                                                 (sched/state-error! images task)
                                                 (remove-watch agt-to-run :observer))
                                        :ready (do
                                                 (µ/log ::run-mp :message "mp-run executed")
                                                 (sched/state-executed! images task)
                                                 (remove-watch agt-to-run :observer))
                                        :noop)))
    (sched/ctrl-run! agt-to-run)))

(defn run-by-title [images {:keys [ContainerTitle Mp] :as task}]
  (let [ndx (model/title->ndx images task ContainerTitle)]
    (run-by-ndx images (assoc task :Container ndx))))
  
(defn run-mp [images {:keys [ContainerTitle Container] :as task}]
  (cond
    ContainerTitle (run-by-title images task)
    Container (run-by-ndx images task)
    :not-found (do
                 (µ/log ::run-mp :error "Neither ContainerTitle nor Container key given")
                 (sched/state-error! images task))))


(defn stop-by-ndx [images {:keys [Container Mp ndx id] :as task}]
  (sched/ctrl-stop! (model/images->state-agent images (task->target task)))
  (when-not (and (= (str ndx) (str Container))
                 (= (keyword Mp) (keyword id)))
    (sched/state-executed! images task)))

(defn stop-by-title [images {:keys [ContainerTitle Mp] :as task}]
  (let [ndx (model/title->ndx images task ContainerTitle)]
    (stop-by-ndx images (assoc task :Container ndx))))

(defn stop-mp [images {:keys [ContainerTitle Container] :as task}]
  (cond
    ContainerTitle (stop-by-title images task)
    Container (stop-by-ndx images task)
    :not-found (do
                 (µ/log ::stop-mp :error "Neither ContainerTitle nor Container key given")
                 (sched/state-error! images task))))
