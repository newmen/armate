# 02 · Collapse name/alias normalization

**Strength:** Worth exploring · **Dependency category:** in-process
**Depends on:** [01 Model seam](./01-model-seam.md) (so the name module's callers are at a real seam).

## Context

The "title → name → alias" chain for one entity is rebuilt in four modules:

- `archi/title.clj` — `normalize-title`, `remove-wrap-hyphens`, `collapse-slash-spaces`
  (cyrillic hyphen merging, slash spacing).
- `builder.clj` — `alias-title`, `patch-raw-name`, `subsplit`, `title-separate`,
  `title-split-long-*` (alignment/length rules, camel splitting, transliteration hook).
- `archi/parser.clj` — calls `tit/normalize-title`.
- `plantuml/parser.clj` — `strait-string` (collapses `\n`/spaces).

Each duplicates a chunk of the same decision: what a "word" is, when a hyphen is kept, when
a slash keeps spaces, and how an alias is built. A change to hyphen handling today means
touching several files and re-checking each call site.

## Goals (vocabulary)

- **Interface**: one module, small surface: e.g. `normalize-name :: string -> string` and
  `lex-name :: name -> [parts]` plus the alias builder.
- **Depth**: the regexes and edge-case rules concentrate; callers stop hand-rolling regex.
- **Locality**: hyphen/slash/camel/length bugs are fixed once, in one file.
- **Leverage**: the four callers share one implementation; tests cross one interface.

## Steps

1. **Enumerate the rules.** In this file, write the full set of title/name/alias rules in
   use today (Window: hyphen merge; slash spacing; camel split; transliteration; length cap;
   alias patch characters; `strait-string` separation), each tagged with the current home.
   *Verify:* questionnaire above matches current behaviour per module.

   ### Rule enumeration

   **Normalization (title → name).** Home: `archi/title.clj`.
   - **Wrap-hyphen merge.** A hyphen is dropped when the surrounding words are cyrillic,
     each ≥ 2 chars, both end/start with a vowel or both with a consonant, and neither word
     is a listed compound word (`бизнес интернет онлайн евро гос процесс`).
     `remove-wrap-hyphens`, `mergeable-hyphen?`, `mergeable-pair?`, `compound-word-hyphens`.
     Upper-case latin words keep the hyphen (checked in `mergeable-hyphen?` via cyrillic
     guards). Short words (`И-ка`, `а-б`) and non-cyrillic (`API-сервис`, `abc-def`) keep it.
   - **Underscore spacing.** `_ | _` and `_` separated by spaces collapse to `_`
     (`s/replace #"_ | _" "_"`).
   - **Slash spacing.** `collapse-slash-spaces`: a slash with spaces on *both* sides is left
     untouched; spaces after `/` collapse; spaces before `/` collapse except when preceded by
     an all-uppercase latin word (kept as `W /`) — guarding `GET /api/...` style paths.
   - **Composition.** `normalize-title = remove-wrap-hyphens → underscore → slash`.

   **Alias build (name → alias).** Home: `builder.clj`.
   - **Transliteration** hook: `tl/transliterate` (cyrillic → latin per `armate.transliteration`).
   - **Length cap**: `cut-too-long`, `max-alias-length` 28 (hard truncation via `subs`).
   - **Patch characters** (`patch-raw-name`): strip `"`/`'`; `= - ~ : # & % $ + * space ( ) [ ] { } ?`
     → `_`; `. ,` → `__`; `/` → `___`.
   - **Lower-casing** before transliteration. `alias-title = lower-case → cut-too-long →
     transliterate → patch-raw-name`.

   **Splitting / length (display title).** Home: `builder.clj` (`split-title?` false by
   default, so only exercised via `add-connector`).
   - **Camel split** (`title-split-long-camel-part`): split `NamePart` runs into words on a
     leading lowercase first-word.
   - **Length cap on words** (`title-sqrt-length`): floor of `2·√len`; `subsplit`, `title-separate`,
     `title-separate` arities, `title-max-length` 12. Only `subsplit` (used in
     `add-connector` `:title`) and the `max-alias-length` path are live today.

   **Name flattening.** Home: `plantuml/parser.clj`.
   - **`strait-string`**: collapse runs of any whitespace or literal `\n` to a single space
     (`s/replace #"(?s)(\s|\\n)+" " "`).

2. **Design the small interface.** Decide the fns and where the seam sits — most naturally a
   new `armate.archimate.name` (or fold into the model seam's module). Keep the exact
   existing output for each documented input by pinning the current behaviour with a few
   cases first.
   *Verify:* baseline `title_test.clj`, `builder`-adjacent tests, and any `.puml` round-trip
   output are unchanged before refactor.

   ### Interface design

   New module **`armate.archimate.name`** (sibling of the model seam's `armate.archimate.model`
   — the name callers in the intakes/builders already sit at that seam; folding name rules
   into `model` would couple layout logic to the context map, so a separate module is
   cleaner). A re-exporting shim `armate.archimate.archi.title` keeps the public surface of
   the archi title namespace stable.

   Public surface (all pure, string → string / seq):
   - `normalize-name :: string -> string` — cyrillic wrap-hyphen merge + underscore/slash
     spacing (moved from `archi/title.clj`, keeps `normalize-title` name as adapter).
   - `lex-name :: name -> [parts]` — split a (possibly long) name into display lines
     (ported `subsplit`/`title-separate`/`title-split-long-*`).
   - `strait-name :: string -> string` — whitespace/`\n` collapse (ported `strait-string`).
   - `alias-name :: string -> string` — the alias builder (ported `alias-title`).
   - `patch-alias :: string -> string` — raw alias patch (ported `patch-raw-name`).
   - `length-cap :: string -> string` — the 28-char alias length cap (ported `cut-too-long`).
   - String constants `max-alias-length`, `title-max-length`.

   Callers re-pointed: `archi/parser.clj` → `name/normalize-name`;
   `plantuml/parser.clj` → `name/strait-name`; `builder.clj` → `name/alias-name`,
   `name/patch-alias`, `name/lex-name` (thin adapters `alias-title`/`patch-raw-name`/`subsplit`
   remain in `builder`).

3. **Introduce the seam.** Add the `name` module; port one rule at a time (normalize first,
   then alias, then lex/split), each time re-pointing the four callers.
   *Verify:* `lein test` green after each rule port; `/clojure-eval` checks each documented
   rule against the old output.

4. **Delete the duplicates.** Remove `strait-string`, `alias-title`, `patch-raw-name`,
   `title-separate`/`subsplit`/`title-split-long-*` from their old homes so the name rules
   live once. Keep only thin adapters.
   *Verify:* grep shows each of those names defined in the name module only; `lein test` green.

5. **Deletion test.** If the name module were deleted, would the four callers each
   re-introduce pieces? Confirm deleting it concentrates (i.e. the rules come back in one
   place, so it is now deep).
   *Verify:* a single doc test asserts the hyphen/slash/camel/length outputs via the one
   interface.

## Acceptance

- [x] Title → name → alias rules defined once, in the name module.
- [x] `strait-string`, `alias-title`, `patch-raw-name`, `subsplit`, `title-separate`,
      `title-split-long-*` no longer defined in their old namespaces (grep).
- [x] Full `lein test` green; no output-format regressions on the sample `.puml`.

## Out of scope

- Changing any rendered output format; behaviour must be pinned, not altered.
- The general model seam (task 01) except for where the name module's callers sit at it.