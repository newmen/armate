# 0002: Lint input is not a model source

We accept a PlantUML document as the input to the `lint_plantuml` MCP tool, which does not violate ADR-0001's rule that PlantUML is an output format, never a model source. The tool parses the document into a throwaway context, runs the existing `armate.archimate.plantuml.parser` checks over it, and returns diagnostics only: nothing is stored in the model registry and no `model_id` is created. The PUML stays a text artifact under validation, not an intake format.

Reusing `plantuml.parser` rather than writing a separate validator keeps a single source of truth for what counts as a legal ArchiMate relationship between two element kinds; duplicating those rules would let the validator and the renderer drift.

Considered: (1) validating only via a generic PlantUML CLI — rejected, it checks grammar but knows nothing about the ArchiMate metamodel, which is the whole point; (2) cross-checking the PUML against a loaded `.archimate` model — deferred as a separate future feature, since the requested scope is the checks the parser already performs.