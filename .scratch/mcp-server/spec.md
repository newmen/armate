# Spec: Armate MCP server

**Feature slug:** `mcp-server`

**Status:** spec (blocking-issue parent for the ticket set)

Expose armate's ArchiMate analytics to an agent as a Model Context Protocol (MCP) server. This spec is the authoritative description of the change; tickets below it implement vertical slices. All domain terms follow `CONTEXT.md`.

## Source and scope

- Source of models: `.archimate` files only. PlantUML is an output format, never a model source.
- The MCP server is pure Clojure: hand-rolled JSON-RPC 2.0 over stdio, no third-party MCP SDK, following the in-house `mcp` (@/Users/alfa/projects/clojure/mcp) reference project. stdio transport, launched as an uberjar under Leiningen (`project.clj`). (ADR-0001).
- A model is a load of one `.archimate` file into the in-memory model registry, addressed by `model_id`. No persistence between server runs.

## Implementation decisions (from implementation + review)

Resolved decisions encoded by the implementation; these are binding constraints for any further work.

- **Single heavyweight graph build per load.** The full graph (`get-full-graph`, the parser's alias allocation) is run **exactly once** per `load_model`/`reload_model`. The one graph provides both the `:archi-alias` id→element map (used to build the view indexes) and the model's global context. Registry and analytics must *thread* that graph through, never rebuild it. Verified by a regression test (`full-graph-built-once-per-load`).
- **No mutable state in analytics/index builders.** `build-view-indexes` and all analytics functions are pure (`reduce`-based, threaded accumulators). Atomics/`swap!` are reserved for the server's global registry (the single global state holder); they are not used to collect data inside a function.
- **Stable, model-global aliases across all tools.** Every tool that renders or addresses elements uses the model's **global aliases** — the ones allocated by the single full-graph build. `render_view`/`merge_views` render their sub-context as a *slice of the global context* (`build-sub-context`), NOT a fresh per-view graph. This guarantees an element has the same alias in `list_elements`, `render_view`, `related_elements`, and the path tools, so an agent can cross-reference views without confusion.
- **View membership is metadata, buildable from the single graph.** `:element-views` / `:relation-views` are derived from the same `:archi-alias` the global context uses (not a separate alias space).
- **Stdio channel is pure JSON-RPC.** Logback/`slf4j` status output must not reach stdout. A `NopStatusListener` is declared in `resources/logback.xml` and appenders target a file, so stdout carries only the JSON-RPC protocol. (Real MCP clients break on any stdout noise.)
- **Every tool advertises an input schema.** `tools/list` returns a per-tool `inputSchema` (property names/types, required, and enum modes), so a client/agent knows the exact parameter names (e.g. `model_id`, `from_name`, `to_name`) instead of guessing.
- **Roasters surface view membership.** `list_elements` (and `filter_by_type` / `filter_by_layer`) append `| views: <view1>,<view2>` per element, so a caller can see where each element is used without a second query.

## Model registry

- `load_model(path) -> model_id` — parse the `.archimate` file, build the enriched model and its view-membership metadata.
- `reload_model(model_id)` — re-read the same file, invalidate cached derivation and metadata, keep the registry `model_id` unchanged.
- `list_models`, `unload_model`.

## Metadata (view membership)

Built during parse, in a separate index layer (source elements/relationships untouched):

- `element-views: {element-alias #{view-names}}`
- `relation-views: {[from-alias to-alias] {relation-type #{view-names}}}`

Aliases are the canonical ids (`alias`, not `id`), and they come from the **same full-graph build** as the model's global context (so `element-views`/`relation-views` keys are exactly the aliases `render_view` and the other tools emit). `build-view-indexes` is pure and takes the `:archi-alias` map as an argument.

`list_elements` / `filter_by_type` / `filter_by_layer` roasters additionally append the element's view set (`| views: ...`) derived from `:element-views`.

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

- `list_elements(model_id, ?view, ?type, ?layer)` — `{name, alias, kind, layer}` roster; each row also appends the views that place the element (`| views: ...`).
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

- Addressing an element/relationship is by human-readable `name`. A non-unique name is an `isError` response listing candidate `{alias, kind, layer}`. An unknown name is an `isError` response that also lists a sample of available element names, to help the caller correct the name.
- `render_view` / `merge_views` sub-context is strictly the placed elements (no transitive expansion), rendered as a **slice of the model's global context** (`build-sub-context` over the placed aliases), so the emitted aliases equal the global ones. `merge_views` unions the placed elements of the given views.
- `related_elements`: place all elements within `depth` hops (undirected, default 1) of a root; render as a dynamic view whose relationships are all relationships between placed elements taken from the model, optionally augmented with `certain` derived relationships. It is an induced subgraph, not a strict tree. The root and placed elements use the model's global aliases.
- `shortest_path` / `all_paths`: whole model, relationship types configurable (structural/dependency/dynamic/other), original relationships only.
- `filter_by_type` / `filter_by_layer`: whole model by default, `view` narrows to the view's sub-context.
- `get_stats`: whole model.
- Addressing by name internally returns `{:single el}` (unique) or `{:candidates [...]}` (ambiguous); tools map `{:single}` to the alias and both error cases to `isError`.
- Output: render tools return PlantUML source as `text/plain`. Errors use MCP `isError: true` with a structured human-readable message.

## Error contract

Standard MCP `{isError: true, content: [...]}` for: unknown `model_id`, unknown `view`, unknown element name (message lists available names as a hint), non-unique element name (candidate list), potential-cap exceeded, invalid `rel-types`. Validate inputs before computation.

The `tools/list` output gives each tool a JSON Schema `inputSchema` (property names/types, required args, and enum modes), so a caller can construct valid arguments instead of guessing parameter names.

## Build / runtime

- Add `clojure.data.json` for the JSON-RPC envelope; the server mirrors `mcp.server` from the in-house `mcp` (@/Users/alfa/projects/clojure/mcp) reference: `BufferedReader`/`OutputStreamWriter` around stdio, `tools/list` + `tools/call` dispatch, `initialize` handshake.
- A `:main` namespace (`armate.mcp.server`, `:main`/`:aot` in `project.clj`) starts the stdio MCP server. `uberjar` artifact; the MCP client section points at `java -jar armate-mcp.jar` (`target/armate-...-standalone.jar`).
- **Stdio must carry only JSON-RPC.** `resources/logback.xml` declares a `NopStatusListener` so logback's startup status never prints to stdout; appenders write to a rolling file, never console stdout.
- `tools/list` returns a per-tool `inputSchema` (JSON Schema with property names/types, required args, enum modes).
- Test fixture: `test/resources/demo.archimate`.
- Regression tests assert `get-full-graph` runs exactly once per load and that `render_view` aliases equal `list_elements` aliases.