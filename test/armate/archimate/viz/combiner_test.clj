(ns armate.archimate.viz.combiner-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.metamodel.derivation.match-test :refer [graph0]]
            [armate.archimate.plantuml.parser :as prr]
            [armate.archimate.viz.combiner :as viz]))

(deftest get-rels-based-weights-test
  (is (= {"partner_br" [-4000 -2000]
          "child_ba" [-2000 -2000]
          "client_br" [-2000 -2]
          "controlFood_bpc" [-1000 -800]
          "configureTurnstile_bpc" [-1000 -800]
          "getRegistry_bpc" [-1000 -800]
          "registry_bs" [-800 -4000]
          "food_bs" [-800 -2000]
          "pass_bs" [-800 -2000]
          "fillForm_bpc" [-2 -1000]}
         (viz/get-rels-based-weights graph0))))

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
  (is (= puml (viz/generate-puml (prr/analyze-content puml)))))
