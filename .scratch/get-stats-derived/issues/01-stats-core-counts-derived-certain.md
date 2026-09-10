# 01: Stats core counts derived certain relations

**What to build:** the statistics building block can now report ArchiMate-derived `certain` relationships. `armate.mcp.analytics/stats` (and the underlying whole-model stats counter it delegates to, `armate.archimate.core/get-stats`) accepts an optional derived context and folds its `:derivate :certain`-marked relations into the `:relations.certain` bucket. Without the derived context the behavior is unchanged, preserving the existing bucket shape and other buckets.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] `stats` accepts an optional derived context argument; with it, `:relations.certain` is populated from the derived `certain` relations.
- [x] Without a derived context, `stats` behavior and output shape are unchanged (existing buckets `:original` / `:nesting` / `:certain` / `:potential` and `:elements` / `:types` / `:lints` intact).
- [x] A model with no derivable relations still reports an empty `:relations.certain`.
- [x] The counting stays pure and side-effect-free; the caller supplies the derived context, the function only counts.