(ns armate.archimate.lint-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [armate.archimate.plantuml.lint :as lint]
            [armate.archimate.plantuml.structure :as structure]))

(def ^:private valid-content
  "@startuml \"Семья\"

!include <archimate/Archimate>

Business_Actor(ba4, \"Мама\")
Business_Process(bpc17, \"Варит суп\")

Rel_Assignment(ba4, bpc17)

@enduml")

(defn- unspecified-content
  "A document with a line, whose only problem is a warn with a line: an access
   relationship between two application components."
  []
  (str "\n@startuml\n\n!$app = \"jar:archimate/application\"\n"
       "!include <archimate/Archimate>\n\nsprite $aComponent $app-component\n\n"
       "rectangle \"Component1\" as c1 <<$aComponent>>\n"
       "rectangle \"Component 2\" as c2 <<$aComponent>>\n\nc1 ~ c2\n\n@enduml"))

(deftest format-summary-empty
  (testing "no lints renders exactly the OK verdict and no closing line"
    (is (= "OK: no problems"
           (lint/format-summary {:lints [] :errors 0 :warnings 0})))))

(deftest format-summary-with-problems
  (testing "verdict counts, per-lint lines and the closing instruction"
    (let [summary {:lints [{:level :error :kind :undefined-relation-to :line 9
                            :in [:relations "a" "b"]
                            :text "relationship target \"b\" is not defined"}
                           {:level :warn :kind :missing-start :line nil :in nil
                            :text "no @startuml directive"}]
                   :errors 1 :warnings 1}
          text (lint/format-summary summary)]
      (is (s/starts-with? text "1 errors, 1 warnings\n"))
      (is (s/includes? text
                       "ERROR line 9 [in relations a b] undefined-relation-to: relationship target \"b\" is not defined"))
      (is (s/includes? text "WARN [document] missing-start: no @startuml directive"))
      (is (s/ends-with? text "Fix the errors and warnings above and lint again")))))

(deftest lint-content-valid
  (testing "a valid document has no lints and zero counts"
    (let [{:keys [lints errors warnings]} (lint/lint-content valid-content)]
      (is (empty? lints))
      (is (zero? errors))
      (is (zero? warnings)))))

(deftest lint-content-unspecified-relation
  (testing "a relationship not allowed between the kinds is a warn with concrete data"
    (let [{:keys [lints errors warnings]} (lint/lint-content (unspecified-content))
          w (first (filter #(= :unspecified-relation-type (:kind %)) lints))]
      (is (= 1 warnings))
      (is (zero? errors))
      (is (= :warn (:level w)))
      (is (some? (:line w)))
      (is (s/includes? (:text w) "access"))
      (is (s/includes? (:text w) "application-component")))))

(deftest lint-content-undefined-endpoint
  (testing "an unresolved endpoint is an error"
    (let [content (s/replace valid-content "Rel_Assignment(ba4, bpc17)"
                             "Rel_Assignment(ba4, nope)")
          {:keys [lints errors]} (lint/lint-content content)
          e (first (filter #(= :undefined-relation-to (:kind %)) lints))]
      (is (= 1 errors))
      (is (= :error (:level e)))
      (is (s/includes? (:text e) "nope")))))

(deftest lint-content-unclosed-block
  (testing "an unclosed block becomes a single :unclosed-block error, not an exception"
    (let [content (str "@startuml\n\n!include <archimate/Archimate>\n\n"
                       "Grouping(g1, \"G\") {\n  Business_Actor(ba4, \"Мама\")\n\n@enduml")
          {:keys [lints errors warnings]} (lint/lint-content content)
          e (first lints)]
      (is (= 1 (count lints)))
      (is (= 1 errors))
      (is (zero? warnings))
      (is (= :unclosed-block (:kind e)))
      (is (= :error (:level e)))
      (is (= [:parse] (:in e)))
      (is (= 5 (:line e))))))

(deftest unclosed-block-throws-machine-signal
  (testing "the parser signals an unclosed block by :armate/error, not by message text"
    (let [e (try
              (structure/get-blocks "Grouping(g1, \"G\") {\n  Business_Actor(ba4, \"M\")\n")
              nil
              (catch clojure.lang.ExceptionInfo e e))]
      (is (some? e))
      (is (= :unclosed-block (:armate/error (ex-data e)))))))

(deftest lint-content-sorts-errors-first
  (testing "errors precede warnings regardless of document order"
    (let [content (str "\n@startuml\n\n!include <archimate/Archimate>\n\n"
                       "Business_Actor(ba4, \"M\")\nBusiness_Process(bpc17, \"P\")\n"
                       "Identifiable(zz, \"Z\")\n\nRel_Assignment(ba4, zz)\n\n@enduml")
          {:keys [lints]} (lint/lint-content content)]
      (is (seq lints))
      (is (= :error (:level (first lints)))))))

(deftest lint-content-line-less-last
  (testing "within a level, lints with a line come before line-less ones"
    (let [content (s/replace (unspecified-content) "@enduml" "")
          {:keys [lints]} (lint/lint-content content)
          warns (filter #(= :warn (:level %)) lints)
          [with-line line-less] (split-with :line warns)]
      (is (seq with-line))
      (is (seq line-less))
      (is (every? nil? (map :line line-less))))))