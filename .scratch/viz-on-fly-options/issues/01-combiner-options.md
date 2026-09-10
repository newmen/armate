# 01: Move grouping and derived-edge-rendering options into the view generator

**What to build:** the view generator `on-fly-generate-puml` accepts options controlling grouping (nesting) of elements and which derived relationships are drawn as edges. The former module-level constants `do-grouping?`, `group-modes`, and `escape-derivated` are removed; those decisions are now taken by the caller via an `opts` argument.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `on-fly-generate-puml` accepts `([context])` and `([context opts])`; when `opts` is absent, it keeps the previous default behavior.
- [x] The `:group-modes` option (map `{element-kind #{relationship-type}}`); default `{:grouping #{:aggregation :composition}}`; an empty map `{}` fully disables nesting. `do-grouping?` is removed (grouping enabled by default).
- [x] The `:render-derivable` option (set of `:derivate` markers drawn as edges); default `#{}` = no derived relationships drawn. `escape-derivated` is removed; `:nesting`/`:connecting` remain always hidden.
- [x] Options are threaded end-to-end through `generate-puml` → `get-relations` → `make-grouped`/`make-nesting` (a function parameter, not a module-level constant).
- [x] Behavioral unit tests (asserting on PlantUML lines): the default draws no derived edges, `:render-derivable` enables `certain`/`potential`, the default nests grouping elements, `{}` produces flat output.