(ns armate.archimate.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.archi.parser :as arr]
            [armate.archimate.core :as core]))

(def demo-path "test/resources/demo.archimate")

(def demo-context
  (-> (arr/get-model demo-path)
      (:source)
      (arr/get-full-graph)
      (first)))

(deftest get-stats-on-demo
  (testing "get-stats reports the demo model's elements and relations"
    (let [stats (core/get-stats demo-context)]
      (is (= 44 (:types stats)))
      (is (= 32 (get-in stats [:elements :total])))
      (is (= {:business 31 :strategy 1}
             (get-in stats [:elements :layer])))
      (is (= 57 (get-in stats [:relations :original :total])))
      (is (= 12 (get-in stats [:relations :original :groups :dynamic])))
      (is (= 0 (get-in stats [:lints]))))))