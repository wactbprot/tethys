(ns tethys.task-test
  (:require [clojure.test :refer :all]
            [tethys.task :refer :all]
            [clojure.data.json :as json]
            [clojure.string :as string]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(comment
  (def m {"@CR" "\r", "@host" "e75551", "@port" 503})
  (def s "{\"Port\":\"@port\",\"group\":\"cont\",\"ndx\":0,\"is\":\"ready\",\"TaskName\":\"PPC_DualGauge-ini\",\"Comment\":\"Initializes the safe gauge\",\"pdx\":0,\"sdx\":1,\"id\":\"mpd-ppc-gas_dosing\",\"Action\":\"@acc\",\"Value\":[\"UNI,0@CR\",\"\\u0005\",\"PR1@CR\",\"\\u0005\"],\"Host\":\"@host\"}")
  (json/read-str (replace-map s m)))

(comment
  ;; https://clojure.org/guides/test_check_beginner
  (def sort-idempotent-prop
    (prop/for-all [v (gen/vector gen/int)]
                  (= (sort v) (sort (sort v)))))
  
  (tc/quick-check 100 sort-idempotent-prop))

(comment
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
    (is (=              "abc" ; !
           (replace-map "ab" {"" "c"})))
    (is (=              "abq" ; !
           (replace-map "ab" {"" ""}))))
  (testing "empty val"
    (is (= "" (replace-map "d" {"d" ""})))
    (is (= "" (replace-map "" {"" ""})))))

(deftest replace-map-ii
  (testing "single and included work for numbers and boolean"
    (is (= "{\"Port\": 100}" (replace-map "{\"Port\": \"@port\"}", {"@port" 100})))
    (is (= "{\"Port\": true}" (replace-map "{\"Port\": \"@port\"}", {"@port" true})))
    (is (= "{\"Port\":\" 100 \"}" (replace-map "{\"Port\":\" @port \"}", {"@port" 100}))))
  (testing "single and included work for strings"
    (is (= "{\"Port\": \"100\"}" (replace-map "{\"Port\": \"@port\"}", {"@port" "100"})))
    (is (= "{\"Port\": \"port\n\"}" (replace-map "{\"Port\": \"port@CR\"}", {"@CR" "\n"}))))
  (testing "only entite words"
    (is (= "{\"Port\": \"@timepath\"}" (replace-map "{\"Port\": \"@timepath\"}", {"@time" "nonono!"})))))


