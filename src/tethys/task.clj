(ns tethys.task
  ^{:author "Thomas Bock <thomas.bock@ptb.de>"}
  (:require [com.brunobonacci.mulog :as µ]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [java-time.api :as jt]
            [tethys.db :as db]))

(defn get-time-object [] (jt/local-time))
(defn get-date-object [] (jt/local-date))
(defn get-hour  [t] (jt/format "HH" t))
(defn get-min   [t] (jt/format "mm" t))
(defn get-sec   [t] (jt/format "ss" t))
(defn get-day   [d] (jt/format "dd"   d))
(defn get-month [d] (jt/format "MM"   d))
(defn get-year  [d] (jt/format "YYYY" d))
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

(defn replace-map [s r]
  (reduce (fn [res [k v]]
            (string/replace res (re-pattern k) v))
          s r))
(comment
 (def Defaults {"%CR%" "\r", "%host%" "e75551", "%port%" 503})
 (def task "{\"Port\":\"@port\",\"group\":\"cont\",\"ndx\":0,\"is\":\"ready\",\"TaskName\":\"PPC_DualGauge-ini\",\"Comment\":\"Initializes the safe gauge\",\"pdx\":0,\"sdx\":1,\"id\":\"mpd-ppc-gas_dosing\",\"Action\":\"@acc\",\"Defaults\":{\"@CR\":\"\\r\",\"@host\":\"e75550\",\"@port\":\"5303\",\"@acc\":\"TCP\",\"%safechannel%\":1},\"Value\":[\"UNI,0@CR\",\"\\u0005\",\"PR1@CR\",\"\\u0005\"],\"Host\":\"@host\"}")
 )

(defn assemble [Replace Use Defaults FromExchange]
  (let [str-task (json/write-str (dissoc task
                                         :Replace
                                         :Use
                                         :Defaults
                                         :FromExchange))]
    (prn (-> str-task
             (replace-map (kw-map->str-map Replace))
             (replace-map (globals))
             (replace-map (kw-map->str-map Defaults))))
  task))
      
;; The `up` checks if the task vector contains any
;; elements. Returns m (the agent) if not. If `:task` is not empty it
;; should contain a map like this:
(defn up [db as]
  (let [f (db/task-fn db)
        w (fn [_ a _ {:keys [task-queue work-queue] :as m}]
            (when (seq task-queue)
              (let [{:keys [TaskName Use Replace] :as task} (first task-queue)
                    {:keys [Defaults FromExchange] :as task} (merge task (f TaskName))
                    task (assemble task Replace Use Defaults)]
              (send a (fn [m] (assoc m
                                    :task-queue (-> task-queue
                                                    rest
                                                    vec)
                                    :work-queue (conj work-queue task)))))))
        ]
    (mapv #(add-watch % :task w) as)))

(defn down [[_ as]] (mapv #(remove-watch % :task) as))

