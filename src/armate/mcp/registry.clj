(ns armate.mcp.registry
  "In-memory model registry for the armate MCP server, keyed by `model_id`.

  A registry is a map `{model_id model-record}` where each model-record is produced by
  @armate.mcp.analytics/build-model-record (the parser's enriched model plus the full-graph
  context), augmented with the lazily-computed `certain` derivation cache under
  `:certain-graph`. `reload_model` re-reads the file and invalidates the cache, keeping the
  same model id. Nothing is persisted across server runs."
  (:require [clojure.string :as s]
            [armate.archimate.archi.parser :as arr]
            [armate.archimate.metamodel.derivation.core :as dcr]
            [armate.mcp.analytics :as ana]))

(def default-potential-cap 30)

(defn potential-cap
  "The element-count cap for `potential` derivation from the MCP_POTENTIAL_CAP env var
   (default 30)."
  []
  (or (some-> (System/getenv "MCP_POTENTIAL_CAP")
              s/trim
              (not-empty)
              (Long/parseLong))
      default-potential-cap))

(defn model-id-for-path
  "The model_id for a file @path: its basename without extension."
  [path]
  (-> (s/split path #"/")
      last
      (s/split #"\.")
      first))

(defn load*
  "Load the .archimate file at @path into a fresh model record (parser + analytics).
   `get-full-graph` runs exactly once, inside @arr/enrich-with-graph; the same graph is
   reused as the model's context (no second build)."
  [path]
  (let [enriched (arr/get-model path)
        [enriched graph] (arr/enrich-with-graph enriched)
        rec (ana/build-model-record enriched graph)
        rec (assoc rec :path path :certain (delay (dcr/derivate-certain-relations (:context rec))))]
    rec))

(defn assert-model
  "Throws ex-info when @model-id is missing from the registry @registry."
  [registry model-id]
  (when-not (contains? registry model-id)
    (throw (ex-info (str "Unknown model_id: " model-id)
                    {:model-id model-id :code :unknown-model})))
  registry)

(defn load-model
  "Assoc a freshly loaded model at @path into @registry under its id, returning
   [registry' loaded-model]."
  [registry path]
  (let [id (model-id-for-path path)
        rec (load* path)]
    [(assoc registry id rec) id]))

(defn reload-model
  "Re-read @model-id's file, invalidating the certain-cache while keeping the id. Returns
   [registry' model-id]."
  [registry model-id]
  (assert-model registry model-id)
  (let [path (get-in registry [model-id :path])]
    [(assoc registry model-id (load* path)) model-id]))

(defn unload-model
  "Remove @model-id from @registry."
  [registry model-id]
  (assert-model registry model-id)
  [(dissoc registry model-id) model-id])

(defn list-models
  "The model ids in @registry."
  [registry]
  (keys registry))

(defn certain-graph
  "Force (lazily compute) the global `certain` derivation for @model-id in @registry and
   return the derived context. Computes once and caches in the model record (invalidated by
   reload). Only certain ArchiMate rules run: no potential relations are inferred, so modes
   and tools built on this graph stay free of potential-derived edges."
  [registry model-id]
  (assert-model registry model-id)
  (force (get-in registry [model-id :certain])))