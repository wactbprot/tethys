(ns tethys.task
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [java-time.api :as jt]
            [tethys.db :as db]
            [tethys.model :as model]
            [tethys.exchange :as exch]))

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

(defn globals [] 
  (let [d (get-date-object)
        t (get-time-object)]
    {"@hour" (get-hour t)
     "@minute" (get-min t)
     "@second" (get-sec t)
     "@year" (get-year d)
     "@month" (get-month d) 
     "@day" (get-day d)
     "@time" (get-time)}))
 
;; In order to overcome the problem of keywords like `:@foo` the
;; function `kw-map->str-map` converts the "keyword map" delivered by
;; the database view to a "string map".
(defn kw-map->str-map [m] (-> m json/write-str json/read-str)) 

(defn key->pattern [s x]
  (re-pattern (if (string? x) (str s "(?![a-z])") (str "\"?" s  "\"?"))))
 
(defn val->safe-val [x] x (if (string? x) x (json/write-str x)))

(defn replace-map [s r]
  (reduce (fn [res [k v]]
            (string/replace res (key->pattern k v) (val->safe-val v)))
          s r))


;; ## Use
;;
;; The `Use`construct allows the encoding of multible entries in one task.
;; (see task_test.clj for an example)
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
       json/write-str
       (replace-map (kw-map->str-map Replace))
       (replace-map (globals))
       (replace-map (kw-map->str-map Defaults))
       (json/read-str :key-fn keyword))))


(defn build [image db exch task]
  (let [f (db/task-fn db)
        {:keys [TaskName Use Replace] :as task} task
        {:keys [Defaults] :as task} (merge task (f TaskName))
        e-map (exch/from exch task)]
    (assemble task Replace Use Defaults e-map)))

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
  (let [w (fn [_ t-agt _ tq]
            (when (seq tq)
              (send t-agt (fn [l]
                            (let [task (build image db exch (first l))]
                              (send worker-queqe (fn [l] (conj l task)))
                              (-> l rest))))))]
    (add-watch task-queqe :queqe w))
  (set-error-handler! task-queqe model/error))

(defn down [[_ t-agt]]
  (µ/log ::down :message "shut down task queqe agent")
  (remove-watch  t-agt :queqe)
  (send t-agt (fn [_] [])))
