# 01: Offview relationship markers in render_view

**What to build:** rendering a single view with `render_view` marks every relationship that is drawn only because both endpoints fall inside the view's sub-context but is not genuinely placed on that view. Such a relationship is labelled with `(offview)` appended to its label, while a relationship genuinely placed on the view carries no marker. This is the first complete vertical slice of the feature: the classification logic plus the renderer seam that appends the label suffix, active for a single view.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] A relationship drawn in `render_view` output whose endpoints are both in the sub-context but that is not placed on the view is labelled `(offview)`.
- [x] A relationship genuinely placed on the view carries no marker.
- [x] The marker is appended as the last parenthesised group of the relationship's existing label; an original relationship with no label renders as a bare `(offview)`.
- [x] A relationship's original label text (`:desc`) is preserved exactly; no other text is added besides the marker.
- [x] The `(offview)` marker appears regardless of the derivation `mode` (even `mode = "none"`).
- [x] Derived relationships are not labelled `(offview)` (they are out of scope for this ticket's classification).
- [x] `related_elements` output is unchanged (no markers, no regressions).
- [x] Tests assert the rendered PlantUML text for all of the above, following the existing view-rendering test patterns.