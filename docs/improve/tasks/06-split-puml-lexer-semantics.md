# 06 · Split the `.puml` lexer from semantics

**Strength:** Speculative · **Dependency category:** in-process
**Depends on:** [01 Model seam](./01-model-seam.md) (so each stage's output is a map the seam owns).

## Context

`plantuml/parser.clj` (713 lines) interleaves three concerns in one file:

- **Lexing** → `quoted-brackets-split`, `get-parts`, `variable?`/`parse-variable`,
  `mask-specials`, `mask-variable`, `apply-variables` — turning a text line into `:parts`
  and block flags.
- **Structure** → `get-blocks` — the recursive block/paren/brace balancer that also folds in
  `left-to-right?` removal and variable application.
- **Semantics** → `match-block`, `append-matched-block`, `append-block`, `process-blocks`,
  `finalize` — turning `:parts` into the model (`:elements`/`:relations`/`:lints`), plus the
  connector-derivation via `combo/cartesian-product`.

A change to "how a bond is spelled" (`match-pos-bond`/`b-matches`/`match-rel`) touches the
same functions as a change to "how `{` nesting is balanced". The whole parse path has one
test surface (`parser_test.clj`, `combiner_test.clj`), so a small lexing change forces a
file-wide test run to see its effect.

## Goals (vocabulary)

- **Interface**: three small seams instead of one wide file — `lex :: line -> parts`,
  `structure :: [parts] -> blocks`, `semantics :: blocks -> model`. Each testable in
  isolation through a tiny surface.
- **Depth**: the bond/pictograph table (`match-pos-bond`) becomes a single seam; block
  balancing lives separately.
- **Locality**: a lexing bug and a semantics bug stop sharing one function body.
- **Leverage**: connector-derivation (and its `cartesian-product`) stays behind semantics
  where it belongs, not mixed with reading tokens.

## Steps

1. **Draw the stage boundary.** List, per stage, exactly which current fns belong to it
   (see Context above); record the data carried between stages: `:parts`+`:block?`/`:line`.
   *Verify:* inventory covers every public fn in the file; boundary items don't overlap.

   **Stage inventory (as built):**

   - **Lexing** — `armate.archimate.plantuml.lex`
     - Token regexes: `call-re`, `full-line-re`, `quoted-split-re`
     - `quoted-brackets-split`, `get-parts`
     - `variable?` / `parse-variable`, `mask-specials`, `mask-variable`, `apply-variables`
     - String helpers: `wrapped?`, `fur?`/`fur-re`/`cut-furs`, `quoted?`/`cut-quotes`, `cut1`, `cut2`
     - Stage contract: `line -> {:parts [..] :block? bool}`; variables `{name value} -> line`
   - **Structure** — `armate.archimate.plantuml.structure`
     - `get-blocks`, `left-to-right?`
     - Stage contract: `content -> [blocks]`, each block `{:parts [..] :line n}`, nested as `:props`
   - **Semantics** — `armate.archimate.plantuml.parser` (thin)
     - `match-*` (`match-block`, `match-start?/end?/include?/skinparam?/type?/rectangle?/element?/group?/connector?`, `match-rel`, `match-rel-b`, `match-f?`)
     - `append-*` (`append-block`, `append-matched-block`)
     - Bond/pictograph table: `match-pos-bond`, `pin-re`, `rel-f-re`, `rel-b-res`, `b-matches`
     - Model building: `process-blocks`, `finalize`, `analyze`, `analyze-content`
     - `get-*` block builders (`get-rectangle-block`, `get-element-block`, `get-group-block`, `get-connector-block`, `get-skinparam-target`, `get-layer-kind`, `get-type-block`, `get-type-kind`), `hidden-rel?`, `color?`, `get-connectors-diff-rels-info`
   - **Data carried between stages:** lexer emits `:parts` + `:block?`; structure emits
     `{:parts .. :line ..}` blocks (nested as `:props`); semantics consumes `:parts`/`:line`.

2. **Pin round-trip output.** Ensure the full analyze → render → re-analyze loop on the
   sample `.puml` is covered so any stage split cannot silently change semantics.
   *Verify:* existing `parser_test.clj`/`combiner_test.clj` are green as a baseline.

   **DONE** — baseline `lein test` green before edit; after the split the combiner
   `generate-puml-test` still asserts `(= puml (viz/generate-puml (prr/analyze-content puml)))`
   and the `/clojure-eval` round-trip returned byte-identical `true`.

3. **Extract the lexer.** New namespace `armate.archimate.plantuml.lex` exposing
   `parse-variable`, `get-parts`, `apply-variables` (and the regex helpers) — no nested
   structure, no model. Keep `quoted-brackets-split` only here.
   *Verify:* `lein test` green; `/clojure-eval` splits each sample line to the same `:parts`
   as before.

   **DONE** — `armate.archimate.plantuml.lex` owns all token/string helpers listed in step 1;
   full `lein test` green; `lex_test.clj` pins `:parts` per line.

4. **Extract the structure.** Namespace `armate.archimate.plantuml.structure` exposing
   `get-blocks` (tokens → blocks) with variable handling moved in; no interaction with
   `:elements`/`:relations`.
   *Verify:* `get-blocks` yields identical block lists on samples; `lein test` green.

   **DONE** — `armate.archimate.plantuml.structure` owns `get-blocks`/`left-to-right?`,
   pulling `lex/parse-variable` + `lex/apply-variables` + `lex/get-parts`; full `lein test`
   green.

5. **Slice the semantics.** Keep `match-*`, `append-*`, `process-blocks`, `finalize`,
   relation/connector table in `plantuml/parser.clj`, now consuming the lexer+structure
   outputs instead of rebuilding tokens. The bond/pictograph table (`match-pos-bond`) is its
   own seam.
   *Verify:* `parser.clj` line count drops meaningfully; `analyze-content` output is
   byte-identical on samples; `lein test` green.

   **DONE** — `parser.clj` consumes `strc/get-blocks` and `lex/*`; line count 705 → 532
   (−173 lines); `analyze-content` byte-identical via `combiner_test` + `/clojure-eval`;
   `lein test` green.

6. **Deletion test.** Deleting the lexer would force semantics to re-implement regex
   splitting. Confirm deletion now concentrates lexing in one module.
   *Verify:* a lex-only test asserts `:parts` for a scripted line without touching the model.

   **DONE** — `test/armate/archimate/plantuml/lex_test.clj` asserts `:parts` for scripted
   lines against `armate.archimate.plantuml.lex` only (no model import).

## Acceptance

- [x] Lexing, structure, semantics live in distinct namespaces with distinct interfaces.
- [x] `parser.clj` no longer owns regex-token entry points (`get-parts`/`parse-variable` live only in `lex.clj`).
- [x] Bond/pictograph table is a single seam (`match-pos-bond` + `b-matches`/`match-rel-b`/`pin-re`).
- [x] Full `lein test` green; `.puml` round-trip unchanged.

## Out of scope

- Any new `.puml` syntax support.
- The model seam (task 01), the ranking seam (task 05) — referenced only.