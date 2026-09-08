# Armate

Armate is a Clojure toolkit for **analysis of ArchiMate architecture models**. It
parses ArchiMate models from `.archimate` files and PlantUML diagrams, normalises
both into a shared graph model, enriches them with relationships derived from the
ArchiMate specification, and renders cleaned-up PlantUML views.

It is primarily an in-memory analysis pipeline: model in, enriched model and
PlantUML output out. The core entry points (`armate.core`) are exercised from a Clojure REPL under Leiningen.

## Features

- **Two model intakes** normalised through a single model seam:
  - `.archimate` files (the ArchiMate Open Exchange Format)
  - PlantUML `.puml` diagrams
- **Graph model** (`multi-graph`) representing elements and typed relationships,
  with view semantics (a view is a named collection of placed elements).
- **Metamodel & derivation** — a derivation engine implementing the ArchiMate
  specification rules:
  - `certain` and `potential` relationship derivation with match/restrictions
    logic and a constraint solver
  - relationship ranking and layer/kind classification per the spec appendix
- **Analysis utilities** — per-layer and per-group element and relationship
  statistics, lint reporting, transitive-relationship erasure, and context
  filtering.
- **PlantUML rendering** — save derived/filtered models back out as `.puml`,
  with element alignment (grid/neighbours), combining, and sync support.
