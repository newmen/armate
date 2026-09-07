# 09: New tool `derived_relations` plus tests

**What to build:** A new MCP tool `derived_relations(model_id, from_name, to_name, ?depth)` that returns the relationships inferred between two elements by ArchiMate derivation rules (the global `certain` derivation) — i.e. derived relations in the neighbourhood of the named elements that the source `.archimate` model does not spell out. The agent can call it directly to see implied structure instead of only getting derived relations as a side effect of `render_view`/`merge_views`/`related_elements` modes. Reuses the registry's lazy global `certain` graph.

**Blocked by:** 08 (Improve tool descriptions to surface derivation rules)

**Status:** done

- [x] `derived_relations(model_id, from_name, to_name, ?depth)` returns the `certain`-derived relationships between the two named elements (and/or within `depth` hops), listed in the same human-readable form as the path tools.
- [x] Unknown `model_id` / unknown or non-unique element name are `isError` per the existing contract.
- [x] Empty result (no derived relation between the elements) returns a clear "no derived relations" message.
- [x] Unit + tool-level tests cover: derivation between linked elements, no derivation (empty), and the `depth`-limited window.