(ns tethys.core.db
   ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [libcdb.core :as db]
            [libcdb.configure :as cf]))
 
;; # Database io functions

;; The database functions used here are simply passed through from
;; library [libcdb](https://gitlab1.ptb.de/vaclab/vl-db)
(defn config [opts] (cf/config opts))

(defn put-doc [doc db] (db/put-doc doc db))

(defn get-doc [id db] (db/get-doc id db))

(defn doc-exist? [id db] (db/doc-exist? id db))

(defn replicate-db [m db] (db/replicate-db m db))

;; The `task` fnunction is generated during the initialisation. It
;; encapsulates the entire `config` so that only the `task-name` has
;; to be given.
(defn task-fn [config]
  (fn [task-name]
    (prn task-name)
    (-> config
        (assoc :key task-name)
        db/get-view
        first 
        :value)))
