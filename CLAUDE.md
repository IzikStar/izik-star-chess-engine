# IzikStar Chess: notes for Claude

Chess and chess variants in the browser (React + TypeScript in `web/`), served by a Java 21
backend (Javalin) with its own alpha-beta engine, a Stockfish/Fairy-Stockfish bridge and an
evolution Lab. Start with [README.md](README.md); the current code map is
[docs/architecture.md](docs/architecture.md).

## Build and test

```bash
mvn -o test -Dskip.web=true      # main Java suite (~12k tests, a few minutes); must stay green
mvn -o test -Psmoke              # full games end to end
mvn -o test -Pstress             # long runs, several minutes
cd web && npx tsc -b             # typecheck the web app
mvn package -DskipTests          # jar with the built web app: target/izikstar-chess-3.1.0.jar
cd web && CHROMIUM_PATH=/opt/pw-browsers/chromium npx playwright test   # browser tests, need the jar and Stockfish
```

- Use the system `mvn` (`./mvnw` needs a download). `-o` works once dependencies are cached;
  drop it after a dependency change. The web build downloads its own Node into `target/`.
- Browser tests start a tiny Lab run that needs Stockfish (`apt-get install stockfish`, found
  at `/usr/games/stockfish`).
- `engine.SameMoveTest` pins the engine's move in 56 positions at depths 1-4. A change that is
  meant to alter play re-records it (command in `src/test/resources/engine/same-moves.txt`) and
  says why in that file's header; any other change must keep it green.
- `architecture.LayeringTest` keeps the headless core (`rules`, `ai`, `engine`, `game`) free of
  UI and server imports.

## Conventions

- Repo docs, code and comments are in English. The owner talks in Hebrew.
- A new phase starts with a research doc (`docs/phase-N-research.md`) and the owner approves its
  decisions before code. Research docs and `docs/experiments/` are decision records: add a
  short status line when something changes, don't rewrite them. Living docs (README,
  docs/architecture.md, guides) must match the code.
- Small reviewable PRs; every PR keeps `mvn test` green. No CI runs on the repo, so run the
  suites yourself before pushing.
- Comments say what the code does now, not what it replaced.

## Code graph (code-review-graph)

`.mcp.json` runs the code-review-graph MCP server, and `.claude/settings.json` builds or
updates its index (`.code-review-graph/`, git-ignored) at session start and after each edit.
Use it to narrow scope before reading files:

- `semantic_search_nodes_tool` / `query_graph_tool` (callers_of, callees_of, imports_of,
  tests_for) instead of grepping for a symbol
- `get_impact_radius_tool` before changing a widely used class
- `detect_changes_tool` + `get_review_context_tool` to review a diff
- `refactor_tool` or `uvx code-review-graph dead-code` to look for dead code; many hits are
  private constructors, records or CLI entry points, so grep before deleting

The source wins when it and the graph disagree; an empty result can mean "not statically
visible" (reflection, JSON keys, routes called from `web/src`).
