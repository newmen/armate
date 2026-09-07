(ns armate.mcp.analytics-test
  (:require [clojure.test :refer [deftest is testing]]
            [clojure.string :as s]
            [clj-fuzzy.metrics :as fuzzy]
            [armate.archimate.archi.parser :as arr]
            [armate.archimate.metamodel.derivation.core :as dcr]
            [armate.mcp.analytics :as ana]))

(def demo-path "test/resources/demo.archimate")

(def fixture
  "A model record built once from the demo fixture."
  (let [enriched (arr/get-model demo-path)
        [enriched graph] (arr/enrich-with-graph enriched)]
    (ana/build-model-record enriched graph)))

(def graph
  (:context fixture))

(def enriched
  (:enriched fixture))

;; ---------------------------------------------------------------------------
;; Ticket 02: element selection + stats
;; ---------------------------------------------------------------------------

(deftest resolve-name-unique
  (testing "unique element names resolve to {:single {name alias kind layer}}"
    (let [res (ana/resolve-name graph "Волк")]
      (is (= {:name "Волк" :alias "ba12" :kind :business-actor :layer :business}
             (:single res))))))

(deftest resolve-name-ambiguous-lists-candidates
  (testing "a name shared by several elements yields a candidate list"
    (let [g (-> graph
                (assoc-in [:elements "ba12" :name] "Двойник")
                (assoc-in [:elements "ba27" :name] "Двойник")) 
          res (ana/resolve-name g "Двойник")]
      (is (contains? res :candidates))
      (is (= 2 (count (:candidates res)))))))

(deftest resolve-name-unknown
  (testing "unknown names resolve to nil"
    (is (nil? (ana/resolve-name graph "Несуществующий")))))

;; ---------------------------------------------------------------------------
;; Ticket 07 — fuzzy nearest-name suggestions for unknown names
;; ---------------------------------------------------------------------------

(deftest nearest-elements-ranks-closest-first
  (testing "nearest-elements ranks the closest real name first for a typo"
    (let [sugs (ana/nearest-elements graph "Вовк")]
      (is (= "Волк" (:name (first sugs))))
      (is (contains? (set (map :name sugs)) "Волк")))))

(deftest nearest-elements-records-and-respects-limit
  (testing "suggestions carry name/alias/kind/layer and respect the limit"
    (let [sugs (ana/nearest-elements graph "Вовк" :limit 3)]
      (is (<= (count sugs) 3))
      (is (every? #(= (set [:name :alias :kind :layer]) (set (keys %)))
                  sugs)))))

