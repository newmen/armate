(ns armate.archimate.archi.title-test
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.archi.title :as t]))

(deftest remove-wrap-hyphens-test
  (testing "wrap hyphen (гласная-согласная) removed"
    (is (= "Идентификация" (t/remove-wrap-hyphens "Идентифи-кация")))
    (is (= "Каталог" (t/remove-wrap-hyphens "Ката-лог")))
    (is (= "Автоматизация" (t/remove-wrap-hyphens "Автомати-зация")))
    (is (= "моделирование" (t/remove-wrap-hyphens "модели-рование"))))
  (testing "wrap hyphen (гласная-гласная) removed"
    (is (= "архитектура" (t/remove-wrap-hyphens "архи-тектура")))
    (is (= "функция" (t/remove-wrap-hyphens "функ-ция"))))
  (testing "wrap hyphen (согласная-согласная) removed when not compound"
    (is (= "приложение" (t/remove-wrap-hyphens "при-ложение")))
    (is (= "документация" (t/remove-wrap-hyphens "докумен-тация"))))
  (testing "fully uppercase words preserve hyphen"
    (is (= "ИДЕНТИФИКАЦИЯ-МОДУЛЬ" (t/remove-wrap-hyphens "ИДЕНТИФИКАЦИЯ-МОДУЛЬ")))
    (is (= "АВТОМАТИЗАЦИЯ-СЕРВИС" (t/remove-wrap-hyphens "АВТОМАТИЗАЦИЯ-СЕРВИС"))))
  (testing "compound words preserved"
    (is (= "бизнес-процесс" (t/remove-wrap-hyphens "бизнес-процесс")))
    (is (= "бизнес-процесс-автоматизации" (t/remove-wrap-hyphens "бизнес-процесс-автоматизации")))
    (is (= "онлайн-заказ" (t/remove-wrap-hyphens "онлайн-заказ")))
    (is (= "интернет-банк" (t/remove-wrap-hyphens "интернет-банк"))))
  (testing "word shorter than min parts kept"
    (is (= "И-ка" (t/remove-wrap-hyphens "И-ка")))
    (is (= "а-б" (t/remove-wrap-hyphens "а-б"))))
  (testing "hyphen with non-cyrillic kept"
    (is (= "работать-по-smart" (t/remove-wrap-hyphens "работать-по-smart")))
    (is (= "API-сервис" (t/remove-wrap-hyphens "API-сервис")))
    (is (= "abc-def" (t/remove-wrap-hyphens "abc-def"))))
  (testing "no hyphen returns as-is"
    (is (= "Простое название" (t/remove-wrap-hyphens "Простое название")))
    (is (= "" (t/remove-wrap-hyphens "")))))

(deftest normalize-title-test
  (testing "wrap hyphen removed"
    (is (= "Идентификация" (t/normalize-title "Идентифи-кация")))
    (is (= "Автоматизация" (t/normalize-title "Автомати-зация"))))
  (testing "compound hyphen preserved"
    (is (= "бизнес-процесс" (t/normalize-title "бизнес-процесс")))
    (is (= "онлайн-заказ" (t/normalize-title "онлайн-заказ"))))
  (testing "underscore with spaces collapsed"
    (is (= "foo_bar" (t/normalize-title "foo_ bar")))
    (is (= "foo_bar" (t/normalize-title "foo _bar")))
    (is (= "a_b_c" (t/normalize-title "a_ b_ c"))))
  (testing "slash with spaces collapsed"
    (is (= "a/b" (t/normalize-title "a/ b")))
    (is (= "a/b" (t/normalize-title "a /b")))
    (is (= "a/b/c" (t/normalize-title "a/ b/ c"))))
  (testing "slash with spaces collapsed (exceptional case)"
    (is (= "GET /api/v1/hello/{id}" (t/normalize-title "GET /api/v1 /hello/{id}")))
    (is (= "PATCH /api/v1/hello/{id}" (t/normalize-title "PATCH /api/ v1 /hello /{id}")))
    (is (= "DELETE /api/v1/hello-world/{id}" (t/normalize-title "DELETE /api/ v1 /hello-world /{id}"))))
  (testing "combined"
    (is (= "Идентификаци/модуль_заказа" (t/normalize-title "Идентифи-каци/ модуль_ заказа"))))
  (testing "no changes"
    (is (= "Простое название" (t/normalize-title "Простое название")))
    (is (= "Existing - name" (t/normalize-title "Existing - name")))
    (is (= "POST /api/v1/hello" (t/normalize-title "POST /api/v1/hello")))
    (is (= "Статус: используется / мигрировал (не доступен)" (t/normalize-title "Статус: используется / мигрировал (не доступен)")))))