- **MCP server** — expose the same analytics to an agent as a Model Context
  Protocol server over stdio (see [MCP server](#mcp-server)).

## Project layout

```
src/armate/
  core.clj                        # Public entry points (analyze, stats, filter, derive)
  archimate/
    archi/parser.clj              .archimate (exchange-format) intake
    plantuml/{lex,structure,parser}.clj   .puml intake (lexer → structure → semantics)
    model.clj                     seam over the shared untagged context map
    multi_graph.clj               graph data structure
    builder.clj                   element/kind normalisation
    name.clj                      name/alias normalisation
    collector.clj                 interface/group collection helpers
    metamodel/
      meta.clj                    layer & kind classification
      rank.clj                    element/relation ranking
      appendix.clj                spec appendix data
      derivation/
        core.clj, match.clj, restrictions.clj, rules.clj, solver.clj, viz.clj
    viz/
      saver.clj                   PlantUML output
      combiner.clj, sync.clj, common.clj
      align/                      element alignment (common, grid, neighbours)
      call_counter.clj
  mcp/
    server.clj                    stdio JSON-RPC 2.0 MCP server (init, tools/list, tools/call)
    registry.clj                  in-memory model registry (load/reload/unload, caches)
    analytics.clj                 model-record build + view-member/derivation analytics
    tools.clj                     tool handlers + declarative tool schemas/descriptions
test/                             unit tests
dev/armate/usecases/              usage examples
```

## Core concepts

The domain vocabulary is defined in [`CONTEXT.md`](CONTEXT.md). Key terms used
throughout the code:

- **Model** — a loaded `.archimate` file: its elements, relationships, and views.
- **View** — a named diagram inside a model.
- **Relationship** — a directed relation between two elements, with a type
  (structural, dependency, dynamic, or other/specialization).
- **Derivation** — adding relationships that follow from the ArchiMate `certain`
  and `potential` rules.
  - `certain` — applied over the whole loaded model.
  - `potential` — applied per view, under an element-count guard.

## MCP server

Armate ships a **Model Context Protocol (MCP) server** (`armate.mcp.server`) that
exposes the ArchiMate analytics to an agent through a JSON-RPC 2.0 interface over
stdio. It is a pure-Clojure, hand-rolled implementation (no third-party MCP SDK),
following the in-house reference server. Models are loaded from `.archimate`
files into an in-memory registry addressed by `model_id`; nothing is persisted
between server runs.

### Build

Build the executable uberjar:

```sh
lein uberjar
```

This produces `target/armate-<version>-standalone.jar`, e.g.
`target/armate-2.0.0-SNAPSHOT-standalone.jar`.

### Run

Launch the server in stdio mode (it reads JSON-RPC requests on stdin and writes
responses to stdout):

```sh
java -jar target/armate-2.0.0-SNAPSHOT-standalone.jar
```

The server is a normal MCP stdio process; you generally do not run it by hand but
register it with your MCP client (see below).

### Tools

`tools/list` advertises every tool below with its input schema. Note that
`render_view`/`merge_views`/`related_elements` accept a derivation `mode`
(`none` | `certain` | `certain+potential`); `certain` adds globally-implied
relationships per the ArchiMate derivation rules, and `certain+potential` also
adds locally-derivable ones (see `derived_relations` for direct access).
Potential derivation is capped at 30 involved elements and denotes a
*possibility*, not a certainty, of an inferred relationship.

| Tool | Description |
|------|-------------|
| `load_model` | Load an `.archimate` file into the model registry, returning its `model_id`. |
| `reload_model` | Re-read an already-loaded `model_id`'s file, keeping the same id. |
| `list_models` | List the loaded model ids. |
| `unload_model` | Remove a loaded model from the registry. |
| `list_views` | List the view names of a model. |
| `render_view` | Render a view to PlantUML `@startuml` text. Alias values are the stable model aliases shared by `list_elements` / `related_elements` / paths. `mode` adds inferred (derived) relationships per ArchiMate rules. |
| `merge_views` | Render the union of several views to PlantUML. Like `render_view`, `mode` adds inferred relationships. |
| `list_elements` | List model elements as `name \| alias \| kind \| layer \| views...`. Optionally narrow by view, type or layer. |
| `filter_by_type` | List elements of a given type, optionally within a view. |
| `filter_by_layer` | List elements of a given layer, optionally within a view. |
| `element_views` | Which view names place a given element (by name). |
| `relation_views` | Which views place a relationship between two elements (by names); without type, one line per relation type with its view set. |
| `derived_relations` | List relationships inferred between two elements (by names) by the global `certain` derivation — implied relations the explicit model does not spell out. |
| `related_elements` | Render the induced subgraph around an element (by name) up to a depth, as PlantUML. `mode` adds inferred relationships. |
| `shortest_path` | Shortest path between two elements (by name) over original relationships, optionally filtered by rel-type category. Undirected by default; pass `directed=true` to follow relationship direction. |
| `all_paths` | All simple paths between two elements over original relationships, limiting total length to 6 edges. Undirected by default; pass `directed=true` to follow direction. |
| `get_stats` | Whole-model statistics. |

Elements and relationships are addressed by human-readable `name`. A non-unique
name returns an error listing the candidate `{alias, kind, layer}`; an unknown
name returns an error listing the nearest available element names to help correct
the call.

### Registering in Kilo

Add an entry under the `"mcp"` object in the global Kilo config
(`~/.config/kilo/kilo.jsonc`), using the uberjar via `java -jar` (Kilo applies a
request timeout, so launch the jar directly rather than through `lein run`):

```jsonc
{
  "mcp": {
    "armate": {
      "type": "local",
      "command": ["/usr/bin/java", "-jar", "/absolute/path/to/armate/target/armate-2.0.0-SNAPSHOT-standalone.jar"],
      "enabled": true,
      "timeout": 150000
    }
  }
}
```

Use absolute paths for `java`, the jar and any `workingDirectory` to avoid PATH
issues when Kilo starts the server. Restart Kilo (or reload its config) for the
new MCP server to be picked up. For a per-project registration, create a
`kilo.json` at the repository root with the same `"mcp"` structure.

## Usage

Armate is driven from a Clojure REPL:

```clojure
(require '[armate.core :as core])

;; Analyse a model file (returns lints from parsing)
(core/lint-file "path/to/model.archimate")

;; Analyse and export stats (per-layer elements, per-kind relationships)
(-> "path/to/model.archimate"
    core/analyze-file
    armate.archimate.metamodel.derivation.core/derivate-relations
    armate.archimate.core/get-stats)

;; filter a model and save a cleaned-up PlantUML view
(armate.archimate.core/generate-filtered-context
  context
  (fn [element] ...)            ; element predicate
  (fn [{:keys [type]}] ...)     ; relationship predicate
  "out/result.puml")
```

A REPL starts with `lein repl`, using the `:dev` profile (which adds the `dev/`
runner paths and resources). Example workflows live under [`dev/`](dev/).

## Development

Run the test suite:

```sh
lein test
```

The repository includes a `dev/` directory with ready-made routers/dev tools. Static
analysis uses `clj-kondo` (config in `.clj-kondo/`).

## License

Private. See [`project.clj`](project.clj).