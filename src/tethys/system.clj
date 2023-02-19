(ns tethys.system
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [integrant.core :as ig]
            [clojure.edn :as edn]
            [clojure.pprint :as pp]
            [clojure.java.io :as io]
            [tethys.core.db :as db]
            [tethys.core.exchange :as exch]
            [tethys.model.core :as model]
            [tethys.core.scheduler :as sched]
            [tethys.core.task :as task]
            [tethys.worker.core :as work]
            [tethys.server :as server]
            [org.httpkit.server :refer [run-server]]
            [compojure.handler :as handler]
            [ring.middleware.json :as middleware])
  (:gen-class))

(defn config [id-set]
  {:log/mulog
   ;; ~~~~~~~~~~
   {:type :multi
    :log-context {:app-name "tethys"
                  :facility (or (System/getenv "TETHYS_FACILITY")
                                (System/getenv "DEVPROXY_FACILITY")
                                (System/getenv "DEVHUB_FACILITY")
                                (System/getenv "METIS_FACILITY"))}
    :publishers[{:type :elasticsearch
                 :url "http://a75438:9200/"
                 :els-version :v7.x
                 :publish-delay 1000
                 :data-stream "tethys_log"
                 :name-mangling false}]}

   :mpd/id-set
   ;; ~~~~~~~~~~
   {:id-set id-set
    :id-sets {:ppc ["mpd-ppc-gas_dosing"]
              :se3 ["mpd-se3-calib"
                    "mpd-se3-state"
                    "mpd-se3-servo"
                    "mpd-se3-valves"]}}

   :mpd/reference
   ;; ~~~~~~~~~~
   {:file-name "mpd-ref.edn"}

   :db/couch
   ;; ~~~~~~~~~~
   {:prot "http",
    :host "localhost",
    :port 5984,
    :usr (System/getenv "CAL_USR")
    :pwd (System/getenv "CAL_PWD")
    :name "vl_db_work"}

   :db/mpds
   ;; ~~~~~~~~~~
   {:db (ig/ref :db/couch)
    :reference-mpd (ig/ref :mpd/reference)
    :id-set (ig/ref :mpd/id-set) 
    :ini {}}
   
   :db/task
   ;; ~~~~~~~~~~
   {:db (ig/ref :db/couch)
    :view "tasks"
    :design "dbmp"}

   :model/conf
   ;; ~~~~~~~~~~
   {:system-relax 200 ; ms
    :stop-if-delay 500 
    :run-if-delay 500 
    :db (ig/ref :db/couch)
    :json-post-header {:content-type :json
                       :socket-timeout 600000
                       :connection-timeout 600000
                       :accept :json}
    :dev-hub-url "http://localhost:9009"
    :db-agent-url "http://localhost:9992"
    :dev-proxy-url "http://localhost:8009"}

   :model/images
   ;; ~~~~~~~~~~
   {:mpds (ig/ref :db/mpds)
    :conf (ig/ref :model/conf)
    :ini {}}

   :model/exch
   ;; ~~~~~~~~~~
   {:images (ig/ref :model/images)}
   
   :model/task
   ;; ~~~~~~~~~~
   {:db-task (ig/ref :db/task)
    :exch-fns (ig/ref :model/exch)
    :images (ig/ref :model/images)}
   
   :model/worker
   ;; ~~~~~~~~~~
   {:build-task-fn (ig/ref :model/task)
    :exch-fns (ig/ref :model/exch)
    :images (ig/ref :model/images)
    :conf (ig/ref :model/conf)}
   
   :scheduler/images
   ;; ~~~~~~~~~~
   {:spawn-work (ig/ref :model/worker)
    :images (ig/ref :model/images)
    :ini {}}
   
   :server/routes
   ;; ~~~~~~~~~~
   {:name "Alice"}
   
   :server/jetty
   ;; ~~~~~~~~~~
   {:images (ig/ref :model/images)
    :opts {:port 9099}
    :routes (ig/ref :server/routes)}})

;; # System
;;
;; The entire system is stored in an `atom` in the
;; `tethys.system` (this) namespace.
(defonce system  (atom {}))

;; The system is build **up** by the following `init-key`
;; multimethods.

;; ## System up multimethods
;;
;; The `init-key`s methods **read a
;; configuration** and **return an implementation**.

(defmethod ig/init-key :model/conf [_ conf] conf)

(defmethod ig/init-key :log/mulog [_ opts]
  (µ/set-global-context! (:log-context opts))
  (µ/start-publisher! opts))

(defmethod ig/init-key :db/couch [_ opts]
  (db/config opts))

(defmethod ig/init-key :mpd/id-set [_ {:keys [id-sets id-set]}]
  (get id-sets id-set))

(defmethod ig/init-key :mpd/reference [_ {:keys [file-name]}]
  (-> (io/file file-name) slurp edn/read-string))

