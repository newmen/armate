# 02: Derived relationship markers (derived-certain / derived-potential)

**What to build:** derived relationships rendered by the derivation modes carry their own marker instead of being labelled offview: a relationship inferred by the global `certain` rules renders with `(derived-certain)`, one inferred locally by the `potential` rules with `(derived-potential)`. A derived relationship always takes its derivation marker and never the `(offview)` token. The computed influence strength in the label is preserved, e.g. `Rel_influence(a, b, "+++ (derived-certain)")`. Derived markers appear only when the corresponding derived edge is rendered (under matching `mode`), reusing the label-suffix seam from ticket 01.

**Blocked by:** 01 (Offview relationship markers in render_view) — reuses the label-suffix rendering seam.

**Status:** done

- [x] A relationship carrying the `:derivate :certain` marker renders with `(derived-certain)`.
- [x] A relationship carrying the `:derivate :potential` marker renders with `(derived-potential)`.
- [x] A derived relationship is never labelled `(offview)`; it carries its own token.
- [x] Under `mode = "certain"` a derived edge shows `(derived-certain)`; under `mode = "certain+potential"`, potential edges show `(derived-potential)` and certain edges `(derived-certain)`; under `mode = "none"` no derived markers appear (no derived edges render).
- [x] An influence relationship's computed strength text (e.g. `+++`) is preserved and the marker is appended after it (e.g. `Rel_influence(a, b, "+++ (derived-certain)")`).
- [x] Each labelled relationship carries exactly one marker (offview or one derived token), never two.
- [x] Tests assert the rendered PlantUML text of the above.