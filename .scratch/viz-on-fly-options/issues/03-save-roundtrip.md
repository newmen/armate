# 03: Confirm save behavior under the now-default-enabled grouping

**What to build:** the save-to-file path (`save-puml`, single-argument call to the generator) keeps producing correct PlantUML after grouping became enabled by default and the constants were removed. It is decided how the saved output is grouped, and that is pinned down: either the default grouping applies to save, or `save-puml` explicitly passes an option disabling nesting, to preserve the previous flat output.

**Blocked by:** 01

**Status:** done

- [x] Explicitly pinned down which grouping behavior `save-puml` should have (default or an explicitly passed `:group-modes {}` option), and implemented it. Resolution: `save-puml` passes an explicit `{:group-modes {}}` so the saved output stays flat, backward-compatible with the pre-ticket-01 rendering.
- [x] Removing `escape-derivated` does not "leak" derived edges into the save path: save output contains no derived relationships.
- [x] Regression checks: the saved .puml is valid, contains the expected nesting (or flat output per the decision), with no regressions against the previous output.