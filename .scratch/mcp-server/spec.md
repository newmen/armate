# Spec: Armate MCP server

**Feature slug:** `mcp-server`

**Status:** spec (blocking-issue parent for the ticket set)

Expose armate's ArchiMate analytics to an agent as a Model Context Protocol (MCP) server. This spec is the authoritative description of the change; tickets below it implement vertical slices. All domain terms follow `CONTEXT.md`.

## Source and scope

- Source of models: `.archimate` files only. PlantUML is an output format, never a model source.
- The MCP server is pure Clojure: hand-rolled JSON-RPC 2.0 over stdio, no third-party MCP SDK, following the in-house `mcp` (@/Users/alfa/projects/clojure/mcp) reference project. stdio transport, launched as an uberjar under Leiningen (`project.clj`). (ADR-0001).
- A model is a load of one `.archimate` file into the in-memory model registry, addressed by `model_id`. No persistence between server runs.

## Model registry

- `load_model(path) -> model_id` — parse the `.archimate` file, build the enriched model and its view-membership metadata.
- `reload_model(model_id)` — re-read the same file, invalidate cached derivation and metadata, keep the registry `model_id` unchanged.
- `list_models`, `unload_model`.

## Metadata (view membership)

Built during parse, in a separate index layer (source elements/relationships untouched):

- `element-views: {element-alias #{view-names}}`
- `relation-views: {[from-alias to-alias] {relation-type #{view-names}}}`

Aliases are the canonical ids (`alias`, not `id`).

## Derivation scope

- `certain`: global over the whole loaded model; lazily computed and cached in the registry; invalidated on `reload_model`.
- `potential`: local to the sub-context of target views. Guarded by an element count cap (count of unique elements across target views; env `MCP_POTENTIAL_CAP`, default 50). Exceeding the cap is a hard rejection (`isError`).
- View tools take a derivation mode `none | certain | certain+potential`.

## Tools (namespace `armate`)

Registry:

- `load_model`, `reload_model`, `list_models`, `unload_model`

Views:

- `list_views(model_id)`
- `render_view(model_id, view, ?mode)`
- `merge_views(model_id, [view...], ?mode)` — union of placed elements; sub-context is first-class (addressable by downstream tools within the request); potential cap counts the union of unique elements.

Elements / relationships:

- `list_elements(model_id, ?view, ?type, ?layer)` — `{name, alias, kind, layer}` roster.
- `filter_by_type(model_id, type, ?view)`
- `filter_by_layer(model_id, layer, ?view)`
- `element_views(model_id, name)`
- `relation_views(model_id, from_name, to_name, ?type)` — several without type -> list with types.
- `related_elements(model_id, name, ?depth=1, ?mode)` — induced subgraph (see below).

Navigation / analytics:

- `shortest_path(model_id, from_name, to_name, ?rel-types)`
- `all_paths(model_id, from_name, to_name, ?rel-types)`
- `get_stats(model_id)` — whole model.

## Semantics

- Addressing an element/relationship is by human-readable `name`. A non-unique name is an `isError` response listing candidate `{alias, kind, layer}`.
- `render_view` / `merge_views` sub-context is strictly the placed elements (`build-views-graph` semantics; no transitive expansion).
- `related_elements`: place all elements within `depth` hops (undirected, default 1) of a root; render as a dynamic view whose relationships are all relationships between placed elements taken from the model, optionally augmented with `certain` derived relationships. It is an induced subgraph, not a strict tree.
- `shortest_path` / `all_paths`: whole model, relationship types configurable (structural/dependency/dynamic), original relationships only.
- `filter_by_type` / `filter_by_layer`: whole model by default, `view` narrows to the view's sub-context.
- `get_stats`: whole model.
- Output: render tools return PlantUML source as `text/plain`. Errors use MCP `isError: true` with a structured human-readable message.

## Error contract

Standard MCP `{isError: true, content: [...]}` for: unknown `model_id`, unknown `view`, unknown element name, non-unique element name, potential-cap exceeded, invalid `rel-types`. Validate inputs before computation.

## Build / runtime

- Add `clojure.data.json` (if not already present) for the JSON-RPC envelope; the server mirrors `mcp.server` from the in-house `mcp` (@/Users/alfa/projects/clojure/mcp) reference: `BufferedReader`/`OutputStreamWriter` around stdio, `tools/list` + `tools/call` dispatch via `defmulti`, `initialize` handshake.
- A `:main` namespace that starts the stdio MCP server. `uberjar` artifact; the MCP client section points at `java -jar armate-mcp.jar`.
- Test fixture: `test/resources/demo.archimate`.