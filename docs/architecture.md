# Architecture (current)

A map of the code on `main`. How the project got here: [the code as found in 2024 and how each
phase changed it](history/architecture-before-refactor.md), and the plan in
[REFACTOR_GUIDE.md](../REFACTOR_GUIDE.md).

## Layers

Dependencies point down only. `architecture.LayeringTest` fails the build if `rules`, `ai`,
`engine` or `game` import Swing/AWT or a higher layer.

| Package | Role |
|---|---|
| `web` | Javalin server on 127.0.0.1 (`--lan` for all interfaces, `--host ADDR` for one), one WebSocket `/ws` for the live game, and REST under `/api/`: saved games, variants (with `betza` and `variant-rules`), the variant `health` check, `analysis`, the `eval` bar, Stockfish install, the `fun-test` ratings, the cloud `sync` state and the lab. `GameHub` runs the session on one "game" thread and sends a full JSON snapshot after every change. |
| `cloud` | `CloudSync` shares runs, saved games, variants and the hall of fame between copies of the game through a Cloudflare `D1` database (local files first; a background pass sends and fetches), set up by `CloudConfig`. See [cloud-server.md](cloud-server.md). |
| `game` | `GameSession`: turn-taking, clock, draw offers; engine moves and hints run as cancellable jobs on one engine thread; a result is dropped if the game moved on (generation check). `GameArchive` saves games as JSON files; `VariantStore` keeps the player's own variants in `variants/`; `PieceBank` keeps pieces apart from any variant (with their pictures) in `variants/piece-bank/`. |
| `engine` | `Engine` interface; `MinimaxEngine` (built-in), `StockfishEngine` (one long-lived UCI process), `EngineSelector` (picks by level, falls back to built-in Level 8), `Levels` (ladder 0-13), `Weights` (tuned/classic), `TimeBudget`. |
| `ai.board` | `Board`, the one way into a position (moves, children, check, game over, keys, FEN), `Move` (a move as one `int`), `ChessPosition` (what the chess evaluation reads), `BoardRules` (a piece set on a grid with a start position) and `GenericBoard`, which plays any piece set. `Boards` picks the implementation; nothing outside `ai.board` names `GenericBoard`. Players are numbered from 0, squares are ids. |
| `ai.piece` | Pieces as data (Phase 6 R2): `PieceType` built from `Atom`s (leap or slide, move / capture / both, symmetry, range, first move only), `StandardPieces`, `Grid`, and `CompiledPiece` (per-square tables). The board compiles its pieces from these. `Betza` writes and reads pieces as Betza text. |
| `ai.variant` | A game as data (Phase 6 R4): `Variant` (pieces, grid, start position, win conditions, royal mode, stalemate, repetition, move limit, forced capture, castling), the built-ins in `Variants` (chess, antichess, King of the Hill, Three-check) and `VariantJson`. `BoardRules.of(variant)` compiles one for the board. The rule building blocks: [variant-rules.md](variant-rules.md). |
| `ai`, `ai.eval` | `Minimax` search with `TranspositionTable`, `ChessEvaluate` (the chess evaluation, with its own attack tables) behind the `Evaluator` interface, `ParamSchema`/`ParamVector` for its weights; `PieceSetEvaluate` (an evaluation built from any variant's pieces), `NetEvaluate` and `Evaluators.forVariant`. |
| `rules` | The single rules authority: FEN in, legal moves, status (mate, stalemate, draws) and SAN out; `Game` is one game's history. Backed by `ai.board`. |
| `analysis` | Game analysis with Stockfish (`GameAnalyzer`, `UciEvaluator`); [game-analysis.md](game-analysis.md). |
| `arena`, `evolution`, `lab` | Engine-vs-engine matches (any variant; outside players: Stockfish for chess, `FairyStockfish` for the built-in variants and, via `FairyConfig`, made ones, plus the `random` mover and `net:FILE` networks), the `Evolution` interface the owner implements, and the runner that stores runs in SQLite (`runs/*.db`: tables `run`, `member`, `game`, `generation`, `yardstick`) plus the hall of fame, the opening tree of a run's games (`OpeningTree`, the run's Openings tab), Texel tuning, training data for any game (`TrainingExport`, `SelfPlayData`, `NetData`), the variant health check (`VariantHealth`), the worker that plays a server's queued runs on this computer (`Worker`, `ServerLink`; `web.LabJobs` is the server's side) and the blind fun test (`FunTest`, [fun-test.md](fun-test.md)). |

The browser (`web/`, React 19 + TypeScript + Vite, react-chessboard) never computes legal moves.

## A move, end to end

1. UI sends `{type:'move', uci}` over the WebSocket (`web/src/protocol.ts`).
2. `GameHub` hands it to the game thread; `GameSession` validates it with `rules` and updates `Game`.
3. If it is the engine's turn, a job goes to the engine thread with a `Cancellation`; `EngineSelector` picks `MinimaxEngine` (Levels 1-8) or `StockfishEngine` (9-13).
4. The result returns to the game thread (dropped if stale), `GameStateJson` builds a snapshot, `GameHub` pushes it to every client.

