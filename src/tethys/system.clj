(ns tethys.system
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [libcdb.core :as db]
            [libcdb.configure :as cf]
            [com.brunobonacci.mulog :as µ]
            [integrant.core :as ig])
  (:gen-class))


(defn config [ids]
  {:log/mulog {:type :multi
               :log-context {:app-name "vl-db-agent"
                             :facility (or (System/getenv "DEVPROXY_FACILITY")
                                           (System/getenv "DEVHUB_FACILITY")
                                           (System/getenv "METIS_FACILITY"))}
               :publishers[{:type :elasticsearch
                            :url  "http://a75438:9200/"
                            :els-version  :v7.x
                            :publish-delay 1000
                            :data-stream  "vl-log-stream"
                            :name-mangling false}]}
   :db/couch {:prot "http",
              :host "localhost",
              :port 5984,
              :usr (System/getenv "CAL_USR")
              :pwd (System/getenv "CAL_PWD")
              :name "vl_db_work"}
   :db/mpds {:db (ig/ref :db/couch)
             :ids ids
             :ini {}}
   :model/cont {:mpds (ig/ref :db/mpds)
                :ids ids
                :group-kw :cont
                :ini {}}})

;; # Database io functions
;; The three functions used here are simply passed through from
;; library [libcdb](https://gitlab1.ptb.de/vaclab/vl-db)
(defn db-config [opts] (cf/config opts))
(defn get-doc [id db] (db/get-doc id db))
(defn put-doc [doc db] (db/put-doc doc db))

(defn flattenv [v] (into [] (flatten v)))

(defn agents-up [id group-kw m]
  (mapv (fn [{:keys [Ctrl Definition]} ndx]
          (agent {:ctrl (or (keyword Ctrl) :ready)
                  :state (flattenv
                          (mapv (fn [s sdx] 
                                  (mapv (fn [t pdx] 
                                          {:id id
                                           :group group-kw
                                           :ndx ndx :sdx sdx :pdx pdx
                                           :is :ready
                                           :task t})
                                        s (range)))
                                Definition (range)))})) 
        m (range)))


(defn agents-down [[id group-agents]]
  (run! #(send % (fn [_] {})) group-agents))

;; The first `if` clause (the happy path) contains the central idea:
;; the request is send to
;; an [agent](https://clojure.org/reference/agents) `a`. This queues
;; up the write requests and avoids *write conflicts*

;; # System
;; The entire system is stored in an `atom` that is build up by the
;; following `init-key` multimethods and shut down by `halt-key!`
;; multimethods.
;; ### System up multimethods
(defonce system (atom nil))

;; The `init-key`s methods **read a configuration** and **return an
;; implementation**.
(defmethod ig/init-key :log/mulog [_ opts]  
  (µ/set-global-context! (:log-context opts))
  (µ/start-publisher! opts))
 
(defmethod ig/init-key :db/couch [_ opts]
  (db-config opts))

;; 
(defmethod ig/init-key :db/mpds [_ {:keys [db ids ini]}]
  (reduce
   (fn [res id] (assoc res (keyword id) (:Mp (get-doc id db))))
   ini ids))

(defmethod ig/init-key :model/cont [_ {:keys [mpds ids ini group-kw]}]
  (reduce
   (fn [res [id {:keys [Container]}]]
     (assoc res id (agents-up id group-kw Container)))
  ini mpds))



;; ## System down multimethods
;; The `halt-keys!` methods **read in the implementation** and shut down
;; this implementation in a contolled way. This is a pure side effect.
(defmethod ig/halt-key! :log/mulog [_ logger]
  (logger))

(defmethod ig/halt-key! :model/cont [_ groups-agents]
  (run! #(agents-down %) groups-agents))

;; ## Start, stop and restart The following functions are intended
;; for [REPL](https://clojure.org/guides/repl/introduction) usage.
(defn start []
  (keys (reset! system (ig/init (config ["mpd-ppc-gas_dosing"
                                         "mpd-se3-calib"])))))
(defn stop []
  (µ/log ::start :message "halt system")
  (ig/halt! @system)
  (reset! system {}))

(defn restart []
  (stop)
  (Thread/sleep 500)
  (start))

(defn mpds [] (keys (:model/cont @system)))
