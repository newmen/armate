# 02: MCP tool `lint_plantuml`

**What to build:** The agent-visible end-to-end tool. `lint_plantuml(content)` is
registered in the MCP tool list and dispatch: it validates `content` (missing or
blank/whitespace-only → `isError=true` argument error), calls the pure lint core from
ticket 01, and returns the formatted report as `ok` (`isError=false`). Lints are data,
not failures; `isError=true` is reserved for the argument errors and any unexpected
exception. It is standalone: no `model_id`, the registry is never mutated. The tool has
no required model and works with an empty registry. The tool description must explain
that it should be called before validating generated PlantUML with a generic PlantUML
CLI, that it checks ArchiMate semantics a grammar checker cannot (legal relationships
between element kinds, unresolved endpoints, undefined types, duplicates, connector
misuse, document structure), and that it is ArchiMate-only — not for sequence or other
UML diagrams.

**Blocked by:** 01 (Pure PlantUML lint core)

**Status:** done

- [x] `lint_plantuml` appears in `tool-list` (18 tools total) with a `content` string input schema and the required guidance in its description.
- [x] A valid ArchiMate document returns `isError=false` with first line `OK: no problems` and no closing line.
- [x] An invalid relationship and an unclosed block each return `isError=false` with the expected problem lines and the closing instruction.
- [x] Missing and blank `content` return `isError=true`; the tool works against an empty registry.
- [x] A non-ArchiMate diagram snippet returns `isError=false` with a report (no crash).
- [x] Tests in `test/armate/mcp/tools_test.clj` cover the above and the description guidance; the tool-count test is updated to 18.

**Refs:** spec `.scratch/plantuml-lint/spec.md` (§ Tool Contract, § Report Format, § Testing Decisions); ticket 01; prior art `test/armate/mcp/tools_test.clj` (`tool-list-17-tools`, `tool-descriptions-document-markers`).