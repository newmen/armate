# Spec: Configurable view rendering via `on-fly-generate-puml`

**Feature slug:** `viz-on-fly-options`

**Status:** `done`

Make the PlantUML view generator's grouping and derived-relationship behaviors configurable from the caller, replacing the module-level constants `do-grouping?`, `group-modes`, and `escape-derivated`. The MCP view tools (`render_view`, `merge_views`, `related_elements`) switch to the new explicit interface instead of relying on the removed constants.

All domain terms follow `CONTEXT.md`.

## Problem Statement

The view renderer (translating a model/graph context to PlantUML source) currently hard-codes three decisions as top-level constants in the combiner:

- `do-grouping?` — a module flag (currently `false`) that disables nesting/grouping of elements into containers during on-the-fly rendering.
- `group-modes` — a module-level map of `{element-kind #{relationship-type}}` deciding which relationship types nest one element inside another.
- `escape-derivable` — a module-level set of derivation markers (`#{:certain :potential}`) that `get-relations` unconditionally filters out, so derived relationships are never rendered as edges.

None of these can be influenced per-view/per-call. `escape-derivable` conflicts with the derivation mode machinery in the MCP analytics: `apply-mode` deliberately merges `certain`/`potential` derived relationships into a sub-context for rendering, but `on-fly-generate-puml` then silently drops them via the module constant — the caller's intent to render derived edges is overridden by unrelated module state.

## Solution

Replace the module-level constants with a single caller-supplied options argument to `on-fly-generate-puml`. The caller decides, per invocation:

1. whether and how to **group** (nest) elements — default `group-modes` = `{:grouping #{:aggregation :composition}}`;
2. which **derived-relationship markers** the graph carries should be rendered as edges — default: none (behavior keeps backward-compatibility with today's `mode :none` and `save-puml` output);
3. grouping on/off is implied by passing a non-nil `group-modes` map (no separate `do-group?` boolean).

Existing deriving in the analytics layer (`with-certain` / `with-potential`) remains untouched — it marks relationships with `:derivate`, and the new interface decides which markers to render, rather than a module constant.

## User Stories

1. As a caller of the PlantUML view generator, I want to pass a `group-modes` option to `on-fly-generate-puml` so that I can decide per call whether elements are nested and which relationship types trigger nesting, instead of being limited to a module-wide constant.
2. As a caller that does not pass `group-modes`, I want the default `{:grouping #{:aggregation :composition}}` applied (with grouping enabled by default), so that out-of-the-box rendering nests elements where aggregation/composition relationships exist.
3. As a caller I want to disable nesting entirely by passing an explicit empty group-modes map, so I can produce flat rendering without a global flag.
4. As a caller I want to decide which derived relationship markers (`:certain`, `:potential`) are rendered as edges on the generated view, so that the caller's intended mode matches the output.
5. As a tool implementing `mode :none`, I want to render no derived edges, and as `mode :certain`/`:certain+potential` I want those markers rendered, so the output reflects the mode the agent asked for.
6. As a tool that previously relied on `escape-derivable` to suppress derivations, I want the new interface to default to that same behavior so nothing breaks when I upgrade.
7. As a developer I want the module-level mutable-ish convenience constants removed so there is a single explicit decision seam for grouping and derivation rendering.

## Implementation Decisions

- **Signatures**: `on-fly-generate-puml` becomes variadic —
  ```
  ([context] (on-fly-generate-puml context nil))
  ([context opts])
  ```
  - `:group-modes` (map `{element-kind #{relationship-type}}`; default when absent or `nil`: `{:grouping #{:aggregation :composition}}`; an explicit empty map `{}` disables grouping entirely)
- `:render-derivable` (set of `:derivate` markers to render as edges, e.g. `#{:certain}` or `#{:certain :potential}`; default `#{}` meaning no derived markers are rendered)
- **Replace `do-group?`**: there is no boolean flag; grouping is on when a non-empty `:group-modes` map is in effect, off when empty/absent.
- **Removal of `escape-derivable`**: the constant is deleted. The relationship filter in `get-relations` (currently a union with `#{:nesting :connecting}`) is re-parameterized to receive the caller's `:render-derivable` set: a relationship is rendered when its `:derivate` marker is absent or belongs to `:render-derivable`. The `:nesting` and `:connecting` markers remain always filtered out (they are internal bookkeeping for grouping, never rendered as edges).
- **Relation filter seam**: the derivation-suppression filter moves from a module constant to a function parameter threaded through the render pipeline (`on-fly-generate-puml` → `generate-puml` relationship/relf). Existing internal markers `:nesting`/`:connecting` stay excluded unconditionally.
- **Analytics render path**: `render-puml` and the MCP view tools (`render_view`, `merge_views`, `related_elements`) adopt the new interface. Derivation mode already known in `apply-mode` is translated into `:render-derivable`:
  - `:none` → `#{}` (render no derived edges)
  - `:certain` → `#{:certain}`
  - `:certain+potential` → `#{:certain :potential}`
  The `build-sub-context`/`apply-mode` machinery that merges derived relationships stays as-is; the new interface then decides which markers show.
- `:render-derivable` default: absent/`nil` opts default to `#{}` (render no derived edges) to keep `mode :none` and the `save-puml` path behavior unchanged.
- **Default `:group-modes`**: when `opts` is absent or carries no `:group-modes`, the default map `{:grouping #{:aggregation :composition}}` applies — matching the module-level constant's value, so the default rendering behavior stays the same; only the ownership changes. An explicit empty map `{}` opts out of grouping.

## Testing Decisions

- Good tests assert **external, visible behavior**: which edges appear in the produced PlantUML source, and which elements are nested, for a given options map — not the internals of the change.
- Unit-level tests in a combiner-oriented test file, plus the existing analytics/tools tests. Prior art: `render-view-produces-plantuml`, `render-views-with-certain-augments`, `certain-mode-excludes-potential` in `test/armate/mcp/analytics_test.clj` and `tools_test.clj` assert PlantUML strings contain/omit specific relation tokens and syntax. Reuse that style.
- Cases:
  - default (no opts) renders no derived edges (markers `:certain`/`:potential` suppressed) — keeps older behavior;
  - passing `:render-derivable #{:certain}` renders a `certain`-derived relationship and suppresses a `:potential` one;
  - passing `:render-derivable #{:certain :potential}` renders both;
  - with no `:group-modes` passed (`nil` or absent opts), grouping uses the default `{:grouping #{:aggregation :composition}}`, so `group` elements are nested;
  - explicit empty `:group-modes {}` disables nesting.
- Keep existing MCP tests passing (no behavior regression for `mode :none` and `save-puml`).

## Out of Scope

- Any change to how `certain`/`potential` derivation is computed (`derivation.core`) — untouched.
- Redesigning the `nesting`/`connecting` internal markers or their handling.
- Any UI/format change of the `mode` parameter at the MCP schema level (mode enum shape stays).
- Performance: no new runtime-cost profiling; this is behavioral control only.

## Further Notes

- Today the constant `escape-derivable` effectively cancels the effect of the MCP `mode` param on rendering; the new interface restores "what the caller asked for."