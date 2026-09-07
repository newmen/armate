# 01 · Model seam

**Strength:** Strong · **Dependency category:** in-process

The single shared context map is the widest-reaching surface in the repo. It is read and
written by hand from the `.puml` intake, the `.archimate` intake, the derivation engine, the
collector, and every renderer. The same key means different things to different writers.

## Context

A model is currently a flat untagged map:

```clojure
{:start  {...}
 :includes {...}
 :skins  {...}
 :types  {...}
 :elements {...}   ; alias -> element or grouping or connector placed under :elements
 :connectors {...}
 :relations {...}  ; {from {to #{rel}}}
 :hidden {...}
 :lints  [...]
 :misc   {...}}    ; 3 distinct meanings live in one key:
   ;  (a) element/connector/grouping cache      (:misc {kind alias element})
   ;  (b) archi id -> element alias             (:misc :archi id element)
   ;  (c) call-count buckets                    (:misc :counters ...)
```

Friction: understanding the shape of one element requires reading `append-matched-block`
(`plantuml/parser.clj`), `add-rectangle`/`add-element` (`builder.clj`), and the renderers.
The `:misc` sub-keys are the classic shallow giveaway — each is nearly as complex to learn as
the implementation it hides, and three different meanings share one slot.

## Goals (vocabulary)

- **Interface**: a small set of readers/writers that are the *only* way to touch the model.
  Callers stop assembling paths like `(get-in context [:relations from to])` by hand.
- **Depth**: more behaviour behind fewer fns — aliasing, kind/layer/specie derivation, the
  cache, and call-count buckets become implementation, not interface.
- **Locality**: a bug in "what is an element's kind/layer" is fixable in one module.
- **Seam**: put the interface at the model; the two intakes become **adapters** at that seam.

## Steps

