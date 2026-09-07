(ns armate.archimate.archi.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.archi.parser :as arr]))

(defn- element
  ([id]
   (element id "ApplicationComponent"))
  ([id xtype]
   {:tag :element
    :attrs {:id id
            :xsi:type (str "archimate:" xtype)
            :name (str "El " id)}}))

(defn- elements
  [n]
  (mapv #(element (str "e" %)) (range n)))

(defn- element-aliases
  [context]
  (->> (:elements context)
       (keys)
       (sort)))

(deftest get-idx-pure-and-deterministic
  (testing "fresh id-map seeds at 1 and allocates sequentially"
    (let [[a m1] (arr/get-idx {} "x")
          [b _] (arr/get-idx m1 "y")
          [a2 _] (arr/get-idx m1 "x")]
      (is (= "1" a))
      (is (= "2" b))
      (is (= "1" a2) "re-reading an already-known id returns its preserved ordinal")))
  (testing "same id-map always yields the same allocation (pure)"
    (is (= (arr/get-idx {} "x")
           (arr/get-idx {} "x")))))

(deftest same-model-parsed-twice-yields-identical-aliases
  (testing "get-full-graph with a fresh id-map is deterministic across calls"
    (let [model {:elements (elements 5) :relations []}
          g1 (arr/get-full-graph model)
          g2 (arr/get-full-graph model)]
      (is (= (element-aliases (first g1))
             (element-aliases (first g2)))
          "two fresh parses of the same model share element aliases"))))

(deftest id-map-threading-continues-from-seed
  (testing "a pre-populated id-map continues counting instead of resetting"
    (let [[_ id-map] (arr/get-idx {} "x")       ; allocate "1"
          model {:elements [(element "x")
                            (element "y")]
                 :relations []}
          [graph result-id-map] (arr/get-full-graph model nil id-map)]
      (is (= {"x" "1" "y" "2"} result-id-map)
          "threaded id-map allocates a fresh ordinal after the seed")
      (is (= ["acp1" "acp2"] (element-aliases graph))
          "acp1 comes from the seed, acp2 is newly allocated"))))

(deftest parallel-view-build-matches-serial
  (testing "independent views built in parallel, each with a fresh id-map, equal a serial build"
    (let [model {:elements (elements 6) :relations []}
          build-fresh (fn [_] (element-aliases (first (arr/get-full-graph model))))
          serial (build-fresh nil)
          parallel (into #{} (pmap build-fresh (range 8)))]
      (is (= #{serial} parallel)
          "every parallel fresh-seed build yields the aliases of the serial build"))))

(deftest views-graph-determinism
  (testing "get-views-graph returns [graph id-map] and is deterministic with a fresh seed"
    (let [model {:maps {:views {"V1" {:attrs {:name "V1"} :content nil}}
                         "V2" {:attrs {:name "V2"} :content nil}}
                 :elements []
                 :relations []}
          [g1 m1] (arr/get-views-graph model)
          [g2 m2] (arr/get-views-graph model)]
      (is (vector? (arr/get-views-graph model)))
      (is (map? m1))
      (is (= (element-aliases g1) (element-aliases g2))
          "two fresh flagless view parses share element aliases"))))