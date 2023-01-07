(ns tethys.core.date-time
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [java-time.api :as jt]))


;; ## date time

;; Small namspace encapsulating date time
;; functionality.
(defn get-time-object [] (jt/local-time))
(defn get-date-object [] (jt/local-date))
(defn get-hour [t] (jt/format "HH" t))
(defn get-min [t] (jt/format "mm" t))
(defn get-sec [t] (jt/format "ss" t))
(defn get-day [d] (jt/format "dd"   d))
(defn get-month [d] (jt/format "MM"   d))
(defn get-year [d] (jt/format "YYYY" d))
(defn get-date [] (jt/format "YYYY-MM-dd" (get-date-object)))
(defn get-time [] (str (jt/to-millis-from-epoch (jt/instant))))

