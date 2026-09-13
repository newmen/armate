# Spec: Mark offview and derived relationships in render_view / merge_views

**Feature slug:** `render-view-relationship-markers`

**Status:** `ready-for-agent`

Mark, in the PlantUML output of `render_view` and `merge_views`, every relationship that is not genuinely placed on the requested view(s): relationships drawn only because both endpoints happen to fall inside the sub-context (not placed on the view) are labelled `(offview)`; relationships added by ArchiMate derivation rules are labelled `(derived-certain)` or `(derived-potential)`. The rendered labels stay human-readable and uniformly machine-strippable, and the computed influence strength is never altered.

## Problem Statement

`render_view` and `merge_views` build their renderable sub-context with `build-sub-context` (analytics.clj:301), which selects **all** model relationships whose endpoints are both among the placed (or merged) aliases — without narrowing to the relationships a view actually places. As a result, the rendered `@startuml` text can contain relationships that were not placed on the source view(s), i.e. relationships the user's original diagram did not draw. When a derivation mode is selected, derived relationships (`:derivate` = `:certain` / `:potential`) are additionally injected. Nothing in the output distinguishes either class from relationships that genuinely belong to the view, so a consumer of the `@startuml` text — human or agent — cannot tell which edges are truly part of the view, which leaked in from the surrounding model, and which were invented by the derivation rules. This ambiguity defeats the purpose of rendering a view as evidence of the model's intended structure.

## Solution

The PlantUML text produced by `render_view` and `merge_views` must annotate every relationship that is not genuinely placed on the source view(s), using a single short English token appended in parentheses to the relationship's existing label (`desc`). The tokens are the fixed vocabulary below; a relationship carries exactly one marker. The annotation is applied to the composed relationship label so that it survives the existing PlantUML rendering unchanged.

### Marker vocabulary

- **`(offview)`** — the relationship is an original (non-derived) model relationship between two elements of the rendered sub-context that is placed on none of the rendered view(s). For `render_view` this means the relationship is absent from the single view; for `merge_views` it means the relationship is absent from **every** merged view.
- **`(derived-certain)`** — the relationship was inferred by the global `certain` derivation rules (carries the `:derivate` marker `:certain`).
- **`(derived-potential)`** — the relationship was inferred by the local `potential` derivation rules (carries the `:derivate` marker `:potential`).

A relationship is either derived or not; a derived relationship is never also `(offview)` and vice-versa — see Q5 in the decisions. Thus a relationship carries **exactly one** of the three markers. Original relationships that are genuinely placed on the view(s) carry **no** marker.

### Label placement

The marker is appended to the relationship's existing label text inside parentheses. Concretely, in the rendered call this yields, for example:

- `Rel_Composition(a, b, "some label (offview)")` — existing `desc` kept, `(offview)` appended.
- `Rel_Composition(a, b, "(offview)")` — no existing `desc` (original relationship with no name), the marker alone becomes the label.
- `Rel_influence(a, b, "+++ (derived-certain)")` — the computed influence strength (`desc`, e.g. `"+++"`) is kept untouched and `(derived-certain)` is appended after it.
- `Rel_influence(a, b, "(offview)")` — an original influence relationship with no strength gets the bare `(offview)` marker.

The marker is always a single parenthesised token. It is appended as the **last** parenthesised group of the label, so the original `desc` (if any) always precedes it and the marker can be stripped deterministically by removing the trailing `(<token>)`. No other delimiter is introduced; the influence strength (its `:desc`) is never modified or recomputed.

## User Stories

1. As an MCP user, I want `render_view` to label a relationship that appears because both endpoints are in the sub-context but that is not placed on the view, so that I can tell the rendered edge is not genuinely part of the view.
2. As an MCP user, I want `merge_views` to label a relationship drawn between two merged elements that is placed on none of the merged views, so that I know it came from the surrounding model rather than any view.
3. As an MCP user, I want a relationship genuinely placed on the view(s) to carry **no** marker, so that the original model's own edges stay un-annotated.
4. As an MCP user, I want derived relationships rendered under `mode = "certain"` to be labelled `(derived-certain)`, so that inferred edges are distinguishable from model edges.
5. As an MCP user, I want derived relationships rendered under `mode = "certain+potential"` to be labelled `(derived-potential)` for potential edges and `(derived-certain)` for certain edges, so that the source of each inferred edge is visible.
6. As an MCP user, I want the marker tokens fixed and uniform, so that a client can parse/regex-strip them reliably.
7. As an MCP user, I want influence relationships to keep their computed strength text (`+++`, `--`, etc.) and only gain a marker suffix, so that the strength information is preserved.
8. As an MCP user, I want the markers to be independent of the `mode` parameter for the offview class — an `(offview)` edge is labelled even at `mode = "none"`, while derived markers appear only when the corresponding derived edges are rendered.
9. As a developer, I want the classification ("offview"? "derived-certain"? "derived-potential"?) computed from the existing `:element-views` / `:relation-views` indexes and the `:derivate` marker, already present in the model record, so that no new metadata model is needed.
10. As a developer, I want `related_elements` to remain unchanged, so that this marking affects only the view-rendering tools.

## Implementation Decisions

