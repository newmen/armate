(ns armate.archimate.metamodel.solver-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.metamodel.solver :as slv]))

(deftest merge-into-test
  (is (= {} (slv/merge-into {})))
  (is (= {} (slv/merge-into {} {})))
  (is (= {} (slv/merge-into {} {} {})))
  (is (= {:a {:b #{1}}}
         (slv/merge-into {:a {:b #{1}}})))
  (is (= {:a {:b #{1}
              :c #{2}}}
         (slv/merge-into {:a {:b #{1}}} {:a {:c #{2}}})))
  (is (= {:a {:b #{1 2}}}
         (slv/merge-into {:a {:b #{1}}} {:a {:b #{2}}}))))

(deftest assoc-if-not-same-test
  (is (= {:a #{:b}} (slv/assoc-if-not-same {} :a #{:b})))
  (is (= {:a #{:b}} (slv/assoc-if-not-same {:a #{:a}} :a #{:b})))
  (is (= {:a #{:b}} (slv/assoc-if-not-same {:a #{:b}} :a #{:a})))
  (is (= {:a #{:b}} (slv/assoc-if-not-same {:a #{:b}} :a #{:c}))))

(deftest build-flat-hierarchy-test
  (is (= {} (slv/build-flat-hierarchy {})))
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
                                    :c #{:a}})))
  (is (= {:a #{:a}
          :b #{:b :c}
          :c #{:b :c}}
         (slv/build-flat-hierarchy #{:b}
                                   {:a #{}
                                    :b #{:c}})))
  (is (= {:a #{:c :d :e}
          :b #{:c :d :e}
          :c #{:c :d :e}
          :d #{:c :d :e}
          :e #{:c :d :e}}
         (slv/build-flat-hierarchy #{:c}
                                   {:a #{:e}
                                    :b {:c #{:d :e}}}))))

(deftest multiply-relationships-test
  (is (= {} (slv/multiply-relationships {} {} {})))
  (is (= {:b {:e #{1} :f #{2 3} :g #{3 5} :b #{4} :i #{5} :j #{5}}
          :c {:e #{1} :i #{6}}
          :d {:e #{1} :f #{2 3} :g #{3 5} :b #{4} :i #{5} :j #{5}}
          :e {:e #{1}}
          :f {:e #{1} :b #{4} :g #{5} :i #{5} :j #{5}}
          :g {:e #{1} :b #{4} :g #{5} :i #{5} :j #{5}}}
         (let [flat-hierarchy (slv/build-flat-hierarchy {:a #{:b :c :d :e :f :g}
                                                         :h #{:g :i :j}})
               flat-domains (slv/extend-hierarchy flat-hierarchy {:x #{:b :d :i}
                                                                  :y #{:f :g :j}})]
           (slv/multiply-relationships flat-hierarchy flat-domains
                                       {:a {:e #{1}}
                                        [:a #{:x}] {:f #{2}
                                                    [:a #{:y}] #{3}}
                                        [:a #{:x :y}] {:b #{4}
                                                       :h #{5}}
                                        :c {[:h #{:x}] #{6}}})))))

(deftest rel-rules-to-mg-test
  (is (= {} (slv/rel-rules-to-mg {} (constantly false))))
  (is (= {:a {:b #{{:type 1}}}
          :c {:b #{{:type 2}}
              :d #{{:type 3}}}}
         (slv/rel-rules-to-mg {:a {:b #{1}}
                               :c {:b #{2}
                                   :d #{3}}}
                              (constantly false))))
  (is (= {:c {:b #{{:type 2}}
              :d #{{:type 3}}}}
         (slv/rel-rules-to-mg {:a {:b #{1}}
                               :c {:b #{2}
                                   :d #{3}}}
                              #{:a})))
  (is (= {:c {:d #{{:type 3}}}}
         (slv/rel-rules-to-mg {:a {:b #{1}}
                               :c {:b #{2}
                                   :d #{3}}}
                              #{:b}))))

(deftest mg-to-rel-rules-test
  (is (= {} (slv/mg-to-rel-rules {})))
  (is (= {:a {:b #{1}}
          :c {:b #{2}
              :d #{3}}}
         (slv/mg-to-rel-rules {:a {:b #{{:type 1}}}
                               :c {:b #{{:type 2}}
                                   :d #{{:type 3}}}}))))