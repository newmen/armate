# 02: Element selection and stats analytics

**What to build:** The analytics building blocks that later become MCP tools: list elements with `{name, alias, kind, layer}`, filter elements by type or by layer (whole model by default, optional `view` to narrow to that view's sub-context), resolve an element name to its alias (error with candidate list when the name is non-unique), and compute model statistics. These are pure library functions, exercised by tests.

**Blocked by:** 01 (View-membership metadata in the `.archimate` parser)

**Status:** done

- [x] `list_elements`-shaped function: roster `{name alias kind layer}`.
- [x] Filter by type / by layer, with optional `view` narrowing.
- [x] Name→alias resolution with non-unique-name candidate list.
- [x] `get_stats` over the whole model.

**Resolved decisions:**
- The tool-level roaster (`list_elements` / `filter_by_type` / `filter_by_layer`) appends each element's view set (`| views: ...`), derived from `:element-views`.
- Unknown-name errors from a tool list a sample of available element names to help the caller correct the name.