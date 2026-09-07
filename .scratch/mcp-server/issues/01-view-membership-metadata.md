# 01: View-membership metadata in the `.archimate` parser

**What to build:** When a model file is parsed, the parser also produces reverse indexes of which views use each element and each relationship, so the MCP server can answer "where is this element/relationship used". Indexes are a separate metadata layer keyed by alias; source elements/relationships are untouched.

**Blocked by:** None (can start immediately).

**Status:** ready-for-agent

- [ ] `element-views`: `{element-alias #{view-names}}`
- [ ] `relation-views`: `{[from-alias to-alias] {relation-type #{view-names}}}`
- [ ] Built during parse of `test/resources/demo.archimate`; unit tests assert the indexes match the views' `child`/`sourceConnection` content.