(ns tethys.system
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [integrant.core :as ig]
            [clojure.edn :as edn]
            [clojure.java.io :as io]
            [tethys.db :as db]
            [tethys.exchange :as exch]
            [tethys.model :as model]
            [tethys.scheduler :as sched]
            [tethys.task :as task]
            [tethys.worker :as work])
  (:gen-class))

(defn config [id-set]
  {:mpd/id-set {:id-set id-set
                :id-sets {:ppc ["mpd-ppc-gas_dosing"]
                          :se3 ["mpd-se3-calib"
                                "mpd-se3-state"
                                "mpd-se3-servo"]}}
   
   :mpd/reference {:file-name "mpd-ref.edn"}

   :db/couch {:prot "http",
              :host "localhost",
              :port 5984,
              :usr (System/getenv "CAL_USR")
              :pwd (System/getenv "CAL_PWD")
              :name "vl_db_work"}

   :db/mpds {:db (ig/ref :db/couch)
             :reference-mpd (ig/ref :mpd/reference)
             :id-set (ig/ref :mpd/id-set) 
             :ini {}}

   :db/task {:db (ig/ref :db/couch)
             :view "tasks"
             :design "dbmp"}

   :model/image {:mpds (ig/ref :db/mpds)
                :ini {}}

   :model/exch {:mpds (ig/ref :db/mpds)
                :ini {}}
   
   :model/worker {:image (ig/ref :model/image)
                  :exch-interface (ig/ref :model/exch)
                  :ini {}}
   
   :model/task {:db (ig/ref :db/task)
                :mpds (ig/ref :db/mpds)
                :exch-interface (ig/ref :model/exch)
                :worker-queqes (ig/ref :model/worker)
                :ini {}}

   :scheduler/image {:image (ig/ref :model/image)
                    :task-queqes (ig/ref :model/task)
                    :ini {}}

   :log/mulog {:type :multi
               :log-context {:app-name "tethys"
                             :facility (or (System/getenv "TETHYS_FACILITY")
                                           (System/getenv "DEVPROXY_FACILITY")
                                           (System/getenv "DEVHUB_FACILITY")
                                           (System/getenv "METIS_FACILITY"))}
               :publishers[{:type :elasticsearch
                            :url "http://a75438:9200/"
                            :els-version :v7.x
                            :publish-delay 1000
                            :data-stream "vl-log-stream"
                            :name-mangling false}]}})

;; # System
;; The entire system is stored in an `atom` in the
;; `tethys.system` (this) namespace.
(defonce system  (atom {}))

;; The system is build **up** by the following `init-key`
;; multimethods.
;;
;; ## System up multimethods
;; The `init-key`s methods **read a
;; configuration** and **return an implementation**.
(defmethod ig/init-key :log/mulog [_ opts]
  (µ/log ::logger :message "start system")
  (µ/set-global-context! (:log-context opts))
  (µ/start-publisher! opts))

(defmethod ig/init-key :db/couch [_ opts]
  (µ/log ::couch :message "start system")
  (db/config opts))

(defmethod ig/init-key :mpd/id-set [_ {:keys [id-sets id-set]}]
  (µ/log ::id-set :message "start system")
  (get id-sets id-set))

(defmethod ig/init-key :mpd/reference [_ {:keys [file-name]}]
  (µ/log ::mpd-reference :message "start system")
  (-> (io/file file-name) slurp edn/read-string))

(defmethod ig/init-key :db/task [_ {:keys [db view design]}]
    (µ/log ::task-db :message "start system")
  (db/config (assoc db :view view :design design)))

(defmethod ig/init-key :db/mpds [_ {:keys [db id-set ini reference-mpd]}]
  (µ/log ::mpd-db :message "start system")
  (let [{:keys [_id Mp]} reference-mpd]
    (assoc 
     (reduce
      (fn [res id] (assoc res (keyword id) (:Mp (db/get-doc id db))))
      ini id-set)
     (keyword _id) Mp)))

(defmethod ig/init-key :model/image [_ {:keys [mpds ini]}]
  (µ/log ::cont :message "start system")
  (reduce
   (fn [res [id {:keys [Container Definitions]}]]
     (assoc res id (model/up id Container Definitions)))
   ini mpds))

(defmethod ig/init-key :model/exch [_ {:keys [mpds ini]}]
  (µ/log ::exch :message "start system")
  (reduce
   (fn [res [id {:keys [Exchange]}]]
     (assoc res id (exch/up id Exchange)))
   ini mpds))

(defmethod ig/init-key :model/worker [_ {:keys [image exch-interface ini]}]
  (µ/log ::worker :message "start system")
  (reduce
   (fn [res [id _]]
     (assoc res id (work/up image (id exch-interface))))
   ini image))

(defmethod ig/init-key :model/task [_ {:keys [db mpds worker-queqes exch-interface ini]}]
  (µ/log ::task :message "start system")
  (reduce
   (fn [res [id _]]
     (assoc res id (task/up db (id worker-queqes) (id exch-interface))))
   ini mpds))

(defmethod ig/init-key :scheduler/image [_ {:keys [image task-queqes ini]}]
  (µ/log ::scheduler :message "start system")
  (reduce
   (fn [res [id as]]
     (assoc res id (sched/up as (id task-queqes))))
   ini image))

;; ## System down multimethods
;; The system may be **shut down** by
;; `halt-key!` multimethods.  The `halt-keys!` methods **read in the
;; implementation** and shut down in a contolled way. This is a pure
;; side effect.
(defmethod ig/halt-key! :log/mulog [_ logger]
  (logger))

(defmethod ig/halt-key! :model/image [_ as]
  (run! #(model/down %) as))

(defmethod ig/halt-key! :model/exch [_ as]
  (µ/log ::exch :message "halt system")
  (run! #(exch/down %) as))

(defmethod ig/halt-key! :scheduler/image [_ as]
  (µ/log ::scheduler :message "halt system")
  (run! #(sched/down %) as))

(defmethod ig/halt-key! :model/worker [_ m]
  (µ/log ::worker :message "halt system")
  (run! #(work/down %) m))

(defmethod ig/halt-key! :model/task [_ m]
  (µ/log ::task :message "halt system")
  (run! #(task/down %) m))

(defn init [id-set] (reset! system (ig/init (config id-set))))

;; Todo: difference (in meaning) between `halt` and `stop`?
(defn stop []  
  (µ/log ::start :message "halt system")
  (ig/halt! @system)
  (reset! system {}))

;; ## Helper functions

;; Extract the `ndx`-th `cont`ainer agent of `mpd`. `mpd`have to be a
;; keyword.
(defn mpd-image [mpd] (-> @system :model/image mpd))

(defn mpd-exch-agent [mpd] (-> @system :model/exch mpd))

(defn mpd-work-agent [mpd] (-> @system :model/worker mpd))

(defn mpd-task-agent [mpd] (-> @system :model/task mpd))
