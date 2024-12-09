(ns armate.archimate.metamodel.derivation.rules-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.metamodel.derivation.rules :as drs]))

(deftest normalize-test
  (is (= [[:specialization :a :b] [:flow :c :b] [:flow :c :a]]
         (drs/normalize [[:specialization :a :b] [:flow :c :b] [:flow :c :a]])))
  (is (= [[:specialization :a :b] [:flow :b :c] [:flow :a :c]]
         (drs/normalize [[:specialization :a :b] [:flow :b :c] [:flow :a :c]])))
  (is (= [[:access :a :b] [:realization :c :b] [:access :a :c]]
         (drs/normalize [[:access :c :b] [:realization :a :b] [:access :c :a]])))
  (is (= [[:access :a :b] [:realization :c :b] [:access :a :c]]
         (drs/normalize [[:access :c :a] [:realization :b :a] [:access :c :b]]))))

(deftest check-invariants-test
  (is (empty? (drs/check-invariants drs/certain-rules)))
  (is (empty? (drs/check-invariants drs/potential-rules)))
  (is (empty? (drs/check-invariants drs/potential-group-around-rules)))
  (is (= {[[:access :c :b] [:realization :a :b] [:access :c :a]]
          [[[:access :c :a] [:realization :b :a] [:access :c :b]]
           [[:access :b :a] [:realization :c :a] [:access :b :c]]]}
         (drs/check-invariants [[[:specialization :a :b] [:flow :c :b] [:flow :c :a]]
                                [[:specialization :a :b] [:flow :b :c] [:flow :a :c]]
                                [[:access :c :b] [:realization :a :b] [:access :c :a]]
                                [[:access :c :a] [:realization :b :a] [:access :c :b]]
                                [[:access :b :a] [:realization :c :a] [:access :b :c]]])))
  (doseq [rule [[[:flow :a :b] [:flow :c :d] [:flow :a :d]]
                [[:flow :a :b] [:flow :b :a] [:flow :a :b]]
                [[:flow :a :b] [:flow :c :b] [:flow :c :c]]
                [[:flow :a :b] [:flow :a :d] [:flow :d :a]]
                [[:flow :a :b] [:flow :a :d] [:flow :x :d]]]]
    (is (thrown? UnsupportedOperationException (drs/check-invariants rule)))))