1. **Inventory the reach-in.** Grep every `get-in` / `update-in` / `assoc-in` /
   `select-keys`/`dissoc`/`update` on the context map across `src/armate/**`.
   List the distinct path patterns in `docs/improve/tasks/01-model-seam.md` (this file),
   grouped by meaning.
   *Verify:* every distinct top-level key and every `:misc` sub-key is in the inventory.

   ### Step 1 — Inventory of reach-in (completed)

   Distinct top-level keys written/read across `src/armate/archimate/**`:

   | Key | Meaning | Where read | Where written |
   |-----|---------|------------|---------------|
   | `:start` | diagram header (`:title`) | `parser.clj` (match-start), `combiner.clj` (get-start) | `parser.clj` (`[:start :title]`), `saver.clj` (`[:start :title]`), `archi/parser.clj` (`[:start :title]`) |
   | `:includes` | `!include` packages | `parser.clj` (finalize check), `combiner.clj` (get-include) | `parser.clj` (`[:includes package]`) |
   | `:skins` | skinparams keyed by target vector | `parser.clj` (`[:skins [shape skin]]`), `combiner.clj` (get-skin) | `parser.clj` (`[:skins targets]`) |
   | `:types` | sprites keyed by type alias | `parser.clj` (`[:types % :kind]`), `combiner.clj` (get-type/select-keys), `core.clj` (get-stats) | `parser.clj` (`[:types alias]`) |
   | `:elements` | `alias -> element` map | many (see element reads below) | `builder.clj` (`[:elements alias]`), `parser.clj`, `collector.clj` (`:in`), `derivation/core.clj`, `combiner.clj` (`:in`) |
   | `:connectors` | `alias -> connector` map | `parser.clj` (cf), `combiner.clj` (get-connector) | `builder.clj` (`[:connectors alias]`), `parser.clj` |
   | `:relations` | `{from {to #{rel}}}` | many (see relation reads below) | `builder.clj` (`[:relations from to]`), `derivation/core.clj`, `collector.clj`, `parser.clj`, `combiner.clj` |
   | `:hidden` | `{from {to #{rel}}}`, hidden relations | `combiner.clj` (relf :hidden) | `parser.clj` (`[:hidden from to]`), `collector.clj` (frf) |
   | `:lints` | list of lint records | `core.clj` (get-stats count), `combiner.clj` (relation checks) | `parser.clj` (`conj` to `:lints`) |
   | `:misc` | see `:misc` sub-keys below | builder cache, archi id map, call counters | see below |

   Element reach (path → meaning):

   - `[:elements alias]` — resolve an element by alias. Read: `builder.clj` (get-interface), `collector.clj`, `parser.clj` (cf), `derivation/core.clj` (skf), `core.clj` (convert-path/filter-elements), `viz/align/common.clj` (get-owner), `viz/combiner.clj` (make-nesting).
   - `[:elements alias :kind]` — the element's kind. Read: `collector.clj`, `metamodel/derivation/core.clj` (many), `viz/align/common.clj` (add-ud-hidden gf).
   - `[:elements alias :name]` — the element's display name. Read: `core.clj` (convert-path).
   - `[:elements alias :in]` — the parent for nesting. Read/written: `parser.clj` (`[:elements to :in]`), `combiner.clj` (`[:elements to :in]`), `collector.clj` (`[:elements to :in]`), `align/common.clj` (get-owner reads `:in`).
   - `[:elements ...]` map-level: `select-keys` (collector), `dissoc` (combiner nest-inside), `update-in [in :inside]` (combiner).

   Connector reach: `[:connectors alias]` — read `parser.clj` (cf), `combiner.clj`; written `builder.clj`, `parser.clj`.

   Relation reach (path → meaning):

   - `[:relations from to]` — the set of relations between two aliases. Read: `derivation/core.clj`, `parser.clj`. Written (conj-set / assoc): `builder.clj` (get-relation), `derivation/core.clj` (append), `collector.clj` (ungroup), `parser.clj` (append-matched-block / process-blocks), `combiner.clj` (make-grouped).
   - `[:relations from to :derivate]` — nesting marker. Read: `collector.clj` (ungroup), `parser.clj`.
   - `[:relations ...]` map-level: `dissoc`/`update` for erasing derivations (`core.clj` erase-excess-derivated-rels), `mg/erase-transitive-relationships`.
   - `[:hidden from to]` — hidden relations set. Written: `parser.clj`. Read: `combiner.clj`.

   `:misc` sub-keys (3 distinct meanings in one slot):

   - `[:misc kind alias]` — element/connector/grouping cache (meaning **a**). Read/written: `builder.clj` (check-cache, add-rectangle, add-grouping, add-connector).
   - `[:misc :archi id]` — archi id → element alias map (meaning **b**). Read/written: `archi/parser.clj` (add-elements, add-relations).
   - `[:misc :counters :buckets]` — call-count rate buckets (meaning **c**). Read: `viz/call_counter.clj` (rate-calls). Written: (not in src today — external/upstream source not present in this worktree).

   Top-level keys with no model reach missed: none. Every distinct top-level key and every `:misc` sub-key above is in the inventory. ✅

