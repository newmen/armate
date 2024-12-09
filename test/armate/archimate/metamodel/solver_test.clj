(ns armate.archimate.metamodel.solver-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.metamodel.solver :as slv]))

(deftest build-flat-hierarchy-test
  (is (= {}
         (slv/build-flat-hierarchy {})))
  (is (= {:a #{:a}}
         (slv/build-flat-hierarchy {:a #{}})))
  (is (= {:a #{:a}
          :b #{:c}
          :c #{:c}}
         (slv/build-flat-hierarchy {:a #{}
                                    :b #{:c}})))
  (is (= {:a #{:d :f :g}
          :b #{:b}
          :c #{:d :f :g}
          :d #{:d}
          :e #{:f :g}
          :f #{:f}
          :g #{:g}}
         (slv/build-flat-hierarchy {:a {:c {:d #{}
                                            :e #{:f :g}}}
                                    :b #{}})))
  (is (= {:a #{:d :f}
          :b #{:d :f}
          :c #{:d}
          :d #{:d}
          :e #{:d :f}
          :f #{:f}}
         (slv/build-flat-hierarchy {:a {:c #{:d}
                                        :e #{:c :f}}
                                    :b #{:a}})))
  (is (= {:a #{:a}
          :b #{:b}}
         (slv/build-flat-hierarchy {:a #{:b}
                                    :b #{:a}})))
  (is (= {:a #{:a :c}
          :b #{:b :c}
          :c #{:c}}
         (slv/build-flat-hierarchy {:a #{:b :c}
                                    :b #{:a}})))
  (is (= {:a #{:a}
          :b #{:b}
          :c #{:c}}
         (slv/build-flat-hierarchy {:a #{:b}
                                    :b #{:c}
                                    :c #{:a}}))))

(deftest multiply-relationships-test
  (is (= {}
         (slv/multiply-relationships {} {} {})))
  (is (= {:b {:e #{1} :f #{2 3} :g #{3 5} :b #{4} :i #{5} :j #{5}}
          :c {:e #{1} :i #{6}}
          :d {:e #{1} :f #{2 3} :g #{3 5} :b #{4} :i #{5} :j #{5}}
          :e {:e #{1}}
          :f {:e #{1} :b #{4} :g #{5} :i #{5} :j #{5}}
          :g {:e #{1} :b #{4} :g #{5} :i #{5} :j #{5}}}
         (let [flat-hierarchy (slv/build-flat-hierarchy {:a #{:b :c :d :e :f :g}
                                                         :h #{:g :i :j}})
               flat-layers (slv/build-flat-hierarchy flat-hierarchy {:x #{:b :d :i}
                                                                     :y #{:f :g :j}})]
           (slv/multiply-relationships flat-hierarchy flat-layers
                                       {:a {:e #{1}}
                                        [:a #{:x}] {:f #{2}
                                                    [:a #{:y}] #{3}}
                                        [:a #{:x :y}] {:b #{4}
                                                       :h #{5}}
                                        :c {[:h #{:x}] #{6}}})))))
