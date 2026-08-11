(ns armate.archimate.multi-graph-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.multi-graph :as mg]))

(def graph0
  {:a {:b #{{:type :assignment}
            {:type :serving}}
       :c #{{:type :assignment}}}
   :b {:d #{{:type :realization}}
       :x #{{:type :assignment}}}
   :c {:d #{{:type :composition}}}
   :d {:a #{{:type :flow}}
       :e #{{:type :aggregation}}}})

(def transitive-graph
  (assoc-in graph0 [:b :c] #{{:type :assignment}}))

(def transitive-graph-diff-rels
  (assoc-in graph0 [:b :c] #{{:type :realization}}))

(def small-trans-graph
  {:p {:x #{{:type :assignment}}
       :y #{{:type :assignment}}}
   :a {:b #{{:type :assignment}}
       :c #{{:type :realization}}
       :p #{{:type :specialization}}}
   :b {:c #{{:type :realization}}}})

(def real-trans-graph
  {"acp35"
   {"asv30"
    #{{:from :application-component :to :application-service :type :realization}}
    "afn60"
    #{{:from :application-component :to :application-function :type :assignment}}
    "acp41"
    #{{:direction :up :from :application-component :to :application-component :type :specialization}}}
   "afn60"
   {"asv30" #{{:from :application-function :to :application-service :type :realization}}}
   "acp41"
   {"ae81" #{{:from :application-component :to :application-event :type :assignment}}
    "ae25" #{{:from :application-component :to :application-event :type :assignment}}}})

(def cyclic1-graph
  (-> graph0
      (assoc-in [:b :a] #{{:type :serving}})
      (assoc-in [:a :e] #{{:type :serving}})))

(deftest reverse-graph-test
  (is (= {:a {:d #{{:type :flow}}}
          :b {:a #{{:type :assignment} {:type :serving}}}
          :c {:a #{{:type :assignment}}}
          :d {:b #{{:type :realization}}
              :c #{{:type :composition}}}
          :e {:d #{{:type :aggregation}}}
          :x {:b #{{:type :assignment}}}}
         (mg/reverse-graph graph0))))

(deftest get-relationship-sets-test
  (is (= #{[:a :b #{{:type :assignment}
                    {:type :serving}}]
           [:a :c #{{:type :assignment}}]
           [:b :d #{{:type :realization}}]
           [:b :x #{{:type :assignment}}]
           [:c :d #{{:type :composition}}]
           [:d :a #{{:type :flow}}]
           [:d :e #{{:type :aggregation}}]}
         (set (mg/get-relationship-sets graph0)))))

(deftest get-relationships-test
  (is (= #{[:a :b {:type :assignment}]
           [:a :b {:type :serving}]
           [:a :c {:type :assignment}]
           [:b :d {:type :realization}]
           [:b :x {:type :assignment}]
           [:c :d {:type :composition}]
           [:d :a {:type :flow}]
           [:d :e {:type :aggregation}]}
         (set (mg/get-relationships graph0)))))

(deftest filter-relationships-test
  (is (= {:a {:b #{{:type :assignment}}
              :c #{{:type :assignment}}}
          :b {:x #{{:type :assignment}}}}
         (mg/filter-relationships (comp (partial = :assignment) :type last)
                                  graph0)))
  (is (= {:c {:d #{{:type :composition}}}
          :d {:e #{{:type :aggregation}}}}
         (mg/filter-relationships (comp #{:aggregation :composition} :type last)
                                  graph0))))

(deftest get-type-nbrs-test
  (is (empty? (mg/get-type-nbrs #{:assignment} graph0 :e)))
  (is (= #{:b :c} (mg/get-type-nbrs #{:assignment} graph0 :a)))
  (is (= #{:d :x} (mg/get-type-nbrs #{:assignment :realization} graph0 :b)))
  (is (= #{:b} (mg/get-type-nbrs #{:serving} graph0 :a))))

(deftest detect-transitive-relationships-test
  (is (empty? (mg/detect-transitive-relationships #{:assignment} graph0)))
  (is (empty? (mg/detect-transitive-relationships #{:serving} cyclic1-graph)))
  (is (= [[:a :c]]
         (mg/detect-transitive-relationships #{:assignment} transitive-graph)))
  (is (empty? (mg/detect-transitive-relationships #{:serving} transitive-graph)))
  (is (= [[:a :c]]
         (mg/detect-transitive-relationships #{:assignment :realization}
                                             transitive-graph-diff-rels)))
  (is (empty? (mg/detect-transitive-relationships #{:assignment :composition}
                                                  transitive-graph-diff-rels)))
  (is (empty? (mg/detect-transitive-relationships #{:realization} small-trans-graph)))
  (is (empty? (mg/detect-transitive-relationships #{:assignment} small-trans-graph)))
  (is (= [[:a :c]]
         (mg/detect-transitive-relationships #{:assignment :realization} small-trans-graph)))
  (is (= [[:a :c]]
         (mg/detect-transitive-relationships #{:assignment :realization :specialization}
                                             small-trans-graph)))
  (is (= [["acp35" "asv30"]]
         (mg/detect-transitive-relationships #{:assignment :realization} real-trans-graph))))

(deftest erase-transitive-relationships-test
  (is (= graph0
         (mg/erase-transitive-relationships #{:assignment} graph0)))
  (is (= (update transitive-graph :a dissoc :c)
         (mg/erase-transitive-relationships #{:assignment} transitive-graph)))
  (is (= (update transitive-graph-diff-rels :a dissoc :c)
         (mg/erase-transitive-relationships #{:assignment :realization} transitive-graph-diff-rels)))
  (is (= (update small-trans-graph :a dissoc :c)
         (mg/erase-transitive-relationships #{:assignment :realization} small-trans-graph)))
  (is (= (update real-trans-graph "acp35" dissoc "asv30")
         (mg/erase-transitive-relationships #{:assignment :realization} real-trans-graph))))

(deftest detect-cyclic1-relationships-test
  (is (empty? (mg/detect-cyclic1-relationships :assignment graph0)))
  (is (empty? (mg/detect-cyclic1-relationships :assignment transitive-graph)))
  (is (empty? (mg/detect-cyclic1-relationships :assignment cyclic1-graph)))
  (is (= #{#{:a :b}}
         (mg/detect-cyclic1-relationships :serving cyclic1-graph))))

(deftest erase-cyclic1-relationships-test
  (is (= graph0
         (mg/erase-cyclic1-relationships :assignment graph0)))
  (is (= cyclic1-graph
         (mg/erase-cyclic1-relationships :assignment cyclic1-graph)))
  (is (= (update cyclic1-graph :b dissoc :a)
         (mg/erase-cyclic1-relationships :serving cyclic1-graph))))
