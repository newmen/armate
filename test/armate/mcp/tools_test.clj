(ns armate.mcp.tools-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [armate.mcp.tools :as tools]))

(def demo-path "test/resources/demo.archimate")

(defn- with-demo
  "Run @f with a registry that has a loaded demo model, binding the rec (registry) and id."
  [f]
  (let [[r _id] (tools/handle-tool "load_model" {} {:path demo-path})]
    (f r "demo")))

(defn- txt [rc] (:text (first (:content rc))))
(defn- ok? [rc] (not (:isError rc)))

(deftest tool-list-16-tools
  (testing "tool-list advertises all 17 armate tools"
    (let [defs (tools/tool-list)
          names (set (map :name defs))]
      (is (= 17 (count names)))
      (is (contains? names "load_model"))
      (is (contains? names "related_elements"))
      (is (contains? names "get_stats"))
      (is (contains? names "derived_relations")))))

(deftest load-model-returns-id
  (testing "load_model loads the demo and returns its model_id")
  (with-demo
    (fn [_r id]
      (is (= "demo" id)))))

(deftest list-views
  (testing "list_views returns the demo views"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "list_views" r
                                        {:model_id id})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "Процесс")))))))

(deftest render-view-produces-plantuml
  (testing "render_view yields @startuml"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "render_view" r
                                        {:model_id id :view "Процесс"})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "@startuml")))))))

(deftest render-view-mode-controls-derived-edges
  (testing "render_view renders derived edges per mode: certain only for :certain, none for :none, both for :certain+potential"
    (with-demo
      (fn [r id]
        (let [[_ none-rc] (tools/handle-tool "render_view" r
                                             {:model_id id :view "Процесс" :mode "none"})
              [_ certain-rc] (tools/handle-tool "render_view" r
                                                {:model_id id :view "Процесс" :mode "certain"})
              [_ both-rc] (tools/handle-tool "render_view" r
                                             {:model_id id :view "Процесс" :mode "certain+potential"})
              none (txt none-rc)
              certain (txt certain-rc)
              both (txt both-rc)]
          (is (ok? none-rc))
          (is (ok? certain-rc))
          (is (ok? both-rc))
          (is (>= (count (s/split-lines certain)) (count (s/split-lines none)))
              "mode :certain added at least the certain-derived relations to the view")
          (is (>= (count (s/split-lines both)) (count (s/split-lines certain)))
              "mode :certain+potential adds at least as many relation lines as :certain"))))))

(deftest render-view-mode-certain-shows-derived-token
  (testing "mode :certain renders a relation token that mode :none suppresses"
    (with-demo
      (fn [r id]
        (let [[_ none-rc] (tools/handle-tool "render_view" r
                                             {:model_id id :view "Процесс" :mode "none"})
              [_ certain-rc] (tools/handle-tool "render_view" r
                                                {:model_id id :view "Процесс" :mode "certain"})
              none (txt none-rc)
              certain (txt certain-rc)]
          (is (ok? none-rc))
          (is (ok? certain-rc))
          (is (some (fn [l] (and (re-find #"^Rel_" l) (not (s/includes? none l))))
                    (s/split-lines certain))
              "certain's derived edge line is absent from mode :none"))))))

(deftest list-elements-with-view
  (testing "list_elements narrows to a view's sub-context"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "list_elements" r
                                        {:model_id id :view "Процесс"})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "Волк")))))))

(deftest element-views-and-relation-views
  (testing "element_views / relation_views resolve by name"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "element_views" r
                                        {:model_id id :name "Волк"})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "Шахматы")))
        (let [[_ rc] (tools/handle-tool "relation_views" r
                                        {:model_id id :from_name "Волк" :to_name "Бухает"})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "assignment")))))))

(deftest related-and-paths
  (testing "related_elements, shortest_path, all_paths render/return"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "related_elements" r
                                        {:model_id id :name "Волк"})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "@startuml")))
        (let [[_ rc] (tools/handle-tool "shortest_path" r
                                        {:model_id id :from_name "Волк" :to_name "Наличные"})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "Волк")))
        (let [[_ rc] (tools/handle-tool "all_paths" r
                                        {:model_id id :from_name "Волк" :to_name "Покупает пойло"})]
          (is (ok? rc)))))))

