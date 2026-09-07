# 03 · Delete the dead aligner, deepen the one that lives

**Strength:** Strong · **Dependency category:** in-process

## Context

The layout behaviour that inserts hidden edges lives in two sibling modules:

- `viz/align/neighbours.clj` — **live**: `append-hidden-aligns` is called by
  `viz/saver.clj`, which `save-puml` (and thus `sync-files`) routes through.
- `viz/align/layers.clj` — **dead**: `append-hidden-aligns` in this file has **zero callers**
  (`grep` finds no `as ... layers` require; only `grid.clj` and `neighbours.clj` require the
  layer helpers). It duplicates the same up/down weight-map and hidden-pair logic under
  config flags (`split-groups?`, `align-layers`, `split-group`).

Both re-implement `calc-hiddens`/`get-hidden-pairs` over `common.clj`'s `add-ud-hidden`,
`get-align-matrix`, and `grid.clj`'s `split-into-layers`. One of them is a shallow twin that
only moves burden.

## Goals (vocabulary)

- **Deletion test**: deleting the dead module must concentrate (its logic is a pass-through
  of `common`/`grid` — it contributes no extra depth), so it should vanish.
- **Depth**: the surviving aligner keeps all hidden-edge behaviour behind one seam.
- **Locality**: hidden-edge rules and weight-map live once, tested once.
- **Leverage**: `saver` (and `sync`) get the aligner through one interface; the flags
  (`groups-as-columns?`, `allow-align-twice?`, `split-groups?`) stop being spread.

## Steps

1. **Confirm dead.** Run `rg "align\\.layers|as ln\\b|align\\.neighbours" src test`; show
   `layers/append-hidden-aligns` is unreferenced.
   *Verify:* `layers` namespace referenced nowhere in `src` or `test`.

2. **Pin the live behaviour.** Add/adjust a couple of tests that assert the hidden-edge
   output of `viz/align/neighbours/append-hidden-aligns` on a small graph (see
   `neighbours_test.clj` conventions).
   *Verify:* those tests pass before any change.

3. **Delete the dead module.** Remove `viz/align/layers.clj` and its test if one exists
   (none in `viz/align/`). Remove any now-unused helper it added to `common`/`grid` if
   nothing else consumes it.
   *Verify:* `rg "align\\.layers"` empty; `lein test` green; `.puml` round-trip output
   unchanged (hidden edges still appear).

4. **Deepen the survivor.** Fold the now-single place that builds hidden edges into one
   seam: `armate.archimate.viz.align` exposing `append-hidden-aligns :: context -> context`
   (and an `align?` toggle for `saver`). Move the config flags under the module so they are
   implementation, not interface.
   *Verify:* `saver.clj` calls the single seam; `lein test` green; flags live in one file.

5. **Deletion test.** If the aligner module were deleted, would hidden-edge rules reappear
   in `saver`/`combiner`? Confirm deletion now concentrates in the one module.
   *Verify:* the new aligner tests would only pass through the one interface.

## Acceptance

- [x] `viz/align/layers.clj` removed; no references anywhere.
- [x] Hidden-edge construction sits behind a single seam consumed by `saver`.
- [x] Layout config flags live in the aligner only.
- [x] Full `lein test` green; `sync-files` output preserved.

## Notes

- `viz/align/layers.clj` deleted: confirmed dead (only self-required; no src/test consumer). Its shared
  helpers in `common`/`grid` remain, all consumed by the live `neighbours.clj`.
- New seam `armate.archimate.viz.align` exposes `append-hidden-aligns :: context -> context` and the
  `align?` toggle; `saver` routes through it. Flags (`align?`, `allow-align-twice?`,
  `groups-as-columns?`, `max-in-row`, `too-many-rels`) all live inside `viz/align/*`.
- `append-hidden-aligns` behaviour pinned directly in `neighbours_test.clj` (new test).

## Out of scope

- Changing the layout algorithm; only its location and single-point-of-truth.
- The model seam (task 01).