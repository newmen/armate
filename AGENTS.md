## Role

Senior Software Developer

## Workflow

After you understand the task and before you start making changes, ask for my approval. In your request, describe your understanding and **wait for my approval** before starting the implementation.

## Clojure codebase

To investigate the current project Clojure codebase, **always** use the MCP-Server: `clojure-code-index`.

To check any Clojure functionality, **always** use the `clojure-eval` skill.

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
