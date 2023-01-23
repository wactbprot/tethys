(ns tethys.core.task
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [tethys.core.date-time :as dt]))

(defn globals [] 
  (let [d (dt/get-date-object)
        t (dt/get-time-object)]
    {"@hour" (dt/get-hour t)
     "@minute" (dt/get-min t)
     "@second" (dt/get-sec t)
     "@year" (dt/get-year d)
     "@month" (dt/get-month d) 
     "@day" (dt/get-day d)
     "@time" (dt/get-time)}))

;; In order to overcome the problem of keywords like `:@foo` the
;; function `kw-map->str-map` converts the "keyword map" (keys in the
;; map are keywords) delivered by the database view to a "string map" (keys in the
;; map are strings).
(defn kw-map->str-map [m] (-> m json/write-str json/read-str)) 

(defn key->pattern [s x]
  (re-pattern (if (string? x) (str s "(?![a-z])") (str "\"?" s  "\"?"))))

(defn val->safe-val [x] x (if (string? x) x (json/write-str x)))

(defn replace-map [s r]
  (reduce (fn [res [k v]]
            (string/replace res (key->pattern k v) (val->safe-val v)))
          s r))

;; ## Use

;; The `Use` construct allows the encoding of multible entries in one
;; task. (see task_test.clj for an example)
(defn singular-kw [kw] (keyword (string/join "" (butlast (name kw)))))

(defn filter-match [m kw] (filterv (fn [[k v]] (= kw k)) m))

(defn replace-vec [task use-map]
  (filterv some? (mapv (fn [[k v]]
                         (let [kw (singular-kw k)
                               v (-> (k task)
                                     (filter-match (keyword v))
                                     first)]
                           (when (vector? v)
                             {kw (second v)})))
                       use-map)))

(defn resolve-use [task use-map]
  (reduce (fn [ini m]
            (merge ini m))
          task (replace-vec task use-map)))

(defn assemble [{:keys [Use Replace FromExchange Defaults] :as task}]
  (-> task
      (resolve-use Use)
      (dissoc  :Replace :Use :Defaults :FromExchange)
      (json/write-str)
      (replace-map (kw-map->str-map Replace))
      (replace-map (kw-map->str-map FromExchange))
      (replace-map (globals))
      (replace-map (kw-map->str-map Defaults))
      (json/read-str :key-fn keyword)))

(defn build-fn [db-task-fn exch-from-fn]
  (fn [{:keys [TaskName pos-str] :as task}]
    (µ/log ::build :message (str "Try build task: " TaskName) :pos-str pos-str)
    (let [task (merge task (db-task-fn TaskName))
          from-exchange (exch-from-fn task)]
      (assemble (assoc task :FromExchange from-exchange)))))  

