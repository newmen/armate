(ns armate.archimate.viz.saver-test
  (:require [clojure.string :as s]
            [clojure.test :refer [deftest is testing]]
            [armate.archimate.viz.saver :as svr]
            [armate.archimate.viz.support :as su]))

(def graph
  {:relations {:a {:b #{{:type :composition :direction :down}}
                   :c #{{:type :assignment :direction :up}}
                   :d #{{:type :assignment :direction :up}}}
               :b {:e #{{:type :assignment :direction :up}}}}
   :elements {:a {:kind :application-component}
              :b {:kind :application-component}
              :c {:kind :application-interface}
              :d {:kind :application-interface}
              :e {:kind :application-interface}}})

(deftest align-toggle-test
  (testing "align? false leaves the context unchanged (no hidden edges)"
    (let [ctx (svr/align-elements false graph)]
      (is (= (select-keys graph [:relations :elements])
             (select-keys ctx [:relations :elements])))
      (is (nil? (:hidden ctx)))))
  (testing "align? true appends hidden edges"
    (let [ctx (svr/align-elements true graph)]
      (is (some? (:hidden ctx))))))

;; ---------------------------------------------------------------------------
;; Ticket 03: save-puml / save roundtrip
;; ---------------------------------------------------------------------------

(deftest save-puml-emits-flat-valid-output
  (let [g {:alias "g" :kind :grouping :type :grouping :title "Group"}
        c {:alias "c" :kind :application-component :title "Comp"}
        out (svr/generate-save-puml (su/ctx [g c]
                                            (su/edge "g" "c" :composition))
                                    "out.puml" false)]
    (testing "output is valid PlantUML wrapper"
      (is (s/includes? out "@startuml"))
      (is (s/includes? out "@enduml")))
(testing "flat output: grouping element not nested (backward compatible)"
      (is (s/includes? out "Grouping(g, \"Group\")"))
      (is (not (s/includes? out "Grouping(g, \"Group\") {"))
          "no nesting `{` block for the grouping element"))))

(deftest save-puml-suppresses-derived-edges
  (let [a {:alias "a" :kind :application-component :title "A"}
        b {:alias "b" :kind :business-actor :title "B"}
        out (svr/generate-save-puml
             (su/ctx [a b]
                  (su/combine-rels (su/edge "a" "b" :serving)
                                (su/derived-edge "a" "b" :specialization :certain)))
             "out.puml" false)]
    (is (s/includes? out "Rel_Serving(a, b)") "plain edge rendered")
    (testing "derived edge marker (:derivate :certain) is not rendered as an edge"
      (is (not (s/includes? out "Rel_Specialization(a, b)"))
          "certain-derived edge suppressed with default #{} render-derivable"))))

(deftest save-puml-roundtrip-stable
  (let [out (svr/generate-save-puml graph "model" false)
        again (svr/generate-save-puml graph "model" false)]
    (testing "save output is deterministic"
      (is (= out again)))
    (testing "save output is valid and complete"
      (is (s/includes? out "@startuml"))
      (is (s/includes? out "@enduml")))))

(deftest save-puml-writes-file
  (let [tmp (java.io.File/createTempFile "saver-test-" ".puml")
        _ (.delete tmp)
        path (.getPath tmp)]
    (svr/save-puml graph path false)
    (testing "file written by save-puml matches generated string"
      (is (= (svr/generate-save-puml graph path false)
             (s/trim (slurp path)))))))