# Spec: `lint_plantuml` MCP tool

**Feature slug:** `plantuml-lint`

**Status:** `implemented`

Expose the existing ArchiMate PlantUML parser's checks to an agent as a new MCP tool,
`lint_plantuml`, so that an agent can validate PlantUML it has just generated — before
writing it to a file or trying to render it — and receive an agent-readable list of
problems plus an overall verdict.

## Problem Statement

Agents that generate ArchiMate PlantUML for armate currently have no way to check their
output before committing to it. The existing parser
(`armate.archimate.plantuml.parser/analyze-content`) already performs a rich set of
checks — undefined element/relationship types, relationships not allowed between two
element kinds, dangling endpoints, duplicates, connector misuse, missing
`@startuml`/`@enduml`/`!include` — but it is **not reachable from the MCP server at
all**. The only trace of linting in MCP is a bare count in `get_stats`
(`(count (:lints context))`); the lint details are discarded. As a result an agent
either cannot self-check or resorts to a generic PlantUML CLI, which validates PlantUML
*grammar* but knows nothing about the ArchiMate metamodel (which relationships are legal
between which elements). The tool's value is precisely that metamodel-aware verdict.

## Solution

Add one MCP tool, `lint_plantuml`, that takes a full PlantUML document as an inline
multiline string (`content`) and returns an agent-readable report built from
`analyze-content`'s `:lints`:

- First line is the verdict with problem counts.
- One line per problem, sorted so errors precede warnings and by line number within a
  level.
- A closing instruction line when there is at least one problem.
- No `model_id`: standalone. The tool builds a throwaway parse context from the content
  and lints it. Nothing is stored in the registry, and the PlantUML never becomes a model
  source (the parse context is diagnostic only).

`isError` is always `false` when a report is produced: lints are *data*, not a tool
failure. `isError` is reserved for genuine call failures (missing/blank `content`, an
unexpected parser exception).

## Locked Decisions (from grilling)

| # | Decision |
|---|----------|
| Q1 | Input is an inline multiline string containing the full PlantUML; the agent has not written it to a file. |
| Q2 | Standalone: no `model_id`, no cross-check against a loaded `.archimate` model. |
| Q3 | Output is a structured, agent-readable list of problems with the overall verdict (counts) on the first line. |
| Q4 | Always `isError=false` for lint results; lints are data, not failure. |
| Q5 | Tool name `lint_plantuml`, with a detailed description explaining *why* to call it (before a generic PlantUML CLI) and that it works only with ArchiMate, not sequence/UML diagrams. |
| Q6 | Parser exceptions that represent a defect in the generated PUML (e.g. unclosed block) are turned into lint records; other exceptions stay real errors. |
| Q7 | Reuse the entire existing check set as-is, without changing warn/error semantics. |
| Q8 | Update domain docs: add the PlantUML lint term to `CONTEXT.md`; add an ADR; fix the stale README. |
| Q9 | Formatting lives in a new pure namespace, not in `armate.mcp.tools`. |
| Q10 | Text format: verdict line, one line per lint, closing instruction. |
| Q11 | Sort errors before warnings; within a level, by line number, line-less last. |
| Q12 | Human explanations include concrete data (relationship type, element kinds, unresolvable alias, duplicate payload). |
| Q13 | Connector sub-problems (`:incorrect-connector-using` `:data`) become one line each. |
| Q14 | No `clojure.spec` (matches existing repo convention; `AGENTS.md` spec rule waived for this change). |
| Q15 | Tests: update the tool-count test, add `lint_plantuml` tool tests, add formatter/sort unit tests. |
| Q16 | Docs updated in the same change (`CONTEXT.md`, ADR-0002, README). |
| Q17 | Non-ArchiMate diagrams are only warned about in the tool description; no new heuristic check. |
| Q18/Q23 | Closing instruction whenever there is **any** problem (errors and/or warnings): `Fix the errors and warnings above and lint again`. |
| Q19 | `Unclosed block` becomes lint `:kind :unclosed-block`, `:level :error`, `:in [:parse]`, with the starting line when available. Other exceptions → `isError=true`. |
| Q20 | Missing argument or blank/whitespace-only `content` → `isError=true` argument error. |
| Q21 | Counts use a constant plural: `1 errors, 1 warnings` is acceptable. |
| Q22 | New namespace `armate.archimate.plantuml.lint`: `lint-content`, `format-summary`, private `format-lints`, private kind→explanation map. |
| Q24 | All output and docs in English. |

