(ns armate.archimate.viz.saver-test
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.viz.saver :as svr]))

(def graph
  {:relations {:a {:b #{{:type :composition :direction :down}}
                   :c #{{:type :assignment :direction :up}}
                   :d #{{:type :assignment :direction :up}}}
               :b {:e #{{:type :assignment :direction :up}}}}
   :elements {:a {:kind :application-component}
              :b {:kind :application-component}
              :c {:kind :application-interface}
              :d {:kind :application-interface}
              :e {:kind :application-interface}}})

(deftest align-toggle-test
  (testing "align? false leaves the context unchanged (no hidden edges)"
    (let [ctx (svr/align-elements false graph)]
      (is (= (select-keys graph [:relations :elements])
             (select-keys ctx [:relations :elements])))
      (is (nil? (:hidden ctx)))))
  (testing "align? true appends hidden edges"
    (let [ctx (svr/align-elements true graph)]
      (is (some? (:hidden ctx))))))