(deftest related-elements-unknown-name-suggests-nearest
  (testing "related_elements with an unknown element name returns an error with hints"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "related_elements" r
                                        {:model_id id :name "Вовк"})]
          (is (not (ok? rc)))
          (let [out (txt rc)]
            (is (s/includes? out "Unknown element: Вовк") out)
            (is (s/includes? out "Did you mean") out)
            (is (s/includes? out "Волк") out)
            (is (re-find #"ba12" out) "suggests the alias/kind/layer of the near-miss")))
        (testing "a known name still renders successfully"
          (let [[_ rc-ok] (tools/handle-tool "related_elements" r
                                              {:model_id id :name "Волк"})]
            (is (ok? rc-ok))
            (is (s/includes? (txt rc-ok) "@startuml"))))))))

(deftest shortest-path-renders-names-and-directions
  (testing "shortest_path shows element names and <- for edges traversed against direction"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "shortest_path" r
                                        {:model_id id
                                         :from_name "Производитель пойла"
                                         :to_name "Волк"})]
          (is (ok? rc))
          (let [out (txt rc)]
            (is (s/includes? out "Производитель пойла -(assignment)-> Варит пойло") out)
            (is (s/includes? out "Варит пойло -(access_w)-> Пойло") out)
            (is (s/includes? out "Пойло <-(access_r)- Бухает") out)
            (is (s/includes? out "Бухает <-(assignment)- Волк") out)
            (is (not (s/includes? out "ba29")) "uses element names, not aliases")
            (is (not (s/includes? out "bpc20")) "uses element names, not aliases")))))))

(deftest directed-path-uses-names-and-forward-arrows
  (testing "directed=true keeps -> everywhere and still uses element names"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "shortest_path" r
                                        {:model_id id
                                         :from_name "Волк" :to_name "Бухает"
                                         :directed true})]
          (is (ok? rc))
          (let [out (txt rc)]
            (is (s/includes? out "Волк -(assignment)-> Бухает") out)
            (is (not (s/includes? out "bpc20")) "uses element names, not aliases")))))))

(deftest all-paths-renders-names-and-directions
  (testing "all_paths renders element names and direction-aware arrows on every path"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "all_paths" r
                                        {:model_id id
                                         :from_name "Производитель пойла"
                                         :to_name "Волк"})]
          (is (ok? rc))
          (let [out (txt rc)]
            (is (s/includes? out "Производитель пойла -(assignment)-> Варит пойло") out)
            (is (s/includes? out "<-") "undirected all_paths surfaces a backward edge")
            (is (not (s/includes? out "ba29")) "uses element names, not aliases")))))))

(deftest all-paths-directed-uses-names-and-forward-arrows
  (testing "directed=true all_paths keeps -> everywhere and still uses element names"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "all_paths" r
                                        {:model_id id
                                         :from_name "Волк" :to_name "Бухает"
                                         :directed true})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "Волк -(assignment)-> Бухает"))
          (is (not (s/includes? (txt rc) "<-")) "directed all_paths has no backward arrows")
          (is (not (s/includes? (txt rc) "bpc20")) "uses element names, not aliases"))))))

(deftest get-stats-tool
  (with-demo
    (fn [r id]
      (let [[_ rc] (tools/handle-tool "get_stats" r
                                      {:model_id id})]
        (is (ok? rc))
        (is (s/includes? (txt rc) ":elements"))))))

(deftest derived-relations-found
  (testing "derived_relations lists certain-derived relations between two elements"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "derived_relations" r
                                        {:model_id id
                                         :from_name "Волк" :to_name "Наличные"})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "access_r") "lists the derived access_r relation")
          (is (s/includes? (txt rc) "Волк") "uses element names in output"))))))

(deftest derived-relations-empty
  (testing "derived_relations reports a clear 'No derived relations' message when none exist"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "derived_relations" r
                                        {:model_id id
                                         :from_name "Деньги" :to_name "Пойло"})]
          (is (ok? rc))
          (is (s/includes? (txt rc) "No derived relations")))))))

(deftest derived-relations-depth-window
  (testing "derived_relations scopes to the depth window around each element"
    (with-demo
      (fn [r id]
        (let [[_ shallow] (tools/handle-tool "derived_relations" r
                                             {:model_id id
                                              :from_name "Волк" :to_name "Покупает пойло"
                                              :depth 1})
              [_ deep] (tools/handle-tool "derived_relations" r
                                          {:model_id id
                                           :from_name "Волк" :to_name "Покупает пойло"
                                           :depth 3})
              sh (txt shallow)
              dp (txt deep)]
          (is (ok? shallow))
          (is (ok? deep))
          (is (not (s/includes? sh "Продавец пойла"))
              "depth 1 does not reach the far relation")
          (is (s/includes? dp "Продавец пойла(triggering) -> Покупает пойло")
              "depth 3 surfaces the farther derived relation"))))))

