(ns tethys.core.worker
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [tethys.core.exchange :as exch]
            [tethys.core.model :as model]
            [tethys.core.scheduler :as sched]
            [tethys.worker.ctrl-mp :as ctrl-mp]
            [tethys.worker.select-definition :as select]
            [tethys.worker.devhub :as devhub]
            [tethys.worker.date-time :as dt]
            [tethys.worker.exchange :as exchange]
            [tethys.worker.wait :as wait]))

(defn dispatch [images {:keys [Action] :as task}]
  (case (keyword Action)
     :wait (wait/wait images task)
     :TCP (devhub/devhub images task)
     :VXI11 (devhub/devhub images task)
     :MODBUS (devhub/devhub images task)
     :EXECUTE (devhub/devhub images task)
     :writeExchange (exchange/write images task)
     :select (select/select images task)
     :runMp (ctrl-mp/run-mp images task)
     :stopMp (ctrl-mp/stop-mp images task)
     :getDate (dt/store-date images task)
     :getTime (dt/store-time images task)
     ;; todo:
     
     ;; 
     ;;        
     ;; :getTime       
     ;; :Anselm        
     ;; :DevProxy      
     ;; :genDbDoc      
     ;; :rmDbDocs      
     ;; :replicateDB   
     ;; :readExchange  
     ;; :message       

    (µ/log ::dispatch :error "no matching case")))

(defn check [images task] 
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

(defn up [{:keys [worker-queqe]} images]
  (µ/log ::up :message "start up worker queqe agent")
  (set-error-handler! worker-queqe error)
  (add-watch worker-queqe :queqe (fn [_ w-agt _ wq]
                                   (when (seq wq)
                                     (future (check images (first wq)))
                                     (send w-agt (fn [l] (-> l rest)))))))

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
