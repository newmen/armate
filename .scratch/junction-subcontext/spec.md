# Spec: Junctions render only on the views that place them

**Feature slug:** `junction-subcontext`

**Status:** ready-for-agent (blocking-issue parent for the ticket set)

Make the MCP view tools (`render_view`, `merge_views`, `related_elements`) respect the **sub-context of a view** rule for Junctions exactly as they do for elements: a Junction appears in the rendered PlantUML only when the target views explicitly place it. Today a Junction is globally leaked into every rendered view because it lives in a separate graph slot from elements. All domain terms follow `CONTEXT.md`.

## Problem Statement

When rendering a view, an agent asks the MCP server for the diagram that matches the original `.archimate` view. But every view currently shows every Junction the model contains, even when that Junction is not on the original view.

The cause is that Junction is a **connector**, stored in the graph under `:connectors` (built by `add-connector`, `kind :connector`), while ordinary elements live under `:elements`. The view-membership metadata (`:element-views`) records placed **elements**; connector membership is not tracked there, and the sub-context builder copies the **full model's** `:connectors` unchanged into every sub-context. The renderer then emits every connector unconditionally (`get-connector` over `(:connectors context)`).

Verified against `test/resources/demo.archimate` (which places a single Junction, "Причина поставить выполнение уроков на паузу", only in the "Семья" view): rendering "Шахматы" and "Процесс" — which never place that Junction — still outputs `Junction_Or(jc26, ...)`.

## Solution

A view's sub-context carries **only the Junctions the target views place**, plus their relationships. Concretely:

- When building the sub-context for a set of placed aliases, select from the model's `:connectors` **only those whose alias is in the placed set**, and drop the rest.
- A Junction's relationships (those whose source or target is the connector alias) are included **only when the Junction itself is placed**, using the same endpoint filter that governs element relationships.

The result: views that do not place a Junction no longer render it; views that do place it render the Junction and its incident relationships correctly.

## User Stories

1. As an agent rendering a single view, I want `render_view` to output exactly the elements and Junctions the original `.archimate` view places, so that the diagram matches the model's view and does not surprise me with extra nodes.
2. As an agent rendering several views together, I want `merge_views` to show a Junction only when at least one of the merged views places it, so the union's nodes are the union's placed elements-plus-Junctions.
3. As an agent exploring around a root element, I want `related_elements` to include a Junction only when the induced sub-graph's induced vertices actually include it, so dynamic views stay consistent with the fixed ones.
4. As an agent rendering views in `:certain` or `:certain+potential` mode, I want a Junction to appear only when it is placed (derivation never introduces new Junctions), so derivation augments relationships but never invents placed nodes.
5. As an agent rendering a view inside which a Junction IS placed, I want its incident relationships rendered too, so the Junction is not stranded but connected as in the model.
6. As a maintainer, I want the sub-context builder to treat connectors and elements by the same "placed-only" rule, so there is a single mental model for what a view contains.

## Implementation Decisions

- **Bug sits in the sub-context builder** (`build-sub-context` in the MCP analytics namespace), not in the renderer. The renderer already behaves correctly given a context: it emits whatever `:connectors` the context carries. The defect is that every context inherits the whole model's connectors.
- **Filter `:connectors` to the placed aliases**, mirroring how `:elements` are selected. A connector (Junction) is selected when its alias is in the target views' placed-alias set (`:element-views` already includes connector aliases, since `view-placed-ids` maps `archimateElement` ids via the global alias map).
- **Treat connectors as graph vertices for relationship selection.** The existing endpoint filter picks relationships whose source and target are both placed. It must consider a connector alias "placed" when that connector is selected, so a placed Junction's incident relationships survive; otherwise those relationships are silently dropped today.
- **Scope stays per target views.** No transitive expansion, no global context leaking: the sub-context remains strictly the placed elements + placed Junctions, per `CONTEXT.md`'s *sub-context of a view*.
- **No change to parser or index shape.** `:element-views`/`:relation-views` and `view-aliases` already expose connector placement through the element alias (Junctions get aliases there). No schema or index change is needed; this is purely a selection fix in the sub-context builder.
- **`related_elements` inherits the fix automatically** because it builds its sub-context through the same builder and passes its induced alias set (which, unlike view rendering, may or may not contain a connector alias depending on traversal).

## Testing Decisions

- Good tests assert **external, visible behavior**: which nodes and edges appear in the produced PlantUML source for a given set of target views — never the internals of the selection.
- Test at the analytics layer using the demo fixture, alongside the existing `render-view*` tests in `test/armate/mcp/analytics_test.clj`. Prior art: `render-view-produces-plantuml` asserts PlantUML contains element tokens; `mode-certain-renders-certain-derived-edge` asserts a token's presence/absence. Reuse that style.
- Cases:
  - rendering a view that does **not** place the demo Junction ("Шахматы", "Процесс") produces PlantUML **without** `Junction_Or(jc26, ...)`;
  - rendering a view that **does** place it ("Семья") produces `Junction_Or(jc26, ...)` **and** its incident `Rel_Triggering` edges;
  - the `:certain` / `:certain+potential` modes do not reintroduce the Junction into an unplaced view;
  - `merge_views` across a placing view and a non-placing view shows the Junction; across only non-placing views it does not.
- Keep all existing MCP tests passing; no behavior regression for views that legitimately place no Junction.

## Out of Scope

- Any change to how Junctions are parsed, aliased, or stored (`parser.clj`, `builder.clj`, `model.clj`) — the graph representation stays as-is; only selection changes.
- Derivation changes (`derivation.core`) — untouched; derivation never creates Junctions.
- Redesigning the view-membership indexes (`:element-views`/`:relation-views`) — they already expose connector placement; no schema change.
- PlantUML source generation itself (`combiner.clj`) — it already renders what the context carries.

## Further Notes

- This is a correctness defect: the rendered diagram misrepresents the view. It surfaced only because the fixture was extended to exercise a Junction placed in exactly one of several views.
- The same selection rule must be honored wherever a sub-context is built from a graph — `related_elements` included — so a Junction is never an unrequested artifact and never silently loses its edges when it is requested.