## Tool Contract

**Name:** `lint_plantuml`

**Arguments:**

| name | type | required | description |
|------|------|----------|-------------|
| `content` | string | yes | The full PlantUML document to lint (multiline, ArchiMate only). |

**Result:** `{:content [{:type "text" :text <report>}] :isError false}`

**Error results (`isError=true`):** missing `content`; blank/whitespace-only `content`;
an unexpected exception from the parser that is not a PUML defect.

**Tool description (must convey):**

- Call this before attempting to validate generated PlantUML with a generic PlantUML CLI.
- It checks ArchiMate semantics that a PlantUML grammar/syntax tool cannot: which
  relationships are legal between which element kinds, unresolved endpoints, undefined
  element/relationship types, duplicates, connector misuse, document structure.
- ArchiMate only. It is **not** for sequence diagrams or other UML diagram types; those
  will not lint meaningfully.

## Report Format

Verdict line:

- `OK: no problems` when there are no lints.
- `E errors, W warnings` otherwise (constant plural, e.g. `1 errors, 1 warnings`).

One line per problem:

```
<LEVEL> <location> <context> <kind>: <explanation>
```

- `<LEVEL>` is `ERROR` or `WARN` (uppercase for grep-ability).
- `<location>` is `line N` when the lint carries a line, else `[document]`.
- `<context>` is `[in ...]` rendered from the lint's `:in` vector; omitted when absent.
- `<kind>` is the machine-readable lint keyword (no leading colon).
- `<explanation>` is a human sentence including concrete data.

Examples:

```
2 errors, 3 warnings
ERROR line 17 [in relations c1 c2] undefined-relation-type: relation "." is not an ArchiMate relationship
WARN line 17 [in relations c1 c2] unspecified-relation-type: access_rw is not allowed between application-component and application-component
ERROR [document] incorrect-connector-using: connector jc30 uses different relation types (triggering, access)
WARN [document] missing-archimate-include: no !include <archimate/Archimate> directive
Fix the errors and warnings above and lint again
```

When there is any problem, the closing line is exactly:

```
Fix the errors and warnings above and lint again
```

preceded by one blank line. When `OK: no problems`, no closing line.

## Lint Kinds and Explanations

Existing kinds, levels unchanged (Q7). Explanations with concrete data (Q12):

| kind | level | explanation |
|------|-------|-------------|
| `:unknown` | error | line matches no known ArchiMate construct; show the raw line parts |
| `:duplicate` | error | element/relation added twice; show what was duplicated |
| `:undefined-element-type` | error | element has no recognized kind and no nested composite content; show alias/title |
| `:undefined-relation-type` | error | relation operator is not an ArchiMate relationship; show `:raw`/`:type` |
| `:undefined-relation-from` | error | relation source alias cannot be resolved; show the alias |
| `:undefined-relation-to` | error | relation target alias cannot be resolved; show the alias |
| `:incorrect-connector-using` | error | connector misuse; one line per sub-problem in `:data` (Q13) |
| `:unsupporting-element-type` | warn | declared `sprite` type is not an ArchiMate element kind; show kind |
| `:unsupporting-element-layer` | warn | explicit layer is not an ArchiMate layer; show layer |
| `:undefined-element-skin` | warn | skin references an undefined shape; show shape/alias |
| `:unspecified-relation-type` | warn | relationship is not allowed between these element kinds; show type and `from-kind → to-kind` |
| `:relation-between-elements-already-present` | warn | the reverse relation already exists; show type and endpoints |
| `:missing-start` | warn | no `@startuml` directive |
| `:missing-end` | warn | no `@enduml` directive |
| `:missing-archimate-include` | warn | no `!include <archimate/Archimate>` |
| `:unclosed-block` | error | unclosed `{` block; show the starting line (synthesized, see Q6/Q19) |

