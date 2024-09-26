(ns armate.viz.combiner-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate :as arch]
            [armate.viz.combiner :as viz]))

(def puml
  "@startuml \"test puml generation\"

!include <archimate/Archimate>

skinparam {
  RoundCorner 8
  Shadowing false
}
skinparam rectangle {
  BorderThickness 1
}
skinparam rectangle<<sub>> {
  backgroundColor #2cc7fe
}

sprite $acp jar:archimate/application-component
sprite $ai jar:archimate/application-interface

Group(a1, \"Application\") #ffaa00 {
  Grouping(b1, \"Grouping\") {
    rectangle \"Component1\" as c1_acp <<$acp>> #Application
    rectangle \"Component 2\" as c2_ai <<$ai>><<sub>>
  }
}

c2_ai -right-* c1_acp
Rel_Aggregation_Left(c1_acp, c2_ai)

c1_acp -[hidden]-> c2_ai

@enduml")

(deftest generate-puml-test
  (is (= puml (viz/generate-puml (arch/analyze (arch/get-blocks puml))))))
