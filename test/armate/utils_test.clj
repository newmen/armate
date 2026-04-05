(ns armate.utils-test
  (:require [clojure.test :refer [deftest is]]
            [armate.utils :as u]))

(deftest fnil-conj-set-test
  (is (= #{1 2} (u/fnil-conj-set nil 1 2)))
  (is (= {:a #{2}} (update {} :a u/fnil-conj-set 2)))
  (is (= {:a #{1 2}} (update {:a #{1}} :a u/fnil-conj-set 2))))

(deftest fnil-union-set-test
  (is (= {:a #{:x :y}} (update {:a #{:x}} :a u/fnil-union-set #{:y})))
  (is (= {:a #{:x} :b #{:y}} (update {:a #{:x}} :b u/fnil-union-set #{:y}))))

(deftest dissoc-if-nil-test
  (is (= {:a 1}
         (u/dissoc-if-nil {:a 1 :b nil} :b)))
  (is (= {:a 1 :b nil}
         (u/dissoc-if-nil {:a 1 :b nil} :a)))
  (is (= {:a 1 :b 2}
         (u/dissoc-if-nil {:a 1 :b 2 :c nil :d nil} :c :d))))

(deftest dissoc-nils
  (is (= {:a 1}
         (u/dissoc-nils {:a 1 :b nil})))
  (is (= {:a 1 :b 2}
         (u/dissoc-nils {:a 1 :b 2 :c nil :d nil}))))

(deftest assoc-if-not-nil-test
  (is (= {:a 1}
         (u/assoc-if-not-nil {:a 1} :b nil)))
  (is (= {:a 1 :b 2}
         (u/assoc-if-not-nil {:a 1} :b 2))))

(deftest update-if-not-nil-test
  (is (= {:a 1}
         (u/update-if-not-nil {:a 1} :b inc)))
  (is (= {:a 1 :b nil}
         (u/update-if-not-nil {:a 1 :b nil} :b + 2)))
  (is (= {:a 1 :b 2}
         (u/update-if-not-nil {:a 1 :b 1} :b inc)))
  (is (= {:a 1 :b 2}
         (u/update-if-not-nil {:a 1 :b 0} :b + 2))))

(deftest replace-last-test
  (is (= [1]
         (u/replace-last [] 1)))
  (is (= [2]
         (u/replace-last [1] 2)))
  (is (= [1 2]
         (u/replace-last [1 3] 2))))

(deftest distinct-by
  (is (= [1 2 3] (u/distinct-by identity [1 2 3 2 3 1 3])))
  (is (= [0 1 2] (take 3 (u/distinct-by identity (range))))))

(deftest make-keyword-keys-test
  (is (nil? (u/make-keyword-keys nil)))
  (is (= 42
         (u/make-keyword-keys 42)))
  (is (= :x
         (u/make-keyword-keys :x)))
  (is (= "x"
         (u/make-keyword-keys "x")))
  (is (= ["x" "y"]
         (u/make-keyword-keys ["x" "y"])))
  (is (= #{"x" "y"}
         (u/make-keyword-keys #{"x" "y"})))
  (is (= '("x" "y")
         (u/make-keyword-keys '("x" "y"))))
  (is (= {:x "y"}
         (u/make-keyword-keys {"x" "y"})))
  (is (= {:x {:y "z"}}
         (u/make-keyword-keys {"x" {"y" "z"}})))
  (is (= [{:a "b"} {:x {:y "z"}}]
         (u/make-keyword-keys [{"a" "b"} {"x" {"y" "z"}}]))))

(deftest make-int-or-float-test
  (is (= 1 (u/make-int-or-float 1N)))
  (is (= 1 (u/make-int-or-float 1.0)))
  (is (= 0.5 (u/make-int-or-float (/ 1 2))))
  (is (= 0.5 (u/make-int-or-float 0.5))))
