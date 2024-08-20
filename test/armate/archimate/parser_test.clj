(ns armate.archimate-test
  (:require [clojure.test :refer :all]
            [clojure.string :as s]
            [armate.archimate :as arch]))

(deftest call-re-test
  (is (= ["Rel_Assignment_Up(operationsI, tapeS)" "Rel_Assignment_Up" "operationsI" "tapeS" nil]
         (re-matches arch/call-re "Rel_Assignment_Up(operationsI, tapeS)")))
  (is (= ["Rel_Assignment_Up(operationsI, tapeS, \"Some description\")" "Rel_Assignment_Up" "operationsI" "tapeS" "\"Some description\""]
         (re-matches arch/call-re "Rel_Assignment_Up(operationsI, tapeS, \"Some description\")")))
  (is (nil? (re-matches arch/call-re "abs *-up- online"))))

(deftest quoted-split-test
  (is (= ["rectangle" "\"Component1\"" "as" "c1" "<<$aComponent>>"]
         (arch/quoted-split "rectangle \"Component1\" as c1 <<$aComponent>>")))
  (is (= ["rectangle" "\"Component 2\"" "as" "c2" "<<$aComponent>>"]
         (arch/quoted-split "rectangle \"Component 2\" as c2 <<$aComponent>>")))
  (is (= ["rectangle" "Component3" "as" "c3" "<<$aComponent>>"]
         (arch/quoted-split "rectangle Component3 as c3 <<$aComponent>>")))
  (is (= ["skinparam" "rectangle<<sub>>" "{"]
         (arch/quoted-split "skinparam rectangle<<sub>> {"))))

(deftest get-parts-test
  (is (= {:parts ["skinparam" "rectangle<<sub>>"] :block? true}
         (arch/get-parts "skinparam rectangle<<sub>> {")))
  (is (= {:parts ["skinparam" "rectangle<<sub>>"] :block? true}
         (arch/get-parts "skinparam rectangle<<sub>>{")))
  (is (= {:parts ["sprite" "$aCollaboration" "jar:archimate/application-collaboration"] :block? false}
         (arch/get-parts "sprite $aCollaboration jar:archimate/application-collaboration")))
  (is (= {:parts ["abs" "*-up-" "online"] :block? false}
         (arch/get-parts "abs *-up- online")))
  (is (= {:parts ["Rel_Assignment_Up" "operationsI" "tapeS" nil] :block? false}
         (arch/get-parts "Rel_Assignment_Up(operationsI, tapeS)")))
  (is (= {:parts ["Rel_Assignment_Up" "operationsI" "tapeS" "\"Some description\""] :block? false}
         (arch/get-parts "Rel_Assignment_Up(operationsI, tapeS, \"Some description\")"))))

(deftest rel-f-re-test
  (is (= ["Rel_Assignment_Up" "Assignment"]
         (re-matches arch/rel-f-re "Rel_Assignment_Up")))
  (is (= ["Rel_Assignment_up" "Assignment"]
         (re-matches arch/rel-f-re "Rel_Assignment_up")))
  (is (= ["Rel_Assignment" "Assignment"]
         (re-matches arch/rel-f-re "Rel_Assignment")))
  (is (= ["Rel_Access_r" "Access_r"]
         (re-matches arch/rel-f-re "Rel_Access_r")))
  (is (= ["Rel_Access" "Access"]
         (re-matches arch/rel-f-re "Rel_Access"))))

(deftest match-rel-b-test
  (is (= ["*--" "*--" ""] (arch/match-rel-b "*--")))
  (is (= ["*-" "*-" ""] (arch/match-rel-b "*-")))
  (is (= ["*-up-" "*-" "-"] (arch/match-rel-b "*-up-")))
  (is (= ["<|.down." "<|." "."] (arch/match-rel-b "<|.down.")))
  (is (= ["---left--#" "---" "--#"] (arch/match-rel-b "---left--#")))
  (is (= ["-----#" "-----" "#"] (arch/match-rel-b "-----#"))))

(deftest pin-re-test
  (is (= ["<|.." "<|" ".." ""] (re-matches arch/pin-re "<|..")))
  (is (= ["-|>" "" "-" "|>"] (re-matches arch/pin-re "-|>")))
  (is (= ["-----#" "" "-----" "#"] (re-matches arch/pin-re "-----#")))
  (is (= ["----" "" "----" ""] (re-matches arch/pin-re "----"))))

