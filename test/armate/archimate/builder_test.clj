(ns armate.archimate.builder-test
  (:require [clojure.test :refer [deftest is]]
            [armate.archimate.name :as name]))

(deftest patch-raw-name-test
  (is (= "" (name/patch-raw-name "")))
  (is (= "hello_world_"
         (name/patch-raw-name "hello-world?")))
  (is (= "hello___world___"
         (name/patch-raw-name "hello *(world)+?"))))