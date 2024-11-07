(ns armate.archimate.viz.align.grid-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.viz.align.grid :as grid]))

(def graph0
  {:a {:b #{{:type :composition :direction :down}}
       :c #{{:type :assignment :direction :up}}
       :d #{{:type :assignment :direction :up}}}
   :b {:e #{{:type :assignment :direction :up}}
       :f #{{:type :assignment :direction :down}}}})

(defn get-nodes
  [graph]
  (set (mapcat (comp (partial apply concat)
                     (juxt (comp vector first)
                           (comp keys second)))
               graph)))

(deftest get-deps-test
  (is (= {:ud {} :du {}}
         (grid/get-deps {})))
  (is (= {:ud {:a #{:b} :b #{:f} :c #{:a} :d #{:a} :e #{:b}}
          :du {:a #{:c :d} :b #{:e :a} :f #{:b}}}
         (grid/get-deps graph0))))

(deftest find-layers-test
  (let [deps (grid/get-deps graph0)
        nodes (get-nodes graph0)]
    (is (= {:a 2 :b 1 :c 3 :d 3 :e 2 :f 0}
           (grid/find-layers (:ud deps) nodes)))
    (is (= {:a 1 :b 2 :c 0 :d 0 :e 0 :f 3}
           (grid/find-layers (:du deps) nodes)))))

(deftest assign-layers
  (is (= {:a 1 :b 2 :c 0 :d 0 :e 1 :f 3}
         (grid/assign-layers graph0 (get-nodes graph0)))))

(deftest get-layers-test
  (is (= [#{:c :d} #{:e :a} #{:b} #{:f}]
         (grid/get-layers {:relations graph0
                           :elements (->> (get-nodes graph0)
                                          (map #(vector % {}))
                                          (into {}))}))))
