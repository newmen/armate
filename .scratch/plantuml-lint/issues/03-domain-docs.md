# 03: Domain docs for PlantUML lint

**What to build:** The intended-language and decision record for the new capability.
`CONTEXT.md` gains the term **PlantUML lint** (checking an ArchiMate PlantUML document as
text for structural correctness and metamodel validity without loading it as a model;
the parse context is diagnostic only). A new `docs/adr/0002-lint-input-is-not-a-model-source.md`
records that accepting PlantUML as lint input does not violate ADR-0001's "PlantUML is an
output format, never a model source", because no model is built and lint reuses
`plantuml.parser` rather than duplicating the metamodel. The README's stale
`(core/lint-file …)` usage snippet is replaced with the real API (`armate.archimate.core/analyze-file`
for models, `armate.archimate.plantuml.lint/lint-content` + `format-summary` for text).

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `CONTEXT.md` defines **PlantUML lint** with an `_Avoid_` line.
- [x] `docs/adr/0002-lint-input-is-not-a-model-source.md` exists and states the ADR-0001 relationship and the reuse rationale.
- [x] README no longer references the non-existent `core/lint-file` and shows the real lint API.

**Refs:** spec `.scratch/plantuml-lint/spec.md` (§ Documentation Deliverables); `docs/adr/0001-mcp-server-pure-clojure-archimate-only.md`; `CONTEXT.md`.