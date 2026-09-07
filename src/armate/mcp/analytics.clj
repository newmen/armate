(ns armate.mcp.analytics
  "Pure analytics over a loaded armate model, reusing the existing parsing, graph,
  derivation and rendering pipeline rather than reimplementing it.

  A *model record* is the registry's value: the parser's enriched model (carrying
  :element-views / :relation-views and :archi-alias) plus the full graph context built by
  @armate.archimate.archi.parser/get-full-graph. Helpers here take either an enriched model
  or a model record, as annotated on each fn."
  (:require [clojure.string :as s]
            [clj-fuzzy.metrics :as fuzzy]
            [armate.archimate.core :as acore]
            [armate.archimate.metamodel.derivation.core :as dcr]
            [armate.archimate.metamodel.meta :as mt]
            [armate.archimate.model :as model]
            [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]
            [armate.archimate.viz.combiner :as viz]))

;; ---------------------------------------------------------------------------
;; Model record construction (used by the registry)
;; ---------------------------------------------------------------------------

(defn build-model-record
  "Build a model record from a parser @enriched model and the @full-context that was
   already built once during load (see @armate.archimate.archi.parser/enrich-with-graph).
   Stores both the enriched (indexed) model and the context, so `get-full-graph` runs
   exactly once for the whole load."
  [enriched full-context]
  (let [ctx (model/without-internals full-context)]
    {:enriched enriched
     :context ctx
     :views (vec (keys (get-in enriched [:maps :views])))}))

;; ---------------------------------------------------------------------------
;; element roster & name resolution
;; ---------------------------------------------------------------------------

(defn element-of
  "The {name alias kind layer} record for alias @a in @graph (nil when absent)."
  [graph a]
  (let [el (model/element graph a)]
    (when el
      {:name (:name el)
       :alias (:alias el)
       :kind (:kind el)
       :layer (model/element-layer el)})))