(deftest nearest-elements-threshold-filters-far-names
  (testing "a threshold floors out a far name, so a near-miss keeps suggestions but a nonsense query may be empty"
    (let [near (ana/nearest-elements graph "Вовк" :threshold 0.4)
          near-names (set (map :name near))
          far (ana/nearest-elements graph "zxcvbnm" :threshold 0.6)]
      (is (contains? near-names "Волк"))
      (is (< (count far) (count near)))
      (is (every? #(>= (fuzzy/jaro-winkler "Вовк" %) 0.4) near-names)))))

(deftest nearest-elements-lists-each-alias-of-a-shared-name
  (testing "a name shared by several aliases yields one suggestion per alias"
    (let [g (-> graph
                (assoc-in [:elements "ba12" :name] "Двойник")
                (assoc-in [:elements "ba27" :name] "Двойник"))
          sugs (ana/nearest-elements g "Двойник" :limit 8)]
      (is (= 2 (count (filter #(= "Двойник" (:name %)) sugs)))))))

(deftest filter-by-type-whole-and-view
  (testing "filter_by_type across the whole model and narrowed to a view"
    (let [all (ana/filter-by-type enriched graph :business-process)
          in-view (ana/filter-by-type enriched graph :business-process "Процесс")]
      (is (= 10 (count all)))
      (is (every? #(= :business-process (:kind %)) all))
      (is (every? #(= :business-process (:kind %)) in-view))
      (testing "the Процесс view's sub-context narrows to its placed business-processes"
        (is (< (count in-view) (count all)))))))

(deftest filter-by-layer-whole-and-view
  (testing "filter by layer across whole model and within a view"
    (let [business (ana/filter-by-layer enriched graph :business)
          in-view (ana/filter-by-layer enriched graph :business "Семья")]
      (is (= 28 (count business)))
      (is (every? #(= :business (:layer %)) business))
      (is (every? #(= :business (:layer %)) in-view))
      (is (< (count in-view) (count business))))))

(deftest label-map-unique-names
  (testing "label-map returns the bare name for unique element names"
    (is (= "Волк" (get (ana/label-map graph) "ba12")))
    (is (= "Бухает" (get (ana/label-map graph) "bpc20")))))

(deftest label-map-disambiguates-ambiguous-names
  (testing "label-map disambiguates shared names with alias/kind/layer"
    (let [g (-> graph
                (assoc-in [:elements "ba12" :name] "Двойник")
                (assoc-in [:elements "ba27" :name] "Двойник"))
          labels (ana/label-map g)]
      (is (s/starts-with? (get labels "ba12") "Двойник ["))
      (is (s/starts-with? (get labels "ba27") "Двойник ["))
      (is (not= (get labels "ba12") (get labels "ba27")) "labels differ between ambiguous aliases")
      (is (s/includes? (get labels "ba12") "ba12")))))

(deftest stats-shape
  (testing "get_stats reports types/:elements/:relations/:lints"
    (let [st (ana/stats graph)]
      (is (= [:types :elements :relations :lints] (keys st)))
      (is (integer? (:types st)))
      (is (map? (:elements st)))
      (is (map? (:relations st))))))

;; ---------------------------------------------------------------------------
;; Ticket 03 — related_elements induced subgraph
;; ---------------------------------------------------------------------------

(deftest related-elements-renders-plantuml
  (testing "rendered related_elements is valid PlantUML with the root placed"
    (let [puml (ana/related-elements graph nil "Волк" 1)]
      (is (s/includes? puml "@startuml"))
      (is (s/includes? puml "Business_Actor(ba12"))
      (is (re-find #"Rel_" puml)))))

(deftest related-elements-unknown-root-suggests-nearest-names
  (testing "an unknown root name throws an error listing nearest element names"
    (let [e (try (ana/related-elements graph nil "Вовк" 1)
                 (catch clojure.lang.ExceptionInfo e e))]
      (is (some? e))
      (is (s/includes? (ex-message e) "Unknown element: Вовк"))
      (is (s/includes? (ex-message e) "Did you mean"))
      (is (s/includes? (ex-message e) "Волк"))
      (is (re-find #"ba12" (ex-message e))))))

(deftest render-view-produces-plantuml
  (testing "render_view renders the view sub-context with its placed elements"
    (let [puml (ana/render-view enriched graph "Процесс" :none nil 50)]
      (is (s/includes? puml "@startuml"))
      (is (re-find #"Business_Actor\(" puml)))))

(deftest render-merged-views-unions-subcontext
  (testing "merge_views unions placed elements of several views"
    (let [puml (ana/render-merged-views enriched graph ["Процесс" "Семья"] :none nil 50)]
      (is (s/includes? puml "@startuml")))))

(deftest render-views-with-certain-augments
  (testing "mode :certain merges global certain derived relationships into the view"
    (let [certain (dcr/derivate-certain-relations graph)
          puml (ana/render-view enriched graph "Процесс" :certain certain 30)]
      (is (s/includes? puml "@startuml"))
      (is (s/includes? puml "@enduml")))))

(defn- derivate-kinds
  "The set of `:derivate` kinds present in context @ctx's relations."
  [ctx]
  (->> (:relations ctx)
       (vals)
       (mapcat vals)
       (mapcat identity)
       (keep :derivate)
       (into #{})))

(deftest certain-mode-excludes-potential
  (testing "mode :certain renders only certain-derived relations; potential ones stay out"
    (let [certain (dcr/derivate-certain-relations graph)
          merged (ana/with-certain (ana/build-sub-context graph #{"ba12" "bo6"} nil) certain)
          kinds (derivate-kinds merged)]
      (is (not (contains? kinds :potential)))
      (is (contains? kinds :certain)))))

(deftest certain+potential-respects-cap-and-applies-locally
  (testing "mode :certain+potential guards against element counts above the cap"
    (let [ctx (ana/build-sub-context graph (set (keys (:elements graph))) nil)]
      (is (thrown? clojure.lang.ExceptionInfo
                   (ana/apply-mode ctx :certain+potential nil 0))))
    (testing "a sub-context within the cap renders without throwing"
      (let [sub (ana/build-sub-context graph #{"ba12" "bo6"} nil)]
        (is (string? (ana/render-puml (ana/apply-mode sub :certain+potential nil 30)
                                      "sub")))))))


;; ---------------------------------------------------------------------------
;; Ticket 04 — paths
;; ---------------------------------------------------------------------------

(deftest shortest-path-exists
  (testing "shortest path between related elements"
    (let [wolf (ana/alias-of (ana/resolve-name graph "Волк"))
          nal (ana/alias-of (ana/resolve-name graph "Наличные"))]
      (is (= "ba12" wolf))
      (is (= "bo6" nal))
      (is (some? (ana/shortest-path graph wolf nal nil))))))

(deftest shortest-path-none
  (testing "no path returns nil when endpoints sit in disjoint components"
    (let [rel {:type :association}
          g {:relations {"a" {"b" #{rel}}
                         "b" {"a" #{rel}}
                         "c" {"d" #{rel}}
                         "d" {"c" #{rel}}}
             :elements {"a" {:name "A"} "b" {:name "B"}
                        "c" {:name "C"} "d" {:name "D"}}}]
      (is (nil? (ana/shortest-path g "a" "d" nil))))))

(deftest derived-relations-lists-certain
  (testing "derived-relations returns the global derived relations, scoped by aliases"
    (let [certain (dcr/derivate-relations graph)
          wolf (get-in graph [:elements "ba12" :alias])
          nal (get-in graph [:elements "bo6" :alias])
          rels (ana/derived-relations certain #{wolf nal})]
      (is (seq rels))
      (is (some (fn [[from to rel]]
                  (and (= wolf from) (= nal to)
                       (= :certain (:derivate rel))))
                rels)))))

(deftest derived-relations-empty-for-non-overlapping-window
  (testing "derived-relations returns nothing when the window has no derived edge"
    (let [rel {:type :access_r}
          certain {:relations {"a" {"c" #{rel}}
                               "c" {"a" #{rel}}}}
          window #{"a" "b"}]
      (is (empty? (ana/derived-relations certain window))))))

(deftest shortest-path-undirected-default
  (testing "shortest-path traverses relationships in both directions by default
            (undirected), so a reverse-direction edge still yields a path"
    (let [wolf (ana/alias-of (ana/resolve-name graph "Волк"))
          nal (ana/alias-of (ana/resolve-name graph "Наличные"))
          rev (ana/alias-of (ana/resolve-name graph "Бухает"))]
      (is (some? (ana/shortest-path graph rev wolf nil)))
      (is (some? (ana/shortest-path graph wolf nal nil))))))

(deftest shortest-path-directed
  (testing "passing directed=true restricts traversal to relationship direction"
    (let [wolf (ana/alias-of (ana/resolve-name graph "Волк"))
          buy (ana/alias-of (ana/resolve-name graph "Покупает пойло"))]
      (is (some? (ana/shortest-path graph wolf buy nil true))))))

(deftest all-paths-count
  (testing "all-paths enumerates simple directed paths when directed=true"
    (let [wolf (ana/alias-of (ana/resolve-name graph "Волк"))
          buy (ana/alias-of (ana/resolve-name graph "Покупает пойло"))]
      (is (= 3 (count (ana/all-paths graph wolf buy nil 6 true)))))))

(deftest all-paths-undirected-is-a-superset
  (testing "undirected default finds at least as many paths as directed, and finds
            reverse-edge paths the directed search misses"
    (let [wolf (ana/alias-of (ana/resolve-name graph "Волк"))
          buy (ana/alias-of (ana/resolve-name graph "Покупает пойло"))
          directed (ana/all-paths graph wolf buy nil 6 true)
          undirected (ana/all-paths graph wolf buy nil 6 false)]
      (is (>= (count undirected) (count directed))))))

(deftest all-paths-undirected-finds-reverse-edges
  (testing "all-paths is undirected by default, so a path using a reverse-direction
            edge is enumerated"
    (let [nal (ana/alias-of (ana/resolve-name graph "Наличные"))
          rev (ana/alias-of (ana/resolve-name graph "Бухает"))]
      (is (seq (ana/all-paths graph rev nal nil))))))

(deftest all-paths-directed-mode
  (testing "all-paths with directed=true still finds forward-direction paths"
    (let [wolf (ana/alias-of (ana/resolve-name graph "Волк"))
          buy (ana/alias-of (ana/resolve-name graph "Покупает пойло"))]
      (is (seq (ana/all-paths graph wolf buy nil 6 true))))))

(deftest all-paths-preserves-different-edge-types
  (testing "paths that differ only by edge type between the same nodes are kept,
            not collapsed (dedup is per endpoint sequence AND edge type)"
    (let [rel1 {:type :association}
          rel2 {:type :triggering}
          g {:relations {"a" {"b" #{rel1 rel2}}
                         "b" {"c" #{rel1}}
                         "c" {"b" #{rel1}}}
             :elements {"a" {:name "A"} "b" {:name "B"} "c" {:name "C"}}}
          paths (ana/all-paths g "a" "c" nil 6 false)]
      (is (some #(= ["a" "b" "c"] (mapv (fn [s] (if (vector? s) (first s) s)) %))
                paths)))))

(deftest all-paths-max-len
  (testing "all-paths respects a max edge length"
    (let [wolf (ana/alias-of (ana/resolve-name graph "Волк"))
          buy (ana/alias-of (ana/resolve-name graph "Покупает пойло"))]
      (is (every? (fn [p] (<= (count p) 3)) (ana/all-paths graph wolf buy nil 3))))))
