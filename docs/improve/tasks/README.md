# armate — Architecture improvement tasks

Step-by-step task plans derived from the architecture review
(`/var/folders/8x/s9n3hjhs4v54lc81d89cdfn40000gn/T/kilo/architecture-review-1724317200.html`).
Each file names the deepening in the review's vocabulary: **module**, **interface**, **depth**,
**seam**, **adapter**, **leverage**, **locality**.

## Sequencing

| # | Task | Strength | Rationale for order |
|---|------|----------|---------------------|
| 01 | [Model seam](./01-model-seam.md) | Strong | **DONE** — `armate.archimate.model` seam introduced (readers + single `upsert-slot` writer); all renderers, collector, derivation and both intakes cross it. `:misc` cache + counters behind the seam; `:misc :archi` sign-posted to 04. See the task file's step notes. |
| 02 | [Collapse name normalization](./02-collapse-name-normalization.md) | Worth exploring | **DONE** — new `armate.archimate.name` module concentrates title→name→alias rules (normalization, alias build, length/camel lex, `strait-name`); archi/.puml intakes and builder callers re-pointed; old-name defs deleted (`archi/title.clj` left as a thin adapter); deletion test in `name_test.clj`. Barely changed 01's seam: only name-method callers were touched. |
| 03 | [Delete dead aligner + deepen the live one](./03-delete-dead-aligner.md) | Strong | **DONE** — `viz/align/layers.clj` (dead twin) deleted; hidden-edge construction behind one seam `armate.archimate.viz.align`, consumed by `saver`; layout flags moved under the aligner. No layout-algorithm change; touches only `viz/align/*` + `saver`. |
| 04 | [Hoist the archi `id` atom](./04-hoist-archi-id-atom.md) | Worth exploring | Restores reentrancy/determinism of the intake; independent of the model seam but needed before parallel/parallel work. Also takes over `:misc :archi` (the id→alias map sign-posted by 01). |
| 05 | [One ranking seam](./05-one-ranking-seam.md) | Worth exploring | Sorting lives in one module; best done after 01 so `layer-order`/weights are read off the model seam. |
| 06 | [Split `.puml` lexer vs semantics](./06-split-puml-lexer-semantics.md) | Speculative | Largest change; keep last, after the model seam makes each stage's out-put a map the seam already owns. |

## How to work a task

1. Read the task file top to bottom before touching code.
2. Follow each step in order; a step is **done** only when its verification passes
   (`lein test` and `/clojure-eval` where the file calls for it).
3. Never skip the deletion-test step: a step that only "moves" behaviour without
   concentrating it is a signal the deepening isn't complete.
4. Update this README and `docs/improve/tasks/<n>.md` acceptance boxes as you complete steps.