# 01: Pure PlantUML lint core (`armate.archimate.plantuml.lint`)

**What to build:** A side-effect-free lint pipeline that turns a full ArchiMate PlantUML
document into an agent-readable problem summary. `lint-content` calls the existing
`armate.archimate.plantuml.parser/analyze-content`, faithfully reuses its `:lints` with
levels unchanged, converts a `structure/get-blocks` "Unclosed block" failure into a lint
record (`:kind :unclosed-block`, `:level :error`, `:in [:parse]`, with the starting line
from `ex-data` when available), and returns `{:lints [...] :errors n :warnings n}` with
the lints sorted (errors before warnings; within a level by line; line-less last).
`format-summary` renders the verdict line (`OK: no problems` or `E errors, W warnings`),
one line per lint in the format `<LEVEL> <location> <context> <kind>: <explanation>`
where location is `line N` or `[document]` and context is `[in ...]` when present, then a
blank line and the closing instruction `Fix the errors and warnings above and lint again`
whenever there is at least one problem. Explanations include concrete data (relationship
operator/type, `from-kind → to-kind`, unresolvable alias, duplicate payload); each
`:incorrect-connector-using` sub-problem becomes its own line. No MCP dependency; no
`clojure.spec` (repo convention).

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `lint-content` returns counts and sorted lints; all existing lint kinds/levels pass through unchanged.
- [x] An unclosed `{` block yields a single `:unclosed-block` error lint with the starting line, not an exception.
- [x] `format-summary` renders the verdict, sorted lines, and the closing instruction only when problems exist.
- [x] Unit tests in `test/armate/archimate/lint_test.clj` cover verdict text (including `OK: no problems` and constant plural), errors-before-warnings sorting, line-less-last sorting, closing-line presence/absence, counts, and unclosed-block mapping.

**Refs:** spec `.scratch/plantuml-lint/spec.md` (§ Implementation Decisions, § Report Format, § Lint Kinds, § Testing Decisions); ADR `docs/adr/0002-lint-input-is-not-a-model-source.md`; prior art `test/armate/archimate/parser_test.clj` (`analyze-content-test`).