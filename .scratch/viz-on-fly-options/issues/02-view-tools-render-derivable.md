# 02: Make MCP view tools show derived relationships per mode via the new options

**What to build:** the view tools (`render_view`, `merge_views`, `related_elements`) move off the workaround (the module-level constant `escape-derivated`, which silently swallowed the derived relationships that `apply-mode` had merged) onto the new `on-fly-generate-puml` interface: they decide for themselves which `:derivate` markers to draw, from the `mode` parameter. This restores the promised behavior: `mode :certain` really shows certain-derived relationships, `mode :certain+potential` shows both certain and potential.

**Blocked by:** 01

**Status:** done

- [x] `render-puml` and the tools `render_view`/`merge_views`/`related_elements` translate `mode` → the `:render-derivable` option: `none→#{}`, `certain→#{:certain}`, `certain+potential→#{:certain :potential}`.
- [x] Derivation stays in `apply-mode`/`with-certain`/`with-potential` (untouched); the new interface merely decides which markers to show.
- [x] All reliance on the removed constant `escape-derivated` is gone (no mentions of it in analytics/tools).
- [x] analytics/tools tests updated: `mode :certain` contains the `certain`-derived edge, `mode :none` does not; `certain+potential` shows both marker types.