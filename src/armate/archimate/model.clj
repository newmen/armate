(ns armate.archimate.model
  "The seam over the shared, untagged model context map.

  Every reader/writer of the model (the .puml intake, the .archimate intake, the
  derivation engine, the collector, and each renderer) crosses this module instead of
  assembling paths by hand. The @:misc@ cache, the call-count buckets, and the archi
  intake id->alias map (@:misc :archi@) all live behind this seam."
  (:require [clojure.string :as s]
            [armate.archimate.multi-graph :as mg]
            [armate.utils :as u]))

(defn element
  "The element (or nil) stored for @alias under @:elements@."
  [context alias]
  (get-in context [:elements alias]))

(defn connector
  "The connector (or nil) stored for @alias under @:connectors@."
  [context alias]
  (get-in context [:connectors alias]))

(defn resolve-model
  "The element if present, else the connector, else nil. The parser's `cf` union."
  [context alias]
  (or (element context alias)
      (connector context alias)))

(defn element-kind
  "The kind of the element at @alias (nil when absent)."
  [context alias]
  (get-in context [:elements alias :kind]))

(defn element-name
  "The display name of the element at @alias (nil when absent)."
  [context alias]
  (get-in context [:elements alias :name]))

(defn element-in
  "The nesting parent alias of the element at @alias (nil when absent)."
  [context alias]
  (get-in context [:elements alias :in]))

