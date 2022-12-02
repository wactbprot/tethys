(ns tethys.core.task
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [tethys.core.dt :as dt]
            [tethys.core.db :as db]
            [tethys.core.exchange :as exchange]))

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

(defn assemble
  ([task] (assemble task {} {} {} {}))
  ([task Replace] (assemble task Replace {} {} {}))
  ([task Replace Use] (assemble task Replace Use {} {} ))
  ([task Replace Use Defaults] (assemble task Replace Use Defaults {}))
  ([task Replace Use Defaults FromExchange]
   (-> task
       (resolve-use Use)
       (dissoc  :Replace :Use :Defaults :FromExchange)
       (json/write-str)
       (replace-map (kw-map->str-map Replace))
       (replace-map (kw-map->str-map FromExchange))
       (replace-map (globals))
       (replace-map (kw-map->str-map Defaults))
       (json/read-str :key-fn keyword))))

(defn build [image exch db-fn {:keys [TaskName Use Replace] :as task}]
  (let [{:keys [Defaults] :as task} (merge task (db-fn TaskName))]
    (assemble task Replace Use Defaults (exchange/from exch task))))

(defn error [a ex]
  (µ/log ::error-handler :error (str "error occured: " ex))
  (Thread/sleep 1000)
  (µ/log ::error-handler :error "try restart agent")
  (restart-agent a @a))

;; The `up` function provides a queqe made of an agent made of a
;; list.
;; This map will be [[assemble]]d and pushed into the work-queue `w-agt`. 
(defn up [db  {:keys [worker-queqe task-queqe exch] :as image}]
  (µ/log ::up :message "start up task queqe agent")
  (let [db-fn (db/task-fn db)]
    (add-watch task-queqe :queqe (fn [_ t-agt _ tq]
                                   (when (seq tq)
                                     (send t-agt (fn [l]
                                                   (let [task (build image exch db-fn (first l))]
                                                     (send worker-queqe (fn [l] (conj l task)))
                                                     (-> l rest))))))))
  (set-error-handler! task-queqe error))

(defn down [[_ t-agt]]
  (µ/log ::down :message "shut down task queqe agent")
  (remove-watch  t-agt :queqe)
  (send t-agt (fn [_] [])))