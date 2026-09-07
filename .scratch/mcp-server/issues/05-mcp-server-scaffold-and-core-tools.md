# 05: MCP server scaffold: registry and core view tools

**What to build:** The hand-rolled stdio JSON-RPC MCP server (mirroring the in-house `mcp` (@/Users/alfa/projects/clojure/mcp) reference: `initialize` handshake, `tools/list`, `tools/call` via `defmulti`, JSON-RPC envelope, stdio read/write). Includes the model registry tools (`load_model`, `reload_model`, `list_models`, `unload_model`) and the core view tools (`list_views`, `render_view`, `merge_views`, `list_elements`), wired to the analytics from tickets 02 and 04. Served over stdio; launched as an uberjar. The server responds to a scripted MCP client over stdin/stdout.

**Blocked by:** 02 (Element selection and stats analytics), 04 (Path analytics and global certain-derivation cache)

**Status:** ready-for-agent

- [ ] stdio JSON-RPC handshake: `initialize`, `tools/list`, `tools/call`.
- [ ] `load_model` / `reload_model` / `list_models` / `unload_model`.
- [ ] `list_views` / `render_view` / `merge_views` / `list_elements`.
- [ ] uberjar launch; scripted-client smoke test via spawn + JSON-RPC.