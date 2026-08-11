(ns armate.archimate.metamodel.meta-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.metamodel.meta :as mt]))

(deftest element?-test
  (is (not (mt/element? :element)))
  (is (not (mt/element? :realization)))
  (is (not (mt/element? :and)))
  (is (mt/element? :business-object))
  (is (mt/element? :application-component)))

(deftest relationship?-test
  (is (not (mt/relationship? :relationship)))
  (is (not (mt/relationship? :business-object)))
  (is (not (mt/relationship? :application-component)))
  (is (not (mt/relationship? :and)))
  (is (mt/relationship? :realization)))

(deftest connector?-test
  (is (not (mt/connector? :connector)))
  (is (not (mt/connector? :business-object)))
  (is (not (mt/connector? :application-component)))
  (is (not (mt/connector? :realization)))
  (is (mt/connector? :and)))