- **Scope: `render_view` and `merge_views` only.** `related_elements`/`derive` are out of scope: it also builds from `build-sub-context` but is not part of this request, and its sub-context is not bounded by "the placed view element set", so the offview notion does not map cleanly onto it. Marking targets the `render-views-puml` path in `armate.mcp.tools` (which dispatches to `ana/render-view` and `ana/render-merged-views`).
- **A relationship is `offview` when its endpoints are both in the sub-context but it is not placed on the source view(s).** The model record already carries `:element-views` (alias → view set) and `:relation-views` (`[from to] type → view set`) from `build-view-indexes`. "Placed on the (single/merged) view" is answered by the union of the source view names: for `render_view` it is `#{view}`, for `merge_views` the merged `view-names`. A relationship `[from to type]` that is not a member of `(:relation-views [from to] type)` for that union is `offview`. Because `build-sub-context` needs the aliases (stable global aliases known to the view indexes), the classification is a pure function of the sub-aliases and the source view-name set — no new graph needed.
- **A relationship is `derived-certain`/`derived-potential` according to its `:derivate` marker.** The `:derivate` marker is already carried on derived relations by the derivation engine (derivation/core.clj:39, `:certain` / `:potential`; `mode->render-derivable` in analytics.clj). A non-`nil` `:derivate` (a) proves the relationship is derived (`:certain` or `:potential`), and the marker string drives the token.
- **Exactly one marker per labelled relationship.** A derived relationship is never also `offview`: derivation runs on the sub-context and produces edges between the sub-context's elements, so it is "the model did not draw it" class too, but by construction it is a derived edge, so the `offview` notion does not apply to it; every relationship is dispatched to exactly one `(offview)` / `(derived-certain)` / `(derived-potential)` branch — see Q5 in Further Notes. Non-derived original edges placed on the view carry no marker.
- **The marker is applied as a label suffix, preserving `desc`.** The rendering seam is `viz/combiner.clj` `get-relation` (which builds `Rel_X(a, b, desc)` from the `:desc` key, and for `:raw` relations the raw arrow). The marker must be threaded from the analytics layer into the rendered label. The cleanest seam: pass a marker-fn (or a `{[[from to] :type] token}` map) into the render options so the combiner appends the chosen token to `desc`. `:raw` relations (parse-time raw arrows, e.g. `a --> b`) have no `desc` funnel; for them the marker is appended to the raw text between the endpoints is the secondary/undefined case, but the standard `Rel_*` path is the primary target. Concretely the render options gain a relationship-marker component: for `render-edable` the `:render-derivable` set still drives which derived edges draw, and now the same `:derivate` value selects the derived token; `offview` is decided independently at the analytics sub-ctx level.
- **The classification decision belongs at the sub-context build, exposed to rendering.** Rather than mutating `:relation-views` data, the view-render path (`render-views-puml`) computes for the given sub-aliases and view-name union an `{edge-key token}` map and passes it to `ana/render-view` / `ana/render-merged-views`, which hand it to the combiner so each edge's label gets the suffix. Edges with no token render unchanged. Influence strength in `:desc` is passed through untouched; the token is appended after it (e.g. `"+++ (derived-certain)"`).
- **`render_views-puml` threads the marker map.** The single entry point already assoc the view names, mode, and cap; it now computes the edge marker map from `:relation-views` + `:element-views` + the chosen view-name set, and passes `derived` tokens from the `:derivate` marker. This keeps `render_view` and `merge_views` behavior identical except for the view-name set used to decide offview.
- **Unknown-model / error behavior unchanged.** The existing error paths (blank view, empty views, unknown model) short-circuit before any marking.
- **Schema/descriptions**: the tool descriptions for `render_view` / `merge_views` are updated to note that non-placed relationships are marked `(offview)` and derived ones with the `(derived-…)` tokens, so the contract is self-documenting.

## Testing Decisions

- **Test the observable PlantUML text, not internals.** A good test renders a view/merge combo that exercises all three classes and asserts the exact edge lines contain `(offview)`, `(derived-certain)`, `(derived-potential)`, and that a genuinely-placed edge contains none.
- **Offview, no marker conflict.** Test that a relationship genuinely placed on the view is unmarked; a non-placed original relationship in the sub-context is `(offview)`; a derived edge is `(derived-…)` and never double-labelled.
- **Influence `desc` preserved.** Assert `Rel_influence(a, b, "+++ (derived-certain)")` keeps the strength text `+++` untouched.
- **Mode-independence of offview:** offview markers appear at `mode = "none"` too; derived markers only in the modes that render them.
- **Prior art:** existing view-render tests in `test/armate/mcp/tools_test.clj` (view rendering with/without derivation modes, `related-elements`/`merge_views`) and the renderer tests in `test/armate/archimate/viz/combiner_test.clj` (asserting the exact PlantUML string as text) establish the pattern of asserting on rendered `@startuml` text. The demo fixture `test/resources/demo.archimate` is known to yield derived relations and is used by the existing tool tests.
- **The seam to test is the rendered string** produced by the MCP tools (high-level) and, where useful, the pure marking helper (mid-level) that returns the `{edge → token}` map.

## Out of Scope

- Marking relationships in `related_elements` (its sub-context is not bounded by a view's placement set).
- Changing the `:derivate` / `:desc` data model or the derivation engine itself.
- Changing how derived edges are selected per mode (that is unchanged; only their annotation is new).
- Recap/weight recomputation for influence relationships (the strength `:desc` stays exactly as computed).
- Machine-readable output format beyond the fixed parenthesised tokens in the label text (no JSON side-channel).

## Further Notes

- A relationship that is *derived* never participates in the *offview* classification; the marker set `(#{} #{:offview} #{:derived-certain} #{:derived-potential})` partitions all rendered relationships. See Q5's premise: a derived edge is inherently offview-flavored but is labelled by its derivation class, never by the `(offview)` token.
- The classification reuses only data already in the model record (`:element-views`, `:relation-views`, `:derivate`) — no new graph build, preserving the "exactly one full-graph build per load" invariant.
- Domain vocabulary follows `CONTEXT.md` (element / relationship / derived relation / certain / potential); server SME decisions from the `merge_views` / `render_view` descriptions apply unchanged.