(ns tethys.core.worker
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.exchange :as exch]
            [tethys.core.model :as model]
            [tethys.core.scheduler :as sched]
            [tethys.worker.ctrl-mp :as ctrl-mp]
            [tethys.worker.select-definition :as select]
            [tethys.worker.devhub :as devhub]
            [tethys.worker.devproxy :as devproxy]
            [tethys.worker.date-time :as dt]
            [tethys.worker.db :as db]
            [tethys.worker.exchange :as exchange]
            [tethys.worker.message :as message]
            [tethys.worker.wait :as wait]))

(defn dispatch [images {:keys [Action pos-str] :as task}]
  (when-let [f (case (keyword Action)
                 :wait wait/wait
                 :TCP devhub/devhub
                 :VXI11 devhub/devhub
                 :MODBUS devhub/devhub
                 :EXECUTE devhub/devhub
                 :writeExchange exchange/write-exchange
                 :readExchange exchange/read-exchange
                 :select select/select
                 :runMp ctrl-mp/run-mp
                 :stopMp ctrl-mp/stop-mp
                 :getDate dt/store-date
                 :getTime dt/store-time
                 :Anselm devproxy/devproxy
                 :DevProxy devproxy/devproxy
                 :message message/message
                 :replicateDB db/replicate-db
                 :genDbDoc db/gen-doc
                 :rmDbDocs db/rm-docs
                 (µ/log ::dispatch :error "no matching case" :pos-str pos-str))]
    (model/spawn-work images task f)))
  
(defn check-spawn! [images task] 
  (let [stop-if-delay 1000
        s-agt (model/images->state-agent images task)
        e-agt (model/images->exch-agent images task)]    
    (if (exch/run-if e-agt task)
      (if (exch/only-if-not e-agt task)
        (do
          (dispatch images task))
        (do
          (Thread/sleep stop-if-delay)
          (µ/log ::check :message "state set by only-if-not")
          (sched/state-executed! s-agt task)))
      (do
        (Thread/sleep stop-if-delay)
        (µ/log ::check :message "state set by run-if")
        (sched/state-ready! s-agt task)))))

(defn error [a ex]
  (µ/log ::error-handler :error (str "error occured: " ex))
  (Thread/sleep 1000)
  (µ/log ::error-handler :message "try to restart agent")
  (restart-agent a @a))

(defn watch-fn [images]
  (fn [_ w-agt _ w-queqe]
    (when (seq w-queqe)
      (send w-agt (fn [l]
                    (check-spawn! images (first w-queqe))
                    (-> l rest))))))

(defn up [{:keys [worker-queqe]} images]
  (µ/log ::up :message "start up worker queqe agent")
  (set-error-handler! worker-queqe error)
  (add-watch worker-queqe :queqe (watch-fn images)))

(defn down [[_ w-agt]]
  (µ/log ::down :message "shut down worker queqe agent")
  (remove-watch w-agt :queqe)
  (send w-agt (fn [_] '())))

(comment
  ;; the futures should be kept with a hash key:
  (format "%x" (hash  {:TaskName "Common-wai",
                       :Replace {:%waittime 2000},
                       :id :mpd-ref,
                       :group :cont,
                       :ndx 0,
                       :sdx 2,
                       :pdx 0,
                       :is :ready}))
  ;; =>
  "6092736d")