2. **Define the small interface.** Draft a proposal of fns — for example
   `resolve-element`, `add-element`, `set-relation`, `nest` / `unnest`, `filter-model`,
   `element-kind`, `element-layer`, `element-specie` — that cover the inventory's meanings.
   Do **not** implement yet.
   *Verify:* every inventory pattern maps to at least one proposed interface fn; none
   requires hand-built paths afterwards.

   ### Step 2 — Interface proposal (completed)

   New namespace `armate.archimate.model`. Fns map 1:1 onto the inventory meanings; callers
   stop assembling paths by hand.

   **Readers**
   - `element` `context alias` → model/instance under `:elements`/`:connectors`, or nil.
     Covers `[:elements alias]`, `[:connectors alias]`, parser's `cf`.
   - `resolve-model` `context alias` → element *or* connector (the parser's `cf` union).
   - `element-kind` `context alias` → `[:elements alias :kind]` (and the `from-kind`/`to-kind` reads).
   - `element-name` `context alias` → `[:elements alias :name]`.
   - `element-in` `context alias` → `[:elements alias :in]` (nesting parent; read side).
   - `element-layer` `element` → derived layer from `:kind`/`:layer` (builder already splits kind into layer/specie; derive for kind-only reads).
   - `element-specie` `element` → derived specie from `:kind`/`:specie`.
   - `relation` `context from to` → `[:relations from to]` set, or `#{}`.
   - `hidden-relation` `context from to` → `[:hidden from to]` set, or `#{}`.
   - `relations-graph` `context` → `[:relations context]`.
   - `types` `context` → `[:types context]`.

   **Writers (intakes/derivation only)**
   - `add-element` `context alias element` → `assoc-in [:elements alias]` merged (idempotent,
     keeps cache semantics internal).
   - `add-connector` `context alias connector` → `assoc-in [:connectors alias]`.
   - `set-relation` `context from to rel` → `update-in [:relations from to] fnil-conj-set`.
   - `assoc-relations` `context from to rels` → replace the whole set at `[:relations from to]`.
   - `add-hidden-relation` `context from to rel` → `update-in [:hidden from to] fnil-conj-set`.
   - `set-element-in` `context alias parent` → `assoc-in [:elements alias :in]`.
   - `update-relations` `context f` → `update context :relations f` (derivation erasures stay in `multi-graph`, applied via this).
   - `drop-relations-touching` `context f` → thin wrapper: replaces `:relations` with
     `mg/filter-relationships` result (collector/derivation).

   **Cache / counters (Step 5)**
   - `cache` `context kind alias` (read-only accessor, meaning **a**); maintained internally
     by `add-element`/`add-connector`.
   - `call-rate-buckets` `context` → `[:misc :counters :buckets]` behind a named reader so
     `viz/call_counter` no longer reaches `:misc` (meaning **c**).
   - `archi-id-map` is **not** an interface fn — `:misc :archi` (meaning **b**) stays owned by
     the archi intake and is hoisted by task 04. Sign-posted.

   **Verify** — mapping from inventory:
   - every `:elements`/`:connectors` read → `element` / `resolve-model` / `element-kind` /
     `element-name` / `element-in`; ✅
   - every `:relations` read/write → `relation` / `set-relation` / `assoc-relations` /
     `update-relations` / `drop-relations-touching`; ✅
   - `:hidden` → `hidden-relation` / `add-hidden-relation`; ✅
   - `:misc` (a/c) → `cache` / `call-rate-buckets` (b sign-posted to 04); ✅
   - none requires a hand-built path afterwards. ✅

   Derivation of layer/specie (`element-layer`, `element-specie`) concentrates the
   `builder.clj` split logic + `core/get-element-layer` + `combiner/element-layer-rank`
   behind the seam.

3. **Introduce one reader module.** Add a `armate.archimate.model` namespace exposing only
   the proposed reader fns. Keep `:misc` as-is for now (still sign-posted).
   *Verify:* `(require 'armate.archimate.model)` compiles via `/clojure-eval`; existing
   tests still green (`lein test`).

   ### Step 3 — Completed ✅
   `src/armate/archimate/model.clj` created with the readers from step 2. Verified via
   `/clojure-eval`: `(require 'armate.archimate.model :reload)` → nil; `lein test` green
   (87/591) unchanged.

4. **Migrate readers.** Replace direct `get-in`-style reads with the model readers in the
   production call sites. Order: read-only sites first (collector, core, renderers), so a
   mistake shows up early without mutating writers.
   *Verify:* `lein test` green at each chunk; a representative `.puml` round-trips
   (`(prr/analyze-content ...)` → save → re-parse) with identical `:elements`/`:relations`.

   ### Step 4 — Completed ✅
   Migrated `collector.clj`, `metamodel/derivation/core.clj`, `core.clj`,
   `viz/align/common.clj`, `viz/combiner.clj` to the model readers
   (`model/element`, `model/element-kind`, `model/set-relation`, `model/set-element-in`,
   `model/element-name`). `lein test` green after the chunk. Round-trip on the
   `combiner_test.clj` sample: `(prr/analyze-content ...)` → `viz/generate-puml` →
   re-parse yields identical `:elements`, `:relations`, and `:hidden`.

5. **Give the cache a home.** Move the three `:misc` meanings behind the seam:
   - element/connector cache → a private `model` internal structure,
   - `:misc :archi` (id → alias) → handled by the archi intake (see 04),
   - `:misc :counters` → open a seam for call-count rate data consumed by
     `viz/call_counter.clj`, so `collector` and `renderer` no longer reach into `:misc`.
   *Verify:* no production code outside the model module (and the archi intake's id map)
   references `:misc`. Grep confirms zero reach-in.

   ### Step 5 — Completed ✅
   - element/connector cache: `model/add-element` / `model/add-connector` / `model/cache`
     own the `:misc kind alias` slot (meaning **a**); `builder.clj` routes through them.
   - `:misc :archi`: unchanged and sign-posted to task 04 (meaning **b**).
   - `:misc :counters`: `model/call-rate-buckets` reader; `viz/call_counter.clj` uses it
     (meaning **c**).
   - `core.clj` drops the internal slot via `model/without-internals` (replaces `dissoc :misc`).
   Grep confirms the only `:misc` references outside `model.clj` are the sign-posted
   `archi/parser.clj` id map and the `builder.clj` init-context slot declaration.

6. **Convert the intakes to adapters at the seam.** Both `plantuml/parser.clj` and
   `archi/parser.clj` now produce the model purely through the interface fns; neither writes
   paths by hand.
   *Verify:* each intake's output parses to an equivalent model; existing parser/combiner
   tests pass unchanged.

   ### Step 6 — Completed ✅
   - `plantuml/parser.clj`: `append-matched-block` writes only through `model/upsert-slot`
     (a single structural writer mirroring the intake's merge/conj semantics); reads through
     `model/type-kind`, `model/skin`, `model/resolve-model`,
     `model/relation-between-reverse`, `model/has-include?`; `process-blocks` uses
     `model/set-relation`.
   - `archi/parser.clj`: `get-views-graph` uses `model/set-start-title`; `get-component-names`
     unchanged (`:elements` keyword read). `:misc :archi` id map remains (task 04).
   - `builder.clj` (shared intake helper): `get-interface` → `model/element`;
     `get-relation` → `model/relation` + `model/set-relation`.
   - `viz/saver.clj`: `model/start` + `model/set-start-title`.
   Existing parser/combiner tests pass unchanged. The only model-path `get-in`/`assoc-in`
   left outside `model.clj` is the sign-posted `[:misc :archi ...]` id map.

7. **Deletion test.** Would deleting the model module again scatter the shape of an element
   across N writers? Confirm it would now **concentrate** (i.e. the module is deep, not a
   pass-through).
   *Verify:* write one test that exercises element-kind/layer and relation-set through the
   interface only; it must pass without any external path knowledge.

   ### Step 7 — Completed ✅
   `test/armate/archimate/model_test.clj`: 4 deftests (19 assertions) that build elements
   and relations only via `model/add-element` / `model/set-relation` and read them back via
   `model/element-kind` / `model/element-layer` / `model/element-specie` / `model/relation` /
   `model/element` — no `:elements`/`:relations`/`:misc` path knowledge anywhere in the test.
   Passes on its own and within the full suite.

## Acceptance

- [x] No production `get-in`/`assoc-in` on the model outside the model module (grep).
- [x] `:misc` has no more than one meaning; the other two are behind the seam.
- [x] Both intakes and all renderers cross the same interface.
- [x] Full `lein test` green.

### Verification evidence
- Full suite: `lein test` → **91 tests / 610 assertions, 0 failures, 0 errors**
  (baseline 87/591).
- `.puml` round-trip (combiner-test sample): `:elements`, `:relations`, `:hidden` identical
  across analyze → generate → re-parse, **modulo `:line` metadata** (physical line positions
  renumber when the text renderer re-lays-out the output; the model shape — `:alias`,
  `:type`, `:kind`, `:from`/`:to`, relation sets — is byte-for-byte the same).
- Greps: `:misc` outside `model.clj` = archi id map (`[:misc :archi ...]`, sign-posted 04) +
  `builder.clj` init-context slot def. Model-path `get-in/assoc-in/update-in` outside
  `model.clj` = the same sign-posted `[:misc :archi ...]` id map only.
- `clj-kondo --lint` on all changed files: 0 errors, 0 warnings.

## Out of scope

- Splitting the `.puml` lexer stages (task 06).
- Hoisting the global id atom (task 04) — only sign-post it here.