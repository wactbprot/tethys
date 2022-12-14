(ns tethys.worker.ctrl-mp
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"
    :doc "The worker for controlling mpds."}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.exchange :as exch]
            [tethys.core.model :as model]
            [tethys.core.scheduler :as sched]))


;; # Helper functions
(defn title->ndx [image title]
  (reduce (fn [_ a]
            (when (= title (-> @a :title))
              (reduced (-> @a :ndx))))
          {} (-> image :conts)))

(defn run-by-ndx [images {:keys [Container Mp] :as task}]
  (let [image (model/images->image images Mp)
        s-agt (model/images->state-agent images task)
        agt-to-run (model/image->cont-agent image Container)]
    (add-watch agt-to-run :observer (fn [_ _ _ m]
                                      (condp = (:ctrl m)
                                        :error (do
                                                 (µ/log ::run-mp :error "mp-run returns with error")
                                                 (sched/state-error! s-agt task)
                                                 (remove-watch agt-to-run :observer))
                                        :ready (do
                                                 (µ/log ::run-mp :message "mp-run executed")
                                                 (sched/state-executed! s-agt task)
                                                 (remove-watch agt-to-run :observer))
                                        :noop)))
    (sched/ctrl-run! agt-to-run)))

(defn run-by-title [images {:keys [ContainerTitle Mp] :as task}]
  (let [image (model/images->image images Mp)
        ndx (title->ndx image ContainerTitle)]
    (run-by-ndx images (assoc task :Container ndx))))
  
(defn run-mp [images {:keys [ContainerTitle Container] :as task}]
  (cond
    ContainerTitle (run-by-title images task)
    Container (run-by-ndx images task)
    :not-found (do
                 (µ/log ::run-mp :error "Neither ContainerTitle nor Container key given")
                 (sched/state-error! (model/images->state-agent images task) task))))


(defn stop-by-ndx [images {:keys [Container Mp] :as task}]
  (let [image (model/images->image images Mp)
        s-agt (model/images->state-agent images task)
        agt-to-stop (model/image->cont-agent image Container)]
    (sched/ctrl-stop! agt-to-stop)
    (when-not (= s-agt agt-to-stop)
      (sched/state-executed! s-agt task))))

(defn stop-by-title [images {:keys [ContainerTitle Mp] :as task}]
  (let [image (model/images->image images Mp)
        ndx (title->ndx image ContainerTitle)]
    (stop-by-ndx images (assoc task :Container ndx))))


(defn stop-mp [images {:keys [ContainerTitle Container] :as task}]
  (cond
    ContainerTitle (stop-by-title images task)
    Container (stop-by-ndx images task)
    :not-found (do
                 (µ/log ::stop-mp :error "Neither ContainerTitle nor Container key given")
                 (sched/state-error! (model/images->state-agent images task) task))))
