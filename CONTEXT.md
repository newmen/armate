# Armate

Analysis of ArchiMate architecture models. Parses `.archimate` files (and PlantUML diagrams, used only as output format), enriches them into a graph model, derives relationships per the ArchiMate spec, and exposes the analytics to agents through an MCP server.

## Language

**Model**:
A loaded `.archimate` file: its elements, relationships, and views, plus the metadata (view-membership indexes) built from the file. Addressed in the MCP server by `model_id`.
_Avoid_: file, diagram

**View**:
A named diagram inside a model. Addressed by view name.
_Avoid_: page, diagram (in the sense of an output)

**Sub-context of a view**:
The elements explicitly placed in a view plus the relationships between them, taken from the model. Built strictly from placed elements; never expanded transitively.
_Avoid_: induced subgraph (that term is reserved for `related_elements`)

**Target views**:
The one or more views a tool call selects for rendering, derivation, or statistics.

**Relationship**:
A directed relation between two elements in a model, with a type (structural, dependency, dynamic or other (specialization)). Addressed by its endpoints and optional type.
_Avoid_: edge, link, relation-type confusion

**Derviation**:
The process of adding relationships that follow from the ArchiMate `certain` and `potential` rules. Runtime-guarded for heavy `potential` computation.
_Avoid_: deduction, inference

**Certain derivation**:
Application of the `certain` rules over the whole loaded model, producing a global set of derived relationships. Computed lazily and cached in the model registry.
_Avoid_: guaranteed, certain (adjective)

**Potential derivation**:
Application of the `potential` rules over the sub-context of target views only, not globally. Guarded by an element-count cap.
_Avoid_: possible, potential (adjective)

**Induced subgraph**:
The output of `related_elements`: all elements within `depth` hops (undirected) of a root element, rendered as a dynamic view whose relationships are all relationships between placed elements taken from the model, optionally augmented with `certain` derived relationships.
_Avoid_: tree, subtree, neighbourhood

**Model registry**:
The in-memory store of loaded models in the MCP server, keyed by `model_id`. No persistence between server runs.
_Avoid_: database, catalog

**Re-center approach**:
Not a term; see **Sub-context of a view** and **Target views**.