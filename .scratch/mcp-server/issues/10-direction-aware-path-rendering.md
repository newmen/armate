# 10: Direction-aware, name-based path rendering in shortest_path / all_paths

**What to build:** When `shortest_path` or `all_paths` searches over the undirected graph (default), edges are traversable in both directions. The returned path text must make the **actual traversal direction of each edge** visible, and must label nodes by their **element names**, not their aliases. Today every segment is rendered as `A(type) -> B` regardless of whether the edge was followed along its real relationship direction or against it, and nodes are shown as internal aliases. An edge traversed backwards (e.g. the real relationship points `bpc20 -> bo3`, but the search walks `bo3 -> bpc20`) must render as `<-`: `bo3 <-(access_r)- bpc20`. So the example short path from "Производитель пойла" to "Волк" should read `Производитель пойла -(assignment)-> <bpc9 name> -(access_w)-> <bo3 name> <-(access_r)- <bpc20 name> <-(assignment)- Волк` rather than the current `ba29(assignment) -> bpc9(access_w) -> bo3(access_r) -> bpc20(assignment) -> ba12`. Directed search (`directed=true`) continues to render with `->` everywhere.

**Blocked by:** None (can start immediately)

**Status:** done

- [x] `shortest_path`/`all_paths` output distinguishes forward (`->`) vs backward (`<-`) traversal of each undirected edge, using each relationship's actual direction from segment data (no re-traversal needed).
- [x] Path nodes are rendered by element name, not alias. Where an element name is ambiguous, the display stays unambiguous (disambiguate by alias/kind/layer in parentheses).
- [x] The example "Производитель пойла" → "Волк" short path renders a `<-` segment where the relationship runs opposite to the walk.
- [x] Directed search (`directed=true`) output is unchanged in direction (all `->`) and also uses element names.
- [x] Tests assert the `<-` arrow on at least one shortest_path and one all_paths result, assert element names (not aliases) appear, and keep the existing substring checks green.