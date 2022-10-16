
(ns tethys.system
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [tethys.db :as db]
            [tethys.model :as model]
            [tethys.scheduler :as sched] 
            [com.brunobonacci.mulog :as µ]
            [integrant.core :as ig])
  (:gen-class))

(defn config [id-set]
  {:log/mulog {:type :multi
               :log-context {:app-name "vl-db-agent"
                             :facility (or (System/getenv "DEVPROXY_FACILITY")
                                           (System/getenv "DEVHUB_FACILITY")
                                           (System/getenv "METIS_FACILITY"))}
               :publishers[{:type :elasticsearch
                            :url "http://a75438:9200/"
                            :els-version :v7.x
                            :publish-delay 1000
                            :data-stream "vl-log-stream"
                            :name-mangling false}]}
   :mpd/id-set {:id-set id-set
                :id-sets {:gas-dosing ["mpd-ppc-gas_dosing"]
                          :se3        ["mpd-se3-calib"
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
   :scheduler/cont {:conts (ig/ref :model/cont)
                    :group-kw :cont
                    :ini {}}})

;; The first `if` clause (the happy path) contains the central idea:
;; the request is send to
;; an [agent](https://clojure.org/reference/agents) `a`. This queues
;; up the write requests and avoids *write conflicts*

;; # System
;; The entire system is stored in an `atom` that is build up by the
;; following `init-key` multimethods and shut down by `halt-key!`
;; multimethods.
;; ### System up multimethods
(defonce system  (atom nil))

;; The `init-key`s methods **read a configuration** and **return an
;; implementation**.
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

(defmethod ig/init-key :scheduler/cont [_ {:keys [conts ini]}]
  (reduce
   (fn [res [id agts]]
     (assoc res id (sched/whatch-up agts)))
   ini conts))
  
;; ## System down multimethods
;; The `halt-keys!` methods **read in the implementation** and shut down
;; this implementation in a contolled way. This is a pure side effect.
(defmethod ig/halt-key! :log/mulog [_ logger]
  (logger))

(defmethod ig/halt-key! :model/cont [_ groups-agents]
  (run! #(model/agents-down %) groups-agents))

(defmethod ig/halt-key! :scheduler/cont [_ groups-agents]
  (run! #(sched/whatch-down %) groups-agents))

(defn init [id-set] (reset! system (ig/init (config id-set))))
  
(defn stop []  
  (µ/log ::start :message "halt system")
  (ig/halt! @system)
  (reset! system {}))

;; ## helper functions

;; Extract the `ndx`-th `cont`ainer agent of `mpd`. `mpd`have to be a
;; keyword.
(defn cont-agent [mpd ndx] (-> @system :model/cont mpd (nth ndx)))
