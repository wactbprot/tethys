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
            [tethys.core.response :as resp]
            [tethys.core.scheduler :as sched]
            [tethys.core.task :as task]
            [tethys.worker.core :as work])
  (:gen-class))

(defn config [id-set]
  {:log/mulog {:type :multi
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
   :mpd/id-set {:id-set id-set
                :id-sets {:ppc ["mpd-ppc-gas_dosing"]
                          :se3 ["mpd-se3-calib"
                                "mpd-se3-state"
                                "mpd-se3-servo"
                                "mpd-se3-valves"]}}
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
   :model/conf {:stop-if-delay 500 ; ms
                :run-if-delay 500 ; ms
                :db (ig/ref :db/couch)
                :json-post-header {:content-type :json
                                   :socket-timeout 600000 ;; 10 min
                                   :connection-timeout 600000
                                   :accept :json}
                :dev-hub-url "http://localhost:9009"
                :db-agent-url "http://localhost:9992"
                :dev-proxy-url "http://localhost:8009"}
   :model/images {:mpds (ig/ref :db/mpds)
                  :conf (ig/ref :model/conf)
                  :ini {}}
   :model/exch {:images (ig/ref :model/images)}
   :model/task {:db-task (ig/ref :db/task)
                :exch-fns (ig/ref :model/exch)
                :images (ig/ref :model/images)}
   :model/worker {:build-task (ig/ref :model/task)
                  :exch-fns (ig/ref :model/exch)
                  :images (ig/ref :model/images)
                  :conf (ig/ref :model/conf)}
   :model/response {:images (ig/ref :model/images)
                    :ini {}}
   :scheduler/images {:spawn-work (ig/ref :model/worker)
                      :images (ig/ref :model/images)
                      :ini {}}})

;; # System
;;
;; The entire system is stored in an `atom` in the
;; `tethys.system` (this) namespace.
(defonce system  (atom {}))

;; The system is build **up** by the following `init-key`
;; multimethods.
;;
;; ## System up multimethods
;;
;; The `init-key`s methods **read a
;; configuration** and **return an implementation**.

(defmethod ig/init-key :model/conf [_ conf] conf)

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

(defmethod ig/init-key :db/mpds [_ {:keys [db id-set ini reference-mpd]}]
  (µ/log ::mpd-db :message "start system")
  (let [{:keys [_id Mp]} reference-mpd]
    (assoc 
     (reduce
      (fn [res id] (assoc res (keyword id) (:Mp (db/get-doc id db))))
      ini id-set)
     (keyword _id) Mp)))

(defmethod ig/init-key :db/task [_ {:keys [db view design]}]
  (µ/log ::task-db :message "start system")
  (db/task-fn (db/config (assoc db
                                :view view
                                :design design))))

(comment
  ((:db/task @system) "Common-wait"))

(defmethod ig/init-key :model/images [_ {:keys [mpds ini conf]}]
  (µ/log ::images :message "start system")
  (reduce
   (fn [res [id {:keys [Container Definitions Exchange]}]]
     (assoc res id (model/up {:id id
                              :cont Container
                              :defins Definitions
                              :exch Exchange
                              :conf conf})))
   ini mpds))

(defmethod ig/init-key :model/exch [_ {:keys [images ini]}]
  (µ/log ::scheduler :message "start system")
  (reduce
   (fn [res [id image]]
     (let [e-agt (model/image->exch-agent image)]
       (assoc res id {:from-fn (exch/from-fn e-agt)
                      :only-if-not-fn (exch/only-if-not-fn e-agt)
                      :run-if-fn (exch/run-if-fn e-agt)}))) 
   ini images))

(defmethod ig/init-key :model/task [_ {:keys [images ini db-task exch-fns]}]
  (µ/log ::scheduler :message "start system")
  (reduce
   (fn [res [id image]]
     (let [exch-from-fn (get-in exch-fns [id :from-fn])]
       (assoc res id (task/build-fn db-task exch-from-fn)))) ;; call with task
   ini images))

(comment
  ((:mpd-ref (:model/task @sys/system)) {:TaskName "Common-wait"}))

(defmethod ig/init-key :model/worker [_ {:keys [images ini build-task exch-fns conf]}]
  (µ/log ::worker :message "start system")
  (reduce
   (fn [res [id image]]
     (let [build-task-fn (get build-task id)
           exch-run-if-fn (get-in exch-fns [id :run-if-fn])
           exch-only-if-not-fn (get-in exch-fns [id :only-if-not-fn])]
       (assoc res id (work/spawn-fn build-task-fn exch-run-if-fn exch-only-if-not-fn conf)))) ;; call with task
   ini images))

(defmethod ig/init-key :model/response [_ {:keys [images ini]}]
  (µ/log ::response :message "start system")
  (reduce
   (fn [res [id image]]
     (assoc res id (resp/up image images)))
   ini images))

(defmethod ig/init-key :scheduler/images [_ {:keys [images ini spawn-work]}]
  (µ/log ::scheduler :message "start system")
  (reduce
   (fn [res [id image]]
     (assoc res id (sched/up images id (get spawn-work id))))
   ini images))

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
  (µ/log ::scheduler :message "halt system")
  (run! #(sched/down %) as))
  
(defmethod ig/halt-key! :model/response [_ m]
  (µ/log ::respons :message "halt system")
  (run! #(resp/down %) m))

(defn init [id-set] (reset! system (ig/init (config id-set))))

;; Todo: difference (in meaning) between `halt` and `stop`?
(defn stop []  
  (µ/log ::start :message "halt system")
  (ig/halt! @system)
  (reset! system {}))

(comment
  ;; give up
  (defn write-edn [file data]
    (io/make-parents file)
    (spit file (with-out-str (pp/pprint data))))
  
  (defn read-edn [file-name] (-> (io/file file-name) slurp edn/read-string))

  (defn system-dump-to-fs [folder]
    (let [file-name (str folder "/" (dt/get-date) "/" (dt/get-time) ".edn")]
      (µ/log ::images :message (str "dump to file system " folder))
      (write-edn file-name system)))  

  (defn list-folders [folder] (sort (.list (io/file folder))))

  (defn system-load-from-fs [folder]
    (let [last-folder (str folder "/" (-> (list-folders folder) last))
          ids (mapv keyword (list-folders last-folder))]
      (model/load-from-fs (first ids) last-folder))))