(defn element-layer
  "The layer of an element, derived from its kind (:business-, :application-, ...)."
  [element]
  (when-let [k (:kind element)]
    (-> (name k)
        (s/split #"-")
        (first)
        (keyword))))

(defn element-specie
  "The specie of an element: everything after the layer in its kind, or its stored :specie."
  [element]
  (or (:specie element)
      (when-let [k (:kind element)]
        (let [parts (s/split (name k) #"-")]
          (when (< 1 (count parts))
            (keyword (s/join "-" (rest parts))))))))

(defn relation
  "The set of relations between @from and @to (nil when absent, #{} when empty explicitly stored)."
  [context from to]
  (get-in context [:relations from to]))

(defn hidden-relation
  "The set of hidden relations between @from and @to."
  [context from to]
  (get-in context [:hidden from to]))

(defn relations-graph
  "The raw @:relations@ {from {to #{rel}}} graph."
  [context]
  (:relations context))

(defn types
  "The @:types@ sprite map."
  [context]
  (:types context))

(defn type-kind
  "The kind of the sprite type @alias (nil when absent)."
  [context alias]
  (get-in context [:types alias :kind]))

(defn skin
  "The skin record for @target (a vector key), or nil."
  [context target]
  (get-in context [:skins target]))

(defn includes
  "The @:includes@ package map."
  [context]
  (:includes context))

;; writers / intakes & derivation only

(defn add-element
  "Assoc/replace the element for @alias under @:elements@ (and its kind cache when the
  element carries @:kind@). Idempotent (callers may re-add an existing element)."
  [context alias element]
  (let [context (assoc-in context [:elements alias] element)]
    (if-let [k (:kind element)]
      (assoc-in context [:misc k alias] element)
      context)))

(defn add-connector
  "Assoc/replace the connector for @alias under @:connectors@ (and its kind cache)."
  [context alias connector]
  (let [context (assoc-in context [:connectors alias] connector)]
    (if-let [k (:kind connector)]
      (assoc-in context [:misc k alias] connector)
      context)))

(defn merge-element
  "Merge @params into the existing element at @alias under @:elements@ (no cache write)."
  [context alias params]
  (update-in context [:elements alias] merge params))

(defn- assoc-relation-set
  [store-key context from to rels]
  (assoc-in context [store-key from to] rels))

(defn set-relation
  "Add @rel to the set of relations between @from and @to in @:relations@."
  [context from to rel]
  (update-in context [:relations from to] u/fnil-conj-set rel))

(defn add-hidden-relation
  "Add @rel to the set of hidden relations between @from and @to in @:hidden@."
  [context from to rel]
  (update-in context [:hidden from to] u/fnil-conj-set rel))

(defn assoc-relations
  "Replace the whole set of relations between @from and @to."
  [context from to rels]
  (assoc-relation-set :relations context from to rels))

(defn set-element-in
  "Set the nesting parent of @alias to @parent (nil clears it)."
  [context alias parent]
  (assoc-in context [:elements alias :in] parent))

(defn update-relations
  "Apply @f to the whole @:relations@ map (derivation erasures, filtering)."
  [context f]
  (update context :relations f))

(defn filter-relations
  "Filter @:relations@ (and optionally @:hidden@) with a predicate over [from to rel]."
  ([context predicate]
   (update context :relations (partial mg/filter-relationships predicate)))
  ([context predicate hidden?]
   (-> (filter-relations context predicate)
       (cond-> hidden?
         (update :hidden (partial mg/filter-relationships predicate))))))

;; :misc seam (already available as readers; :misc still stored as-is)

(defn call-rate-buckets
  "The call-count rate buckets held under @:misc :counters :buckets@ (meaning c)."
  [context]
  (get-in context [:misc :counters :buckets]))

(def set-slot-stores
  #{:relations :hidden})

(defn upsert-slot
  "The single writer for the loose model tree at the slot @path of @model
  (e.g. [:elements alias], [:relations from to]).

  Structural rules mirror the .puml intake's append logic:
  * no previous value at @path -> assoc @body (replace).
  * map-style slots (@:start :includes :skins :types :elements :connectors) -> merge @body.
  * set-style slots (@:relations :hidden): a 1-element @body set is conj'd; a caller
    @duplicate?@ fn decides whether the re-add is a duplicate worth linting (set conj is a
    data no-op either way, matching the intake).

  Returns `{:model <new> :duplicate <dup-body-or-nil>}`."
  [duplicate? model path body]
  (let [prev (get-in model path)
        store (first path)
        set-slot? (set-slot-stores store)]
    (if set-slot?
      (if (and (set? prev) (set? body) (= 1 (count body)))
        (let [bd1 (first body)]
          {:model (assoc-in model path (conj prev bd1))
           :duplicate (when (duplicate? prev bd1) bd1)})
        (if (and (nil? prev) (set? body))
          {:model (assoc-in model path body)
           :duplicate nil}
          {:model (update-in model path merge body)
           :duplicate body}))
      (if prev
        {:model (update-in model path merge body)
         :duplicate body}
        {:model (assoc-in model path body)
         :duplicate nil}))))

(defn relation-between-reverse
  "Whether a relation is already stored at the given store (@:relations@ or @:hidden@)
  at @from to@."
  [context store from to]
  (boolean (get-in context [store from to])))

(defn has-include?
  "Whether the @:includes@ map has the given package key."
  [context package]
  (boolean (get-in context [:includes package])))

(defn start
  "The @:start@ header map."
  [context]
  (:start context))

(defn set-start-title
  "Set @:title@ on the @:start@ header."
  [context title]
  (assoc-in context [:start :title] title))

(defn cache
  "The element/connector/grouping cache (meaning a). Read-only accessor."
  [context kind alias]
  (get-in context [:misc kind alias]))

(defn without-internals
  "Drop the internal @:misc@ slot (cache, counters, archi map) from the output model."
  [context]
  (dissoc context :misc))

;; archi id -> alias map (meaning b), owned by this seam

(defn cache-alias
  "Record the archi id -> alias resolution for @id in @:misc :archi@ and return the updated
  context. Idempotent: re-recording the same @id overwrites the alias. Deterministic —
  the @:misc :archi@ map is derived solely from the invocations made against a context."
  [context id alias]
  (assoc-in context [:misc :archi id] alias))

(defn alias-for-id
  "The element/alias stored for archi @id under @:misc :archi@, or nil."
  [context id]
  (get-in context [:misc :archi id]))

(defn archi-id-map
  "The whole archi id -> alias map under @:misc :archi@."
  [context]
  (get-in context [:misc :archi]))

(defn archi-id?
  "Whether @id is a registered archi id (present in @:misc :archi@)."
  [context id]
  (boolean (get-in context [:misc :archi id])))