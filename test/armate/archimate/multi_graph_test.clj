(ns armate.archimate.multi-graph-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.multi-graph :as mg]))

(def graph0
  {:a {:b #{{:type :assignment}
            {:type :serving}}
       :c #{{:type :assignment}}}
   :b {:d #{{:type :realization}}}
   :c {:d #{{:type :composition}}}
   :d {:a #{{:type :flow}}
       :e #{{:type :aggregation}}}})

(deftest get-relationship-sets-test
  (is (= #{[:a :b #{{:type :assignment}
                    {:type :serving}}]
           [:a :c #{{:type :assignment}}]
           [:b :d #{{:type :realization}}]
           [:c :d #{{:type :composition}}]
           [:d :a #{{:type :flow}}]
           [:d :e #{{:type :aggregation}}]}
         (set (mg/get-relationship-sets graph0)))))

(deftest get-relationships-test
  (is (= #{[:a :b {:type :assignment}]
           [:a :b {:type :serving}]
           [:a :c {:type :assignment}]
           [:b :d {:type :realization}]
           [:c :d {:type :composition}]
           [:d :a {:type :flow}]
           [:d :e {:type :aggregation}]}
         (set (mg/get-relationships graph0)))))

(deftest filter-relationships-test
  (is (= {:a {:b #{{:type :assignment}}
              :c #{{:type :assignment}}}}
         (mg/filter-relationships (comp (partial = :assignment) :type last)
                                  graph0)))
  (is (= {:c {:d #{{:type :composition}}}
          :d {:e #{{:type :aggregation}}}}
         (mg/filter-relationships (comp #{:aggregation :composition} :type last)
                                  graph0))))
