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

(def transitive-graph
  (assoc-in graph0 [:b :c] #{{:type :assignment}}))

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

(deftest get-nbrs-test
  (is (empty? (mg/get-nbrs :assignment graph0 :e)))
  (is (= #{:b :c} (mg/get-nbrs :assignment graph0 :a)))
  (is (= #{:b} (mg/get-nbrs :serving graph0 :a))))

(deftest detect-transitive-relationships-test
  (is (empty? (mg/detect-transitive-relationships :assignment graph0)))
  (is (= [[:a :c]]
         (mg/detect-transitive-relationships :assignment transitive-graph)))
  (is (empty? (mg/detect-transitive-relationships :serving transitive-graph))))

(deftest erase-transitive-relationships-test
  (is (= graph0
         (mg/erase-transitive-relationships :assignment graph0)))
  (is (= (update transitive-graph :a dissoc :c)
         (mg/erase-transitive-relationships :assignment transitive-graph))))
