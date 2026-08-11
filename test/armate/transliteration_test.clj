(ns armate.transliteration-test
  (:require [clojure.test :refer [deftest is]]
            [armate.transliteration :as tl]))

(deftest transliterate-test
  (is (= "hello world"
         (tl/transliterate "hello world")))
  (is (= "Debetovaya karta"
         (tl/transliterate "Дебетовая карта"))))
