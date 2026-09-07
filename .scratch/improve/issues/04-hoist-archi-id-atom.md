# 04 · Hoist the archi `id` atom

**Strength:** Worth exploring · **Dependency category:** in-process
**Depends on:** none strictly; unblocks parallel/parallel intake-agnostic work alongside [01](./01-model-seam.md).

## Context

`archi/parser.clj` keeps two global atoms for the id → alias mapping:

```clojure
(defonce idx-value (atom 0))
(defonce idx-map (atom {}))
```

`get-idx` (`add-element`, and transitively `get-full-graph`) reads/writes both. Consequences:

- The model a given `.archimate` file produces depends on **call history**, not on its input:
  two identical files parsed in different orders yield different alias numbers.
- The intake is **not reentrant**: the same file parsed twice gives different aliases.
- Tests share global state across cases, so ordering a test run can change assertions.

This is the purest "interface nearly as complex as the implementation" leak: the alias logic
is trivially pure given an id map passed in, but a hidden global makes the seam dishonest.

## Goals (vocabulary)

- **Interface**: the intake accepts an id → alias map and returns the next one, like the
  model already threads `:elements`/`:relations` through `add-element`.
- **Depth**: the alias rule (`get-element-alias`, `only-int?`, cache) stays behind the seam;
  only the outer entry points gain a parameter.
- **Adapter**: the atoms become an *adapter* kept outside the seam for callers that still
  want a persistent map across a session; they no longer live inside the algorithm.
- **Testability**: parsing becomes a pure function; tests stop depending on run order.

## Steps

1. **Enumerate alias entry points.** List every public fn that transitively calls `get-idx`
   (e.g. `add-element`, `add-elements`, `add-relations`, `get-full-graph`, `get-views-graph`)
   and their current callers (`sync-files`, tests).
   *Verify:* inventory in this file matches `rg get-idx|get-full-graph|get-views-graph`.

   **Inventory (from `rg`, matches source):**
   - `get-idx` — called only by `add-element`.
   - `add-element` — called only by `add-elements`.
   - `add-elements` — called only by `get-full-graph`.
   - `get-full-graph` — called only by `get-views-graph`. No external callers.
   - `get-views-graph` — called only by `viz/sync/sync-files`.
   - `add-relations`, `get-model` — do **not** allocate indices (relations read aliases back
     via `[:misc :archi <archi-id>]`, allocated in `add-elements`).
   - No existing tests exercise the archi parser entry points (the parser test suite targets the
     separate plantuml parser).

2. **Pin current determinism (or its absence).** Write a test that parses the same file
   twice in one JVM run and asserts alias stability. Record the observed behaviour as the
   baseline (today it may be stable *only within* one run, and **not** across runs / call order).
   *Verify:* baseline recorded with a failing/known assertion so the change is measurable.

   **Baseline (pre-change):** aliases came from module-global `idx-value`/`idx-map`. Two separate
   calls to `get-full-graph` on the same model advanced the global counter, so the *second* call
   produced different aliases than the first. Same-input → different-output across calls.
   Post-change semantics: each fresh (empty) id-map seeds indexing at 1, so two calls on the same
   model with fresh seeds yield identical aliases.

3. **Thread the id map as a parameter.** Give the intake fns an optional id-map arg that
   defaults to a fresh (immutable) seed; `get-idx` becomes `get-idx :: id-map, id -> [alias id-map]`
   and the reduce in `add-elements` threads it.
   *Verify:* same-input-same-output now holds across separate calls; `/clojure-eval` checks
   two identical parses yield identical models.

4. **Move the atoms to an adapter.** Remove `defonce idx-value/idx-map` from
   `archi/parser.clj`; expose a thin holder (e.g. `make-idx` / an atom-wrapping helper) **in
   the caller's namespace or a sync-level adapter**, so persistence is opt-in.
   *Verify:* `rg "idx-value|idx-map"` in `archi/parser.clj` empty; full `lein test` green.

5. **Determinism test.** Upgrade the baseline to assert cross-run stability; `get-views-graph`
   over N views becomes trivially paralleliseable (map, not doseq) with the same id map.
   *Verify:* determinism test passes; parallel view build produces identical models to serial.

## Acceptance

- [x] No global alias state inside `archi/parser.clj`.
- [x] Parsing is a pure function of (content, id-map) → model, id-map.
- [x] Same file, parsed twice → identical model (test).
- [x] Full `lein test` green; `sync-files` output unchanged.

## Out of scope

- The general model seam (task 01); this only restores reentrancy at the intake.
- Any change to alias *format* — only who holds the counter changes.