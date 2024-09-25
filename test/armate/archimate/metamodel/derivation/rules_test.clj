(ns armate.derivation.rules-test
  (:require [clojure.test :refer [deftest is]]
            [armate.derivation.rules :as rs]))

(deftest normalize-test
  (is (= [[:specialization :a :b] [:flow :c :b] [:flow :c :a]]
         (rs/normalize [[:specialization :a :b] [:flow :c :b] [:flow :c :a]])))
  (is (= [[:specialization :a :b] [:flow :b :c] [:flow :a :c]]
         (rs/normalize [[:specialization :a :b] [:flow :b :c] [:flow :a :c]])))
  (is (= [[:access :a :b] [:realization :c :b] [:access :a :c]]
         (rs/normalize [[:access :c :b] [:realization :a :b] [:access :c :a]])))
  (is (= [[:access :a :b] [:realization :c :b] [:access :a :c]]
         (rs/normalize [[:access :c :a] [:realization :b :a] [:access :c :b]]))))

(deftest check-invariants-test
  (is (empty? (rs/check-invariants rs/certain-rules)))
  (is (empty? (rs/check-invariants rs/potential-rules)))
  (is (empty? (rs/check-invariants rs/potential-group-around-rules)))
  (is (= {[[:access :c :b] [:realization :a :b] [:access :c :a]]
          [[[:access :c :a] [:realization :b :a] [:access :c :b]]
           [[:access :b :a] [:realization :c :a] [:access :b :c]]]}
         (rs/check-invariants [[[:specialization :a :b] [:flow :c :b] [:flow :c :a]]
                               [[:specialization :a :b] [:flow :b :c] [:flow :a :c]]
                               [[:access :c :b] [:realization :a :b] [:access :c :a]]
                               [[:access :c :a] [:realization :b :a] [:access :c :b]]
                               [[:access :b :a] [:realization :c :a] [:access :b :c]]])))
  (doseq [rule [[[:flow :a :b] [:flow :c :d] [:flow :a :d]]
                [[:flow :a :b] [:flow :b :a] [:flow :a :b]]
                [[:flow :a :b] [:flow :c :b] [:flow :c :c]]
                [[:flow :a :b] [:flow :a :d] [:flow :d :a]]
                [[:flow :a :b] [:flow :a :d] [:flow :x :d]]]]
    (is (thrown? UnsupportedOperationException (rs/check-invariants rule)))))
