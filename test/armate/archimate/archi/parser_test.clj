(ns armate.archimate.archi.parser-test
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.archi.parser :as arr]))

(def demo-path "test/resources/demo.archimate")

(def demo-indexed
  (first (arr/enrich-with-graph (arr/get-model demo-path))))

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
          [g2 _m2] (arr/get-views-graph model)]
      (is (vector? (arr/get-views-graph model)))
      (is (map? m1))
      (is (= (element-aliases g1) (element-aliases g2))
          "two fresh flagless view parses share element aliases"))))

;; ---------------------------------------------------------------------------
;; Ticket 01: view-membership metadata (element-views / relation-views)
;; ---------------------------------------------------------------------------

(deftest view-index-produces-element-views
  (testing ":element-views maps every placed element alias to the views that place it"
    (let [ev (:element-views demo-indexed)]
      (is (seq ev))
      ;; "Волк" (ba13) is placed in both "Процесс" and "Шахматы" demo views
      (is (= #{"Процесс" "Шахматы"} (get ev "ba13")))
      ;; "Петя" (ba32) appears in "Процесс" and "Семья"
      (is (= #{"Процесс" "Семья"} (get ev "ba32"))))))

(deftest view-index-produces-relation-views
  (testing ":relation-views maps [from-alias to-alias] {rel-type #{view-names}}"
    (let [rv (:relation-views demo-indexed)]
      (is (seq rv))
      (is (every? (fn [[[from to] types]]
                    (and (string? from)
                         (string? to)
                         (map? types)
                         (every? keyword? (keys types))))
                  rv))
      ;; Дедушка (ba11) --assignment--> Играет в шахматы (bin22) appears in "Шахматы"
      (is (= (get-in rv [["ba11" "bin22"] :assignment]) #{"Шахматы"}))
      ;; Волк (ba13) --assignment--> Покупает пойло (bpc29) appears in "Процесс"
      (is (= (get-in rv [["ba13" "bpc29"] :assignment]) #{"Процесс"})))))

(deftest view-index-places-grouping-children-recursively
  (testing "view-membership walks the diagram tree, so a grouping's nested children count as placed"
    (let [ev (:element-views demo-indexed)
          rv (:relation-views demo-indexed)]
      ;; "Досуг дедушки" (g19) nests children via composition/aggregation in "Семья";
      ;; those children are drawn as `<child>` DiagramObjects inside the grouping, so
      ;; they must be registered as placed in "Семья" (and their relations collected).
      (is (contains? (get ev "g19") "Семья") "grouping placed in Семья")
      (is (contains? (get ev "bpc24") "Семья") "composed child 'Ловит рыбу' placed in Семья")
      (is (contains? (get ev "bin36") "Семья") "aggregated child 'Катается на роликах' placed in Семья")
      (is (contains? (get ev "bin22") "Семья") "composed child 'Играет в шахматы' placed in Семья")
      ;; the composition relation g19 -> bpc24 must be visible in the "Семья" view
      (is (contains? (get-in rv [["g19" "bpc24"] :composition]) "Семья")))))