# 02: get_stats tool forces the certain cache and reports derived relations

**What to build:** calling `get_stats` on a model immediately after (or any time after) `load_model` reports the derived `certain` relationships in `:relations.certain`, consistent with what the derivation modes of `render_view` / `related_elements` / `derived_relations` reveal. The tool forces the registry's existing lazy `certain` cache (reusing the one derivation budget per load) and feeds the derived graph into the stats core from ticket 01.

**Blocked by:** 01 (Stats core counts derived certain relations)

**Status:** done

- [x] A fresh `get_stats` right after `load_model` reports non-empty `:relations.certain` on a model that derives relations (e.g. the demo fixture).
- [x] The derived counts/types in `get_stats` agree with those produced by the registry's `certain` derivation, so stats and deried-view modes cannot drift.
- [x] `potential` derived relations are never counted in `get_stats`.
- [x] `reload_model` invalidates the derived counts so stats reflect the re-read file, not a stale derivation (derivation is recomputed at most once per load).
- [x] Unknown `model_id` still returns the standard `isError` result before any derivation runs.
- [x] The `get_stats` tool description notes that `:relations.certain` reports globally-derived relations.
- [x] Regression + consistency + empty-derivation tests at the tool seam pass.