(defmethod ig/init-key :db/mpds [_ {:keys [db id-set ini reference-mpd]}]
  (let [{:keys [_id Mp]} reference-mpd]
    (assoc 
     (reduce
      (fn [res id] (assoc res (keyword id) (:Mp (db/get-doc id db))))
      ini id-set)
     (keyword _id) Mp)))

(defmethod ig/init-key :db/task [_ {:keys [db view design]}]
  (db/task-fn (db/config (assoc db
                                :view view
                                :design design))))

(comment
  ((:db/task @system) "Common-wait"))

(defmethod ig/init-key :model/images [_ {:keys [mpds ini conf]}]
  (reduce
   (fn [res [id {:keys [Container Definitions Exchange]}]]
     (assoc res id (model/up {:id id
                              :cont Container
                              :defins Definitions
                              :exch Exchange
                              :conf conf})))
   ini mpds))

(defmethod ig/init-key :model/exch [_ {:keys [images ini]}]
  (reduce
   (fn [res [id _]]
     (let [e-agt (model/images->exch-agent images {:id id})]
       (assoc res id {:from-fn (exch/from-fn e-agt)
                      :only-if-not-fn (exch/only-if-not-fn e-agt)
                      :run-if-fn (exch/run-if-fn e-agt)}))) 
   ini images))

(defmethod ig/init-key :model/task [_ {:keys [images ini db-task exch-fns]}]
  (reduce
   (fn [res [id image]]
     (let [exch-from-fn (get-in exch-fns [id :from-fn])]
       (assoc res id {:build-task-fn (task/build-fn db-task exch-from-fn)})))
   ini images))

(comment
  ((:mpd-ref (:model/task @sys/system)) {:TaskName "Common-wait"}))

(defmethod ig/init-key :model/worker [_ {:keys [images ini build-task-fn exch-fns conf]}]
  (reduce
   (fn [res [id image]]
     (let [build-task-fn       (get-in build-task-fn [id :build-task-fn])
           exch-run-if-fn      (get-in exch-fns [id :run-if-fn])
           exch-only-if-not-fn (get-in exch-fns [id :only-if-not-fn])]
       (assoc res id (work/spawn-fn build-task-fn exch-run-if-fn exch-only-if-not-fn conf))))
   ini images))

(defmethod ig/init-key :scheduler/images [_ {:keys [images ini spawn-work]}]
  (reduce
   (fn [res [id image]]
     (assoc res id (sched/up images id (get spawn-work id))))
   ini images))

(defmethod ig/init-key :server/routes [_ {:keys [images]}]
  (server/gen-routes images))

(defmethod ig/init-key :server/jetty [_ {:keys [images opts routes]}]
  (run-server (-> (handler/site routes)
                  (middleware/wrap-json-body {:keywords? true})
                  (middleware/wrap-json-response))
              opts))

(defn start [id-set]
  (µ/log ::start :message "start system")
  (reset! system (ig/init (config id-set))))

;; ## System down multimethods
;;
;; The system may be **shut down** by
;; `halt-key!` multimethods.  The `halt-keys!` methods **read in the
;; implementation** and shut down in a contolled way. This is a pure
;; side effect.
(defmethod ig/halt-key! :log/mulog [_ logger]
  (logger))

(defmethod ig/halt-key! :model/images [_ as]
  (run! #(model/down %) as))
  
(defmethod ig/halt-key! :scheduler/images [_ as]
  (run! #(sched/down %) as))

(defmethod ig/halt-key! :server/jetty [_ server]
  (.stop server))


;; Todo: difference (in meaning) between `halt` and `stop`?
(defn stop []
  (µ/log ::stop :message "shut down system")
  (ig/halt! @system)
  (reset! system {}))

;; ## helper

(defn mpds [] (-> @system :db/mpds))
(defn images [] (-> @system :model/images))
(defn exch-agent [id] (model/images->exch-agent (images) {:id id}))

(comment
  (exch/stop-if (exch-agent :mpd-se3-servo)  {:StopIf "Servo_1_Stop.Bool"}))

(comment
  ;; give up
  (defn write-edn [file data]
    (io/make-parents file)
    (spit file (with-out-str (pp/pprint data))))
  
  (defn read-edn [file-name] (-> (io/file file-name) slurp edn/read-string))

  (defn system-dump-to-fs [folder]
    (let [file-name (str folder "/" (dt/get-date) "/" (dt/get-time) ".edn")]
      (write-edn file-name system)))  

  (defn list-folders [folder] (sort (.list (io/file folder))))

  (defn system-load-from-fs [folder]
    (let [last-folder (str folder "/" (-> (list-folders folder) last))
          ids (mapv keyword (list-folders last-folder))]
      (model/load-from-fs (first ids) last-folder))))
