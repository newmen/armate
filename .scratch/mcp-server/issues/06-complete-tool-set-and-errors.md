# 06: Complete tool set and error contract

**What to build:** The remaining MCP tools on top of the scaffold: `filter_by_type`, `filter_by_layer`, `element_views`, `relation_views`, `related_elements` (from 03), `shortest_path`, `all_paths` (from 04), `get_stats`, plus the error contract. Errors use MCP `isError: true` with structured messages: unknown `model_id`, unknown `view`, unknown element name, non-unique element name (candidate list), potential-cap exceeded (`potential` only when the union of unique target-view elements ≤ `MCP_POTENTIAL_CAP` env, default 50, else hard rejection), invalid rel-types. Validate inputs before computation.

**Blocked by:** 03 (Induced subgraph around a root element), 05 (MCP server scaffold and core view tools)

**Status:** done

- [x] `filter_by_type`, `filter_by_layer`, `element_views`, `relation_views`, `related_elements`, `shortest_path`, `all_paths`, `get_stats`.
- [x] `isError: true` error contract with structured messages.
- [x] Potential cap enforcement (`MCP_POTENTIAL_CAP`, default 50, hard rejection on exceed).
- [x] Input validation before computation.

**Resolved decisions:**
- Unknown element names produce an `isError` that lists a sample of available element names (helpful hint).
- `relation_views` with no `type` returns one line per relation type with its view set as `type: {view1, view2}`.
- All error paths (unknown model/view/element, ambiguous name, potential-cap, invalid rel-types) are validated before computation.