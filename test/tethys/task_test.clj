(ns tethys.task-test
  (:require [clojure.test :refer :all]
            [tethys.task :refer :all]
            [clojure.test.check :as tc]
            [clojure.test.check.generators :as gen]
            [clojure.test.check.properties :as prop]))

(comment
  ;; https://clojure.org/guides/test_check_beginner
  (def sort-idempotent-prop
    (prop/for-all [v (gen/vector gen/int)]
                  (= (sort v) (sort (sort v)))))
  
  (tc/quick-check 100 sort-idempotent-prop))

(comment
  (gen/sample gen/string)
  ("" "" "¡" "" "" "O" "Av&Ç" "{" "ÿoý^\"$" "¬.V"))
