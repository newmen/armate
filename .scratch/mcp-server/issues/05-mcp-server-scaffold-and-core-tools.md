# 05: MCP server scaffold: registry and core view tools

**What to build:** The hand-rolled stdio JSON-RPC MCP server (mirroring the in-house `mcp` (@/Users/alfa/projects/clojure/mcp) reference: `initialize` handshake, `tools/list`, `tools/call` via `defmulti`, JSON-RPC envelope, stdio read/write). Includes the model registry tools (`load_model`, `reload_model`, `list_models`, `unload_model`) and the core view tools (`list_views`, `render_view`, `merge_views`, `list_elements`), wired to the analytics from tickets 02 and 04. Served over stdio; launched as an uberjar. The server responds to a scripted MCP client over stdin/stdout.

**Blocked by:** 02 (Element selection and stats analytics), 04 (Path analytics and global certain-derivation cache)

**Status:** done

- [x] stdio JSON-RPC handshake: `initialize`, `tools/list`, `tools/call`.
- [x] `load_model` / `reload_model` / `list_models` / `unload_model`.
- [x] `list_views` / `render_view` / `merge_views` / `list_elements`.
- [x] uberjar launch; scripted-client smoke test via spawn + JSON-RPC.

**Resolved decisions:**
- `render_view` / `merge_views` render the view sub-context as a **slice of the model's global context** (`build-sub-context` over the placed aliases) rather than a fresh per-view graph, so aliases are model-global and stable across all tools.
- `get-full-graph` runs **once** per load; the registry threads the returned graph into `build-model-record` (never rebuilds). Regression test `full-graph-built-once-per-load`.
- **Stdio is pure JSON-RPC:** `logback.xml` declares a `NopStatusListener` so logback status never pollutes stdout; appenders write to a rolling file.
- `tools/list` advertises a per-tool `inputSchema` (names/types/required/enums).