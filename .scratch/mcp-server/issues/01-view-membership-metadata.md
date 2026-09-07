# 01: View-membership metadata in the `.archimate` parser

**What to build:** When a model file is parsed, the parser also produces reverse indexes of which views use each element and each relationship, so the MCP server can answer "where is this element/relationship used". Indexes are a separate metadata layer keyed by alias; source elements/relationships are untouched.

**Blocked by:** None (can start immediately).

**Status:** done

- [x] `element-views`: `{element-alias #{view-names}}`
- [x] `relation-views`: `{[from-alias to-alias] {relation-type #{view-names}}}`
- [x] Built during parse of `test/resources/demo.archimate`; unit tests assert the indexes match the views' `child`/`sourceConnection` content.

**Resolved decisions:**
- `build-view-indexes` is **pure** (single threaded `reduce`, no atoms); it takes the `:archi-alias` id→element map as an argument.
- `enrich-with-graph` runs `get-full-graph` **exactly once** per load and derives both `:archi-alias` and the indexes from that one graph, returning `[enriched graph]`. The same graph feeds the model context (no rebuild).
- Aliases in the indexes are exactly the model-global aliases the tools and `render_view` emit.