## Pieces and the board

A piece is a list of *atoms*, each a leap (a fixed offset, like the knight's (1,2)) or a slide (a
direction until blocked, with an optional range), marked move, capture or both, with a symmetry
(all eight directions, mirrored left-right, or one) and optionally "first move only". A piece type
adds whether it is royal, what it promotes to, en passant and castling roles, and a starting value.
A new piece is a new definition, not new code ([Phase 6 research](phase-6-research.md)).

`GenericBoard` stores one 64-bit mask per player and piece type, plus side to move, castling
rights (found from the start position), the en-passant square, unmoved pieces and the move
clocks. Making a move returns a new position, so positions are immutable and the search has
nothing to undo.

Each piece is compiled once into per-square tables (`CompiledPiece`): one mask of leap targets,
and for slides the squares of each ray, so the first piece in the way is the lowest or highest set
bit of "ray AND occupied". Moves that leave the mover's royal piece attacked are dropped. A square
is attacked by a piece exactly when the same piece of the other side, standing on that square,
would attack the attacker, so one table serves both questions; the test is skipped for moves that
provably cannot expose the king. Perft tests compare the move counts of 23 positions with the
published values and with Stockfish (Phase 4b).

## Search

`ai/Minimax.java` (reads positions only through `ai.board.Board`): iterative deepening (depth 1, 2, ... up to the level's depth, stop at 5 s or on cancel, checked every 256 nodes), alpha-beta, quiescence (captures, promotions, replies to check, at most 8 extra plies), repetition scored as a draw (a per-branch stack of Zobrist keys, `BoardStateTracker`), and a small random "variety" (20 cp) among near-best moves; a forced mate is never passed up. Per call, the depths share a transposition table (Zobrist keys, paired slots, EXACT/LOWER/UPPER bounds, up to 16 MB), killer moves and a history table. Move order: table move, captures by MVV-LVA, two killers, history. These only save work; the value at a given depth is unchanged, and the middlegame search at depth 6 is about 5 times faster ([Phase 5b research](phase-5b-research.md)). Depth gets +1 with 12 or fewer pieces and +2 with 8 or fewer.

## Evaluation

`ChessEvaluate`: 56 named features times a middlegame and an endgame weight, blended by remaining material (phase 0-24), plus piece-square tables; 499 parameters, in centipawns (classic material: pawn 100, knight 300, bishop 330, rook 500, queen 900). The feature groups: material, pawns (advancement, centre, doubled, isolated, defended, passed by rank), king placement and king safety (pawn shield, open files, attackers), castling, development, pieces (bishop pair, outposts, rooks on open files and the seventh, tempo), mobility and activity (attacked squares and pieces). Checkmate and stalemate are terminal values. Presets: `classic.json` (frozen, pinned by `engine.SameMoveTest`) and `tuned-v1.json` (Texel-fitted, the app default). Other variants get `PieceSetEvaluate`, built from their pieces.

`Evaluator` is an interface, so the search can play with any scorer. `NetEvaluate` is a small neural network: `NetFeatures` turns a `PieceBoard` into 768 on/off inputs (side, piece type, square, seen from the player to move), one hidden ReLU layer, one score in centipawns; weights come from a `net-v1` JSON file a Python trainer writes (`lab.NetData` writes the training file, `tools/net/` reads it; [net-training-guide.md](net-training-guide.md)).

## Stockfish

`StockfishEngine` keeps one Stockfish process for the session: the UCI handshake runs once, and
each move sends the game's moves so far. The suggested move is checked against the program's own
rules before it is played. Levels 9-12 hold Stockfish to a UCI_Elo of 2150, 2400, 2650 and 2900
at 500 ms a move; Level 13 plays full strength at 1 s, and hints full strength at 4 s
(`Levels.HINT_MOVE_TIME_MS`). If Stockfish crashes it is restarted; if it is missing or keeps
failing, the built-in engine takes over. `StockfishLocator` and `FairyStockfishLocator` find the
binaries (see the README).

## Running and testing

```bash
./mvnw package                       # build the jar (runs tests, builds the UI)
java -jar target/izikstar-chess-3.1.0.jar [--port N] [--no-browser] [--lan | --host ADDR] [--games DIR] [--variants DIR] [--runs DIR] [--local]
./mvnw test [-Psmoke | -Pstress]     # main suite / end-to-end / long runs
cd web && npm run e2e                # Playwright
java -cp target/izikstar-chess-3.1.0.jar arena.Cli match A B --depth 3
java -cp target/izikstar-chess-3.1.0.jar lab.Cli run runs/first.db --algorithm evolution.RandomMutationExample
```
