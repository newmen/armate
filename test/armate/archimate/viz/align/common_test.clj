(ns armate.archimate.viz.align.common-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.viz.align.common :as cmn]))

(deftest get-align-matrix-test
  (is (empty? (cmn/get-align-matrix 0)))
  (is (= [1] (cmn/get-align-matrix 1)))
  (is (= [2] (cmn/get-align-matrix 2)))
  (is (= [2 2] (cmn/get-align-matrix 3)))
  (is (= [2 2] (cmn/get-align-matrix 4)))
  (is (= [3 2] (cmn/get-align-matrix 5)))
  (is (= [3 3] (cmn/get-align-matrix 6)))
  (is (= [4 3] (cmn/get-align-matrix 7)))
  (is (= [3 3 3] (cmn/get-align-matrix 8)))
  (is (= [3 3 3] (cmn/get-align-matrix 9)))
  (is (= [4 3 3] (cmn/get-align-matrix 10)))
  (is (= [4 4 3] (cmn/get-align-matrix 11))))

(deftest add-ud-hidden-test
  (is (= {:a {:b #{{:from :application-component
                    :to :application-interface
                    :raw "-[hidden]->"}}}}
         (cmn/add-ud-hidden {:elements {:a {:kind :application-component}
                                        :b {:kind :application-interface}}}
                            {}
                            [:a :b])))
  (is (= {:a {:b #{{:from :application-component
                    :to :application-interface
                    :raw "-[hidden]->"}}
              :c #{{:from :application-component
                    :to :application-component
                    :raw "-[hidden]->"}}}}
         (cmn/add-ud-hidden {:elements {:a {:kind :application-component}
                                        :b {:kind :application-interface}}}
                            {:a {:c #{{:from :application-component
                                       :to :application-component
                                       :raw "-[hidden]->"}}}}
                            [:a :b]))))

(deftest get-groups-test
  (is (empty? (cmn/get-groups 1 {})))
  (is (empty?
       (cmn/get-groups 1 {:elements {:a {:kind :application-component}
                                     :b {:kind :application-component}
                                     :c {:kind :application-interface}
                                     :d {:kind :application-interface}
                                     :e {:kind :application-interface}}})))
  (is (= #{#{:d :h} #{:e :f :g}}
         (cmn/get-groups 1 {:elements {:a {:kind :application-component :alias :a}
                                       :b {:kind :application-component :alias :b}
                                       :c {:kind :application-interface :alias :c :in :h}
                                       :d {:kind :application-interface :alias :d :in :b}
                                       :e {:kind :application-interface :alias :e :in :a}
                                       :f {:kind :application-interface :alias :f :in :a}
                                       :g {:kind :application-interface :alias :g :in :a}
                                       :h {:kind :application-interface :alias :h :in :b}}}))))
