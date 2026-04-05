(ns armate.archimate.parser-test
  (:require [clojure.test :refer [deftest is]]
            [clojure.string :as s]
            [armate.archimate.plantuml.parser :as prr]))

(deftest call-re-test
  (is (= ["Rel_Assignment_Up(operationsI, tapeS)" "Rel_Assignment_Up" "operationsI" "tapeS" nil]
         (re-matches prr/call-re "Rel_Assignment_Up(operationsI, tapeS)")))
  (is (= ["Rel_Assignment_Up(operationsI, tapeS, \"Some description\")" "Rel_Assignment_Up" "operationsI" "tapeS" "\"Some description\""]
         (re-matches prr/call-re "Rel_Assignment_Up(operationsI, tapeS, \"Some description\")")))
  (is (nil? (re-matches prr/call-re "abs *-up- online"))))

(deftest full-line-re-test
  (is (= ["Rel_Serves(A, \"B\", \"C\", \"D\")" "Rel_Serves" "A" "\"B\"" "\"C\", \"D\"" nil nil]
         (re-matches prr/full-line-re "Rel_Serves(A, \"B\", \"C\", \"D\")")))
  (is (= ["Grouping(business_r, \"Бизнес\") #1122f3 {" "Grouping" "business_r" "\"Бизнес\"" nil "#1122f3" "{"]
         (re-matches prr/full-line-re "Grouping(business_r, \"Бизнес\") #1122f3 {")))
  (is (nil? (re-matches prr/full-line-re "rectangle \"Получение\nспискa\" as list_as <<$aService>> #Application")))
  (is (nil? (re-matches prr/full-line-re "accountOptionsService_acp -[hidden]down-> templatesService_acp"))))

(deftest quoted-brackets-split-test
  (is (= ["@startuml"]
         (prr/quoted-brackets-split "@startuml")))
  (is (= ["@startuml" "\"Какое-то длинное описание\""]
         (prr/quoted-brackets-split "@startuml \"Какое-то длинное описание\"")))
  (is (= ["rectangle" "\"Component1\"" "as" "c1" "<<$aComponent>>"]
         (prr/quoted-brackets-split "rectangle \"Component1\" as c1 <<$aComponent>>")))
  (is (= ["rectangle" "\"Component 2\"" "as" "c2" "<<$aComponent>>"]
         (prr/quoted-brackets-split "rectangle \"Component 2\" as c2 <<$aComponent>>")))
  (is (= ["rectangle" "Component3" "as" "c3" "<<$aComponent>>"]
         (prr/quoted-brackets-split "rectangle Component3 as c3 <<$aComponent>>")))
  (is (= ["skinparam" "rectangle<<sub>>" "{"]
         (prr/quoted-brackets-split "skinparam rectangle<<sub>> {")))
  (is (= ["Rel_Serves" "A" "\"B\"" "\"C\", D"]
         (prr/quoted-brackets-split "Rel_Serves(A, \"B\", \"C\", D)")))
  (is (= ["Grouping" "business_r" "\"Бизнес\"" "#1122f3" "{"]
         (prr/quoted-brackets-split "Grouping(business_r, \"Бизнес\") #1122f3 {"))))

(deftest get-parts-test
  (is (= {:parts ["skinparam" "rectangle<<sub>>"] :block? true}
         (prr/get-parts "skinparam rectangle<<sub>> {")))
  (is (= {:parts ["skinparam" "rectangle<<sub>>"] :block? true}
         (prr/get-parts "skinparam rectangle<<sub>>{")))
  (is (= {:parts ["sprite" "$aCollaboration" "jar:archimate/application-collaboration"] :block? false}
         (prr/get-parts "sprite $aCollaboration jar:archimate/application-collaboration")))
  (is (= {:parts ["abs" "*-up-" "online"] :block? false}
         (prr/get-parts "abs *-up- online")))
  (is (= {:parts ["Rel_Assignment_Up" "operationsI" "tapeS" nil] :block? false}
         (prr/get-parts "Rel_Assignment_Up(operationsI, tapeS)")))
  (is (= {:parts ["Rel_Assignment_Up" "operationsI" "tapeS" "\"Some description\""] :block? false}
         (prr/get-parts "Rel_Assignment_Up(operationsI, tapeS, \"Some description\")"))))

(deftest parse-variables-test
  (is (= ["$a" "1"]
         (prr/parse-variable "!$a = 1")))
  (is (= ["a" "1"]
         (prr/parse-variable "!a = 1")))
  (is (= ["a" "123"]
         (prr/parse-variable "!a=123")))
  (is (= ["a" "11"]
         (prr/parse-variable "!a =11")))
  (is (= ["abc" "11"]
         (prr/parse-variable "!abc= 11"))))

