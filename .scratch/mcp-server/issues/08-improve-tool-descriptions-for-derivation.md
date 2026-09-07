# 08: Improve tool descriptions to surface derivation rules

**What to build:** The MCP `tools/list` output for the tools that already accept a derivation mode (`render_view`, `merge_views`, `related_elements`) explicitly explains what the `mode` parameter (`none | certain | certain+potential`) does: the `certain` / `certain+potential` modes add relationships inferred by ArchiMate derivation rules on top of the model's original relationships. A description and the parameter hints make it clear to an agent reading the tool list that derivation rules are available, what they produce, and when to switch them on (e.g. to see implied relations that the source model omits).

**Blocked by:** None (can start immediately).

**Status:** done

- [x] `render_view`'s tool `description` and its `mode` schema explain that `certain`/`certain+potential` add inferred (derived) relationships per ArchiMate rules, not just "apply derivation".
- [x] The same for `merge_views` and `related_elements`.
- [x] Wording is agent-facing: names the inference as derived relations and hints when they help (finding implied structure beyond the explicit model).