(deftest derived-relations-tool-schema
  (testing "derived_relations is advertised with an inputSchema"
    (let [defs (tools/tool-list)
          rv (first (filter #(= "derived_relations" (:name %)) defs))]
      (is (some? rv))
      (is (= "object" (get-in rv [:inputSchema :type])))
      (is (every? #(contains? (get-in rv [:inputSchema :properties]) %)
                  ["model_id" "from_name" "to_name"])))))

(deftest error-unknown-model
  (testing "unknown model_id is an isError result"
    (let [[_ rc] (tools/handle-tool "list_views" {} {:model_id "nope"})]
      (is (not (ok? rc)))
      (is (s/includes? (txt rc) "Unknown model_id")))))

(deftest error-unknown-element
  (testing "unknown element name is an isError result with fuzzy nearest-name hints"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "shortest_path" r
                                        {:model_id id :from_name "Волк" :to_name "Наличн"})]
          (is (not (ok? rc)))
          (is (s/includes? (txt rc) "Unknown element"))
          (is (s/includes? (txt rc) "Did you mean"))
          (is (s/includes? (txt rc) "Наличные")))))))

(deftest error-unknown-element-suggests-nearest
  (testing "a near-miss name suggests the closest real element with its alias/kind/layer"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "element_views" r
                                        {:model_id id :name "Вовк"})]
          (is (not (ok? rc)))
          (is (s/includes? (txt rc) "Волк"))
          (is (re-find #"ba12" (txt rc)))
          (is (re-find #"business-actor.*business" (txt rc))))))))

(deftest error-ambiguous-element-lists-candidates
  (testing "a name shared by several elements is an isError listing candidates, not fuzzy hints"
    (with-demo
      (fn [r id]
        (let [r (assoc-in r [id :context :elements "ba27" :name] "Волк")
              [_ rc] (tools/handle-tool "element_views" r
                                        {:model_id id :name "Волк"})]
          (is (not (ok? rc)))
          (is (s/includes? (txt rc) "Ambiguous element name"))
          (is (s/includes? (txt rc) "ba12"))
          (is (s/includes? (txt rc) "ba27"))
          (is (not (s/includes? (txt rc) "Did you mean"))))))))

(deftest tool-list-has-input-schemas
  (testing "every advertised tool carries a non-empty inputSchema"
    (let [defs (tools/tool-list)
          rv (first (filter #(= "render_view" (:name %)) defs))
          props (get-in rv [:inputSchema :properties])]
      (is (every? #(map? (get-in % [:inputSchema :properties])) defs))
      (is (contains? props "model_id"))
      (is (contains? props "view"))
      (is (contains? props "mode"))
      (is (= "object" (get-in rv [:inputSchema :type])))
      (is (contains? (set (get-in rv [:inputSchema :required])) "view")))))

(deftest list-elements-shows-views
  (testing "list_elements annotates each element with the views that place it"
    (with-demo
      (fn [r id]
        (let [[_ rc] (tools/handle-tool "list_elements" r {:model_id id})
              волк-line (->> (s/split-lines (txt rc))
                             (some #(when (s/includes? % "Волк") %)))]
          (is (some? волк-line))
          (is (s/includes? волк-line "views: Процесс"))
          (is (s/includes? волк-line "Шахматы")))))))

(deftest render-uses-stable-aliases
  (testing "render_view uses the same global aliases as list_elements"
    (with-demo
      (fn [r id]
        (let [[_ rc-list] (tools/handle-tool "list_elements" r {:model_id id})
              line (some #(when (s/includes? % "Волк") %) (s/split-lines (txt rc-list)))
              list-alias (second (re-find #"Волк \| ([a-z0-9]+)" line))
              [_ rc-render] (tools/handle-tool "render_view" r {:model_id id :view "Процесс"})]
          (is (some? list-alias))
          (is (re-find (re-pattern (str "Business_Actor\\(" list-alias ", \"Волк\""))
                       (txt rc-render))))))))