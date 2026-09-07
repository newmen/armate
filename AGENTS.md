## Role

Senior Software Developer

## Workflow

After you understand the task and before you start making changes, ask for my approval. In your request, describe your understanding and **wait for my approval** before starting the implementation.

## Clojure codebase

To investigate the current project Clojure codebase, **always** use the MCP-Server: `clojure-code-index`.

To check any Clojure functionality, **always** use the `/clojure-eval` skill. Use this when you need to test code, check if edited files compile, verify function behavior, or interact with a running REPL session.

## Memory
`memorygraph` CLI is installed. Use it for persistent memory across sessions.

### REQUIRED: Before Starting Work
You MUST use `memorygraph recall --query "<task>" --limit 10` and `memorygraph briefing` before any task. Query by project, tech, or task type.

### REQUIRED: Automatic Storage Triggers
Store memories on ANY of:
– New task started and some reason made
- Architecture decision: choice + rationale
- Bug fix: problem + solution
- Git commit: what was fixed/added
- Pattern discovered: reusable approach

On decisions/fixes: `memorygraph store --type solution --title "<title>" --content "<what>" --tags "<component>,fix"`
On errors: `memorygraph store --type error --title "<error>" --content "<details>" --tags "<component>,error"`
Link: `memorygraph link <from-id> <to-id> SOLVES --strength 0.8`
Session end: `memorygraph store --type conversation --title "Session: <topic>" --content "<summary>" --tags "<tags>"`

Do NOT wait to be asked. Store automatically on triggers.

### Memory Fields
- Type: solution | problem | code_pattern | fix | error | workflow | command | technology
- Title: Specific, searchable
- Content: Accomplishment, decisions, patterns
- Tags (lowercase, hyphenated, include component (auth, database, cli), 2-5 per memory): project, tech, category (required), etc.
- Importance: 0.8+ critical, 0.5-0.7 standard, 0.3-0.4 minor
- Relationships: Link related memories when they exist

### Memory link types

The relationship types linking memories fall into six semantic categories:

#### Causal
CAUSES, TRIGGERS, LEADS_TO, PREVENTS, BREAKS

#### Solution
SOLVES, ADDRESSES, ALTERNATIVE_TO, IMPROVES, REPLACES

#### Context
OCCURS_IN, APPLIES_TO, WORKS_WITH, REQUIRES, USED_IN

#### Learning
BUILDS_ON, CONTRADICTS, CONFIRMS, GENERALIZES, SPECIALIZES

#### Similarity
SIMILAR_TO, VARIANT_OF, RELATED_TO, ANALOGY_TO, OPPOSITE_OF

#### Workflow
FOLLOWS, DEPENDS_ON, ENABLES, BLOCKS, PARALLEL_TO

#### Quality
EFFECTIVE_FOR, INEFFECTIVE_FOR, PREFERRED_OVER, DEPRECATED_BY, VALIDATED_BY

## Agent skills

### Issue tracker

Issues and specs are tracked as local markdown files under `.scratch/<feature-slug>/`. See `docs/agents/issue-tracker.md`.

### Triage labels

Default five-role vocabulary (`needs-triage`, `needs-info`, `ready-for-agent`, `ready-for-human`, `wontfix`). See `docs/agents/triage-labels.md`.

### Domain docs

Single-context: one `CONTEXT.md` plus `docs/adr/` at the repo root. See `docs/agents/domain.md`.
