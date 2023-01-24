(ns tethys.worker.core
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.scheduler :as sched]
            [tethys.worker.ctrl-container :as ctrl-cont]
            [tethys.worker.ctrl-definition :as ctrl-defins]
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
                 :select ctrl-defins/select
                 :runMp ctrl-cont/run-mp
                 :stopMp ctrl-cont/stop-mp
                 :getDate dt/store-date
                 :getTime dt/store-time
                 :Anselm devproxy/devproxy
                 :DevProxy devproxy/devproxy
                 :message message/message
                 :replicateDB db/replicate-db
                 :genDbDoc db/gen-doc
                 :rmDbDocs db/rm-docs
                 (µ/log ::dispatch :error "no matching case" :pos-str pos-str))]
    (f images task)))

(defn spawn-fn [build-task-fn run-if-fn only-if-not-fn {:keys [run-if-delay stop-if-delay] :as conf}]
  (fn [images task]
    (let [task (build-task-fn task)]    
    (if (run-if-fn task)
      (if (only-if-not-fn task)
        (do
          (dispatch images task))
        (do
          (Thread/sleep stop-if-delay)
          (µ/log ::check :message "state set by only-if-not")
          (sched/state-executed! images task)))
      (do
        (Thread/sleep run-if-delay)
        (µ/log ::check :message "state set by run-if")
        (sched/state-ready! images task))))))

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
