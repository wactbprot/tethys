(ns tethys.db
   ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [libcdb.core :as db]
            [libcdb.configure :as cf]))
 
;; # Database io functions

;; The database functions used here are simply passed through from
;; library [libcdb](https://gitlab1.ptb.de/vaclab/vl-db)
(defn config [opts] (cf/config opts))

(defn get-doc [id db] (db/get-doc id db))

;; The `task` fnunction is generated during the initialisation. It
;; encapsulates the entire `config` so that only the `task-name` has
;; to be given.
(defn task-fn [config]
  (fn [task-name]
    (-> config
        (assoc :key task-name)
        db/get-view
        first 
        :value)))
