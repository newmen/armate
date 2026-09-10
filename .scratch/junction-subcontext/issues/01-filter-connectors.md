# 01: Filter sub-context connectors to placed Junctions

**Feature slug:** `junction-subcontext`

**Parent spec:** `.scratch/junction-subcontext/spec.md`

**What to build:** When building a sub-context in the MCP analytics (the `build-sub-context` entry that underlies `render_view`, `merge_views`, and `related_elements`), select from the model's `:connectors` only those whose alias is in the placed-alias set, and treat a selected connector as a placed vertex when filtering incident relationships. Drop all non-placed connectors.

**Blocked by:** —

**Status:** done

- [x] Rendering "Шахматы" and "Процесс" of `test/resources/demo.archimate` outputs no `Junction_Or(jc26, ...)`.
- [x] Rendering "Семья" outputs `Junction_Or(jc26, ...)` plus its `Rel_Triggering` edges.
- [x] `:certain` / `:certain+potential` modes do not reintroduce the Junction into an unplaced view.
- [x] `merge_views` shows the Junction only when a placing view is merged.
- [x] `related_elements` shows the Junction only when the induced sub-graph includes it.
- [x] All existing MCP analytics/tools tests still pass (`clj-kondo` clean).

## Comments

Spec: `.scratch/junction-subcontext/spec.md`. Root cause verified via REPL against the extended fixture: `build-sub-context` carries the full graph's `:connectors` into every sub-context; the renderer then emits every connector. Junctions (kind `:connector`) live in `:connectors`, not `:elements`, so the placed-only element filter never touches them. The fix is selection-side only; parser, index, and renderer are unchanged.

**Status:** done