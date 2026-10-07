# Architecture of IzikStar Chess 3.1, as found

*Historical: the code as found in Phase 0 (2024) and how each phase changed it; the current map is [docs/architecture.md](../architecture.md).*

Older documents (REFACTOR_GUIDE.md, the phase notes and research) call this file `ARCHITECTURE.md`;
it was moved here once the refactor was complete. Sections 1-4 map the code before Phase 3,
including its broken and duplicated parts, so the redesign could be planned with full knowledge
of what was really going on; the annotations inside them say what a phase changed. Section 5
ranks the flaws, each marked with the phase that resolved it. No source files were modified to
produce the original version. Paths that read `src/<pkg>` are `src/main/java/<pkg>` since Phase
0; most of the classes named below no longer exist.

## How each phase changed it

- **Phase 0 (2026-09-05).** A Maven build (`pom.xml`) with dependencies from Maven Central;
  `libs/` and the vendored Stockfish source removed; the Stockfish executable moved to `engine/`;
  the standard `src/main/java/` layout and a characterization test suite
  ([phase-0-notes](../phase-0-notes.md)).
- **Phase 1 (2026-09-05).** Deleted the `player/` package (a second, unused minimax),
  `ChessServer/` (a commented-out REST stub) and the unused `pieces/Piece2`, `PieceUT`
  ([phase-1-notes](../phase-1-notes.md)).
- **Phase 2 (2026-09-06).** The two board models and three check/mate/draw implementations
  collapsed into one rules authority: the headless `rules` package (`Rules`, `Game`,
  `Position`, `ChessMove`; FEN in, status and legal UCI moves out) over the bitboard.
  `main.CheckScanner` and the `BoardState` simulate-and-revert family deleted; `BoardState`
  delegated its rules questions to `rules.Rules`; the four draw-detection bugs fixed
  ([phase-2-research](../phase-2-research.md)).
- **Phase 3 (2026-10-01).** The code became layered (`rules` < `ai` < `engine` < `game` < UI),
  enforced by `architecture.LayeringTest`. `game.GameSession` runs headless with an immutable
  `GameConfig`; the search takes its side and level as parameters. Deleted: `ai.BoardState`,
  all of `pieces/*`, `main.Move`, `ChoosePlayFormat`, `SavedStatesForDraws`, `ai.myEngine`,
  `GoBack`, `SoundPlayer`. Engine bugs A/B/C (§2.4, §2.5) and a false-repetition bug in the
  search hash fixed ([phase-3-research](../phase-3-research.md)).
- **Phase 4 (2026-10-02).** One concurrency pattern, documented on `game.GameSession`: engine
  moves and hints are cancellable jobs on one engine thread; the search deepens one ply at a time
  with a 5 s cap; Stockfish is one process per session (handshake once, restart on crash, fall
  back after repeated failures) ([phase-4-research](../phase-4-research.md)).
- **Phase 4b (2026-10-02).** Perft found nine move-generator bugs, all fixed and pinned by tests;
  attacks came from tables instead of per-piece scans; the search stopped keeping its tree in
  memory, so Levels 6-7 reach their depth within the cap ([phase-4b-research](../phase-4b-research.md)).
- **Phase 4c (2026-10-02).** The Swing UI (`main`, `GUI`) replaced by a browser UI: `web`
  serves a React app and one WebSocket; the browser never computes legal moves
  ([ui-research](../ui-research.md)).
- **Phase 5 / 5b.** Every evaluation weight a parameter, the arena, the run record and the Lab;
  then a working transposition table and killer/history move ordering
  ([phase-5-research](../phase-5-research.md), [phase-5b-research](../phase-5b-research.md)).
- **Phase 6 (2026-10-05).** `ai.BitBoard` deleted: the search plays on `ai.board.GenericBoard`,
  which reads its pieces from data (`ai.piece`); the evaluation moved to `ai.eval.ChessEvaluate`
  ([phase-6-research](../phase-6-research.md)).

## 1. Project shape

