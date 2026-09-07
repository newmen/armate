(ns armate.archimate.metamodel.rank-test
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.metamodel.rank :as rank]
            [armate.archimate.metamodel.derivation.rules :as drs]
            [armate.archimate.metamodel.derivation.match :as mch]))

(deftest element-rank-test
  (testing "layer ranks match the documented order"
    (is (= 0 (rank/element-rank {:layer :motivation})))
    (is (= 1 (rank/element-rank {:layer :strategy})))
    (is (= 2 (rank/element-rank {:layer :business})))
    (is (= 3 (rank/element-rank {:layer :application})))
    (is (= 4 (rank/element-rank {:layer :technology})))
    (is (= 5 (rank/element-rank {:layer :implementation})))
    (is (= rank/default-layer-rank (rank/element-rank {:layer :unknown})))))

(deftest kind-weights-test
  (testing "kind weights ascend by steps of 5 from the kinds order"
    (is (= 1 (rank/kind-weights (first rank/element-kinds-order))))
    (is (= 96 (rank/kind-weights :technology-interaction)))
    (is (every? #(= (rank/kind-weights %)
                    (+ 1 (* 5 (count (take-while (partial not= %) rank/element-kinds-order)))))
                rank/element-kinds-order))))

(deftest technology-system-software-slot-test
  (testing "reordered slot keeps its documented position, before application-component"
    (is (= :technology-system-software
           (nth rank/element-kinds-order 12)))
    (is (= 61 (rank/kind-weights :technology-system-software)))
    (is (= 66 (rank/kind-weights :application-component)))
    (is (> (count (filter #(= :technology-system-software %)
                          (take-while (partial not= :application-component)
                                      rank/element-kinds-order)))
           0))))

(def all-rels
  (concat drs/dynamic-rels drs/dependency-rels drs/structural-rels [:specialization]))

(deftest relation-weights-are-a-single-table-test
  (testing "the seam exposes one weight per relation, read by both adapters"
    (is (every? pos? (map rank/relation-weight all-rels)))
    (is (= 1 (rank/relation-weight :triggering)))
    (is (= 2 (rank/relation-weight :flow)))
    (is (= 100 (rank/relation-weight :association)))
    (is (= 800 (rank/relation-weight :serving)))
    (is (= 1000 (rank/relation-weight :realization)))
    (is (= 4000 (rank/relation-weight :composition)))
    (is (= 10000 (rank/relation-weight :specialization)))
    (is (thrown? Exception (rank/relation-weight :bogus)))))

(deftest derive-and-render-agree-on-weights-test
  (testing "deleting the seam would force both adapters to re-declare the same weights"
    ;; The renderer orders relations by descending relation-weight.
    (let [render-order (->> [:flow :serving :assignment :composition]
                            (sort-by (comp - rank/relation-weight))
                            (vec))]
      (is (= [:composition :assignment :serving :flow] render-order))
      ;; The deriver orders a rule group the same way (second-step rel by weight).
      (let [rules (mapv (fn [rel]
                          [[:serving :a :b] [rel :b :c] [:access :a :c]])
                        [:flow :serving :assignment :composition])
            rule-map (mch/make-rules-map rules)
            group (get rule-map :serving)]
        (is (= (count rules) (count group)))
        (is (= render-order
               (mapv (comp first second) group)))))))