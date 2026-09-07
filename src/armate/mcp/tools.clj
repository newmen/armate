(ns armate.mcp.tools
  "The armate MCP tool handlers.

  `handle-tool` dispatches each MCP `tools/call`. Handlers take `[registry args]` and
  return `[registry' rc]` (the registry is updated by load/unload/reload), where @rc is the
  MCP result shape `{:content [{:type \"text\" :text ...}], :isError bool}`. Inputs are
  validated before computation."
  (:require [clojure.string :as s]
            [armate.archimate.metamodel.meta :as mt]
            [armate.mcp.analytics :as ana]
            [armate.mcp.registry :as reg]))

(set! *warn-on-reflection* true)

(defn- ok [text] {:content [{:type "text" :text text}] :isError false})
(defn- err [text] {:content [{:type "text" :text text}] :isError true})

(defn- existing
  [registry model_id]
  (if (contains? registry model_id)
    {:rec (get registry model_id)}
    {:error (err (str "Unknown model_id: " model_id))}))

(defn- ambiguous-name-message
  "The error text for an ambiguous element @name, listing the @candidates as 'name [alias]'."
  [name candidates]
  (str "Ambiguous element name: " name "\nCandidates: "
       (s/join ", " (mapv #(str (:name %) " [" (:alias %) "]") candidates))))

(defn- resolve-alias
  "Resolve @name against @graph to an alias, producing an informative error when unknown.
   Unknown-name errors list the nearest element names (fuzzy match, threshold 0.67) with
   alias/kind/layer to help the caller."
  [graph name]
  (let [r (ana/resolve-name graph name)]
    (cond
      (nil? r) (let [sug (ana/nearest-elements graph name :threshold 0.67)]
                 {:error (err (ana/unknown-name-message name sug))})
      (contains? r :candidates) {:error (err (ambiguous-name-message name (:candidates r)))}
      :else {:alias (get-in r [:single :alias])})))

(defn- mode-kw
  "Parse a derivation mode string to a keyword, or nil when invalid."
  [mode]
  (condp = mode
    nil :none
    "" :none
    "none" :none
    "certain" :certain
    "certain+potential" :certain+potential
    nil))

(defn- rel-type-kw
  [name]
  (let [kw (keyword name)]
    (if (or (contains? #{:structural :dependency :dynamic :other} kw)
            (mt/structural? kw) (mt/dependency? kw) (mt/dynamic? kw)
            (= :specialization kw))
      kw
      (throw (ex-info (str "Invalid rel-type: " name) {:code :bad-rel-type})))))

(defn- rel-types-set
  [rel-types]
  (cond
    (nil? rel-types) nil
    (string? rel-types) #{(rel-type-kw rel-types)}
    (coll? rel-types) (into #{} (map rel-type-kw) rel-types)
    :else (throw (ex-info (str "Invalid rel-types: " rel-types) {:code :bad-rel-type}))))

(defn- seg-str
  "Render one path segment `[alias rel]` as `alias -(type)-> ` or `alias <-(type)- `,
   following the traversal direction, using labels from @labels (an alias→label map, see
   @armate.mcp.analytics/label-map)."
  [labels [alias rel]]
  (str (get labels alias)
       (if (ana/path-segment-reversed? rel)
         (str " <-(" (name (:type rel)) ")- ")
         (str " -(" (name (:type rel)) ")-> "))))

(defn path-str
  "Render a path (a vector of `[alias rel]` segments ending in the goal alias) as a
   human-readable chain, using @labels (alias→label) and direction-aware arrows. A
   `-(...)->` arrow means the edge is traversed along its intended direction; a `<-` marks
   an edge traversed against its intended direction."
  [labels p]
  (if (seq p)
    (str (s/join (map (fn [seg] (seg-str labels seg)) (butlast p)))
         (get labels (last p)))
    ""))

;; ---------------------------------------------------------------------------
;; registry tools
;; ---------------------------------------------------------------------------

(defn load_model
  [registry {:keys [path]}]
  (if (s/blank? path)
    [registry (err "load_model requires a 'path' argument")]
    (try
      (let [[r id] (reg/load-model registry path)]
        [r (ok id)])
      (catch Exception e
        [registry (err (str "Failed to load " path ": " (ex-message e)))]))))

(defn reload_model
  [registry {:keys [model_id]}]
  (let [{:keys [error]} (existing registry model_id)]
    (if error
      [registry error]
      (try
        (let [[r id] (reg/reload-model registry model_id)]
          [r (ok id)])
        (catch Exception e
          [registry (err (str "Error reloading: " (ex-message e)))])))))

(defn list_models
  [registry _]
  [registry (ok (if (seq (reg/list-models registry))
                  (s/join "\n" (sort (reg/list-models registry)))
                  "No models loaded"))])

(defn unload_model
  [registry {:keys [model_id]}]
  (try
    (let [[r id] (reg/unload-model registry model_id)]
      [r (ok id)])
    (catch Exception e
      [registry (err (ex-message e))])))

;; ---------------------------------------------------------------------------
;; views
;; ---------------------------------------------------------------------------

(defn list_views
  [registry {:keys [model_id]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      [registry (ok (if (seq (:views rec))
                      (s/join "\n" (sort (:views rec)))
                      "no views"))])))

(defn- render-views-puml
  [registry model_id view-names mode]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      {:error error}
      (let [enriched (:enriched rec)
            graph (:context rec)
            mode (mode-kw mode)
            known (set (keys (get-in enriched [:maps :views])))
            unknown (seq (remove known view-names))]
        (cond
          (nil? mode) {:error (err (str "Invalid mode: " mode))}
          unknown {:error (err (str "Unknown view(s): " (s/join ", " unknown)))}
          :else
          (try
            (let [certain? (#{:certain :certain+potential} mode)
                  certain (when certain? (reg/certain-graph registry model_id))
                  aliases (when (= :certain+potential mode)
                            (->> view-names
                                 (mapcat #(ana/view-aliases enriched %))
                                 (into #{})))
                  cap (reg/potential-cap)]
              (when (and aliases (> (count aliases) cap))
                (throw (ex-info (str "Potential derivation exceeds cap " cap)
                                {:code :potential-cap :count (count aliases)})))
              (let [puml (if (= 1 (count view-names))
                           (ana/render-view enriched graph (first view-names) mode certain cap)
                           (ana/render-merged-views enriched graph view-names mode certain cap))]
                {:puml puml}))
            (catch Exception e {:error (err (ex-message e))})))))))

(defn render_view
  [registry {:keys [model_id view mode]}]
  (if (s/blank? view)
    [registry (err "render_view requires a 'view' argument")]
    (let [{:keys [puml error]} (render-views-puml registry model_id [view] mode)]
      [registry (if error error (ok puml))])))

(defn merge_views
  [registry {:keys [model_id views mode]}]
  (if (or (not (coll? views)) (empty? views))
    [registry (err "merge_views requires a non-empty 'views' list")]
    (let [{:keys [puml error]} (render-views-puml registry model_id (vec views) mode)]
      [registry (if error error (ok puml))])))

(defn- el-line
  "One roster line: name | alias | kind | layer | views..."
  [enriched e]
  (let [views (get-in enriched [:element-views (:alias e)])]
    (str (:name e) " | " (:alias e) " | " (name (:kind e)) " | " (name (:layer e))
         (when (seq views) (str " | views: " (s/join "," (sort views)))))))

(defn list_elements
  [registry {:keys [model_id view type layer]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      (let [enriched (:enriched rec)
            g (:context rec)
            type (some-> type keyword)
            layer (some-> layer keyword)
            els (cond->> (ana/all-elements g)
                  type (filter #(= type (:kind %)))
                  layer (filter #(= layer (:layer %)))
                  view (filter #(contains? (ana/view-aliases enriched view) (:alias %))))]
        (if (seq els)
          [registry (ok (s/join "\n" (map #(el-line enriched %) (sort-by :name els))))]
          [registry (ok "No elements")])))))

(defn filter_by_type
  [registry {:keys [model_id type view]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      (let [enriched (:enriched rec)
            els (ana/filter-by-type enriched (:context rec) (keyword type) view)]
        [registry (ok (if (seq els)
                        (s/join "\n" (map #(el-line enriched %) (sort-by :name els)))
                        "No elements"))]))))

(defn filter_by_layer
  [registry {:keys [model_id layer view]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      (let [enriched (:enriched rec)
            els (ana/filter-by-layer enriched (:context rec) (keyword layer) view)]
        [registry (ok (if (seq els)
                        (s/join "\n" (map #(el-line enriched %) (sort-by :name els)))
                        "No elements"))]))))

(defn element_views
  [registry {:keys [model_id name]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      (let [a (resolve-alias (:context rec) name)]
        (if-let [e (:error a)]
          [registry e]
          (let [views (get-in (:enriched rec) [:element-views (:alias a)])]
            [registry (ok (if (seq views)
                            (s/join "\n" (sort views))
                            "Not placed in any view"))]))))))

(defn relation_views
  [registry {:keys [model_id from_name to_name type]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      (let [fa (resolve-alias (:context rec) from_name)
            ta (resolve-alias (:context rec) to_name)]
        (cond
          (:error fa) [registry (:error fa)]
          (:error ta) [registry (:error ta)]
          :else
          (let [rv (get-in (:enriched rec) [:relation-views [(:alias fa) (:alias ta)]] {})]
            (if type
              (let [views (get rv (keyword type))]
                [registry (ok (if (seq views) (s/join "\n" (sort views))
                                "no views for this relation type"))])
              [registry (ok (if (seq rv)
                              (s/join "\n" (map (fn [[t vs]]
                                                  (str (name t) ": {" (s/join ", " (sort vs)) "}"))
                                                (sort rv)))
                              "no relations between these elements"))])))))))

(defn derived_relations
  "MCP tool: list the relationships inferred between two elements by ArchiMate derivation
   rules (the global `certain` derivation). Scoped to @depth hops around each of the two
   elements (default 1); the from/to names themselves are always in scope. Returns one
   line per derived relation; empty when none."
  [registry {:keys [model_id from_name to_name depth]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      (let [fa (resolve-alias (:context rec) from_name)
            ta (resolve-alias (:context rec) to_name)]
        (cond
          (:error fa) [registry (:error fa)]
          (:error ta) [registry (:error ta)]
          :else
          (try
            (let [graph (:context rec)
                  depth (or depth 1)
                  from-alias (:alias fa)
                  to-alias (:alias ta)
                  window (-> (ana/depth-aliases graph from-alias depth)
                             (into (ana/depth-aliases graph to-alias depth)))
                  endpoint? (fn [alias] (or (= alias from-alias)
                                            (= alias to-alias)))
                  derived (->> (ana/derived-relations (reg/certain-graph registry model_id) window)
                               (filter (fn [[_ _ rel]] (= :certain (:derivate rel))))
                               (filter (fn [[from to _]] (or (endpoint? from)
                                                             (endpoint? to))))
                               (map (fn [[from to rel]]
                                      (let [fn' (:name (ana/element-of graph from))
                                            tn (:name (ana/element-of graph to))]
                                        (str fn' "(" (name (:type rel)) ") -> " tn))))
                               (distinct)
                               (vec))]
              (if (seq derived)
                [registry (ok (s/join "\n" derived))]
                [registry (ok (str "No derived relations between " from_name
                                   " and " to_name))]))
            (catch Exception e [registry (err (ex-message e))])))))))

(defn related_elements
  [registry {:keys [model_id name depth mode]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      (let [a (resolve-alias (:context rec) name)]
        (if-let [e (:error a)]
          [registry e]
          (try
            (let [depth (or depth 1)
                  mode (mode-kw mode)
                  certain (when (#{:certain :certain+potential} mode)
                            (reg/certain-graph registry model_id))
                  aliases (ana/depth-aliases (:context rec) (:alias a) depth)
                  cap (reg/potential-cap)]
              (when (and (= :certain+potential mode) (> (count aliases) cap))
                (throw (ex-info (str "Potential derivation exceeds cap " cap)
                                {:code :potential-cap :count (count aliases)})))
              [registry (ok (ana/related-elements (:context rec)
                                                  certain
                                                  name
                                                  depth
                                                  mode
                                                  cap))])
            (catch Exception e [registry (err (ex-message e))])))))))

(defn shortest_path
  [registry {:keys [model_id from_name to_name rel_types directed]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      (let [fa (resolve-alias (:context rec) from_name)
            ta (resolve-alias (:context rec) to_name)]
        (cond
          (:error fa) [registry (:error fa)]
          (:error ta) [registry (:error ta)]
          :else
          (try
            (let [types (rel-types-set rel_types)
                  result (ana/shortest-path (:context rec)
                                            (:alias fa)
                                            (:alias ta)
                                            types
                                            directed)]
              [registry (ok (if (nil? result)
                              (str "No path from " from_name " to " to_name)
                              (path-str (ana/label-map (:context rec)) result)))])
            (catch clojure.lang.ExceptionInfo e
              [registry (err (ex-message e))])))))))

(defn all_paths
  [registry {:keys [model_id from_name to_name rel_types directed]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      (let [fa (resolve-alias (:context rec) from_name)
            ta (resolve-alias (:context rec) to_name)]
        (cond
          (:error fa) [registry (:error fa)]
          (:error ta) [registry (:error ta)]
          :else
          (try
            (let [types (rel-types-set rel_types)
                  result (ana/all-paths (:context rec)
                                        (:alias fa)
                                        (:alias ta)
                                        types
                                        6
                                        directed)
                  labels (ana/label-map (:context rec))]
              [registry (ok (if (seq result)
                              (s/join "\n\n" (map (partial path-str labels) result))
                              (str "No paths from " from_name " to " to_name)))])
            (catch clojure.lang.ExceptionInfo e
              [registry (err (ex-message e))])))))))

(defn get_stats
  [registry {:keys [model_id]}]
  (let [{:keys [rec error]} (existing registry model_id)]
    (if error
      [registry error]
      [registry (ok (pr-str (ana/stats (:context rec))))])))

(defn- handle-dispatch
  [tool-name]
  (get {"load_model" load_model
        "reload_model" reload_model
        "list_models" list_models
        "unload_model" unload_model
        "list_views" list_views
        "render_view" render_view
        "merge_views" merge_views
        "list_elements" list_elements
        "filter_by_type" filter_by_type
        "filter_by_layer" filter_by_layer
        "element_views" element_views
        "relation_views" relation_views
        "derived_relations" derived_relations
        "related_elements" related_elements
        "shortest_path" shortest_path
        "all_paths" all_paths
        "get_stats" get_stats}
       tool-name))

(defn handle-tool
  "Dispatch a tools/call for @tool-name with @args against the @registry. Returns
   [registry' result]."
  [tool-name registry args]
  (if-let [f (handle-dispatch tool-name)]
    (f registry args)
    [registry (err (str "Unknown tool: " tool-name))]))

(declare tool-schemas)

(defn tool-list
  "The MCP `tools/list` payload, with a per-tool input schema so a client can tell the
   parameter names/types and required fields."
  []
  (mapv (fn [{:keys [name description properties required]}]
          {:name name
           :description description
           :inputSchema {:type "object"
                         :properties properties
                         :required required}})
        (sort-by :name tool-schemas)))

(def tool-schemas
  "Declarative metadata: name, description, JSON Schema properties and required args per
   armate MCP tool."
  [{:name "load_model"
    :description "Load an .archimate file into the model registry, returning its model_id."
    :required ["path"]
    :properties {"path" {:type "string" :description "Path to the .archimate file"}}}
   {:name "reload_model"
    :description "Re-read an already-loaded model_id's file, keeping the same id."
    :required ["model_id"]
    :properties {"model_id" {:type "string" :description "Id of a loaded model"}}}
   {:name "list_models"
    :description "List the loaded model ids."
    :required []
    :properties {}}
   {:name "unload_model"
    :description "Remove a loaded model from the registry."
    :required ["model_id"]
    :properties {"model_id" {:type "string"}}}
   {:name "list_views"
    :description "List the view names of a model."
    :required ["model_id"]
    :properties {"model_id" {:type "string"}}}
   {:name "render_view"
    :description "Render a view to PlantUML @startuml text. Alias values = the stable model aliases shared by list_elements / related_elements / paths. The mode param adds relationships inferred by ArchiMate derivation rules on top of the explicit model: certain adds globally-implied relations, certain+potential also adds locally-derivable ones (see derived_relations for direct access). Potential derivation is capped at 30 involved elements and denotes a possibility, not a certainty, of an inferred relationship."
    :required ["model_id" "view"]
    :properties {"model_id" {:type "string"}
                 "view" {:type "string" :description "View name"}
                 "mode" {:type "string" :enum ["none" "certain" "certain+potential"]
                         :description "Derivation mode (default none). certain/certain+potential render inferred (derived) relationships per ArchiMate rules, not just the explicit model relations. Potential derivation is capped at 30 involved elements and denotes a possibility, not a certainty."}}}
   {:name "merge_views"
    :description "Render the union of several views to PlantUML. Like render_view, the mode param adds relationships inferred by ArchiMate derivation rules (certain/certain+potential) to reveal implied structure beyond the explicit model. Potential derivation is capped at 30 involved elements and denotes a possibility, not a certainty, of an inferred relationship."
    :required ["model_id" "views"]
    :properties {"model_id" {:type "string"}
                 "views" {:type "array" :items {:type "string"}
                          :description "View names to merge"}
                 "mode" {:type "string" :enum ["none" "certain" "certain+potential"]
                         :description "Derivation mode (default none). certain/certain+potential add inferred (derived) relationships per ArchiMate rules. Potential derivation is capped at 30 involved elements and denotes a possibility, not a certainty."}}}
   {:name "list_elements"
    :description "List model elements as 'name | alias | kind | layer | views...'. Optionally narrow by view, type or layer."
    :required ["model_id"]
    :properties {"model_id" {:type "string"}
                 "view" {:type "string" :description "Only elements placed in this view"}
                 "type" {:type "string" :description "ArchiMate element kind, e.g. business-process"}
                 "layer" {:type "string" :description "ArchiMate layer, e.g. business"}}}
   {:name "filter_by_type"
    :description "List elements of a given type, optionally within a view."
    :required ["model_id" "type"]
    :properties {"model_id" {:type "string"}
                 "type" {:type "string"}
                 "view" {:type "string"}}}
   {:name "filter_by_layer"
    :description "List elements of a given layer, optionally within a view."
    :required ["model_id" "layer"]
    :properties {"model_id" {:type "string"}
                 "layer" {:type "string"}
                 "view" {:type "string"}}}
   {:name "element_views"
    :description "Which view names place a given element (by name)."
    :required ["model_id" "name"]
    :properties {"model_id" {:type "string"}
                 "name" {:type "string" :description "Element name"}}}
   {:name "relation_views"
    :description "Which views place a relationship between two elements (by names); without type, one line per relation type with its view set."
    :required ["model_id" "from_name" "to_name"]
    :properties {"model_id" {:type "string"}
                 "from_name" {:type "string"}
                 "to_name" {:type "string"}
                 "type" {:type "string" :description "Optional relation type, e.g. assignment"}}}
   {:name "derived_relations"
    :description "List the relationships inferred between two elements (by names) by ArchiMate derivation rules (the global certain derivation) — i.e. implied relations the explicit model does not spell out. Scoped to a depth window around each of the two elements when depth is given (default 1). Returns one line per derived relation; 'No derived relations' when none."
    :required ["model_id" "from_name" "to_name"]
    :properties {"model_id" {:type "string"}
                 "from_name" {:type "string"}
                 "to_name" {:type "string"}
                 "depth" {:type "integer" :description "Undirected hop window around each element (default 1)"}}}
   {:name "related_elements"
    :description "Render the induced subgraph around an element (name) up to a depth, as PlantUML. Use mode certain/certain+potential to include relationships inferred by ArchiMate derivation rules, revealing implied structure the explicit model omits. Potential derivation is capped at 30 involved elements and denotes a possibility, not a certainty, of an inferred relationship."
    :required ["model_id" "name"]
    :properties {"model_id" {:type "string"}
                 "name" {:type "string"}
                 "depth" {:type "integer" :description "Undirected hop count (default 1)"}
                 "mode" {:type "string" :enum ["none" "certain" "certain+potential"]
                         :description "Derivation mode (default none). certain/certain+potential add inferred (derived) relationships per ArchiMate rules. Potential derivation is capped at 30 involved elements and denotes a possibility, not a certainty."}}}
   {:name "shortest_path"
    :description "Shortest path between two elements (by name) over original relationships, optionally filtered by rel-type category. Undirected by default, so a chain like a->b->c<-d->e is found; pass directed=true to follow only relationship direction."
    :required ["model_id" "from_name" "to_name"]
    :properties {"model_id" {:type "string"}
                 "from_name" {:type "string"}
                 "to_name" {:type "string"}
                 "directed" {:type "boolean" :description "When true, traverse only along relationship direction (default false/undirected)"}
                 "rel_types" {:type "array" :items {:type "string"}
                              :description "Category/type filter, e.g. ['structural','dependency']"}}}
   {:name "all_paths"
    :description "All simple paths between two elements over original relationships, limiting total length to 6 edges. Undirected by default, so a chain like a->b->c<-d->e is found; pass directed=true to follow only relationship direction. Paths differing only by a duplicate edge of the same type across the same node pair are collapsed."
    :required ["model_id" "from_name" "to_name"]
    :properties {"model_id" {:type "string"}
                 "from_name" {:type "string"}
                 "to_name" {:type "string"}
                 "directed" {:type "boolean" :description "When true, traverse only along relationship direction (default false/undirected)"}
                 "rel_types" {:type "array" :items {:type "string"}}}}
   {:name "get_stats"
    :description "Whole-model statistics."
    :required ["model_id"]
    :properties {"model_id" {:type "string"}}}])
