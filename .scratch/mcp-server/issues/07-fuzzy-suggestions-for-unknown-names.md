# 07: Fuzzy nearest-name suggestions for unknown element names

**What to build:** When a tool is called with a name that does not match any element, the MCP server no longer returns a plain static sample of element names. Instead it returns a short list of the **nearest available element names** computed with fuzzy string similarity (`clj-fuzzy`, synchronized to version `0.4.1` in `project.clj`). The agent sees the closest real names next to the name it typed, so it can correct a typo or near-miss in one step instead of guessing from a generic sample.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] Add `clj-fuzzy "0.4.1"` to `project.clj` dependencies.
- [x] Unknown-name error paths compute nearest matching element names via `clj-fuzzy` and list them (each suggestion, if helpful, with its alias/kind/layer) instead of the current fixed-size alphabetical sample.
- [x] Suggestion count/similarity threshold chosen so output stays short and predictable; behaviour documented in the tool/its docstring.
- [x] Input validation unchanged: a genuinely unknown name is still an `isError`; only the wording of the hint changes.