Sub-kinds inside `:incorrect-connector-using` (`:data`): `:different-using-relations`,
`:no-outgoing-relations`, `:no-incoming-relations`, `:excess-connector`.

## Implementation Decisions

- **New namespace `armate.archimate.plantuml.lint`** (Q9, Q22), pure:
  - `lint-content [content] -> {:lints [...] :errors n :warnings n}` — calls
    `parser/analyze-content`, catches `clojure.lang.ExceptionInfo` from the parser and
    converts a PUML defect (unclosed block) into a lint record, then sorts.
  - `format-summary [summary] -> String` — verdict, sorted per-lint lines, closing line.
  - private `format-lint` and private `kind-explanation` map.
  - Docstrings + type descriptions per repo style.
- **`armate.mcp.tools/lint_plantuml`** is a thin handler: validate `content`
  (Q20), call `lint/lint-content` + `lint/format-summary`, return `ok`. Any unexpected
  exception → `err`.
- **Register** the tool in `handle-dispatch` and `tool-schemas`.
- **Sorting** (Q11): stable sort by `[level-rank line-rank line]`, where level-rank puts
  `:error` before `:warn`, and line-less lints come last within their level.
- **Line extraction**: block lints carry `:body {:line N}`; `:incorrect-connector-using`
  carries per-sub-problem `:connector {:line N}`; `:unclosed-block` carries the line from
  `ex-data`.
- **No registry mutation**: returns `[registry result]` with the registry unchanged.

## Testing Decisions

- **`test/armate/mcp/tools_test.clj`**:
  - Update `tool-list-17-tools` → 18 tools, adding `lint_plantuml` to the expected name
    set (sort order: after `list_views`, before `load_model`).
  - `lint-plantuml-valid`: a valid ArchiMate PUML document (e.g. the `EXP-RENDER-SEMYA`
    fixture) → `isError=false` and first line `OK: no problems`; no closing line.
  - `lint-plantuml-invalid-relation`: a document with a disallowed relationship operator
    → an `ERROR … unspecified-relation-type` or `undefined-relation-type` line and the
    closing instruction line.
  - `lint-plantuml-unclosed-block`: unbalanced `{` → `ERROR [parse] unclosed-block …`,
    `isError=false`.
  - `lint-plantuml-missing-arg` and `lint-plantuml-blank`: → `isError=true`.
  - `lint-plantuml-no-model`: works with an empty registry (standalone, no `model_id`).
  - `lint-plantuml-not-archimate`: a sequence-diagram snippet → `isError=false` with a
    report (documents the Q17 behavior; no crash).
- **New `test/armate/archimate/lint_test.clj`**: unit tests for `format-summary`
  (verdict text, sorting errors-before-warnings, line-less last, closing line presence)
  and for `lint-content` (counts; unclosed-block mapping).
- **Description test**: assert the `lint_plantuml` description mentions ArchiMate and
  the generic-CLI guidance (mirrors `tool-descriptions-document-markers`).
- **Prior art**: existing tests in `test/armate/archimate/parser_test.clj`
  (`analyze-content-test`) and `test/armate/mcp/tools_test.clj`.

## Documentation Deliverables (same change)

- **`CONTEXT.md`**: add the term **PlantUML lint**.
- **`docs/adr/0002-lint-input-is-not-a-model-source.md`**: records that accepting
  PlantUML as input to the lint tool does not violate ADR-0001's "PlantUML is an output
  format, never a model source", because no model is built — the parse context is
  diagnostic only, and lint reuses `plantuml.parser` rather than duplicating the
  metamodel.
- **`README.md`**: fix the stale `(core/lint-file …)` snippet to the real API.

## Out of Scope

- Cross-checking the PUML against a loaded `.archimate` model (render-fidelity checks).
  Recorded here as a candidate future feature.
- A heuristic "this is not an ArchiMate diagram" detector (Q17 chose description-only).
- Changing the semantics, levels, or set of existing parser checks.
- Changing `get_stats`' `:lints` count or any other MCP tool.
- `clojure.spec` adoption (Q14).
- A CLI wrapper for linting files.