> **Updated after Phase 0 (2026-09-05):** there is now a Maven build (`pom.xml`); dependencies
> come from Maven Central; `libs/` and the vendored Stockfish source tree were removed; the
> Stockfish executable moved to `engine/`. Source moved to the standard `src/main/java/` /
> `src/main/resources/` layout (package names unchanged), with a characterization test suite
> under `src/test/java/`. The description below is otherwise still accurate; paths that read
> `src/<pkg>` are now `src/main/java/<pkg>`. See [docs/phase-0-notes.md](../phase-0-notes.md).

Originally there was no build tool — a raw IntelliJ IDEA project (`chessGame_3.iml`) with
third-party jars checked into `libs/` (Retrofit, OkHttp, Gson, Kotlin stdlib) and compiled
output committed under `out/`. All source lived under `src/`, organized by Java package rather
than by architectural layer:

```
src/
├── main/                 UI shell, input handling, move bookkeeping, "settings"
│   ├── setting/          global game-mode flags (static, mutable)
│   ├── savedGames/       FEN-list persistence + a move-list side panel
│   └── Board.java        Swing JPanel that ALSO owns rules/state (see §4)
├── GUI/                  audio, sprite animation, a custom Swing button
├── pieces/               Piece, King/Queen/Rook/Bishop/Knight/Pawn
├── ai/                   the engine: BoardState, Minimax, StockfishEngine, myEngine
│   ├── BitBoard/         the canonical board representation + move generator (since Phase 2)
│   └── openingBook/      Retrofit/Lichess client wiring — built but never called
├── rules/                Phase 2: the one headless rules authority (Rules/Game/Position/
│                         ChessMove) — FEN in, status + legal UCI moves out, no Swing/AWT
```

> **Updated after Phase 1 (2026-09-05):** the `player/` package (a second, unused minimax
> implementation), `ChessServer/` (a 100%-commented-out REST stub), and the two unused
> `pieces/` variants (`Piece2`, `PieceUT`) were deleted. See §3 and
> [docs/phase-1-notes.md](../phase-1-notes.md).

Nothing in the tree marks any of this as legacy — dead and live code sit side by side with the
same visibility as the code that actually runs, which is itself one of the flaws below.

## 2. Core components

### 2.1 Board representation

> **Phase 2:** no longer "two, independent, and out of sync." The **bitboard (B)** is the one
> rules authority, wrapped by `rules.Rules`. The **object model (A)** is kept as a mutable
> container for the renderer and for move bookkeeping, but every legality/status question it is
> asked it forwards to `rules.Rules` (which builds a `BitBoard` from a FEN). They can no longer
> disagree about check / mate / draw. Merging A's *state* into B, and retiring A, is Phase 3.

**A. Object-oriented board (kept as a render/bookkeeping container; delegates rules):**
`ai/BoardState.java` holds an `ArrayList<Piece>`, current-turn flag,
castling rights, en-passant square, etc. Pieces are polymorphic objects
(`pieces/Piece.java` and its six subclasses) that carry their own
`col`/`row` **and their own Swing pixel coordinates (`xPos`, `yPos`)** and even their own
rendering sprite. `BoardState` is serialized to/from FEN strings for every state change.

**B. Bitboard (used only inside the search):**
`ai/BitBoard/BitBoard.java` represents the position as twelve
`long` bitmasks (one per piece type/color) plus flags, with piece-specific move generators in
`ai/BitBoard/BitPiece/`. It is immutable: every move produces a
brand-new `BitBoard` via `getNewBoardFromMove`, rebuilt field-by-field.

