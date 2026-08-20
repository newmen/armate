(ns armate.archimate.viz.combiner-test
  (:require [clojure.string :as s]
            [clojure.test :refer [deftest is testing]]
            [armate.archimate.plantuml.parser :as prr]
            [armate.archimate.viz.combiner :as viz]))

(defn strip-lines
  "Removes :line so the (non-line) ordering logic is exercised."
  [context]
  (letfn [(strip-el [el]
            (-> el
                (dissoc :line)
                (update :inside #(mapv strip-el %))))]
    (-> context
        (update :elements
                (fn [els]
                  (into {} (map (fn [[k v]] [k (strip-el v)])) els)))
        (update :relations
                (fn [rels]
                  (into {} (map (fn [[k v]]
                                  [k (into {} (map (fn [[k2 v2]]
                                                     [k2 (mapv #(dissoc % :line) v2)]))
                                            v)]))
                         rels))))))

(defn generate-sorted
  [puml-content]
  (viz/generate-puml (strip-lines (prr/analyze-content puml-content))))

(defn element-aliases
  [out]
  (->> (s/split-lines out)
       (map (fn [l] (second (re-find #"^[A-Za-z_]+\(([^,\)\s]+)" l))))
       (remove nil?)))

(defn rel-fns
  [out]
  (->> (s/split-lines out)
       (filter #(re-find #"^Rel_" %))
       (map (fn [l] (re-find #"Rel_[A-Za-z_]+" l)))))

(defn rel-lines
  [out]
  (->> (s/split-lines out)
       (filter #(re-find #"^Rel_" %))))

(def puml
  "@startuml \"test puml generation\"

!include <archimate/Archimate>

skinparam rectangle<<sub>> {
  backgroundColor #99d6ff
}

Group(a1, \"Application\") #ffaa00 {
  Grouping(b1, \"Grouping\") {
    rectangle \"Component1\" as c1_acp <<$acp>> #Application
    rectangle \"Component 2\" as c2_ai <<$ai>><<sub>>
  }
}
Motivation_Goal(mg1, \"Motivation 1\")
Application_DataObject(ado1, \"Data Object 1\")

c2_ai -right-* c1_acp
Rel_Aggregation_Left(c1_acp, c2_ai, \"Duplicate relation\")
Rel_Association_dir_Up(mg1, ado1)

c1_acp -[hidden]-> c2_ai

@enduml")

(deftest generate-puml-test
  (testing "elements/relations with :line preserve original file order"
    (is (= puml (viz/generate-puml (prr/analyze-content puml))))))

(deftest element-layer-ordering-test
  (let [out (generate-sorted (str "@startuml t\n"
                                  "Technology_Device(z2, \"Z\")\n"
                                  "Business_Process(a1, \"A\")\n"
                                  "Technology_Device(z1, \"T1\")\n"
                                  "Motivation_Goal(m1, \"M\")\n"
                                  "Application_Component(c1, \"C\")\n"
                                  "Strategy_Capability(s1, \"S\")\n"
                                  "Implementation_Deliverable(i1, \"I\")\n"
                                  "@enduml"))]
    (testing "elements ordered by layer then alias"
      (is (= ["m1" "s1" "a1" "c1" "z1" "z2" "i1"]
             (element-aliases out))))))

(deftest composite-elements-last-test
  (let [out (generate-sorted (str "@startuml t\n"
                                  "Grouping(g1, \"Group\")\n"
                                  "Implementation_Deliverable(i1, \"I\")\n"
                                  "Implementation_WorkPackage(i2, \"W\")\n"
                                  "@enduml"))]
    (testing "composite (grouping) come after implementation"
      (is (= ["i1" "i2" "g1"]
             (element-aliases out))))))

(deftest nested-elements-sorted-test
  (let [out (generate-sorted (str "@startuml t\n"
                                  "Application_Component(parent1, \"Parent\") {\n"
                                  "  Application_Function(f_z, \"Z\")\n"
                                  "  Application_Function(f_a, \"A\")\n"
                                  "}\n"
                                  "@enduml"))]
    (testing "nested elements sorted lexicographically by alias"
      (is (< (s/index-of out "Application_Function(f_a")
             (s/index-of out "Application_Function(f_z"))))))

(deftest relation-category-ordering-test
  (let [out (generate-sorted (str "@startuml t\n"
                                  "Rel_Triggering(a, b)\n"
                                  "Rel_Serving(c, d)\n"
                                  "Rel_Specialization(e, f)\n"
                                  "Rel_Composition(g, h)\n"
                                  "Rel_Access(i, j)\n"
                                  "Rel_Assignment(k, l)\n"
                                  "@enduml"))]
    (testing "relations ordered by category: special, structural, dependency, dynamic"
      (is (= ["Rel_Specialization"
              "Rel_Composition" "Rel_Assignment"
              "Rel_Serving" "Rel_Access"
              "Rel_Triggering"]
             (rel-fns out))))))

(deftest relation-endpoint-lexicographic-test
  (let [out (generate-sorted (str "@startuml t\n"
                                  "Rel_Serving(z, a)\n"
                                  "Rel_Serving(a, z)\n"
                                  "Rel_Serving(b, c)\n"
                                  "@enduml"))]
    (testing "relations of same category sorted by both endpoints"
      (is (= ["Rel_Serving(a, z)" "Rel_Serving(b, c)" "Rel_Serving(z, a)"]
             (rel-lines out))))))