(ns armate.archimate.name-test
  "Deletion test for `armate.archimate.name`.

  If this module were deleted, the archi intake, .puml intake and builder would each have
  to re-introduce a slice of hyphen/slash/camel/length handling. These cases pin the four
  families so refactors concentrate through one interface: if they regress, the rules need
  a home again."
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.name :as n]))

(deftest hyphen-normalization-test
  (testing "wrap hyphen merged only when cyrillic + mergeable"
    (is (= "Идентификация" (n/normalize-name "Идентифи-кация")))
    (is (= "Автоматизация" (n/normalize-name "Автомати-зация")))
    (is (= "Передедубликация" (n/normalize-name "Пере-дедублика-ция")))
    (is (= "бизнес-процесс" (n/normalize-name "бизнес-процесс")))
    (is (= "ИДЕНТИФИКАЦИЯ-МОДУЛЬ" (n/normalize-name "ИДЕНТИФИКАЦИЯ-МОДУЛЬ"))))
  (testing "slash spacing keeps both-side spaces and uppercase paths"
    (is (= "GET /api/v1/hello/{id}" (n/normalize-name "GET /api/v1 /hello/{id}")))
    (is (= "a/b" (n/normalize-name "a/ b")))))

(deftest strait-name-test
  (testing "whitespace and inline newlines collapse to one space"
    (is (= "Получение списка a b c"
           (n/strait-name "Получение\nсписка  a b\n\nc")))))

(deftest camel-length-lex-test
  (testing "long camelCase names split into display lines"
    (is (= "ОченьДлинная\\nНадписьДля\\nПроверки\\nРаскладкиСлов"
           (n/lex-name "ОченьДлиннаяНадписьДляПроверкиРаскладкиСлов")))
    (is (= "hello world\\nsecond line"
           (n/lex-name "hello world\nsecond line"))))
  (testing "alias length cap and patch characters"
    (is (= "бизнес-процесс-автоматизации"
           (n/length-cap "бизнес-процесс-автоматизации-заказа-клиента")))
    (is (= "biznes_process_avtomatizacii"
           (n/alias-name "бизнес-процесс-автоматизации-заказа-клиента")))
    (is (= "hello_world_"
           (n/patch-raw-name "hello-world?")))
    (is (= "hello___world___"
           (n/patch-raw-name "hello *(world)+?")))))