(deftest b-matches-test
  (is (= {:type :composition :reverse? true :raw "---left--*" :cut "-*"}
         (arch/b-matches "---left--*")))
  (is (= {:type :unknown :raw "---left--#" :cut "-#"}
         (arch/b-matches "---left--#")))
  (is (= {:type :unknown :raw "<|.down." :cut "<|."}
         (arch/b-matches "<|.down.")))
  (is (= {:type :unknown :raw "<|.." :cut "<|."}
         (arch/b-matches "<|..")))
  (is (= {:type :specialization :raw "--down-|>" :cut "-|>"}
         (arch/b-matches "--down-|>")))
  (is (= {:type :specialization :raw "--|>" :cut "-|>"}
         (arch/b-matches "--|>")))
  (is (= {:type :specialization :raw "-|>" :cut "-|>"}
         (arch/b-matches "-|>")))
  (is (nil? (arch/b-matches "hello"))))

(deftest match-rel-test
  (is (= {:type :assignment :from "A" :to "B" :desc "desc"}
         (arch/match-rel ["Rel_Assignment" "A" "B" "desc"])))
  (is (= {:type :assignment :from "A" :to "B" :desc "desc"}
         (arch/match-rel ["Rel_Assignment_Up" "A" "B" "desc"])))
  (is (= {:type :serving :raw "-->" :cut "->" :desc nil :from "A" :to "B"}
         (arch/match-rel ["A" "-->" "B" nil])))
  (is (= {:type :specialization :reverse? true :raw "<|--" :cut "<|-" :desc nil :from "B" :to "A"}
         (arch/match-rel ["A" "<|--" "B" nil])))
  (is (= {:type :unknown :raw ".." :cut "." :desc nil :from "A" :to "B"}
         (arch/match-rel ["A" ".." "B" nil])))
  (is (= {:type :unknown :from "A" :to "B" :desc "desc" :raw "Rel_Some" :cut :some}
         (arch/match-rel ["Rel_Some" "A" "B" "desc"]))))

(deftest fur-re-test
  (is (= [["rectangle" "rectangle"] ["<<db>>" "<<db>>"]]
         (re-seq arch/fur-re "rectangle<<db>>")))
  (is (= [["<<$aCollaboration>>" "<<$aCollaboration>>"] ["<<platform>>" "<<platform>>"]]
         (re-seq arch/fur-re "<<$aCollaboration>><<platform>>"))))

(deftest cut-fur-test
  (is (= ["rectangle" "db"] (arch/cut-fur "rectangle<<db>>")))
  (is (= ["$aCollaboration" "platform"] (arch/cut-fur "<<$aCollaboration>><<platform>>")))
  (is (= ["$aCollaboration"] (arch/cut-fur "<<$aCollaboration>>"))))

(deftest cut1-test
  (is (= "archimate/Archimate" (arch/cut1 "<archimate/Archimate>"))))

(deftest cut2-test
  (is (= "$aComponent" (arch/cut2 "<<$aComponent>>"))))

