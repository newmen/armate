# 05 · One ranking seam across the metamodel

**Strength:** Worth exploring · **Dependency category:** in-process
**Depends on:** [01 Model seam](./01-model-seam.md) (so ranking reads off the model seam).

## Context

Ordering information is re-declared in several modules today:

- `viz/combiner.clj` — `layer-order` (motivation…implementation), `default-layer-rank`,
  `relation-sort-key` (uses weight then alias).
- `viz/align/neighbours.clj` — `element-kinds-order`, `kind-weights` (a different kind→weight
  mapping for layout).
- `metamodel/derivation/match.clj` — `get-rel-wieght` (dynamic/dependency/structural →
  weight for rule ordering).

You cannot change "what sorts a relation" in one place; each module re-encodes the decision.
The weight numbers also encode *strength* assumptions that the derivation rules rely on
(`match.clj`), so a drift between the combiner and the deriver silently changes output ordering.

## Goals (vocabulary)

- **Interface**: one module exposing `element-rank :: element -> comparable` and
  `relation-weight :: rel -> comparable` (plus the layer/kind-order tables as data).
- **Depth**: all sorting/ranking decisions live behind one small surface; callers stop
  hand-encoding order.
- **Locality**: "what sorts" is fixed once; the two adapters rule applies — `derive` and
  `render` are the two seats the same weights serve.
- **Leverage**: one implementation paid across four call sites and their tests.

## Steps

1. **Inventory the orderings.** Record each current ranking table and comparator
   (`layer-order`, `element-kinds-order`/`kind-weights`, `get-rel-wieght`, `relation-sort-key`)
   with its home and its current exact values.
   *Verify:* table documented in this file; matches source (`/clojure-eval` `(deref #'...)`).

   | Table / comparator | Home (before 05) | Exact values (verified via `/clojure-eval`) |
   |---|---|---|
   | `layer-order` | `viz/combiner.clj` | `{:motivation 0, :strategy 1, :business 2, :application 3, :technology 4, :implementation 5}` |
   | `default-layer-rank` | `viz/combiner.clj` | `6` |
   | `element-layer-rank` | `viz/combiner.clj` | `(layer-order (:layer element) default-layer-rank)` |
   | `element-kinds-order` | `viz/align/neighbours.clj` | `[:business-actor :business-role :business-interaction :business-product :business-service :business-event :business-function :business-process :business-collaboration :application-service :application-data-object :application-interface :technology-system-software :application-component :application-collaboration :technology-artifact :technology-node :technology-collaboration :technology-path :technology-interaction]` |
   | `weight-step` | `viz/align/neighbours.clj` | `5` |
   | `kind-weights` | `viz/align/neighbours.clj` | `{:business-actor 1, :business-role 6, :business-interaction 11, :business-product 16, :business-service 21, :business-event 26, :business-function 31, :business-process 36, :business-collaboration 41, :application-service 46, :application-data-object 51, :application-interface 56, :technology-system-software 61, :application-component 66, :application-collaboration 71, :technology-artifact 76, :technology-node 81, :technology-collaboration 86, :technology-path 91, :technology-interaction 96}` |
   | `get-rel-wieght` | `metamodel/derivation/match.clj` | `triggering 1, flow 2; association 100, association_dir 200, influence 300, access 400, access_r 500, access_w 600, access_rw 700, serving 800; realization 1000, assignment 2000, aggregation 3000, composition 4000; specialization 10000` |
   | `relation-sort-key` | `viz/combiner.clj` | Compose `[:line weight from to]` — caller-specific ordering policy, kept. |

   The new seam `armate.archimate.metamodel.rank` yields identical values (`/clojure-eval`
   confirmed `layer-order-same`, `default-rank-same`, `element-rank-same`, `kinds-same`,
   `kind-weights-same`, `rel-weights-same` all `true`; `relation-weight` ≡ old `get-rel-wieght`).

2. **Pin the behaviour.** Add tests asserting current sort results for:
   - element ordering by layer-then-alias (`combiner/sort-elements` semantics),
   - relation ordering by weight-then-from/to (`combiner/relation-sort-key` semantics),
   - derivation rule ordering (`match/get-rel-wieght` semantics).
   *Verify:* tests green against current code; they are red-proof pin of "what sorts".

   Pins: `element-layer-ordering-test`, `relation-category-ordering-test`,
   `relation-endpoint-lexicographic-test` (combiner); `rank-test` element/kind/rel-weight pins;
   `relation-weight-test` (re-pointed from `get-rel-wieght-test`). Green before and after.

3. **Introduce the seam.** Create `armate.archimate.metamodel.rank` exposing
   `element-rank` and `relation-weight`; port the tables there verbatim.
   *Verify:* `/clojure-eval` shows the new module yields identical values to the old homes.
   Confirmed — see step 1 verification line.

4. **Re-point callers.** Make `combiner`, `neighbours`, and `derivation/match` use the seam;
   delete the local tables. Keep only the sort-key *composition* (e.g. `relation-sort-key`)
   at each caller, since that's caller-specific ordering policy, not ranking data.
   *Verify:* `lein test` green; result `.puml` ordering unchanged on the sample.
   `lein test` green (104 tests / 657 assertions); `element-layer-ordering` and
   `relation-category` pins still pass, so output ordering unchanged.

5. **Deletion test.** Deleting the ranking seam would force each caller to re-declare both
   tables to keep output and derivation aligned. Confirm deletion now concentrates.
   *Verify:* a single table-driven test passes via the seam only, proving both adapters
   (derive + render) read the same weights.
   `rank-test/derive-and-render-agree-on-weights-test` orders the same relation set by
   `rank/relation-weight` (render) and via `make-rules-map` (derive) — both
   `[:composition :assignment :serving :flow]` from one table.

## Acceptance

- [x] Layer/kind/rel-weight tables defined once, in the ranking module.
- [x] `layer-order`, `kind-weights`, `get-rel-wieght` not re-declared at call sites (grep).
- [x] Both deriving and rendering observe the same weights (one table).
- [x] Full `lein test` green; output ordering unchanged.

## Out of scope

- Changing any numeric weight; only their single place of residence.
- The model seam (task 01) plumbing.