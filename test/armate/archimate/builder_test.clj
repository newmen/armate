(ns armate.archimate.builder-test
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.builder :as abd]
            [armate.archimate.name :as name]))

(deftest patch-raw-name-test
  (is (= "" (name/patch-raw-name "")))
  (is (= "hello_world_"
         (name/patch-raw-name "hello-world?")))
  (is (= "hello___world___"
         (name/patch-raw-name "hello *(world)+?"))))

(deftest connector-name-unifies-like-element-name
  (testing "a Junction's :name and :title are one unified pair, matching elements"
    (let [title "по-прежнему уроки"
          [_ connector] (abd/add-connector abd/init-context :or title)
          [_ element] (abd/add-element abd/init-context :business-process "bpc01" title)]
      (is (= (:name connector) (:title connector))
          "the Junction stores one unified name/title pair")
      (is (= (:name connector) (:name element))
          "the Junction and element names unify the same way")
      (is (= (:title connector) (:title element))
          "the Junction and element titles unify the same way"))))