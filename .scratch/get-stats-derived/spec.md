# Spec: get_stats reports derived (certain) relations

**Feature slug:** `get-stats-derived`

**Status:** `done`

Expose the derived relationships that ArchiMate derivation rules imply inside the whole-model `get_stats` tool, so that statistics reflects derived relations immediately after a model load (and on every subsequent call), consistent with the derivation modes of the render/derived_relations tool set.

## Problem Statement

After a model is first loaded into the MCP registry, calling `get_stats` never reports any derived relationships. The `:relations.certain` bucket always shows `0`, even though asserting the global `certain` derivation produces a non-empty set of derived relations. `get_stats` computes its statistics from the plain model context, which carries no `:derivate`-marked relations; the lazy `certain` cache (in the model record under `:certain`) is never forced, so its derived relations never reach the stats. This makes the statistics under-report the model's implied structure right after load and stay wrong until some other tool happens to force the cache.

## Solution

`get_stats` must force the global `certain` derivation for the model before computing statistics and report those derived relations under `:relations.certain`. Every `get_stats` call then reflects derived relations — immediately after `load_model`, and on every later call (the derivation is cached once, invalidated only by `reload_model`). The `:relations.certain` bucket becomes populated with the same derived relations the render tools and `derived_relations` expose, so one source of truth drives both stats and the derivation modes.

## User Stories

1. As an MCP user, I want `get_stats` right after `load_model` to report derived `certain` relations in `:relations.certain`, so that statistics reflect the model's implied structure without needing a separate derivation-triggering call.
2. As an MCP user, I want the `:relations.certain` counts in `get_stats` to equal what the derivation modes of `render_view` / `related_elements` / `derived_relations` reveal, so that statistics and derived-view output agree.
3. As an MCP user, I want `get_stats` on a model with no derivable relations to still report `:relations.certain` as empty, so that the shape of the response stays stable and only the content changes.
4. As an MCP user, I want to pay the derivation cost at most once per model load, so that repeated `get_stats` calls are cheap after the first.
5. As an MCP user, I want `get_stats` to ignore `potential` derived relations, so that only guaranteed implications are counted (consistent with the global `certain` scope).
6. As an MCP user, I want the fix not to disturb the existing `:elements` / `:types` / `:lints` and original/nesting stats buckets, so that existing consumers keep working.
7. As an MCP user, I want `reload_model` to invalidate the derived counts, so that stats reflect the re-read file and not a stale derivation.
8. As a developer, I want the derived-relations computation to reuse the existing registry `certain` cache (`:certain` delay), so that there is exactly one derivation budget per load and no second, divergent derivation path.
9. As a developer, I want the stats computation to stay pure and side-effect-free, so that the analytics functions remain deterministic and testable.
10. As a developer, I want the behavior covered by a regression test asserting that `get_stats` reports derived relations after a fresh load, so that this bug cannot silently regress.

## Implementation Decisions

- **The single seam is the `get_stats` MCP tool handler** (in `armate.mcp.tools`). It is the only place that must know a *model record* (which carries the `:context` and the `:certain` cache) and produces the *output shape*. The pure `ana/stats` function stays as the core counter; the handler threads the derived graph into it.
- **Force the registry's lazy `certain` cache** by calling the existing `reg/certain-graph` for the model. Per ADR-0001 derivation scoping and the mcp-server spec, `certain` is global and lazily computed + cached; forcing it here reuses the one derivation budget per load and is invalidated by `reload_model`.
- **Merge the derived `certain` relations into the statistics under `:relations.certain`.** To report them in the existing stats schema, the pure stats core (`ana/stats`, delegating to `armate.archimate.core/get-stats`) needs an optional entry that supplies the globally-derived relations. The merge closes the `:relations.certain` bucket so it reflects the `certain` derived graph. `potential` relations are never counted (the global `certain` graph excludes them).
- **Stats purity is preserved.** `ana/stats` keeps accepting a context; the derived `certain` relations are added either by (a) passing the derived context/relations into `get-stats` (preferred, since it keeps the counting in one function), or (b) summing the certain-graph's relations into the existing `:relations.certain` bucket after `get-stats` returns. The handler decides where the derived relations come from (the forced cache); the counting stays pure.
- **Unknown-model error behavior is unchanged.** `get_stats` on an unknown `model_id` still returns the standard `isError` result before any derivation runs.
- **Tool schema and description unchanged** except — to match the new guarantee — the `get_stats` description may note that `:relations.certain` reports globally-derived relations.

## Testing Decisions

- **Test external, observable behavior only:** assert the `get_stats` response text, never internal state of the cache. A good test loads the demo model, calls `get_stats`, and asserts `:relations.certain` is non-empty (or reports the specific derived counts/relation types the demo model yields), then asserts it matches the derived relations surfaced by the existing derivation modes.
- **Regression test at the tool seam:** the `get-stats-tool` deftest (`test/armate/mcp/tools_test.clj`) is extended to assert that after a fresh `load_model` the response includes derived `certain` relations (mirroring how `derived-relations-found` proves derived output). This runs against the existing `with-demo` fixture (`test/resources/demo.archimate`), which is known to yield derived relations (28 certain relations under the global derivation).
- **Consistency test:** a related test compares the `certain` counts/types in `get_stats` with the relations produced by `reg/certain-graph`, so the stats and the derivation modes cannot drift apart.
- **No-change test for empty derivation:** a model with no derivable relations must still return the stable stats shape with empty `:relations.certain`.
- **Prior art:** existing tool tests in `test/armate/mcp/tools_test.clj` (e.g. `get-stats-tool`, `derived-relations-found`, `derived-relations-empty`) and the registry cache tests in `test/armate/mcp/registry_test.clj` (`certain-graph-is-lazy-and-cached`, `certain-graph-excludes-potential`) show the established patterns for asserting tool responses and the certain cache.

## Out of Scope

- Reporting `potential` derived relations in `get_stats` (only the global `certain` derivation counts; potential stays local to view sub-contexts per ADR-0001).
- Changing the semantics, schema, or derivation scope of any other tool (`render_view`, `merge_views`, `related_elements`, `derived_relations`).
- Refactoring the broader registry caching strategy or the derivation engine itself.
- Performance tuning of derivation beyond relying on the existing cache.

## Further Notes

- Root cause confirmed at the tool seam: `reg/load_model` stores `:certain (delay ...)` in the model record but never forces it; `get_stats` reads only `:context` (plain, no `:derivate` relations). Forcing `reg/certain-graph` before counting is the minimal, consistent fix.
- The demo fixture derives 28 certain relations under the global `certain` derivation — ready-made evidence that `get_stats`''s consistent `certain: 0` is the regression this spec fixes.
- Domain vocabulary follows `CONTEXT.md` (element / relationship / derived relation / certain / potential); derivation scope follows ADR-0001 and the `mcp-server` parent spec.