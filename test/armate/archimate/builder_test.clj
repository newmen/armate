(ns armate.archimate.builder-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.builder :as abd]))

(deftest patch-raw-name-test
  (is (= "" (abd/patch-raw-name "")))
  (is (= "hello_world_"
         (abd/patch-raw-name "hello-world?")))
  (is (= "hello___world___"
         (abd/patch-raw-name "hello *(world)+?"))))
