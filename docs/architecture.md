# Architecture (current)

A short map of the code on `main`. `ARCHITECTURE.md` at the repository root is the history of how
the project got here; its sections 2-4 describe the code before Phase 3. A longer walkthrough with
diagrams (Hebrew) is kept by the project owner.

## Layers

Dependencies point down only. `architecture.LayeringTest` fails the build if `rules`, `ai`,
`engine` or `game` import Swing/AWT or a higher layer.

| Package | Role |
|---|---|
| `web` | Javalin server on 127.0.0.1 (`--lan` for all interfaces), one WebSocket `/ws` for the live game, REST for saved games, analysis, eval bar, Stockfish install and the lab. `GameHub` runs the session on one "game" thread and sends a full JSON snapshot after every change. |
| `game` | `GameSession`: turn-taking, clock, draw offers; engine moves and hints run as cancellable jobs on one engine thread; a result is dropped if the game moved on (generation check). `GameArchive` saves games as JSON files. |
| `engine` | `Engine` interface; `MinimaxEngine` (built-in), `StockfishEngine` (one long-lived UCI process), `EngineSelector` (picks by level, falls back to built-in Level 8), `Levels` (ladder 0-13), `Weights` (tuned/classic), `TimeBudget`. |
| `ai.board` | `Board`, the one way into a position (moves, children, check, game over, keys, FEN), `Move` (a move as one `int`), `ChessPosition` (what the chess evaluation reads), `BoardRules` (a piece set on a grid with a start position) and `GenericBoard`, which plays any piece set. `Boards` picks the implementation; nothing outside `ai.board` names `GenericBoard`. Players are numbered from 0, squares are ids. |
| `ai.piece` | Pieces as data (Phase 6 R2): `PieceType` built from `Atom`s (leap or slide, move / capture / both, symmetry, range, first move only), `StandardPieces`, `Grid`, and `CompiledPiece` (per-square tables). The board compiles its pieces from these. |
| `ai.variant` | A game as data (Phase 6 R4): `Variant` (pieces, grid, start position, goal, forced capture, castling), the built-ins in `Variants` (chess, antichess, King of the Hill, Three-check) and `VariantJson`. `BoardRules.of(variant)` compiles one for the board. `ai.piece.Betza` writes and reads pieces as Betza text; `game.VariantStore` keeps the player's own variants in `variants/`. |
| `ai`, `ai.eval` | `Minimax` search with `TranspositionTable`, `ChessEvaluate` (the chess evaluation, with its own attack tables) behind the `Evaluator` interface, `ParamSchema`/`ParamVector` for its weights; `PieceSetEvaluate` (an evaluation built from any variant's pieces) and `Evaluators.forVariant`. |
| `rules` | The single rules authority: FEN in, legal moves, status (mate, stalemate, draws) and SAN out. Backed by `ai.board`. |
| `analysis` | Game analysis with Stockfish (`GameAnalyzer`, `UciEvaluator`). |
| `arena`, `evolution`, `lab` | Engine-vs-engine matches (any variant; outside players: Stockfish for chess, `FairyStockfish` for the built-in variants and, via `FairyConfig`, made ones, plus the `random` mover), the `Evolution` interface the owner implements, and the runner that stores runs in SQLite (`runs/*.db`: tables `run`, `member`, `game`, `generation`, `yardstick`) plus the hall of fame, Texel tuning and training data for any game (`TrainingExport`, `SelfPlayData`). |

The browser (`web/`, React 19 + TypeScript + Vite, react-chessboard) never computes legal moves.

## A move, end to end

1. UI sends `{type:'move', uci}` over the WebSocket (`web/src/protocol.ts`).
2. `GameHub` hands it to the game thread; `GameSession` validates it with `rules` and updates `Game`.
3. If it is the engine's turn, a job goes to the engine thread with a `Cancellation`; `EngineSelector` picks `MinimaxEngine` (Levels 1-8) or `StockfishEngine` (9-13).
4. The result returns to the game thread (dropped if stale), `GameStateJson` builds a snapshot, `GameHub` pushes it to every client.

## Search

`ai/Minimax.java` (reads positions only through `ai.board.Board`): iterative deepening (depth 1, 2, ... up to the level's depth, stop at 5 s or on cancel, checked every 256 nodes), alpha-beta, quiescence (captures, promotions, replies to check, at most 8 extra plies), repetition scored as a draw, and a small random "variety" (20 cp) among near-best moves. Per call, the depths share a transposition table (Zobrist keys, paired slots, EXACT/LOWER/UPPER bounds), killer moves and a history table. Move order: table move, captures by MVV-LVA, two killers, history. These only save work; the value at a given depth is unchanged. Depth gets +1 with 12 or fewer pieces and +2 with 8 or fewer.

## Evaluation

56 named features times a middlegame and an endgame weight, blended by remaining material (phase 0-24), plus piece-square tables; about 500 parameters, in centipawns. Presets: `classic.json` (frozen, pinned by `engine.SameMoveTest`) and `tuned-v1.json` (Texel-fitted, the app default). Other variants get `PieceSetEvaluate`, built from their pieces.

`Evaluator` is an interface, so the search can play with any scorer. `NetEvaluate` is a small neural network: `NetFeatures` turns a `PieceBoard` into 768 on/off inputs (side, piece type, square, seen from the player to move), one hidden ReLU layer, one score in centipawns; weights come from a `net-v1` JSON file a Python trainer writes (`lab.NetData` writes the training file, `tools/net/` reads it; docs/net-training-guide.md).

## Running and testing

```bash
./mvnw package                       # build the jar (runs tests, builds the UI)
java -jar target/izikstar-chess-3.1.0.jar [--port N] [--no-browser] [--lan] [--games DIR] [--runs DIR]
./mvnw test [-Psmoke | -Pstress]     # main suite / end-to-end / long runs
cd web && npm run e2e                # Playwright
java -cp target/izikstar-chess-3.1.0.jar arena.Cli match A B --depth 3
java -cp target/izikstar-chess-3.1.0.jar lab.Cli run runs/first.db --algorithm evolution.RandomMutationExample
```
