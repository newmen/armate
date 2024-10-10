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

(deftest get-relationships-test
  (is (= #{[:a :b {:type :assignment}]
           [:a :b {:type :serving}]
           [:a :c {:type :assignment}]
           [:b :d {:type :realization}]
           [:c :d {:type :composition}]
           [:d :a {:type :flow}]
           [:d :e {:type :aggregation}]}
         (set (mg/get-relationships graph0)))))
