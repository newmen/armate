# 11: Add fuzzy nearest-name suggestions to related-elements' unknown-root error

**What to build:** When `related-elements` (in `mcp/analytics`) is given a root element name that matches no element, the thrown error no longer reads as a bare `Unknown element: <name>`. Instead it carries a short list of the nearest real element names (computed with the Jaro-Winkler fuzzy match used elsewhere, threshold 0.67), each with its alias/kind/layer, so a caller that mis-typed or nearly-matched a name can correct it in one step. This mirrors the wording the other name-based tools already produce via `resolve-alias`.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `related-elements`'s unknown-root throw includes the nearest matching element names via the same fuzzy `nearest-elements` helper (threshold 0.67) and the same message shape as the `Unknown element:` error other tools emit.
- [x] A direct call to `related-elements` (the analytics function, not the MCP handler) with an unknown name surfaces the "Did you mean" hint list in the thrown error.
- [x] Behaviour covered by a test in the `mcp/analytics` test namespace (`related-elements-unknown-root-suggests-nearest-names`) asserting the hint list appears for an unknown name.