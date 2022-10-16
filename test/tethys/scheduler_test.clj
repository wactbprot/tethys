(ns tethys.scheduler-test
  (:require [clojure.test :refer :all]
            [tethys.scheduler :refer :all]))

(def v   [{:id :mpd-ppc-gas_dosing,
           :group :cont,
           :ndx 0,
           :sdx 0,
           :pdx 0,
           :is :ready,
           :task {:TaskName "PPC_MaxiGauge-ini"}}
          {:id :mpd-ppc-gas_dosing,
           :group :cont,
           :ndx 0,
           :sdx 1,
           :pdx 0,
           :is :ready,
           :task {:TaskName "PPC_DualGauge-ini"}}
          {:id :mpd-ppc-gas_dosing,
           :group :cont,
           :ndx 0,
           :sdx 2,
           :pdx 0,
           :is :ready,
           :task {:TaskName "PPC_VAT_DOSING_VALVE-ini"}}
          {:id :mpd-ppc-gas_dosing,
           :group :cont,
           :ndx 0,
           :sdx 3,
           :pdx 0,
           :is :ready,
           :task {:TaskName "PPC_Faulhaber_Servo-comp_ini"}}])


(deftest error?-test-i
  (testing "basics"
    (is (false? (error? [{:is :ready} {:is :ready}{:is :ready}])))
    (is (true? (error? [{:is :error} {:is :ready}{:is :ready}]))))
  (testing "empty means no errors"
    (is (false? (error? []))))
  (testing "nil"
    (is (false? (error? [nil])))
    (is (false? (error? [{:is nil}])))))

(deftest all-exec?-test-i
  (testing "basics"
    (is (false? (all-exec? [{:is :executed} {:is :ready}{:is :ready}])))
    (is (true? (all-exec? [{:is :executed} {:is :executed}{:is :executed}]))))
  (testing "empty means all done"
    (is (true? (all-exec? []))))
  (testing "nil"
    (is (false? (all-exec? [nil])))
    (is (false? (all-exec? [{:is nil}])))))