These never merge into one model. Instead, `Minimax.getBestMove(BoardState board)`
(`ai/Minimax.java:26`) converts A → B once at the root
(`new BitBoard(board)`), searches entirely in bitboard space, and the result (`BitMove`) is
converted back to a `Move` against the object-oriented board via
`main/Move.java:35` (`new Move(BoardState board, BitMove move)`).
Every legality/status check elsewhere in the app (UI move validation, drag-and-drop, "take a
hint", the random-move engine, PGN notation) ~~instead runs against representation **A** using a
completely different rules implementation~~ **now delegates to `rules.Rules` → `BitBoard`
(Phase 2)**. `BoardState.isValidMove` / `getLegalMoves` / `getAccurateStatus` / `getStatus` /
`getIsCheck` build a FEN (`BoardState.toRulesFen()`), hand it to `rules.Rules`, and cache the
result per position.

### 2.2 Move generation

> **Phase 2:** one generator. `BitBoard.getMovesForColor` + the `BitPiece` subclasses (bitwise
> ray/attack generation, castling and en-passant as special cases) is now the only source of
> legal moves, reached through `rules.Rules.legalMoves` / `BoardState.getLegalMoves`. The
> old list-based `pieces/*.getValidMoves` still exists and is still called by the skill-0
> random engine and by SAN disambiguation, but it now brute-forces against the *delegating*
> `BoardState.isValidMove`, so it returns the same set the bitboard does (it was previously
> missing the two-square pawn push, among other things). `Piece.isValidMovement` /
> `moveCollidesWithPiece` and `King.canCastle`'s check-safety terms are dead.

Two bitboard move-generation bugs were fixed in Phase 2: `BitQueen.getAttackedTiles()` computed
its up-left diagonal with a stale step counter (an adjacent up-left check — e.g. Scholar's Mate
— went undetected), and `BitPawn.getEnPassantMoves()` removed the capturing pawn without placing
it on the target square (the pawn vanished).

> **Phase 4b:** perft found nine more, all fixed and each pinned by a test
> ([docs/phase-4b-research.md](../phase-4b-research.md) §2.3). Two knight jumps and one king
> step were not counted as attacks, and pawn attacks used the wrong edge test (white pawns
> attacked nothing).
> The piece setters left the occupied-square masks stale, so an en-passant capture could expose
> the king. Castling rights did not follow the rooks, queen-side castling wrongly required b1/b8
> to be safe, sideways queen moves dropped the side's other queens, and the search computed the
> wrong en-passant square. The per-piece `getAttackedTiles` scans are gone; attacks come from
> `ai.BitBoard.Attacks`.

### 2.3 Check / checkmate / stalemate / draw detection

> **Phase 2: one path.** `rules.Rules.status(fen)` / `evaluate(fen)` over a `BitBoard`:
> `BitBoard.getNextStates().isEmpty()` + `BitBoard.isSideToMoveInCheck()` for
> check/mate/stalemate, and `rules.Rules` for the draw rules (50-move at 100 plies, insufficient
> material; threefold via `rules.Game`'s position history). `BoardState.getAccurateStatus` /
> `getStatus` / `getIsCheck` and `Board.paintComponent` / `updateGameState` all read this.

Historically this was **triplicated** and the three answers could disagree depending on which
call path asked — a direct explanation for "doesn't recognize draws" and "sometimes avoids
checkmate":

1. ~~`main/CheckScanner.java` — ray-casts from the king's square
   against `BoardState`~~ — **DELETED in Phase 2** (213 lines).
2. `BitBoard.isCheckOn` / `getAllAttackedTiles` / `getStatus` — the bitwise implementation,
   **now the one that everything uses** (via `rules.Rules`), not just the search.
3. ~~The side-effecting path: `Move.setRepresentation` → `getStatusString` →
   `board.makeMoveAndGetStatus(this)`, which simulated the move on the live `BoardState`, asked
   `CheckScanner`, then reverted it~~ — **removed in Phase 2**; `getStatusString` now asks
   `rules.Rules` about the position *after* the move (`Rules.status(Rules.applyMove(fen, mv))`),
   with no effect on the live board. `makeMoveAndGetStatus` is deleted.

### 2.4 "Minimax Engine" (`ai.Minimax` / `ai.myEngine`)

> **Phase 2 touched this only lightly:** the search already ran on the bitboard (now the
> canonical representation), and its terminal test `BitBoard.getStatus()` is the unified status
> (its 50-move threshold was `>= 50` on a per-ply counter — fixed to `>= 100`). `myEngine` no
> longer calls the deleted `CheckScanner` / `makeMoveToCheckIt` — it uses
> `BoardState.isValidMove` (delegating) and `getIsCheck()`. Everything below still holds; the
> search's dependence on `ChoosePlayFormat` statics and its ad hoc concurrency are **Phase 3/4**.

> **Known bug (reported, not yet fixed — Phase 3/4):** the engine sometimes *stops making
> moves* — most visibly when it is losing badly / a forced mate against it is within the search
> horizon. Mechanism: `Minimax.bestMoves` (a `public static` list) is only re-initialised when
> `lastDepth` is true, and `lastDepth` is computed from the `ChoosePlayFormat.isPlayingWhite /
> isEnginePlayingBlack / isComputersGame` statics (`ai/Minimax.java:97`).
> `Input.makeEngineMove` / `takeEngineHint` / the `myEngine` fallback **flip those statics and
> flip them back around an *asynchronous* engine call** (`Input.java:100-106`,
> `Input.java:130-133`, `Input.java:182-185`, `myEngine.java:91-114`). When the search's
> `lastDepth` check runs while the flags are in the "flipped"/torn state, `bestMoves` stays
> `null` or holds a *stale* list from the previous search → `Minimax.getBestMove` NPEs at
> `Minimax.java:44` (`bestMoves.isEmpty()`) or returns a move that
> belongs to a different position. The exception propagates out of the `myEngine` `Callable`,
> `Input.makeEngineMove`'s `catch (Exception e) {}` **swallows it silently**, `latch.countDown()`
> runs, and the engine's turn ends with no move played. Fixing this properly needs the search to
> take "who is the engine" as a parameter (Phase 3 retires the `ChoosePlayFormat` statics) and a
> single, non-swallowing concurrency model (Phase 4). A band-aid null-guard risks an infinite
> loop in `myEngine.makeMove`'s `while (move == null)` retry.

`ai/Minimax.java` is alpha-beta over the bitboard model, with static
mutable search state (`bestMoves`, `maxDepth`, `nodesChecked`, ... all `public static`, so the
class is not reentrant/thread-safe — only one search can exist in the process at a time).
Depth is not a constant: `myEngine.getBestMove` recomputes `Minimax.maxDepth` from
`SettingPanel.skillLevel` and `board.getGameState()` immediately before every search
(`ai/myEngine.java:242-272`). Move ordering
(`getSortedNextStates`) is a full sort by a `moveValue` heuristic computed once per generated
child, not iterative deepening or a proper evaluation-guided search. `TranspositionTable` and
`ZobristHashing` exist but are wired into `minimax()` only in **commented-out** form
(`ai/Minimax.java:76-81`, `#146`), so no transposition caching
actually happens despite the classes existing. The recursive search itself
(`Minimax.minimax`, `ai/Minimax.java:73-149`) reads
**UI-layer static globals** (`ChoosePlayFormat.isComputersGame`, `isEnginePlayingBlack`,
`isPlayingWhite`) to decide, mid-search, whether the current recursion depth is "the root from
the engine's perspective" — the search algorithm's own notion of whose move it's picking is
borrowed from Swing settings state rather than being a parameter.

`ai/myEngine.java` is the actual orchestrator invoked by the UI: it holds a single-thread
`ExecutorService`, wraps `Minimax`/random-move selection, and **also reaches back into the UI**
— `makeMove()` calls `realBoard.makeMove(move)`, `realBoard.repaint()`, and pops a
`JOptionPane` game-over dialog directly from its background task
(`ai/myEngine.java:60-121`), all without
`SwingUtilities.invokeLater`. The "AI" is not a pure function of a position; it is a
controller that drives the Swing board.

### 2.5 Stockfish integration (`ai.StockfishEngine`)

> **Update (showcase prep):** the Stockfish binary is no longer committed. The path resolves
> from `-Dstockfish.path`, then `STOCKFISH_PATH`, then any `stockfish*` file in an `engine/`
> folder near the working directory or the jar, then the `PATH` (`engine.StockfishLocator`,
> October 2026; before that only the exact `engine/stockfish-windows-x86-64.exe` worked). If none
> is found or it can't be launched, `StockfishEngine.isAvailable()` turns false and Levels 8-10
> play the built-in engine at Level 7. See the README.

A thin wrapper around a `ProcessBuilder`-launched `stockfish-windows-x86-64.exe`
(`ai/StockfishEngine.java`), talking UCI over stdin/stdout.
Every single call to `getBestMove` resends `uci` / `isready` / `ucinewgame` / skill-level
before asking for a move (`ai/StockfishEngine.java:76-107`)
— there is no persistent session/position tracking, so Stockfish restarts its "conversation"
from scratch each move. Output is read by polling `reader.ready()` after a fixed `Thread.sleep`
rather than a dedicated reader thread, and the search is bounded by both a movetime (`150`ms)
**and** a wall-clock retry loop in `Input.makeEngineMove`
(`main/Input.java:66-97`) that keeps re-asking for up to
1200ms if the first answer wasn't a legal/parseable move. This — not engine strength — is the
likely cause of "Stockfish plays badly": it's being given a 150ms budget per attempt at a
throwaway skill level, hard-capped to whatever the first parseable UCI reply is.

### 2.6 UI (`main.Board`, `main.Input`, `GUI.*`, `main.setting.*`, `main.savedGames.*`)

`main/Board.java` `extends JPanel` and is simultaneously: the Swing
paint routine, the mouse-drag animation host, the audio trigger, the promotion-dialog launcher,
**and** the owner of the authoritative `BoardState` plus the move-execution logic itself
(`makeMove`, `movePawn`, `moveKing`, `promotePawn`, `updateGameState`). There is no separation
between "the widget that draws a board" and "the thing that knows chess rules" — they are the
same class. `main/Input.java` (a `MouseAdapter`) owns *both* engine
instances (`StockfishEngine engine` and `myEngine myEngine`) and decides which one to invoke
per move based on `SettingPanel.skillLevel` vs.a hard-coded threshold
(`switchToStockFish = 13`). `main/Main.java` is the Swing entry point,
but also contains a hand-rolled "computer vs. computer" game loop
(`Main.play()`, `main/Main.java:131-194`) built from a
`SwingWorker`, a shared `CountDownLatch`, and `Thread.sleep(6000)` polling — a fourth,
ad hoc concurrency pattern layered on top of the two already inside `myEngine`/`Input`.

### 2.7 Persistence

`main/savedGames/SaveGame.java` /
`LoadGame.java` just write/read a flat list of FEN strings to a text file; there is no
database, no game metadata (opponent, result, date), and nothing that could later be used as
training data. `ai/openingBook/` has a complete Retrofit/OkHttp/Gson client aimed at the Lichess
API plus a binary opening-book file format, but `myEngine`'s only call site for it is commented
out (`ai/myEngine.java:34-44, 254-265`) — none of it currently
runs.

## 3. Dead and parallel code left in the tree

> **Updated after Phase 1–2 (2026-09-06).** Phase 1 deleted the `player/` package, `pieces/Piece2`,
> `pieces/PieceUT`, `ai/EvaluationLevel2`, `ai/ChessMoveConverter`, `ChessServer/ChessServer.java`.
> Phase 2 deleted `main/CheckScanner.java` and the `BoardState` simulate-and-revert /
> bulk-generator family. See [docs/phase-1-notes.md](../phase-1-notes.md) and
> [docs/phase-2-research.md](../phase-2-research.md).

| Path | Status |
|---|---|
| ~~`player/ai/*`, `player/Player.java`~~ | **DELETED in Phase 1** — a second, unused minimax/evaluator abstraction. |
| ~~`pieces/Piece2.java`, `pieces/PieceUT.java`~~ | **DELETED in Phase 1** — empty unreferenced classes. |
| ~~`ai/EvaluationLevel2.java`, `ai/ChessMoveConverter.java`~~ | **DELETED in Phase 1** (ideas captured in phase-1-notes §3 first). |
| ~~`ChessServer/ChessServer.java`~~ | **DELETED in Phase 1** — 100% commented-out Spark stub. |
| ~~`main/CheckScanner.java`~~ | **DELETED in Phase 2** — one of the three check/mate impls; everything now uses `rules.Rules` → `BitBoard`. |
| ~~`ai/BoardState.getAllPossibleMoves()` / `getAllPossibleMovesForASide()` / `makeMoveToCheckIt()` / `makeMoveAndGetStatus/Value/Fen()` / `cancelMove()` / `main()`~~ | **DELETED in Phase 2** — the mutate-a-fact-out-of-the-live-board family. Replaced by `BoardState.getLegalMoves()` → `rules.Rules`. |
| `pieces/*.isValidMovement()` / `moveCollidesWithPiece()`, `King.canCastle()` | **Dead since Phase 2** (only `CheckScanner` and the old `isValidMove` pipeline called them). Left in place; removed in Phase 3 with the `pieces` / UI decouple. |
| `ai/openingBook/*` (Retrofit/Lichess client, binary book reader) | Fully implemented but never invoked. **Deferred to Phase 5** (completion, not removal). |
| `ai/TranspositionTable.java`, `ai/BitBoard/ZobristHashing.java` | Wired into `Minimax` in Phase 5b: fixed-size table with bounds, keyed by `ZobristHashing.searchKey` (castling, en passant, move number included); docs/phase-5b-research.md. |
| `ai/BoardState.convertPiecesToFEN()` / `convertPiecesToDrawFEN()` | Still used by `Board` / `SavedStatesForDraws`, but the rules path now uses the cleaner `BoardState.toRulesFen()`. Consolidate in Phase 3. |

## 4. How the pieces actually interact today

### 4.1 Package dependency graph is circular, not layered

A "clean" chess engine would layer roughly as `pieces/model → move-gen/rules → engine → UI`,
each layer only depending on the ones below it. Here, `main ↔ ai ↔ pieces` still form one
mutually-dependent cluster (Phase 3 is where that gets broken):

- `pieces.*` imports `ai.BoardState` (its own container) **and** `main.Board`,
  `main.setting.ChoosePlayFormat` — a chess piece class depends on the Swing panel and on
  global UI settings just to compute its own pixel position at construction time
  (`Pawn`'s constructor reads `Board.tileSize` and `ChoosePlayFormat.isPlayingWhite` directly,
  `pieces/Pawn.java:15-26`).
- `ai.BoardState` imports `main.Move`, `main.setting.ChoosePlayFormat`, and (Phase 2) the
  `rules` package. ~~`main.CheckScanner`~~ — deleted.
- ~~`main.CheckScanner` imports `ai.BoardState` back and reaches into `main.Board.selectedPiece`
  mid-algorithm~~ — **gone in Phase 2**; no rules code reads `Board.selectedPiece` any more.
- `ai.myEngine` imports `main.Board` and `main.Main` (to call `Main.showEndGameMessage`).
- `ai.Minimax` and `ai.eval.ChessEvaluate` import `main.setting.ChoosePlayFormat`.
- **New in Phase 2:** the `rules` package depends only on `ai.BitBoard` (one bridge class,
  `ai.BitBoard.BitBoardRules`) — **no `main.*`, `GUI`, `javax.swing` or `java.awt`**. It is the
  first module that could be lifted into a headless service unchanged.

### 4.2 Walkthrough: a human dragging a piece

1. `Input.mousePressed`/`mouseDragged`/`mouseReleased` (a `MouseAdapter` registered directly on
   the `Board` panel) constructs a `Move` and asks `board.state.isValidMove(move)` — which
   since Phase 2 delegates to `rules.Rules` (one legal-move set, cached per position).
2. `Board.makeMove(move)` mutates `BoardState` fields directly, plays a sound
   (`AudioPlayer`), starts a `ChessAnimation`, and builds the move's SAN string — whose
   `+`/`#`/`1/2-1/2` suffix now asks `rules.Rules` about the position *after* the move
   (Phase 2; no longer a simulate-and-revert on the live board).
3. `Board.updateGameState` reads the **one** status (`state.getRulesStatus()`), plus
   `SavedStatesForDraws` for threefold (position history, which the stateless status can't see).
4. If it's the engine's turn next, `Input.makeEngineMove` spins up a raw `Thread` (Stockfish
   path) or delegates to `myEngine.makeMove` (its own `ExecutorService`), which **itself**
   calls back into `Board.makeMove`, closing a loop where the "AI" layer drives the UI layer
   which drives the "AI" layer.

### 4.3 Walkthrough: the engine's turn (skill level ≥ 13, i.e. Stockfish)

`Input.makeEngineMove` spins a new thread that busy-loops (up to 1200ms, `Thread.sleep`-free
tight loop) calling `StockfishEngine.getBestMove`, which itself restarts the UCI handshake
every call and polls output with `Thread.sleep(180)`. A returned move is validated through
`BoardState.isValidMove` before being applied (Phase 2: that now means a `rules.Rules` legal-set
lookup — cheap and cached — rather than the old brute-force path).

## 5. Biggest architectural flaws, ranked

1. ~~**UI and domain/engine logic are the same objects, not merely "coupled".**~~ —
   **RESOLVED in Phase 3.** `rules.Game` + `game.GameSession` run headless (unit-tested without
   Swing); `Board` only renders `Position`s and reacts to `GameListener` events.
   `Board extends JPanel` *is* the rules engine, the animation host, the audio trigger, and the
   dialog launcher all at once (§2.6). There is no `Game`/`Rules` class you could run headless,
   write a unit test against, or reuse for a server/CLI front end. Any change to
   how a move is applied risks breaking painting, sound, and engine turn-taking simultaneously,
   because they're all the same method (`Board.makeMove`).

2. ~~**Two board representations and three independent check/checkmate/stalemate
   implementations, with no single source of truth**~~ — **RESOLVED in Phase 2.** There is now
   one rules authority (`rules.Rules` over `BitBoard`); `CheckScanner` and the SAN
   simulate-and-revert are deleted, and the four draw-detection bugs are fixed. The object
   model still holds a *copy* of the position for rendering/bookkeeping and could drift from the
   FEN it hands the rules engine — collapsing that duplication of *state* (not of *rules*) is
   Phase 3's job.

3. ~~**Circular package dependencies (`main ↔ ai ↔ pieces`)**~~ — **RESOLVED in Phase 3**
   (`pieces` deleted; layering enforced by `architecture.LayeringTest`). with no compiler-enforced boundary
   (§4.1). A `Piece` needing `Board.tileSize` and `ChoosePlayFormat.isPlayingWhite` just to
   exist means the "model" layer cannot be instantiated, tested, or reasoned about without the
   Swing UI and its global settings.

4. ~~**Global mutable static state doubles~~ — **RESOLVED in Phase 3.** `ChoosePlayFormat`
   is deleted; settings live in an immutable `game.GameConfig`; the search takes the root side
   from the position and its skill level as a parameter; Bugs A/B/C are fixed.
   **Global mutable static state doubles as an implicit parameter channel into the search
   algorithm.** `ChoosePlayFormat.isPlayingWhite/isOnePlayer/isComputersGame/
   isEnginePlayingBlack` and `SettingPanel.skillLevel` are read from inside `Minimax.minimax`
   itself to decide root-move bookkeeping (§2.4), and are routinely flipped-and-restored as a
   hack to reuse a method meant for "my move" when computing a hint or a forced move for the
   other side (e.g. `main/Input.java:130-133`,
   `ai/myEngine.java:100-105`). This is not thread-safe (the
   `Minimax` class itself is all `static` fields), and makes the search's behavior depend on
   UI state that has nothing to do with the position being searched.

5. ~~**Ad hoc, inconsistent concurrency.**~~ — **RESOLVED in Phase 3 + 4.** Phase 3 put engine
   moves on one engine executor with results on the EDT; Phase 4 made every job cancellable
   (searches stop at once on take-back / new game), capped search time, kept at most one hint
   pending, and replaced the per-move Stockfish handshake with one session. Phase 4b made
   Levels 6-7 fast enough to finish their full depth within the 5 s, and an engine job that dies
   with an `Error` is now reported instead of silently ending. Original text: At least four different patterns for "do work off the
   UI thread" coexist: a raw `Thread` in `Input.makeEngineMove`'s Stockfish branch, a
   single-thread `ExecutorService` + `Future` in `myEngine`, a `SwingWorker` +
   `CountDownLatch` + `Thread.sleep(6000)` polling loop in `Main.play()`, and one-off
   `new Thread(() -> { Thread.sleep(500); ... })` calls scattered through `Board`/`Input` purely
   to sequence a sound after an animation. Several of these mutate Swing components
   (`board.repaint()`, `JOptionPane`) from background threads without `invokeLater`
   (§2.4), which is a Swing thread-safety violation that can manifest as intermittent
   UI glitches or hangs.

6. ~~**A formatting method has a game-logic side effect.**~~ — **RESOLVED in Phase 2.**
   `Move.setRepresentation()` → `getStatusString()` now calls
   `rules.Rules.status(rules.Rules.applyMove(fen, move))` — a pure query on a derived position,
   no mutation of the live board.

7. ~~**Expensive resource loading tied to the domain model.**~~ — **RESOLVED in Phase 3**
   (`GUI.PieceSprites` decodes the sprite sheet once; there are no piece objects). `pieces.Piece` decodes and
   rescales the sprite sheet image (`ImageIO.read` + `getScaledInstance`) in an **instance**
   initializer (§4, cross-referenced in `pieces/Piece.java:23-31`),
   so every `Piece` object built anywhere — including every board clone created by
   `BoardState.loadPiecesFromFen`, which runs on every move via the notation side effect above
   — redoes this work. A pure rules/model object is paying an image-decoding cost that only the
   renderer needs.

8. **No layering means no testability** — *mostly resolved in Phase 3:* the game API, the
   session's turn-taking and the search are unit-tested headless; only the Swing glue is not.
   Original text: *improving.* `pieces`, `ai`, and `main` still depend
   on each other, but Phase 2's `rules` package is fully headless and directly unit-tested
   (`rules.RulesTest`), and `BoardState`'s rules API is now exercised without Swing. The Swing
   `Board` / `Input` glue and the `Minimax` search remain untested — Phase 3/4.

9. **Dead and parallel implementations** (§3) — mostly cleared. *Phase 3:* `pieces/*`,
   `BoardState` and its FEN serializers are deleted. *Phase 4b:* the six per-piece attack scans
   are replaced by `ai.BitBoard.Attacks`. Left: the opening-book client (Phase 5) and the
   transposition table wired in only as comments (left for a later phase; Phase 4b research §4). Phase 1 deleted the second
   minimax engine, the extra `Piece` variants, the unused evaluator, the REST stub. Phase 2
   deleted `CheckScanner` and the `BoardState` simulate-and-revert / bulk-generator family.
   What remains: the opening-book/Lichess client (Phase 5), the transposition/Zobrist classes
   wired in only as comments (later phase), the now-dead `pieces/*` movement predicates (Phase 3),
   and the two FEN serializers on `BoardState` (Phase 3).

10. **No structured persistence despite that being a stated goal.** Saved games are flat FEN
    text files (§2.7); there is no schema that could support the "database of past games to
    learn from" goal mentioned for this project — that would need to be designed from scratch,
    not extended from `SaveGame`/`LoadGame`.

## 6. Summary

**After Phase 3**, the path that runs is: `Input` (mouse gesture) → `GameSession.play(uci)` →
`rules.Game.play` (one legality check, returns a `MoveResult` with SAN and status) →
`GameListener.onMove` on the EDT → `Board` animates and repaints, sounds play, the move list
and score update. If the engine is to move next, `GameSession` asks its `Engine`
(`EngineSelector`: built-in `MinimaxEngine` below level 13, `StockfishEngine` from 13 with a
fallback) on its engine thread, and plays the answer through the same `Game.play`.

There is one rules authority (`rules.Rules` over `BitBoard`, since Phase 2) and now one copy of
the game state (`rules.Game`). Nothing below the UI imports Swing or reads UI flags.

**After Phase 4**, engine work follows one pattern (§5.5) and Stockfish keeps one session.

**After Phase 4b**, the move generator agrees with Stockfish (perft and 300 random games), and
Levels 6-7 reach their full depth within the time cap.

**After Phase 5b**, the search has a working transposition table and killer/history move ordering
(docs/phase-5b-research.md): about 5x faster in the middlegame, with the same score at every depth.

**Since then:** every game is saved as a JSON file with its players, clocks and result
(`game.GameArchive`, the *My games* tab), and every evolution run as a SQLite file (Phase 5),
which settles flaw 10. The unwired opening-book client was deleted in the October 2026 cleanup
(PR #65), with its Retrofit/OkHttp dependencies.
