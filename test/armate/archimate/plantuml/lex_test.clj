(ns armate.archimate.plantuml.lex-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.plantuml.lex :as lex]))

(deftest call-re-test
  (is (= ["Rel_Assignment_Up(operationsI, tapeS)" "Rel_Assignment_Up" "operationsI" "tapeS" nil]
         (re-matches lex/call-re "Rel_Assignment_Up(operationsI, tapeS)")))
  (is (= ["Rel_Assignment_Up(operationsI, tapeS, \"Some description\")" "Rel_Assignment_Up" "operationsI" "tapeS" "\"Some description\""]
         (re-matches lex/call-re "Rel_Assignment_Up(operationsI, tapeS, \"Some description\")")))
  (is (nil? (re-matches lex/call-re "abs *-up- online"))))

(deftest full-line-re-test
  (is (= ["Rel_Serves(A, \"B\", \"C\", \"D\")" "Rel_Serves" "A" "\"B\"" "\"C\", \"D\"" nil nil]
         (re-matches lex/full-line-re "Rel_Serves(A, \"B\", \"C\", \"D\")")))
  (is (= ["Grouping(business_r, \"Бизнес\") #1122f3 {" "Grouping" "business_r" "\"Бизнес\"" nil "#1122f3" "{"]
         (re-matches lex/full-line-re "Grouping(business_r, \"Бизнес\") #1122f3 {")))
  (is (nil? (re-matches lex/full-line-re "rectangle \"Получение\nспискa\" as list_as <<$aService>> #Application")))
  (is (nil? (re-matches lex/full-line-re "accountOptionsService_acp -[hidden]down-> templatesService_acp"))))

(deftest quoted-brackets-split-test
  (is (= ["@startuml"]
         (lex/quoted-brackets-split "@startuml")))
  (is (= ["@startuml" "\"Какое-то длинное описание\""]
         (lex/quoted-brackets-split "@startuml \"Какое-то длинное описание\"")))
  (is (= ["rectangle" "\"Component1\"" "as" "c1" "<<$aComponent>>"]
         (lex/quoted-brackets-split "rectangle \"Component1\" as c1 <<$aComponent>>")))
  (is (= ["rectangle" "\"Component 2\"" "as" "c2" "<<$aComponent>>"]
         (lex/quoted-brackets-split "rectangle \"Component 2\" as c2 <<$aComponent>>")))
  (is (= ["rectangle" "Component3" "as" "c3" "<<$aComponent>>"]
         (lex/quoted-brackets-split "rectangle Component3 as c3 <<$aComponent>>")))
  (is (= ["skinparam" "rectangle<<sub>>" "{"]
         (lex/quoted-brackets-split "skinparam rectangle<<sub>> {")))
  (is (= ["Rel_Serves" "A" "\"B\"" "\"C\", D"]
         (lex/quoted-brackets-split "Rel_Serves(A, \"B\", \"C\", D)")))
  (is (= ["Grouping" "business_r" "\"Бизнес\"" "#1122f3" "{"]
         (lex/quoted-brackets-split "Grouping(business_r, \"Бизнес\") #1122f3 {"))))

(deftest get-parts-test
  (is (= {:parts ["skinparam" "rectangle<<sub>>"] :block? true}
         (lex/get-parts "skinparam rectangle<<sub>> {")))
  (is (= {:parts ["skinparam" "rectangle<<sub>>"] :block? true}
         (lex/get-parts "skinparam rectangle<<sub>>{")))
  (is (= {:parts ["sprite" "$aCollaboration" "jar:archimate/application-collaboration"] :block? false}
         (lex/get-parts "sprite $aCollaboration jar:archimate/application-collaboration")))
  (is (= {:parts ["abs" "*-up-" "online"] :block? false}
         (lex/get-parts "abs *-up- online")))
  (is (= {:parts ["Rel_Assignment_Up" "operationsI" "tapeS" nil] :block? false}
         (lex/get-parts "Rel_Assignment_Up(operationsI, tapeS)")))
  (is (= {:parts ["Rel_Assignment_Up" "operationsI" "tapeS" "\"Some description\""] :block? false}
         (lex/get-parts "Rel_Assignment_Up(operationsI, tapeS, \"Some description\")"))))

(deftest parse-variables-test
  (is (= ["$a" "1"]
         (lex/parse-variable "!$a = 1")))
  (is (= ["a" "1"]
         (lex/parse-variable "!a = 1")))
  (is (= ["a" "123"]
         (lex/parse-variable "!a=123")))
  (is (= ["a" "11"]
         (lex/parse-variable "!a =11")))
  (is (= ["abc" "11"]
         (lex/parse-variable "!abc= 11"))))

(deftest mask-variable-test
  (is (= "\\$a\\b"
         (lex/mask-variable "$a")))
  (is (= "\\$\\$a\\b"
         (lex/mask-variable "$$a")))
  (is (= "\\$a\\$"
         (lex/mask-variable "$a$")))
  (is (= "\\ba\\b"
         (lex/mask-variable "a"))))

(deftest apply-variables-test
  (is (= "-1- line has variable"
         (lex/apply-variables {"a" "1"
                               "with" "has"} "-a- line with variable")))
  (is (= "a line with 1 variable"
         (lex/apply-variables {"$a" "1"} "a line with $a variable"))))

(deftest fur-re-test
  (is (= [["rectangle" "rectangle"] ["<<db>>" "<<db>>"]]
         (re-seq lex/fur-re "rectangle<<db>>")))
  (is (= [["<<$aCollaboration>>" "<<$aCollaboration>>"] ["<<platform>>" "<<platform>>"]]
         (re-seq lex/fur-re "<<$aCollaboration>><<platform>>"))))

(deftest cut-furs-test
  (is (= ["rectangle" "db"] (lex/cut-furs "rectangle<<db>>")))
  (is (= ["$aCollaboration" "platform"] (lex/cut-furs "<<$aCollaboration>><<platform>>")))
  (is (= ["$aCollaboration"] (lex/cut-furs "<<$aCollaboration>>"))))

(deftest cut1-test
  (is (= "archimate/Archimate" (lex/cut1 "<archimate/Archimate>"))))

(deftest cut2-test
  (is (= "$aComponent" (lex/cut2 "<<$aComponent>>"))))