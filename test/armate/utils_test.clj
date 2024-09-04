(ns armate.utils-test
  (:require [clojure.test :refer :all]
            [armate.utils :as u]))

(deftest dissoc-if-nil-test
  (is (= {:a 1}
         (u/dissoc-if-nil {:a 1 :b nil} :b)))
  (is (= {:a 1 :b 2}
         (u/dissoc-if-nil {:a 1 :b 2 :c nil :d nil} :c :d))))

(deftest assoc-if-not-nil-test
  (is (= {:a 1}
         (u/assoc-if-not-nil {:a 1} :b nil)))
  (is (= {:a 1 :b 2}
         (u/assoc-if-not-nil {:a 1} :b 2))))
