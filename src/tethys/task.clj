(ns tethys.task
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [java-time.api :as jt]
            [tethys.db :as db]
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
            (string/replace res  (key->pattern k v) (val->safe-val v)))
          s r))

(defn assemble
  ([task] (assemble task {} {} {} {}))
  ([task Replace] (assemble task Replace {} {} {}))
  ([task Replace Use] (assemble task Replace Use {} {} ))
  ([task Replace Use Defaults] (assemble task Replace Use Defaults {}))
  ([task Replace Use Defaults FromExchange]
   (->  task
        (dissoc  :Replace :Use :Defaults :FromExchange)
        json/write-str
        (replace-map (kw-map->str-map Replace))
        (replace-map (globals))
        (replace-map (kw-map->str-map Defaults))
        json/read-str)))

;; The `up` checks if the `task-queue` (a vector) contains any
;; elements. Returns `m` (the agent) if not. If `:task-queue` is not empty it
;; should contain a map like this:
(comment
  {:TaskName "PPC_Faulhaber_Servo-comp_ini",
   :id :mpd-ppc-gas_dosing,
   :group :cont,
   :ndx 0,
   :sdx 3,
   :pdx 0,
   :is :ready})

;; This map will be [[assemble]]d and pushed into the work-queue `wa`. 
(defn up [db w-agt e-agt]
  (µ/log ::up :message "start up task queqe agent")
  (let [f (db/task-fn db)
        a (agent '()) ;; becomes t-agt
        w (fn [_ t-agt _ v]
            (when (seq v)
              (let [{:keys [TaskName Use Replace] :as task} (first v)
                    {:keys [Defaults FromExchange] :as task} (merge task (f TaskName))
                    e-map (exch/from e-agt FromExchange)
                    task (assemble task Replace Use Defaults e-map)]
                (send t-agt (fn [v]
                              (send w-agt (fn [v] (conj v task)))
                              (-> v rest))))))]

    (set-error-handler! a (fn [a ex]
                            (µ/log ::error-handler :error (str "error occured: " ex))
                            (Thread/sleep 1000)
                            (restart-agent a @a)))
    (add-watch a :queqe w)))

(defn down [[_ a]]
  (µ/log ::down :message "shut down task queqe agent")
  (remove-watch a :queqe)
  (send a (fn [_] [])))

(comment
  (defn up []
    (let [a (agent [])
          w (fn [_ q _ v]
              (when (seq q)
                ;; do stuff with (first q)
                (send q (fn [v] (-> v rest vec)))))]
      (add-watch a :queqe w))))
