(ns tethys.task-test
  (:require [clojure.test :refer :all]
            [tethys.task :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(comment
  (def Defaults {"@CR" "\r", "@host" "e75551", "@port" 503})
  (def task "{\"Port\":\"@port\",\"group\":\"cont\",\"ndx\":0,\"is\":\"ready\",\"TaskName\":\"PPC_DualGauge-ini\",\"Comment\":\"Initializes the safe gauge\",\"pdx\":0,\"sdx\":1,\"id\":\"mpd-ppc-gas_dosing\",\"Action\":\"@acc\",\"Value\":[\"UNI,0@CR\",\"\\u0005\",\"PR1@CR\",\"\\u0005\"],\"Host\":\"@host\"}"))

(comment
  ;; https://clojure.org/guides/test_check_beginner
  (def sort-idempotent-prop
    (prop/for-all [v (gen/vector gen/int)]
                  (= (sort v) (sort (sort v)))))
  
  (tc/quick-check 100 sort-idempotent-prop)
  
  (gen/sample gen/string)
  ("" "" "¡" "" "" "O" "Av&Ç" "{" "ÿoý^\"$" "¬.V")

(def replace-map-works
  (prop/for-all [m (gen/map gen/string-ascii gen/string)]
                (let [k (keys m)
                      v (vals m)
                      l (gen/sample  gen/string-ascii)
                      r (gen/sample  gen/string-ascii)]
                  (=  v (replace-map k  m)))))

(tc/quick-check 10000 replace-map-works))

(deftest replace-map-i
  (testing "empty key"
    (is (= "cacbc" (replace-map "ab" {"" "c"})))
    (is (= "ab" (replace-map "ab" {"" ""}))))
  (testing "empty val"
    (is (= "" (replace-map "d" {"d" ""})))
    (is (= "" (replace-map "" {"" ""})))))


(deftest replace-map-ii
  (testing "bracket space"
    (is (= "[[c]]" (replace-map "[[@s]]" {"@s" "c"})))
    (is (= "[{c}]" (replace-map "[{%s%}]" {"%s%" "c"})))
    (is (= "[{\r\n}]" (replace-map "[{%s%}]" {"%s%" "\r\n"})))
    (is (= "   \r\n   " (replace-map "   %s%   " {"%s%" "\r\n"})))))


(deftest replace-map-iii
  (testing "number"
    (is (= "[[c]]" (replace-map "[[@s]]" {"@s" 100})))
    (is (= "[{c}]" (replace-map "[{%s%}]" {"%s%" "c"})))
    (is (= "[{\r\n}]" (replace-map "[{%s%}]" {"%s%" "\r\n"})))
    (is (= "   \r\n   " (replace-map "   %s%   " {"%s%" "\r\n"})))))
