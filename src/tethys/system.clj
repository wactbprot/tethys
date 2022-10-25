(ns tethys.system
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [integrant.core :as ig]
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
   :db/couch {:prot "http",
              :host "localhost",
              :port 5984,
              :usr (System/getenv "CAL_USR")
              :pwd (System/getenv "CAL_PWD")
              :name "vl_db_work"}
   :db/mpds {:db (ig/ref :db/couch)
             :id-set (ig/ref :mpd/id-set) 
             :ini {}}
   :model/cont {:mpds (ig/ref :db/mpds)
                :group-kw :cont
                :ini {}}
   :model/exch {:mpds (ig/ref :db/mpds)
                :ini {}}
   :task/cont {:conts (ig/ref :model/cont)
               :ini {}}
   :worker/cont {:conts (ig/ref :task/cont)
                 :ini {}}
   :scheduler/cont {:conts (ig/ref :worker/cont)
                    :ini {}}
   :log/mulog {:type :multi
               :log-context {:app-name "vl-db-agent"
                             :facility (or (System/getenv "DEVPROXY_FACILITY")
                                           (System/getenv "DEVHUB_FACILITY")
                                           (System/getenv "METIS_FACILITY"))}
               :publishers[{:type :elasticsearch
                            :url "http://a75438:9200/"
                            :els-version :v7.x
                            :publish-delay 1000
                            :data-stream "vl-log-stream"
                            :name-mangling false}]}})

;; # System The entire system is stored in an `atom` in the
;; `tethys.system` (this) namespace.
(defonce system  (atom nil))

;; The system is build **up** by the following `init-key`
;; multimethods.
;;
;; ### System up multimethods
;; The `init-key`s methods **read a
;; configuration** and **return an implementation**.
(defmethod ig/init-key :log/mulog [_ opts]  
  (µ/set-global-context! (:log-context opts))
  (µ/start-publisher! opts))

(defmethod ig/init-key :db/couch [_ opts]
  (db/config opts))

(defmethod ig/init-key :mpd/id-set [_ {:keys [id-sets id-set]}]
  (get id-sets id-set))

(defmethod ig/init-key :db/mpds [_ {:keys [db id-set ini]}]
  (reduce
   (fn [res id] (assoc res (keyword id) (:Mp (db/get-doc id db))))
   ini id-set))

(defmethod ig/init-key :model/cont [_ {:keys [mpds ini group-kw]}]
  (reduce
   (fn [res [id {:keys [Container]}]]
     (assoc res id (model/agents-up id group-kw Container)))
   ini mpds))

(defmethod ig/init-key :model/exch [_ {:keys [mpds ini]}]
  (reduce
   (fn [res [id {:keys [Exchange]}]]
     (assoc res id (exch/agent-up id Exchange)))
   ini mpds))

(defmethod ig/init-key :worker/cont [_ {:keys [conts ini]}]
  (reduce
   (fn [res [id as]]
     (assoc res id (work/up as)))
   ini conts))

(defmethod ig/init-key :task/cont [_ {:keys [conts ini]}]
  (reduce
   (fn [res [id as]]
     (assoc res id (task/up as)))
   ini conts))

(defmethod ig/init-key :scheduler/cont [_ {:keys [conts ini]}]
  (reduce
   (fn [res [id as]]
     (assoc res id (sched/whatch-up as)))
   ini conts))

;; ## System down multimethods
;; The system may be **shut down** by
;; `halt-key!` multimethods.  The `halt-keys!` methods **read in the
;; implementation** and shut down in a contolled way. This is a pure
;; side effect.
(defmethod ig/halt-key! :log/mulog [_ logger]
  (logger))

(defmethod ig/halt-key! :model/cont [_ as]
  (run! #(model/agents-down %) as))

(defmethod ig/halt-key! :model/exch [_ a]
  (exch/agent-down a))

(defmethod ig/halt-key! :scheduler/cont [_ as]
  (run! #(sched/whatch-down %) as))

(defmethod ig/halt-key! :worker/cont [_ as]
  (run! #(work/down %) as))

(defmethod ig/halt-key! :task/cont [_ as]
  (run! #(task/down %) as))

(defn init [id-set] (reset! system (ig/init (config id-set))))

;; Todo: difference (in meaning) between `halt` and `stop`?
(defn stop []  
  (µ/log ::start :message "halt system")
  (ig/halt! @system)
  (reset! system {}))

;; ## Helper functions

;; Extract the `ndx`-th `cont`ainer agent of `mpd`. `mpd`have to be a
;; keyword.
(defn cont-agent [mpd ndx] (-> @system :model/cont mpd (nth ndx)))

(defn exch-agent [mpd] (-> @system :model/exch mpd))