(deftest mask-variable-test
  (is (= "\\$a\\b"
         (prr/mask-variable "$a")))
  (is (= "\\$\\$a\\b"
         (prr/mask-variable "$$a")))
  (is (= "\\$a\\$"
         (prr/mask-variable "$a$")))
  (is (= "\\ba\\b"
         (prr/mask-variable "a"))))

(deftest apply-variables-test
  (is (= "-1- line has variable"
         (prr/apply-variables {"a" "1"
                                "with" "has"} "-a- line with variable")))
  (is (= "a line with 1 variable"
         (prr/apply-variables {"$a" "1"} "a line with $a variable"))))

(deftest rel-f-re-test
  (is (= ["Rel_Assignment_Up" "Assignment" "Up"]
         (re-matches prr/rel-f-re "Rel_Assignment_Up")))
  (is (= ["Rel_Assignment_up" "Assignment" "up"]
         (re-matches prr/rel-f-re "Rel_Assignment_up")))
  (is (= ["Rel_Assignment" "Assignment" nil]
         (re-matches prr/rel-f-re "Rel_Assignment")))
  (is (= ["Rel_Access_r" "Access_r" nil]
         (re-matches prr/rel-f-re "Rel_Access_r")))
  (is (= ["Rel_Access" "Access" nil]
         (re-matches prr/rel-f-re "Rel_Access"))))

(deftest match-rel-b-test
  (is (= ["*--" "*--" ""] (prr/match-rel-b "*--")))
  (is (= ["*-" "*-" ""] (prr/match-rel-b "*-")))
  (is (= ["*-up-" "*-" "up" "-"] (prr/match-rel-b "*-up-")))
  (is (= ["<|.DOWN." "<|." "DOWN" "."] (prr/match-rel-b "<|.DOWN.")))
  (is (= ["---Left--#" "---" "Left" "--#"] (prr/match-rel-b "---Left--#")))
  (is (= ["-----#" "-----" "#"] (prr/match-rel-b "-----#"))))

(deftest pin-re-test
  (is (= ["<|.." "<|" ".." ""] (re-matches prr/pin-re "<|..")))
  (is (= ["-|>" "" "-" "|>"] (re-matches prr/pin-re "-|>")))
  (is (= ["-----#" "" "-----" "#"] (re-matches prr/pin-re "-----#")))
  (is (= ["----" "" "----" ""] (re-matches prr/pin-re "----"))))

(deftest b-matches-test
  (is (= {:type :composition :reverse? true :direction :left :raw "---left--*" :cut "-*"}
         (prr/b-matches "---left--*")))
  (is (= {:type :unknown :raw "---left--#" :direction :left :cut "-#"}
         (prr/b-matches "---left--#")))
  (is (= {:type :unknown :raw "<|.down." :direction :down :cut "<|."}
         (prr/b-matches "<|.down.")))
  (is (= {:type :unknown :raw "<|.." :direction nil :cut "<|."}
         (prr/b-matches "<|..")))
  (is (= {:type :specialization :direction :down :raw "--down-|>" :cut "-|>"}
         (prr/b-matches "--down-|>")))
  (is (= {:type :specialization :direction nil :raw "--|>" :cut "-|>"}
         (prr/b-matches "--|>")))
  (is (= {:type :specialization :direction nil :raw "-|>" :cut "-|>"}
         (prr/b-matches "-|>")))
  (is (nil? (prr/b-matches "hello"))))

(deftest match-rel-test
  (is (= {:type :assignment :from "A" :to "B" :direction nil :desc "desc"}
         (prr/match-rel ["Rel_Assignment" "A" "B" "desc"])))
  (is (= {:type :assignment :from "A" :to "B" :direction :up :desc "desc"}
         (prr/match-rel ["Rel_Assignment_Up" "A" "B" "desc"])))
  (is (= {:type :serving :raw "-->" :cut "->" :direction nil :from "A" :to "B"}
         (prr/match-rel ["A" "-->" "B" nil])))
  (is (= {:type :specialization :reverse? true :direction nil
          :raw "<|--" :cut "<|-" :from "B" :to "A"}
         (prr/match-rel ["A" "<|--" "B" nil])))
  (is (= {:type :unknown  :direction nil :raw ".." :cut "." :from "A" :to "B"}
         (prr/match-rel ["A" ".." "B" nil])))
  (is (= {:type :unknown :direction nil :from "A" :to "B" :desc "desc"
          :raw "Rel_Some" :cut :some}
         (prr/match-rel ["Rel_Some" "A" "B" "desc"]))))

(deftest fur-re-test
  (is (= [["rectangle" "rectangle"] ["<<db>>" "<<db>>"]]
         (re-seq prr/fur-re "rectangle<<db>>")))
  (is (= [["<<$aCollaboration>>" "<<$aCollaboration>>"] ["<<platform>>" "<<platform>>"]]
         (re-seq prr/fur-re "<<$aCollaboration>><<platform>>"))))

