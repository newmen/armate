(ns armate.archimate.viz.align.grid-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.multi-graph :as mg]
            [armate.archimate.viz.align.grid :as grid]))

(def graph0
  {:a {:b #{{:type :composition :direction :down}}
       :c #{{:type :assignment :direction :up}}
       :d #{{:type :assignment :direction :up}}}
   :b {:e #{{:type :assignment :direction :up}}
       :f #{{:type :assignment :direction :down}}}})

(def graph1
  {:a {:b #{{:type :composition :direction :down}}
       :c #{{:type :assignment :direction :up}}
       :d #{{:type :assignment :direction :up}}}
   :b {:e #{{:type :assignment :direction :up}}
       :f #{{:type :assignment :direction :down}}}
   :e {:g #{{:type :assignment :direction :up}}}
   :g {:h #{{:type :assignment :direction :up}}}})

(deftest get-deps-test
  (is (= {:ud {} :du {}}
         (grid/get-deps {})))
  (is (= {:ud {:a #{:b} :b #{:f} :c #{:a} :d #{:a} :e #{:b}}
          :du {:a #{:c :d} :b #{:e :a} :f #{:b}}}
         (grid/get-deps graph0)))
  (is (= {:ud {:a #{:b} :b #{:f} :c #{:a} :d #{:a} :e #{:b} :g #{:e} :h #{:g}}
          :du {:a #{:c :d} :b #{:e :a} :f #{:b} :e #{:g} :g #{:h}}}
         (grid/get-deps graph1))))

(deftest find-layers-test
  (let [deps (grid/get-deps graph0)
        nodes (mg/get-nodes graph0)]
    (is (= {:a 2 :b 1 :c 3 :d 3 :e 2 :f 0}
           (grid/find-layers (:ud deps) nodes)))
    (is (= {:a 1 :b 2 :c 0 :d 0 :e 0 :f 3}
           (grid/find-layers (:du deps) nodes))))
  (let [deps (grid/get-deps graph1)
        nodes (mg/get-nodes graph1)]
    (is (= {:a 2 :b 1 :c 3 :d 3 :e 2 :f 0 :g 3 :h 4}
           (grid/find-layers (:ud deps) nodes)))
    (is (= {:a 1 :b 3 :c 0 :d 0 :e 2 :f 4 :g 1 :h 0}
           (grid/find-layers (:du deps) nodes)))))

(deftest assign-layers
  (is (= {:a 1 :b 2 :c 0 :d 0 :e 1 :f 3}
         (grid/assign-layers graph0 (mg/get-nodes graph0))))
  (is (= {:a 2 :b 3 :c 1 :d 1 :e 2 :f 4 :g 1 :h 0}
         (grid/assign-layers graph1 (mg/get-nodes graph1))))
  (is (= {:a 2 :b 3 :c 1 :e 2}
         (grid/assign-layers graph1 [:a :b :c :e]))))

(deftest get-layers-test
  (let [ctxf (fn [graph]
               {:relations graph
                :elements (->> (mg/get-nodes graph)
                               (map #(vector % {}))
                               (into {}))})]
    (is (= [#{:c :d} #{:a :e} #{:b} #{:f}]
           (grid/get-layers (ctxf graph0))))
    (is (= [#{:h} #{:c :d :g} #{:a :e} #{:b} #{:f}]
           (grid/get-layers (ctxf graph1))))))
