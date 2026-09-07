# 03: Induced subgraph around a root element

**What to build:** The `related_elements` capability: place all elements within `depth` hops (undirected, default 1) of a root element, then render the dynamic view whose relationships are all relationships between the placed elements taken from the model, optionally augmented with global `certain` derived relationships. It is an induced subgraph, not a strict tree. Rendered as PlantUML.

**Blocked by:** 01 (View-membership metadata in the `.archimate` parser)

**Status:** done

- [x] Depth-limited (undirected) element selection from a root.
- [x] Relationships = all model relationships between the selected elements (+ optional `certain`).
- [x] Rendered as PlantUML (a dynamic view).

**Resolved decisions:**
- `related_elements` runs on the model's global aliases (the root and placed elements are addressed by the global aliases, same as `render_view` / `list_elements`), so the output is cross-referencable with the other tools.