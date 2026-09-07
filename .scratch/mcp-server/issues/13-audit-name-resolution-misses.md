# 13: Audit all bare name-resolution misses for fuzzy suggestions

**What to build:** Every tool path that resolves a user-supplied element name to an alias reports a miss with the same useful, suggestion-bearing error. A review of the codebase finds only one remaining bare `Unknown element` throw that does not offer nearest-name hints — the one in `related-elements` covered by tickets 11–12. This ticket holds the acceptance gate that no other name-based tool or analytics function falls back to a hint-less miss, and that the shared message helper is the single source of the wording.

**Blocked by:** 11, 12

**Status:** ready-for-agent

- [ ] A sweep of name-resolution entry points (the alias-bearing tools and the analytics resolution functions) confirms each unknown-name miss surfaces fuzzy nearest-name hints, with no bare hint-less `Unknown element` throw left.
- [ ] The unknown-name message text is produced by one shared helper, so wording/levelling stays consistent across tools.
- [ ] The sweep is recorded in the ticket comments: which entry points were checked and the result for each.

## Comments

Sweep (ticket 13, done alongside 11–12). Entry points checked:

- `ana/related-elements` — was the sole hint-less throw (bare `"Unknown element: <name>"`); now throws `ana/unknown-name-message` with `nearest-elements` hints (threshold 0.67). Pass.
- `tools/resolve-alias` — now calls the shared `ana/unknown-name-message` for misses; ambiguous names still route to `ambiguous-name-message` (handler-layer formatting, correct to keep in tools). Pass.
- Alias-bearing tool handlers (`element_views`, `relation_views`, `derived_relations`, `related_elements`, `shortest_path`, `all_paths`) — all resolve via `resolve-alias`, so they inherit the shared wording. Pass.

No hint-less `Unknown element` throw remains in source. The literal `"Unknown element"` now lives only in `ana/unknown-name-message` (single source) and test assertions. A `rg` for `"Unknown element"` in `src/` returns no source occurrences besides the helper. Done.

**Status:** done