(deftest match-block-test
  (is (= {:body {:line 1} :in [:start]}
         (arch/match-block {:parts ["@startuml"] :line 1})))
  (is (= {:body {:line 1} :in [:end]}
         (arch/match-block {:parts ["@enduml"] :line 1})))
  (is (= {:body {:line 1} :in [:includes "archimate/Archimate"]}
         (arch/match-block {:parts ["!include" "<archimate/Archimate>"] :line 1})))
  (is (= {:body {:line 1 :props [["fontColor" "#eeeeee"]]}
          :in [:skins "rectangle" "sub"]}
         (arch/match-block {:parts ["skinparam" "rectangle<<sub>>"] :line 1
                            :props [["fontColor" "#eeeeee"]]})))
  (is (= {:body {:line 1 :props [["fontColor" "#eeeeee"]]}
          :in [:skins :default]}
         (arch/match-block {:parts ["skinparam"] :line 1
                            :props [["fontColor" "#eeeeee"]]})))
  (is (= {:body {:line 1 :kind :application-component}
          :in [:types "$aComponent"]}
         (arch/match-block {:parts ["sprite"
                                    "$aComponent"
                                    "jar:archimate/application-component"] :line 1})))
  (is (= {:body {:line 1 :title "X" :type "$aComponent" :skin nil :meta nil}
          :in [:components "x"]}
         (arch/match-block {:parts ["rectangle" "X" "as" "x" "<<$aComponent>>"] :line 1})))
  (is (= {:body {:line 1 :title "X" :type "$aComponent" :skin "pin" :meta nil}
          :in [:components "x"]}
         (arch/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>><<pin>>"] :line 1})))
  (is (= {:body {:line 1 :title "X" :type "$aComponent" :skin "pin" :meta nil}
          :in [:components "x"]}
         (arch/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>>" "<<pin>>"] :line 1})))
  (is (= {:body {:line 1 :title "X" :type "$aComponent" :skin "pin" :meta "#Application"}
          :in [:components "x"]}
         (arch/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>><<pin>>" "#Application"] :line 1})))
  (is (= {:body {:line 1 :title "X" :type "$aComponent" :skin "pin" :meta "#Application"}
          :in [:components "x"]}
         (arch/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>>" "<<pin>>" "#Application"] :line 1})))
  (is (= {:body {:line 1 :title "X" :type "$aComponent" :skin nil :meta "#Application"}
          :in [:components "x"]}
         (arch/match-block {:parts ["rectangle"
                                    "X" "as" "x"
                                    "<<$aComponent>>" "#Application"] :line 1})))
  (is (= {:body {:line 1 :type :composition :raw "*-up-" :cut "*-" :desc nil
                 :from "manager" :to "docsI"}
          :in [:relations "manager" "docsI"]}
         (arch/match-block {:parts ["manager" "*-up-" "docsI"] :line 1})))
  (is (= {:body {:line 1 :type :assignment :from "profitI" :to "calcS" :desc nil}
          :in [:relations "profitI" "calcS"]}
         (arch/match-block {:parts ["Rel_Assignment_Up" "profitI" "calcS" nil] :line 1})))
  (is (= {:body {:line 1 :parts ["blah"]} :in [:unknowns]}
         (arch/match-block {:parts ["blah"] :line 1}))))

(deftest possible-components-test
  (is (= #{:application-collaboration
           :application-component
           :application-data-object
           :application-function
           :application-interface
           :application-service
           :business-actor
           :business-collaboration
           :business-event
           :business-function
           :business-interaction
           :business-process
           :business-role
           :business-service}
         arch/possible-components)))

(deftest lint-content-test
  (defn- make-content
    [rel]
    (str "
@startuml

!include <archimate/Archimate>

skinparam rectangle<<sub>> {
    backgroundColor #2cc7fe
}

sprite $aComponent jar:archimate/application-component
sprite $anInterface jar:archimate/application-interface

rectangle \"Component1\" as c1 <<$aComponent>>
rectangle \"Component 2\" as c2 <<$aComponent>><<sub>>

c1 " rel " c2

@enduml
     "))

  (is (empty? (arch/lint-content (make-content "*-"))))
  (is (empty? (arch/lint-content (make-content "*--"))))
  (is (empty? (arch/lint-content (make-content "*-up-"))))
  (is (empty? (arch/lint-content (make-content "*-right-"))))
  (is (empty? (arch/lint-content (make-content "*-down-"))))
  (is (empty? (arch/lint-content (make-content "*-left-"))))
  (is (= [{:level :error
           :kind :undefined-relation-type
           :in [:relations "c1" "c2"]
           :body {:line 16 :type :unknown :raw "."
                  :from :application-component
                  :to :application-component}}]
         (arch/lint-content (make-content "."))))
  (is (= [{:level :warn
           :kind :unspecified-relation-type
           :in [:relations "c1" "c2"]
           :body {:line 16 :type :serving :raw "->"
                  :from :application-component
                  :to :application-component}}]
         (arch/lint-content (make-content "->"))))
  (is (= [{:level :error
           :kind :undefined-relation-from
           :in [:relations "c3" "c2"]
           :body {:line 16 :type :composition :raw "*-"
                  :from "c3" :to :application-component}}]
         (arch/lint-content (s/replace (make-content "*-") "c1 *- c2" "c3 *- c2"))))
  (is (= [{:level :error
           :kind :undefined-relation-to
           :in [:relations "c1" "c3"]
           :body {:line 16 :type :composition :raw "*-"
                  :from :application-component :to "c3"}}]
         (arch/lint-content (s/replace (make-content "*-") "c1 *- c2" "c1 *- c3"))))
  (is (= [{:level :error
           :kind :undefined-relation-from
           :in [:relations "c3" "c4"]
           :body {:line 16 :type :composition :raw "*-" :from "c3" :to "c4"}}
          {:level :error
           :kind :undefined-relation-to
           :in [:relations "c3" "c4"]
           :body {:line 16 :type :composition :raw "*-" :from "c3" :to "c4"}}]
         (arch/lint-content (s/replace (make-content "*-") "c1 *- c2" "c3 *- c4"))))
  (is (= [{:level :warn
           :kind :relation-between-components-already-present
           :in [:relations "c2" "c1"]
           :body {:line 17 :type :composition :raw "-*" :reverse? true
                  :from :application-component :to :application-component}}]
         (arch/lint-content (s/replace (make-content "*-") "c1 *- c2" "c1 *- c2\nc1 -* c2"))))
  (is (= [{:level :warn :kind :missing-start}
          {:level :warn :kind :missing-end}]
         (arch/lint-content (s/replace (make-content "*-") #"@\w+" ""))))
  (is (= [{:level :warn :kind :missing-archimate-include}]
         (arch/lint-content (s/replace (make-content "*-") #"!include.+?\n" "")))))