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