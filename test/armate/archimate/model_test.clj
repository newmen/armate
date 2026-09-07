(ns armate.archimate.model-test
  "Deletion test for the model seam.

  If the model module were deleted again, would the shape of an element scatter across
  N writers? This test proves the module concentrates the shape: every element-kind /
  element-layer / relation-set access goes through the interface, and no test here reaches
  into :elements / :relations / :misc paths by hand. Updating the model's internal layout
  requires touching only armate.archimate.model and this test stays green."
  (:require [clojure.test :refer [deftest is testing]]
            [armate.archimate.model :as model]))

(defn- add
  "Add an element named @alias (a string) of the given kind through the interface only."
  [context alias kind]
  (model/add-element context
                     alias
                     {:alias alias
                      :kind kind
                      :name (name kind)}))

(deftest element-kind-through-interface-test
  (testing "element-kind/layer/specie are derived through the interface, not paths"
    (let [ctx (-> (add {} "app1" :application-component)
                  (add "bus1" :business-process)
                  (add "grp1" :grouping))]
      (is (= :application-component (model/element-kind ctx "app1")))
      (is (= :business-process (model/element-kind ctx "bus1")))
      (is (= :grouping (model/element-kind ctx "grp1")))
      (is (nil? (model/element-kind ctx "missing")))
      (is (= :application (model/element-layer (model/element ctx "app1"))))
      (is (= :business (model/element-layer (model/element ctx "bus1"))))
      (is (= :component (model/element-specie (model/element ctx "app1"))))
      (is (= :process (model/element-specie (model/element ctx "bus1"))))
      (is (= :grouping (model/element-layer (model/element ctx "grp1")))))))

(deftest relation-set-through-interface
  (testing "relations are written and read only via the interface"
    (let [ctx (-> (add {} "from" :application-component)
                  (add "to" :business-service)
                  (model/set-relation "from" "to"
                                      {:from :application-component
                                       :to :business-service
                                       :type :serving}))
          rels (model/relation ctx "from" "to")]
      (is (= 1 (count rels)))
      (is (= :serving (:type (first rels))))
      (is (nil? (model/relation ctx "from" "from"))
          "absent relation set reads as nil (no empty #{} planted)")
      (is (nil? (model/relation ctx "nope" "to")))
      (let [ctx2 (model/set-relation ctx "from" "to" {:type :access})
            rels2 (model/relation ctx2 "from" "to")]
        (is (= 2 (count rels2)))))))

(deftest layer-kind-concentrated-in-module
  (testing "kind/layer derivation is a single module responsibility (locality)"
    (let [el {:kind :application-interface}]
      (is (= :application (model/element-layer el))))))

(deftest add-element-keeps-element-readable
  (let [ctx (model/add-element {} "x" {:kind :technology-node :name "Node"})
        el (model/element ctx "x")]
    (is el "add-element stores the element under its alias, readable via the interface")
    (is (= :technology-node (:kind el)))
    (is (= :technology-node (model/element-kind ctx "x")))
    (is (= :technology (model/element-layer el))
        "a raw element (no :layer stored) still derives its layer from its kind")))