(defn all-elements
  "Every {name alias kind layer} element record in the model @graph."
  [graph]
  (->> (:elements graph)
       (keys)
       (map #(element-of graph %))
       (remove nil?)))

(defn label-map
  "A `{alias label}` map for every element of @graph, built in one pass. Each label is the
   element name, or `\"name [alias kind layer]\"` when the name is shared by more than one
   element, so displayed names stay unambiguous."
  [graph]
  (let [els (all-elements graph)
        duplicates (->> els (map :name) frequencies
                        (filter (fn [[_ n]] (> n 1)))
                        (into #{} (map first)))
        k->str (fn [k] (if (keyword? k) (name k) (str k)))]
    (into {}
          (map (fn [{:keys [alias name kind layer]}]
                 [alias (if (duplicates name)
                          (str name " [" alias " " (k->str kind) " " (k->str layer) "]")
                          name)]))
          els)))

(defn resolve-name
  "Resolve a human-readable @name to `{:single el}` when unique, `{:candidates [el ...]}`
   when ambiguous, or nil when unknown."
  [graph name]
  (let [hits (->> (:elements graph)
                  (vals)
                  (filter #(= name (:name %)))
                  (mapv #(element-of graph (:alias %))))]
    (cond
      (empty? hits) nil
      (= 1 (count hits)) {:single (first hits)}
      :else {:candidates hits})))

(defn alias-of
  "The alias for a resolved `{:single el}` result; nil otherwise."
  [res]
  (get-in res [:single :alias]))

(defn nearest-elements
  "The element records (each {name alias kind layer}) whose names are nearest to @name,
   ranked by decreasing Jaro-Winkler similarity. Accepts @:limit (number, default 8) and a
   @:threshold (double in 0..1, default nil = no floor); only names scoring >= threshold are
   returned. Used to offer a short, predictable hint on an unknown element name. Ties on a
   name shared by several aliases are allowed: callers should surface those as an ambiguous
   name error (see @armate.mcp.analytics/resolve-name) rather than rely on deduplication."
  [graph name & {:keys [limit threshold]}]
  (let [limit (or limit 8)]
    (->> (:elements graph)
         (vals)
         (map #(vector (fuzzy/jaro-winkler name (:name %)) %))
         (filter #(or (nil? threshold) (>= (first %) threshold)))
         (sort-by first >)
         (take limit)
         (keep (fn [[_ el]]
                 (when el (element-of graph (:alias el))))))))

(defn- suggestion-label
  "A short 'name [alias kind layer]' label for one fuzzy @hint element record, for the
   unknown-name 'Did you mean' message."
  [{:keys [name alias kind layer]}]
  (str name " [" alias " " (clojure.core/name kind) " " (clojure.core/name layer) "]"))

(defn unknown-name-message
  "The error text for an unknown element @name, listing the nearest @hints (or empty when
   none pass the threshold)."
  [name hints]
  (str "Unknown element: " name
       (when (seq hints)
         (str "\nDid you mean: " (s/join ", " (map suggestion-label hints))))))

;; ---------------------------------------------------------------------------
;; view membership
;; ---------------------------------------------------------------------------

(defn view-aliases
  "The set of element aliases placed in @view-name of @enriched (inverted from
   :element-views). Returns #{} when the view is unknown or empty."
  [enriched view-name]
  (into #{} (keep (fn [[alias views]] (when (views view-name) alias)))
        (:element-views enriched)))

;; ---------------------------------------------------------------------------
;; filters
;; ---------------------------------------------------------------------------

(defn filter-by-type
  "Element records of @kind-type (a keyword like :business-process) over the whole model,
   optionally narrowed to the elements placed in @view."
  ([enriched graph type] (filter-by-type enriched graph type nil))
  ([enriched graph type view]
   (let [aliases (if view
                   (view-aliases enriched view)
                   (set (keys (:elements graph))))]
     (->> aliases
          (map #(element-of graph %))
          (remove nil?)
          (filter #(= type (:kind %)))
          (sort-by :name)))))

(defn filter-by-layer
  "Element records of @layer (e.g. :business) over the whole model, optionally narrowed to
   @view."
  ([enriched graph layer] (filter-by-layer enriched graph layer nil))
  ([enriched graph layer view]
   (let [aliases (if view
                   (view-aliases enriched view)
                   (set (keys (:elements graph))))]
     (->> aliases
          (map #(element-of graph %))
          (remove nil?)
          (filter #(= layer (:layer %)))
          (sort-by :name)))))

;; ---------------------------------------------------------------------------
;; relationships
;; ---------------------------------------------------------------------------

(defn rel-category
  "The ArchiMate category of a @rel-type keyword: :structural, :dependency, :dynamic or
   :other."
  [rel-type]
  (cond
    (mt/structural? rel-type) :structural
    (mt/dependency? rel-type) :dependency
    (mt/dynamic? rel-type) :dynamic
    :else :other))

(defn rel-types?
  "Whether the relationship @rel-type matches the requested @types spec. @types is a set of
   category keywords (:structural :dependency :dynamic :other) and/or concrete type
   keywords."
  [types rel-type]
  (or (types rel-type)
      (types (rel-category rel-type))))

(defn derived-relations
  "All derived relationships in a derivation context @graph (the global `certain`
   derivation from @armate.mcp.registry/certain-graph), as `[from to rel]` triples. A
   derived relation is one that carries a `:derivate` marker. Optionally restricts to
   relations whose endpoints are both within the alias set @aliases (used for a
   depth-limited window); nil @aliases means no restriction."
  ([graph] (derived-relations graph nil))
  ([graph aliases]
   (->> (mg/get-relationships (:relations graph))
        (filter (comp :derivate last))
        (filter (fn [[from to _]]
                  (or (nil? aliases) (and (aliases from) (aliases to))))))))

(def ^:private reversed-key
  "Marker key applied to a relationship when it is traversable in the reverse of its
   intended direction (used only in the undirected path-search adjacency)."
  ::reversed)

(defn- reversed-rel
  [rel]
  (assoc rel reversed-key true))

(defn- path-adjacency
  "Adjacency map for path search over original (non-derived) relationships in @rels,
   optionally filtered by @types (see @rel-types?). When @directed is false (default),
   each relationship is traversable in both directions, so a chain like a->b->c<-d->e is
   navigable from a to e. Relationship direction never affects the rel-type filter. When a
   relationship is stored in the reverse of its intended direction, the entry is marked
   with `::reversed` (see @reversed-key) so path rendering can display the correct arrow."
  [rels types directed]
  (let [pred (fn [rel] (if types (rel-types? types (:type rel)) true))
        original (->> (mg/get-relationships rels)
                      (remove (comp :derivate last))
                      (filter (fn [[_ _ rel]] (pred rel))))
        add (fn [acc [from to rel]]
              (update-in acc [from to] u/fnil-conj-set rel))]
    (if directed
      (reduce add {} original)
      (reduce add
              (reduce add {} original)
              (map (fn [[from to rel]] [to from (reversed-rel rel)]) original)))))

(defn path-segment-reversed?
  "True when @rel (a path-search segment relationship) was traversed in the reverse of its
   intended direction, i.e. the undirected adjacency marked it with `::reversed`."
  [rel]
  (boolean (reversed-key rel)))

(defn shortest-path
  "The shortest path from @from-alias to @to-alias across the whole model (original
   relationships, optionally filtered by @types). Returns a vector of pairs, or nil.
   By default @directed is false, so relationships are traversable in both directions;
   pass a truthy @directed to restrict traversal to the relationship direction."
  ([graph from-alias to-alias] (shortest-path graph from-alias to-alias nil false))
  ([graph from-alias to-alias types] (shortest-path graph from-alias to-alias types false))
  ([graph from-alias to-alias types directed]
   (when (and from-alias to-alias)
     (mg/bfs-shortest-path (path-adjacency (:relations graph) types directed)
                           from-alias to-alias (constantly true)))))

(defn all-paths
  "All simple paths from @from-alias to @to-alias over original relationships (optionally
   filtered by @types), limiting total length to @max-len edges (default 6). By default
   @directed is false, so relationships are traversable in both directions; pass a truthy
   @directed to restrict traversal to the relationship direction. Paths that differ only
   in which duplicate edge spans a node pair (same endpoint sequence and same per-edge
   relationship types) are collapsed."
  ([graph from-alias to-alias] (all-paths graph from-alias to-alias nil 6 false))
  ([graph from-alias to-alias types] (all-paths graph from-alias to-alias types 6 false))
  ([graph from-alias to-alias types max-len] (all-paths graph from-alias to-alias types max-len false))
  ([graph from-alias to-alias types max-len directed]
   (when (and from-alias to-alias)
     (let [raw (mg/all-paths-under-len (path-adjacency (:relations graph) types directed)
                                       from-alias to-alias max-len (constantly true))
           key-fn (fn [path]
                    (mapv (fn [seg]
                            (if (vector? seg)
                              [(first seg) (:type (second seg))]
                              seg))
                          path))]
       (second (reduce (fn [[seen acc] path]
                         (let [k (key-fn path)]
                           (if (seen k)
                             [seen acc]
                             [(conj seen k) (conj acc path)])))
                       [#{} []]
                       raw))))))

;; ---------------------------------------------------------------------------
;; related_elements (induced subgraph)
;; ---------------------------------------------------------------------------

(defn depth-aliases
  "The set of aliases within @depth hops (undirected) of @start-alias in @graph.
   Implements a BFS over the undirected graph; depth 0 is just the root."
  [graph start-alias depth]
  (let [undirected (mg/get-undirected-graph (:relations graph))
        neighbours (fn [a] (keys (undirected a)))]
    (loop [level #{start-alias}
           frontier [start-alias]
           seen #{start-alias}
           d 0]
      (if (or (>= d depth) (empty? frontier))
        level
        (let [next (->> frontier
                        (mapcat neighbours)
                        (remove seen)
                        (distinct))]
          (recur (into level next)
                 next
                 (into seen next)
                 (inc d)))))))

(defn build-sub-context
  "A renderable context containing only @aliases and relationships between them taken from
   the model (per CONTEXT.md's *sub-context of a view*: strictly the placed elements, no
   transitive expansion), optionally augmented with `certain` derived relationships (when
   @with-certain is true and the registry has computed them)."
  [graph aliases certain-relations]
  (let [elements (into {} (keep (fn [a]
                                  (when-let [el (model/element graph a)]
                                    [a el]))
                                aliases))
        selected? (fn [a] (contains? elements a))
        base-rels (->> (mg/get-relationships (:relations graph))
                       (filter (fn [[from to _]] (and (selected? from) (selected? to))))
                       (reduce (fn [acc [from to rel]]
                                 (update-in acc [from to] u/fnil-conj-set rel))
                               {}))
        with-certain (reduce (fn [acc [from to rel]]
                               (update-in acc [from to] u/fnil-conj-set rel))
                             base-rels
                             (or certain-relations []))]
    (assoc graph :elements elements :relations with-certain)))

(defn render-puml
  "Render @ctx (a model/sub-graph context) to PlantUML source."
  [ctx title]
  (-> ctx
      (model/set-start-title title)
      (viz/on-fly-generate-puml)))

;; ---------------------------------------------------------------------------
;; view rendering (render_view / merge_views)
;; ---------------------------------------------------------------------------

(defn filter-relations-to
  "Relationships of @src-graph whose endpoints are both in @aliases, as `[from to rel]`."
  [aliases src-graph]
  (->> (mg/get-relationships (:relations src-graph))
       (filter (fn [[from to _]] (and (aliases from) (aliases to))))))

(defn with-certain
  "Merge @derived-graph's (`certain`) relationships that fall within @view-ctx's placed
   elements into @view-ctx."
  [view-ctx derived-graph]
  (let [aliases (set (keys (:elements view-ctx)))
        derived (filter-relations-to aliases derived-graph)]
    (update view-ctx :relations
            (fn [rels]
              (reduce (fn [acc [from to rel]]
                        (update-in acc [from to] u/fnil-conj-set rel))
                      rels
                      derived)))))

(defn with-potential
  "Run the `potential` derivation locally on @view-ctx's sub-context and merge those
   derived relationships (endpoints within the sub-context) into it. Guarded by @potential
   -cap: throws when the sub-context element count exceeds it."
  [view-ctx potential-cap]
  (let [n (count (:elements view-ctx))]
    (when (> n potential-cap)
      (throw (ex-info (str "Potential derivation exceeds cap " potential-cap)
                      {:code :potential-cap :count n :cap potential-cap})))
    (let [derived (dcr/derivate-relations view-ctx)
          potential (->> (mg/get-relationships (:relations derived))
                         (filter (fn [[_ _ rel]] (= :potential (:derivate rel)))))]
      (update view-ctx :relations
              (fn [rels]
                (reduce (fn [acc [from to rel]]
                          (update-in acc [from to] u/fnil-conj-set rel))
                        rels
                        potential))))))

(defn apply-mode
  "Apply a derivation @mode (:none | :certain | :certain+potential) to the view @ctx using
   the global @certain-graph and the local @potential-cap. Returns the augmented context, or
   throws when the potential cap is exceeded."
  [ctx mode certain-graph potential-cap]
  (case mode
    :none ctx
    :certain (if certain-graph (with-certain ctx certain-graph) ctx)
    :certain+potential (-> (if certain-graph (with-certain ctx certain-graph) ctx)
                           (with-potential potential-cap))))

(defn render-view
  "Render the view @view-name to PlantUML. The sub-context is strictly the view's placed
   elements (no transitive expansion), sliced from the model's GLOBAL full context so the
   Element aliases are the same stable ones @list-elements / @related-elements / paths use.
   Mode and certain/potential handled per @apply-mode."
  [enriched graph view-name mode certain-graph potential-cap]
  (let [aliases (view-aliases enriched view-name)
        sub (build-sub-context graph aliases nil)]
    (render-puml (apply-mode sub mode certain-graph potential-cap) view-name)))

(defn render-merged-views
  "Render the union of views @view-names (merge_views) to PlantUML. The sub-context is the
   union of the placed elements (stable global aliases); eager on their aliases."
  [enriched graph view-names mode certain-graph potential-cap]
  (let [aliases (->> view-names
                     (mapcat #(view-aliases enriched %))
                     (into #{}))
        sub (build-sub-context graph aliases nil)]
    (render-puml (apply-mode sub mode certain-graph potential-cap)
                 (s/join ", " view-names))))

(defn related-elements
  "The induced subgraph around @root-name: all elements within @depth hops (undirected,
   default 1), rendered to PlantUML. Relationships are all model relationships between the
   selected elements, optionally augmented with `certain` derived relationships (when
   @certain-graph is the registry's global certain derivation) and with locally-derived
   `potential` relationships (when @mode is :certain+potential, guarded by @potential-cap).
   Returns the rendered PlantUML string, or throws when the root name is unknown."
  ([graph certain-graph root-name depth]
   (related-elements graph certain-graph root-name depth :none nil))
  ([graph certain-graph root-name depth mode potential-cap]
   (let [root (alias-of (resolve-name graph root-name))]
     (when (nil? root)
       (let [hints (nearest-elements graph root-name :threshold 0.67)]
         (throw (ex-info (unknown-name-message root-name hints)
                         {:name root-name :suggestions hints}))))
     (let [aliases (if (pos? depth)
                     (depth-aliases graph root depth)
                     #{root})
           certain (when certain-graph
                     (->> (mg/get-relationships (:relations certain-graph))
                          (filter (fn [[from to _]] (and (aliases from) (aliases to))))))
           sub (build-sub-context graph aliases certain)]
       (render-puml (apply-mode sub mode certain-graph potential-cap) root-name)))))

;; ---------------------------------------------------------------------------
;; statistics
;; ---------------------------------------------------------------------------

(defn stats
  "Whole-model statistics, reusing armate's own get-stats."
  [graph]
  (acore/get-stats graph))