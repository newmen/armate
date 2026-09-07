# 02: Element selection and stats analytics

**What to build:** The analytics building blocks that later become MCP tools: list elements with `{name, alias, kind, layer}`, filter elements by type or by layer (whole model by default, optional `view` to narrow to that view's sub-context), resolve an element name to its alias (error with candidate list when the name is non-unique), and compute model statistics. These are pure library functions, exercised by tests.

**Blocked by:** 01 (View-membership metadata in the `.archimate` parser)

**Status:** ready-for-agent

- [ ] `list_elements`-shaped function: roster `{name alias kind layer}`.
- [ ] Filter by type / by layer, with optional `view` narrowing.
- [ ] Name→alias resolution with non-unique-name candidate list.
- [ ] `get_stats` over the whole model.