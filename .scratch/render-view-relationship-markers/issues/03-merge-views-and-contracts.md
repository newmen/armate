# 03: merge_views offview marking and tool contracts

**What to build:** `merge_views` marks a relationship drawn between two elements of the union as `(offview)` when it is placed on **none** of the merged views, whereas a relationship placed on at least one merged view carries no marker. The marking decision is computed once in the view-rendering entry point from the model record (the placed-element/relation view indexes plus the `:derivate` marker) and drives both `render_view` and `merge_views` uniformly. The tool descriptions for `render_view` and `merge_views` are updated to document the marker vocabulary, so the contract is self-describing; end-to-end MCP-level tests cover the whole feature.

**Blocked by:** 02 (Derived relationship markers) — reuses the shared classification and the common view-rendering entry point.

**Status:** done

- [x] In `merge_views`, a relationship between two merged elements placed on none of the merged views is labelled `(offview)`.
- [x] In `merge_views`, a relationship placed on at least one merged view carries no marker (it is internal to the merged result).
- [x] The `(offview)` decision for both tools uses the same classification, differing only in the set of source view names used (a single view for `render_view`, the merged union for `merge_views`).
- [x] The markers compose correctly: offview and derived markers coexist in one output, each relationship carrying exactly one marker.
- [x] The `render_view` and `merge_views` tool descriptions document the `(offview)`, `(derived-certain)` and `(derived-potential)` markers.
- [x] End-to-end MCP tool tests verify offview marking at `mode = "none"`, derived markers only in their matching modes, no markers on genuinely placed edges, and that `related_elements` remains unmarked.