(deftest cut-furs-test
  (is (= ["rectangle" "db"] (prr/cut-furs "rectangle<<db>>")))
  (is (= ["$aCollaboration" "platform"] (prr/cut-furs "<<$aCollaboration>><<platform>>")))
  (is (= ["$aCollaboration"] (prr/cut-furs "<<$aCollaboration>>"))))

(deftest cut1-test
  (is (= "archimate/Archimate" (prr/cut1 "<archimate/Archimate>"))))

(deftest cut2-test
  (is (= "$aComponent" (prr/cut2 "<<$aComponent>>"))))

(deftest match-block-test
  (is (= {:body {:line 1} :in [:start]}
         (prr/match-block {:parts ["@startuml"] :line 1})))
  (is (= {:body {:line 1 :title "Какое-то длинное описание"} :in [:start]}
         (prr/match-block {:parts ["@startuml" "\"Какое-то длинное описание\""] :line 1})))
  (is (= {:body {:line 1} :in [:end]}
         (prr/match-block {:parts ["@enduml"] :line 1})))
  (is (= {:body {:line 1 :package "archimate/Archimate"}
          :in [:includes "archimate/Archimate"]}
         (prr/match-block {:parts ["!include" "<archimate/Archimate>"] :line 1})))
  (is (= {:body {:line 1 :shape "rectangle" :alias "sub" :props [["fontColor" "#f1f3f1"]]}
          :in [:skins ["rectangle" "sub"]]}
         (prr/match-block {:parts ["skinparam" "rectangle<<sub>>"] :line 1
                            :props [["fontColor" "#f1f3f1"]]})))
  (is (= {:body {:line 1 :shape nil :alias nil :props [["fontColor" "#f1f3f1"]]}
          :in [:skins [:default]]}
         (prr/match-block {:parts ["skinparam"] :line 1
                            :props [["fontColor" "#f1f3f1"]]})))
  (is (= {:body {:line 1 :alias "$aComponent" :kind :application-component}
          :in [:types "$aComponent"]}
         (prr/match-block {:parts ["sprite"
                                    "$aComponent"
                                    "jar:archimate/application-component"] :line 1})))
  (is (= {:body {:line 1 :inside []
                 :title "X" :name "X" :alias "x" :type "$aComponent"
                 :shape "rectangle" :skin nil :layer nil :color nil}
          :in [:elements "x"]}
         (prr/match-block {:parts ["rectangle" "X" "as" "x" "<<$aComponent>>"] :line 1})))
  (is (= {:body {:line 1 :inside []
                 :title "X" :name "X" :alias "x" :type "$aComponent"
                 :shape "rectangle" :skin "pin" :layer nil :color nil}
          :in [:elements "x"]}
         (prr/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>><<pin>>"] :line 1})))
  (is (= {:body {:line 1 :inside []
                 :title "X" :name "X" :alias "x" :type "$aComponent"
                 :shape "rectangle" :skin "pin" :layer nil :color nil}
          :in [:elements "x"]}
         (prr/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>>" "<<pin>>"] :line 1})))
  (is (= {:body {:line 1 :inside []
                 :title "X" :name "X" :alias "x" :type "$aComponent"
                 :shape "rectangle" :skin "pin" :layer "#Application" :color nil}
          :in [:elements "x"]}
         (prr/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>><<pin>>" "#Application"] :line 1})))
  (is (= {:body {:line 1 :inside []
                 :title "X" :name "X" :alias "x" :type "$aComponent"
                 :shape "rectangle" :skin "pin" :layer "#Application" :color nil}
          :in [:elements "x"]}
         (prr/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>>" "<<pin>>" "#Application"] :line 1})))
  (is (= {:body {:line 1 :inside []
                 :title "X" :name "X" :alias "x" :type "$aComponent"
                 :shape "rectangle" :skin nil :layer "#Application" :color nil}
          :in [:elements "x"]}
         (prr/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>>" "#Application"] :line 1})))
  (is (= {:body {:line 1 :type :composition :raw "*-up-" :cut "*-"
                 :direction :up :from "manager" :to "docsI"}
          :in [:relations "manager" "docsI"]}
         (prr/match-block {:parts ["manager" "*-up-" "docsI"] :line 1})))
  (is (= {:body {:line 1 :type :assignment :desc nil
                 :direction :up :from "profitI" :to "calcS"}
          :in [:relations "profitI" "calcS"]}
         (prr/match-block {:parts ["Rel_Assignment_Up" "profitI" "calcS" nil] :line 1})))
  (is (= {:body {:line 1 :parts ["blah"]} :in [:unknowns]}
         (prr/match-block {:parts ["blah"] :line 1}))))

(deftest analyze-content-test
  (letfn [(lint-content [content]
            (:lints (prr/analyze-content content)))
          (make-content [rel]
            (str "
@startuml

!$app = \"jar:archimate/application\"
!include <archimate/Archimate>

skinparam rectangle<<sub>> {
  backgroundColor #99d6ff
}

sprite $aComponent $app-component
sprite $anInterface $app-interface

rectangle \"Component1\" as c1 <<$aComponent>>
rectangle \"Component 2\" as c2 <<$aComponent>><<sub>>

c1 " rel " c2
c1 -[hidden]> c2

@enduml"))]

    (is (empty? (lint-content (make-content "-|>"))))
    (is (empty? (lint-content (make-content "<|-"))))
    (is (empty? (lint-content (make-content "-"))))
    (is (empty? (lint-content (make-content "->"))))
    (is (empty? (lint-content (make-content "<-"))))
    (is (empty? (lint-content (make-content "->>"))))
    (is (empty? (lint-content (make-content "<<-"))))
    (is (empty? (lint-content (make-content ".>>"))))
    (is (empty? (lint-content (make-content "<<."))))
    (is (empty? (lint-content (make-content "~|>"))))
    (is (empty? (lint-content (make-content "<|~"))))
    (is (empty? (lint-content (make-content "-o"))))
    (is (empty? (lint-content (make-content "o-"))))
    (is (empty? (lint-content (make-content "-*"))))
    (is (empty? (lint-content (make-content "*-"))))
    (is (empty? (lint-content (make-content "*--"))))
    (is (empty? (lint-content (make-content "*-up-"))))
    (is (empty? (lint-content (make-content "*-right-"))))
    (is (empty? (lint-content (make-content "*-down-"))))
    (is (empty? (lint-content (make-content "*-left-"))))
    (is (= [{:level :error
             :kind :undefined-relation-type
             :in [:relations "c1" "c2"]
             :body {:line 17 :type :unknown :raw "." :direction nil
                    :from :application-component
                    :to :application-component}}]
           (lint-content (make-content "."))))
    (is (= [{:level :warn
             :kind :unspecified-relation-type
             :in [:relations "c1" "c2"]
             :body {:line 17 :type :access :raw "~" :direction nil
                    :from :application-component
                    :to :application-component}}]
           (lint-content (make-content "~"))))
    (is (= [{:level :warn
             :kind :unspecified-relation-type
             :in [:relations "c1" "c2"]
             :body {:line 17 :type :access_rw :raw "<~>" :direction nil
                    :from :application-component
                    :to :application-component}}]
           (lint-content (make-content "<~>"))))
    (is (= [{:level :warn
             :kind :unspecified-relation-type
             :in [:relations "c1" "c2"]
             :body {:line 17 :type :assignment :raw "@->>" :direction nil
                    :from :application-component
                    :to :application-component}}]
           (lint-content (make-content "@->>"))))
    (is (= [{:level :error
             :kind :undefined-relation-from
             :in [:relations "c3" "c2"]
             :body {:line 17 :type :composition :raw "*-" :direction nil
                    :from "c3" :to :application-component}}]
           (lint-content (s/replace (make-content "*-") "c1 *- c2" "c3 *- c2"))))
    (is (= [{:level :error
             :kind :undefined-relation-to
             :in [:relations "c1" "c3"]
             :body {:line 17 :type :composition :raw "*-" :direction nil
                    :from :application-component :to "c3"}}]
           (lint-content (s/replace (make-content "*-") "c1 *- c2" "c1 *- c3"))))
    (is (= [{:level :error
             :kind :undefined-relation-from
             :in [:relations "c3" "c4"]
             :body {:line 17 :type :composition :raw "*-" :direction nil
                    :from "c3" :to "c4"}}
            {:level :error
             :kind :undefined-relation-to
             :in [:relations "c3" "c4"]
             :body {:line 17 :type :composition :raw "*-" :direction nil
                    :from "c3" :to "c4"}}]
           (lint-content (s/replace (make-content "*-") "c1 *- c2" "c3 *- c4"))))
    (is (= [{:level :warn
             :kind :relation-between-elements-already-present
             :in [:relations "c2" "c1"]
             :body {:line 18 :type :aggregation :raw "-o" :direction nil :reverse? true
                    :from :application-component :to :application-component}}]
           (lint-content (s/replace (make-content "*-") "c1 *- c2" "c1 *- c2\nc1 -o c2"))))
    (is (= [{:level :warn :kind :missing-end}
            {:level :warn :kind :missing-start}]
           (lint-content (s/replace (make-content "*-") #"@\w+" ""))))
    (is (= [{:level :warn :kind :missing-archimate-include}]
           (lint-content (s/replace (make-content "*-") #"!include.+?\n" ""))))))