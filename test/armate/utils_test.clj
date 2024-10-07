(ns armate.utils-test
  (:require [clojure.test :refer [deftest is]]
            [armate.utils :as u]))

(deftest fnil-conj-set-test
  (is (= #{1 2} (u/fnil-conj-set nil 1 2)))
  (is (= {:a #{2}} (update {} :a u/fnil-conj-set 2)))
  (is (= {:a #{1 2}} (update {:a #{1}} :a u/fnil-conj-set 2))))

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

(deftest distinct-by
  (is (= [1 2 3] (u/distinct-by identity [1 2 3 2 3 1 3])))
  (is (= [0 1 2] (take 3 (u/distinct-by identity (range))))))
