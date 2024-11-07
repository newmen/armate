(ns armate.archimate.viz.align.neighbours-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.rules :as rls]
            [armate.archimate.viz.align.neighbours :as alg]))

(deftest build-up-down-map-test
  (is (= {:a [-1 1]
          :b [0 2]
          :c [-2 0]}
         (alg/build-up-down-map {:a {:b #{{:direction :up}}
                                     :c #{{:direction :down}}}
                                 :c {:b #{{:direction :up}}}})))
  (is (= {:a [-2 1]
          :b [0 3]
          :c [-2 0]}
         (alg/build-up-down-map {:a {:b #{{:direction :up :line 1}
                                          {:direction :up :line 2}}
                                     :c #{{:direction :down}}}
                                 :c {:b #{{:direction :up}}}})))
  (is (= {:a [-3 1]
          :b [-2 0]
          :c [0 1]
          :d [0 1]
          :e [0 1]
          :f [0 1]}
         (alg/build-up-down-map {:a {:b #{{:direction :down}}
                                     :c #{{:direction :up}}
                                     :d #{{:direction :up}}
                                     :e #{{:direction :up}}}
                                 :b {:f #{{:direction :up}}}})))
  (is (= {:a [-2 -1]
          :b [0 3]
          :c [-4 0]
          :e [0 1]
          :d [0 1]}
         (alg/build-up-down-map {:a {:b #{{:direction :up :line 1}
                                          {:direction :up :line 2}}
                                     :c #{{:direction :down}}
                                     :d #{{:derivate :nesting}}
                                     :e #{{:derivate :nesting}}}
                                 :c {:b #{{:direction :up}}}
                                 :d {:c #{{:direction :down}}}
                                 :e {:c #{{:direction :down}}}}))))

(deftest element-kinds-order-test
  (is (= (set alg/element-kinds-order)
         rls/possible-elements)))

(deftest build-weight-map-test
  (is (= {:a [15 17 :a]
          :b [46 48 :b]
          :c [69 71 :c]}
         (alg/build-weight-map {:elements {:a {:alias :a :kind :business-product}
                                           :b {:alias :b :kind :application-service}
                                           :c {:alias :c :kind :application-collaboration}}
                                :relations {:a {:b #{{:direction :up}}
                                                :c #{{:direction :down}}}
                                            :c {:b #{{:direction :up}}}}}))))

(deftest get-align-matrix-test
  (is (empty? (alg/get-align-matrix 0)))
  (is (= [1] (alg/get-align-matrix 1)))
  (is (= [2] (alg/get-align-matrix 2)))
  (is (= [2 2] (alg/get-align-matrix 3)))
  (is (= [2 2] (alg/get-align-matrix 4)))
  (is (= [3 2] (alg/get-align-matrix 5)))
  (is (= [3 3] (alg/get-align-matrix 6)))
  (is (= [4 3] (alg/get-align-matrix 7)))
  (is (= [3 3 3] (alg/get-align-matrix 8)))
  (is (= [3 3 3] (alg/get-align-matrix 9)))
  (is (= [4 3 3] (alg/get-align-matrix 10)))
  (is (= [4 4 3] (alg/get-align-matrix 11))))

(deftest get-align-pyramid-test
  (is (empty? (alg/get-align-pyramid 0)))
  (is (= [1] (alg/get-align-pyramid 1)))
  (is (= [1 1] (alg/get-align-pyramid 2)))
  (is (= [2 1] (alg/get-align-pyramid 3)))
  (is (= [2 1 1] (alg/get-align-pyramid 4)))
  (is (= [2 2 1] (alg/get-align-pyramid 5)))
  (is (= [3 2 1] (alg/get-align-pyramid 6)))
  (is (= [3 2 1 1] (alg/get-align-pyramid 7)))
  (is (= [3 2 2 1] (alg/get-align-pyramid 8)))
  (is (= [3 3 2 1] (alg/get-align-pyramid 9)))
  (is (= [4 3 2 1] (alg/get-align-pyramid 10)))
  (is (= [4 3 2 1 1] (alg/get-align-pyramid 11))))

(deftest align-items-with-test
  (is (empty? (alg/align-items-with [] [])))
  (is (= [[1]] (alg/align-items-with [1] [1])))
  (is (= [[1 3] [2 3]] (alg/align-items-with [2 2] [1 2 3])))
  (is (= [[1 3] [2 4]] (alg/align-items-with [2 2] [1 2 3 4])))
  (is (= [[1 3 4] [2 3 4]]
         (alg/align-items-with [2 1 1] [1 2 3 4])))
  (is (= [[1 5 8] [2 6 9] [3 7 10] [4 7 10]]
         (alg/align-items-with [4 3 3] [1 2 3 4 5 6 7 8 9 10])))
  (is (= [[1 2 3 4]]
         (alg/align-items-with [1 1 1 1] [1 2 3 4]))))

(deftest get-hidden-pairs
  (is (= #{[1 2]}
         (alg/get-hidden-pairs [[1 2]])))
  (is (= #{[1 3] [2 3]}
         (alg/get-hidden-pairs [[1 3] [2 3]])))
  (is (= #{[1 3] [2 4]}
         (alg/get-hidden-pairs [[1 3] [2 4]])))
  (is (= #{[1 3] [2 3] [3 4] [5 6]}
         (alg/get-hidden-pairs [[1 3 4] [2 3 4] [5 6]]))))

(deftest add-ud-hidden-test
  (is (= {:a {:b #{{:from :application-component
                    :to :application-interface
                    :raw "-[hidden]->"}}}}
         (alg/add-ud-hidden {:elements {:a {:kind :application-component}
                                        :b {:kind :application-interface}}}
                            {}
                            [:a :b])))
  (is (= {:a {:b #{{:from :application-component
                    :to :application-interface
                    :raw "-[hidden]->"}}
              :c #{{:from :application-component
                    :to :application-component
                    :raw "-[hidden]->"}}}}
         (alg/add-ud-hidden {:elements {:a {:kind :application-component}
                                        :b {:kind :application-interface}}}
                            {:a {:c #{{:from :application-component
                                       :to :application-component
                                       :raw "-[hidden]->"}}}}
                            [:a :b]))))

(deftest get-groups-test
  (is (empty? (alg/get-groups {})))
  (is (empty?
       (with-redefs [alg/max-in-row 2]
         (alg/get-groups {:relations {:a {:b #{{:type :composition :direction :down}}
                                          :c #{{:type :assignment :direction :up}}
                                          :d #{{:type :assignment :direction :up}}}
                                      :b {:e #{{:type :assignment :direction :up}}}}
                          :elements {:a {:kind :application-component}
                                     :b {:kind :application-component}
                                     :c {:kind :application-interface}
                                     :d {:kind :application-interface}
                                     :e {:kind :application-interface}}}))))
  (is (= #{#{:d :h} #{:e :f :g}}
         (with-redefs [alg/max-in-row 1]
           (alg/get-groups {:relations {:a {:b #{{:type :composition :direction :down}}
                                            :c #{{:type :assignment :direction :up}}}
                                        :b {:e #{{:type :assignment :direction :up}}}}
                            :elements {:a {:kind :application-component :alias :a}
                                       :b {:kind :application-component :alias :b}
                                       :c {:kind :application-interface :alias :c :in :h}
                                       :d {:kind :application-interface :alias :d :in :b}
                                       :e {:kind :application-interface :alias :e :in :a}
                                       :f {:kind :application-interface :alias :f :in :a}
                                       :g {:kind :application-interface :alias :g :in :a}
                                       :h {:kind :application-interface :alias :h :in :b}}})))))

(deftest calc-hidden-groups
  (is (= {} (alg/calc-hidden-groups {} {})))
  (is (empty?
       (with-redefs [alg/max-in-row 2]
         (let [graph {:a {:b #{{:type :composition :direction :down}}
                          :c #{{:type :assignment :direction :up}}
                          :d #{{:type :assignment :direction :up}}}
                      :b {:e #{{:type :assignment :direction :up}}}}]
           (alg/calc-hidden-groups {:relations graph
                                    :elements {:a {:kind :application-component}
                                               :b {:kind :application-component}
                                               :c {:kind :application-interface}
                                               :d {:kind :application-interface}
                                               :e {:kind :application-interface}}}
                                   (alg/build-up-down-map graph))))))
  (is (= {:c {:d #{{:from :application-interface
                    :to :application-interface
                    :raw "-[hidden]->"}}}
          :e {:d #{{:from :application-interface
                    :to :application-interface
                    :raw "-[hidden]->"}}}}
         (with-redefs [alg/max-in-row 2]
           (let [graph {:a {:b #{{:type :composition :direction :down}}
                            :c #{{:type :assignment :direction :up}}
                            :d #{{:type :assignment :direction :up}}
                            :e #{{:type :assignment :direction :up}}}
                        :b {:f #{{:type :assignment :direction :up}}}}]
             (alg/calc-hidden-groups {:relations graph
                                      :elements {:a {:kind :application-component}
                                                 :b {:kind :application-component}
                                                 :c {:kind :application-interface}
                                                 :d {:kind :application-interface}
                                                 :e {:kind :application-interface}
                                                 :f {:kind :application-interface}}}
                                     (alg/build-up-down-map graph)))))))

(deftest calc-hidden-sources-test
  (is (= {} (alg/calc-hidden-sources {} {})))
  (is (= {}
         (with-redefs [alg/too-many-rels 2]
           (let [graph {:a {:b #{{:type :composition :direction :down}}
                            :c #{{:type :assignment :direction :up}}
                            :d #{{:type :assignment :direction :up}}}
                        :b {:e #{{:type :assignment :direction :up}}}}]
             (alg/calc-hidden-sources {:relations graph
                                       :elements {:a {:kind :application-component}
                                                  :b {:kind :application-component}
                                                  :c {:kind :application-interface}
                                                  :d {:kind :application-interface}
                                                  :e {:kind :application-interface}}}
                                      (alg/build-up-down-map graph))))))
  (is (= {:a {:g #{{:from :application-component
                    :to :application-component
                    :raw "-[hidden]->"}}}}
         (with-redefs [alg/too-many-rels 2]
           (let [graph {:a {:b #{{:type :composition :direction :down}}
                            :c #{{:type :assignment :direction :up}}
                            :d #{{:type :assignment :direction :up}}}
                        :g {:e #{{:type :assignment :direction :up}}
                            :f #{{:type :assignment :direction :up}}
                            :h #{{:type :aggregation :direction :up}}}}]
             (alg/calc-hidden-sources {:relations graph
                                       :elements {:a {:kind :application-component}
                                                  :b {:kind :application-component}
                                                  :c {:kind :application-interface}
                                                  :d {:kind :application-interface}
                                                  :e {:kind :application-interface}
                                                  :f {:kind :application-interface}
                                                  :g {:kind :application-component}
                                                  :h {:kind :application-component}}}
                                      (alg/build-up-down-map graph))))))
  (is (= {:g {:a #{{:from :application-component
                    :to :application-component
                    :raw "-[hidden]->"}}}}
         (with-redefs [alg/too-many-rels 2]
           (let [graph {:a {:b #{{:type :composition :direction :up}}
                            :c #{{:type :assignment :direction :up}}
                            :d #{{:type :assignment :direction :up}}}
                        :g {:e #{{:type :assignment :direction :up}}
                            :f #{{:type :assignment :direction :up}}
                            :h #{{:type :aggregation :direction :down}}}}]
             (alg/calc-hidden-sources {:relations graph
                                       :elements {:a {:kind :application-component}
                                                  :b {:kind :application-component}
                                                  :c {:kind :application-interface}
                                                  :d {:kind :application-interface}
                                                  :e {:kind :application-interface}
                                                  :f {:kind :application-interface}
                                                  :g {:kind :application-component}
                                                  :h {:kind :application-component}}}
                                      (alg/build-up-down-map graph)))))))

(deftest calc-hiddens-test
  (is (= {} (alg/calc-hiddens {})))
  (is (= {:c {:f #{{:from :application-interface
                    :to :application-interface
                    :raw "-[hidden]->"}}}
          :d {:f #{{:from :application-interface
                    :to :application-interface
                    :raw "-[hidden]->"}}}}
         (with-redefs [alg/max-in-row 1
                       alg/too-many-rels 2]
           (alg/calc-hiddens {:relations {:a {:b #{{:type :composition :direction :down}}
                                              :c #{{:type :assignment :direction :up}}
                                              :d #{{:type :assignment :direction :up}}
                                              :f #{{:type :assignment :direction :up}}}
                                          :b {:e #{{:type :assignment :direction :up}}}}
                              :elements {:a {:kind :application-component}
                                         :b {:kind :application-component}
                                         :c {:kind :application-interface}
                                         :d {:kind :application-interface}
                                         :e {:kind :application-interface}
                                         :f {:kind :application-interface}}}))))
  (is (= {:e {:f #{{:from :application-interface
                    :to :application-interface
                    :raw "-[hidden]->"}}}
          :h {:i #{{:from :application-component
                    :to :application-component
                    :raw "-[hidden]->"}}}
          :a {:g #{{:from :application-component
                    :to :application-component
                    :raw "-[hidden]->"}}}}
         (with-redefs [alg/max-in-row 2
                       alg/too-many-rels 2]
           (alg/calc-hiddens {:relations {:a {:b #{{:type :composition :direction :down}}
                                              :c #{{:type :assignment :direction :up}}
                                              :d #{{:type :assignment :direction :up}}}
                                          :g {:e #{{:type :assignment :direction :up}}
                                              :f #{{:type :assignment :direction :up}}
                                              :h #{{:type :aggregation :direction :up}}
                                              :i #{{:type :aggregation :direction :up}}}}
                              :elements {:a {:kind :application-component}
                                         :b {:kind :application-component}
                                         :c {:kind :application-interface}
                                         :d {:kind :application-interface}
                                         :e {:kind :application-interface}
                                         :f {:kind :application-interface}
                                         :g {:kind :application-component}
                                         :h {:kind :application-component}
                                         :i {:kind :application-component}}}))))
  (is (= {:b {:d #{{:from :application-component
                    :to :application-interface
                    :raw "-[hidden]->"}}}
          :c {:d #{{:from :application-interface
                    :to :application-interface
                    :raw "-[hidden]->"}}}
          :g {:a #{{:from :application-component
                    :to :application-component
                    :raw "-[hidden]->"}}}}
         (with-redefs [alg/max-in-row 2
                       alg/too-many-rels 2]
           (alg/calc-hiddens {:relations {:a {:b #{{:type :composition :direction :up}}
                                              :c #{{:type :assignment :direction :up}}
                                              :d #{{:type :assignment :direction :up}}}
                                          :g {:e #{{:type :assignment :direction :up}}
                                              :f #{{:type :assignment :direction :up}}
                                              :h #{{:type :aggregation :direction :down}}}}
                              :elements {:a {:kind :application-component}
                                         :b {:kind :application-component}
                                         :c {:kind :application-interface}
                                         :d {:kind :application-interface}
                                         :e {:kind :application-interface}
                                         :f {:kind :application-interface}
                                         :g {:kind :application-component}
                                         :h {:kind :application-component}}})))))
