# 12: Surface related_elements' unknown-name suggestions through the MCP handler

**What to build:** Calling the `related_elements` MCP tool with an element name that matches no element returns an `isError` response that includes the fuzzy nearest-name hint list (alias/kind/layer), identical in shape to what the other name-based tools return. The handler's resolution path stays in sync with the analytics-level suggestions, so the agent sees actionable "Did you mean" hints in one round trip instead of a bare miss.

**Blocked by:** 11

**Status:** done

- [x] `related_elements` tool request with an unknown element name returns an `isError` result whose text includes the nearest matching element names (and "Did you mean").
- [x] An ambiguous name (shared by several elements) still returns the existing candidates error, unchanged.
- [x] Covered by a test in the `mcp/tools` test namespace (`related-elements-unknown-name-suggests-nearest`) asserting the hint list appears for an unknown name and that a known name still succeeds.