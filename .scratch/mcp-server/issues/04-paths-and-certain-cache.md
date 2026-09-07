# 04: Path analytics and global certain-derivation cache

**What to build:** `shortest_path` and `all_paths` between two elements by name, across the whole model, with relationship-type sets configurable (structural/dependency/dynamic), original relationships only. Plus the lazily-computed `certain` derivation over the whole loaded model, cached in the registry and invalidated on reload. Exercised by tests.

**Blocked by:** 01 (View-membership metadata in the `.archimate` parser)

**Status:** ready-for-agent

- [ ] `shortest_path` / `all_paths` (whole model, configurable rel-types, original only).
- [ ] Lazy global `certain` derivation cache in the registry.
- [ ] Cache invalidated by reload.