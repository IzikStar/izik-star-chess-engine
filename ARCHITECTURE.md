# Architecture of IzikStar Chess 3.1 (as-is)

This document maps the *current* architecture of the codebase before any refactor. It is
descriptive, not prescriptive: it explains how the pieces actually interact today (including
the broken/duplicated parts), so that a redesign can be planned with full knowledge of what's
really going on under the hood.

No source files were modified to produce this document.

## 1. Project shape

> **Updated after Phase 0 (2026-09-05):** there is now a Maven build (`pom.xml`); dependencies
> come from Maven Central; `libs/` and the vendored Stockfish source tree were removed; the
> Stockfish executable moved to `engine/`. Source moved to the standard `src/main/java/` /
> `src/main/resources/` layout (package names unchanged), with a characterization test suite
> under `src/test/java/`. The description below is otherwise still accurate; paths that read
> `src/<pkg>` are now `src/main/java/<pkg>`. See [docs/phase-0-notes.md](docs/phase-0-notes.md).

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
├── pieces/               Piece, King/Queen/Rook/Bishop/Knight/Pawn, +2 unused variants
├── ai/                   the "real" engine: BoardState, Minimax, StockfishEngine, myEngine
│   ├── BitBoard/         a second, independent board representation + move generator
│   └── openingBook/      Retrofit/Lichess client wiring — built but never called
├── player/ai/            a second, entirely separate, unused minimax implementation
└── ChessServer/          a REST server stub — 100% commented out
```

Nothing in the tree marks any of this as legacy — dead and live code sit side by side with the
same visibility as the code that actually runs, which is itself one of the flaws below.

## 2. Core components

### 2.1 Board representation — there are two, independent, and out of sync

**A. Object-oriented board (the one the UI and rules actually use):**
[`ai/BoardState.java`](src/ai/BoardState.java) holds an `ArrayList<Piece>`, current-turn flag,
castling rights, en-passant square, etc. Pieces are polymorphic objects
([`pieces/Piece.java`](src/pieces/Piece.java) and its six subclasses) that carry their own
`col`/`row` **and their own Swing pixel coordinates (`xPos`, `yPos`)** and even their own
rendering sprite. `BoardState` is serialized to/from FEN strings for every state change.

**B. Bitboard (used only inside the search):**
[`ai/BitBoard/BitBoard.java`](src/ai/BitBoard/BitBoard.java) represents the position as twelve
`long` bitmasks (one per piece type/color) plus flags, with piece-specific move generators in
[`ai/BitBoard/BitPiece/`](src/ai/BitBoard/BitPiece/). It is immutable: every move produces a
brand-new `BitBoard` via `getNewBoardFromMove`, rebuilt field-by-field.

These never merge into one model. Instead, `Minimax.getBestMove(BoardState board)`
([`ai/Minimax.java:26`](src/ai/Minimax.java#L26)) converts A → B once at the root
(`new BitBoard(board)`), searches entirely in bitboard space, and the result (`BitMove`) is
converted back to a `Move` against the object-oriented board via
[`main/Move.java:35`](src/main/Move.java#L35) (`new Move(BoardState board, BitMove move)`).
Every legality/status check elsewhere in the app (UI move validation, drag-and-drop, "take a
hint", the random-move engine, PGN notation) instead runs against representation **A** using a
completely different rules implementation (§2.3). Two board models means two places piece
values, move legality, and game-over conditions can be defined — and they already disagree in
practice (see §5.3).

### 2.2 Move generation — also duplicated, at different quality levels

- **List-based generation** (`pieces/*.getValidMoves`): brute-forces the piece's mobility by
  literally trying all 64 destination squares and asking `BoardState.isValidMove` to filter
  them (e.g. [`pieces/Pawn.java:60-80`](src/pieces/Pawn.java#L60-L80),
  [`pieces/Piece.java:49-62`](src/pieces/Piece.java#L49-L62) for the default/king-style
  fallback). This is what the UI, the random-move ("skill 0") engine, and PGN disambiguation
  use.
- **Bitboard generation** (`BitBoard.getMovesForColor` and the `BitPiece` subclasses): proper
  bitwise ray/attack generation, plus castling and en-passant handled as special cases
  ([`ai/BitBoard/BitBoard.java:517-690`](src/ai/BitBoard/BitBoard.java#L517)). This is what
  `Minimax` searches over.

They are not just two implementations of the same rules — they're the only two places some
rules exist at all (e.g. castling legality is computed in `BitBoard.getCastles` but is
absent from the object-oriented king's own move list; the object model instead special-cases
castling execution later in `Board.moveKing`).

### 2.3 Check / checkmate / stalemate detection — triplicated

1. [`main/CheckScanner.java`](src/main/CheckScanner.java) — ray-casts from the king's square
   against `BoardState`, used by the UI (`isValidMove`, highlighting, "is this checkmate")
   and by the random-move engine.
2. `BitBoard.isCheckOn` / `getAllAttackedTiles` / `getStatus`
   ([`ai/BitBoard/BitBoard.java:267-738`](src/ai/BitBoard/BitBoard.java#L267)) — a separate,
   bitwise implementation used only inside the minimax search.
3. A **third**, side-effecting path: producing algebraic notation for a move
   (`Move.setRepresentation` → `getStatusString`,
   [`main/Move.java:135-141`](src/main/Move.java#L135-L141)) calls
   `board.makeMoveAndGetStatus(this)`, which **simulates the move on the live `BoardState`,
   asks `CheckScanner` for the result, then reverts it** — meaning formatting a move's PGN
   string as a side effect re-derives check/mate/stalemate a third time, using yet another
   BoardState method (`BoardState.getAccurateStatus`,
   [`ai/BoardState.java:525-533`](src/ai/BoardState.java#L525-L533)).

Three independent code paths computing the same fact, invoked from different layers, are a
direct explanation for the symptoms in the last commit message ("Stockfish plays worse than it
should", "my engine doesn't recognize draws", "sometimes avoids checkmate"): whichever of the
three answers gets consulted for a given decision is a function of *which code path called it*,
not of the actual position.

### 2.4 "Minimax Engine" (`ai.Minimax` / `ai.myEngine`)

[`ai/Minimax.java`](src/ai/Minimax.java) is alpha-beta over the bitboard model, with static
mutable search state (`bestMoves`, `maxDepth`, `nodesChecked`, ... all `public static`, so the
class is not reentrant/thread-safe — only one search can exist in the process at a time).
Depth is not a constant: `myEngine.getBestMove` recomputes `Minimax.maxDepth` from
`SettingPanel.skillLevel` and `board.getGameState()` immediately before every search
([`ai/myEngine.java:242-272`](src/ai/myEngine.java#L242-L272)). Move ordering
(`getSortedNextStates`) is a full sort by a `moveValue` heuristic computed once per generated
child, not iterative deepening or a proper evaluation-guided search. `TranspositionTable` and
`ZobristHashing` exist but are wired into `minimax()` only in **commented-out** form
([`ai/Minimax.java:76-81`](src/ai/Minimax.java#L76-L81), `#146`), so no transposition caching
actually happens despite the classes existing. The recursive search itself
(`Minimax.minimax`, [`ai/Minimax.java:73-149`](src/ai/Minimax.java#L73-L149)) reads
**UI-layer static globals** (`ChoosePlayFormat.isComputersGame`, `isEnginePlayingBlack`,
`isPlayingWhite`) to decide, mid-search, whether the current recursion depth is "the root from
the engine's perspective" — the search algorithm's own notion of whose move it's picking is
borrowed from Swing settings state rather than being a parameter.

`ai/myEngine.java` is the actual orchestrator invoked by the UI: it holds a single-thread
`ExecutorService`, wraps `Minimax`/random-move selection, and **also reaches back into the UI**
— `makeMove()` calls `realBoard.makeMove(move)`, `realBoard.repaint()`, and pops a
`JOptionPane` game-over dialog directly from its background task
([`ai/myEngine.java:60-121`](src/ai/myEngine.java#L60-L121)), all without
`SwingUtilities.invokeLater`. The "AI" is not a pure function of a position; it is a
controller that drives the Swing board.

### 2.5 Stockfish integration (`ai.StockfishEngine`)

A thin wrapper around a `ProcessBuilder`-launched `stockfish-windows-x86-64.exe`
([`ai/StockfishEngine.java`](src/ai/StockfishEngine.java)), talking UCI over stdin/stdout.
Every single call to `getBestMove` resends `uci` / `isready` / `ucinewgame` / skill-level
before asking for a move ([`ai/StockfishEngine.java:76-107`](src/ai/StockfishEngine.java#L76))
— there is no persistent session/position tracking, so Stockfish restarts its "conversation"
from scratch each move. Output is read by polling `reader.ready()` after a fixed `Thread.sleep`
rather than a dedicated reader thread, and the search is bounded by both a movetime (`150`ms)
**and** a wall-clock retry loop in `Input.makeEngineMove`
([`main/Input.java:66-97`](src/main/Input.java#L66-L97)) that keeps re-asking for up to
1200ms if the first answer wasn't a legal/parseable move. This — not engine strength — is the
likely cause of "Stockfish plays badly": it's being given a 150ms budget per attempt at a
throwaway skill level, hard-capped to whatever the first parseable UCI reply is.

### 2.6 UI (`main.Board`, `main.Input`, `GUI.*`, `main.setting.*`, `main.savedGames.*`)

[`main/Board.java`](src/main/Board.java) `extends JPanel` and is simultaneously: the Swing
paint routine, the mouse-drag animation host, the audio trigger, the promotion-dialog launcher,
**and** the owner of the authoritative `BoardState` plus the move-execution logic itself
(`makeMove`, `movePawn`, `moveKing`, `promotePawn`, `updateGameState`). There is no separation
between "the widget that draws a board" and "the thing that knows chess rules" — they are the
same class. [`main/Input.java`](src/main/Input.java) (a `MouseAdapter`) owns *both* engine
instances (`StockfishEngine engine` and `myEngine myEngine`) and decides which one to invoke
per move based on `SettingPanel.skillLevel` vs.a hard-coded threshold
(`switchToStockFish = 13`). [`main/Main.java`](src/main/Main.java) is the Swing entry point,
but also contains a hand-rolled "computer vs. computer" game loop
(`Main.play()`, [`main/Main.java:131-194`](src/main/Main.java#L131-L194)) built from a
`SwingWorker`, a shared `CountDownLatch`, and `Thread.sleep(6000)` polling — a fourth,
ad hoc concurrency pattern layered on top of the two already inside `myEngine`/`Input`.

### 2.7 Persistence

[`main/savedGames/SaveGame.java`](src/main/savedGames/SaveGame.java) /
`LoadGame.java` just write/read a flat list of FEN strings to a text file; there is no
database, no game metadata (opponent, result, date), and nothing that could later be used as
training data. `ai/openingBook/` has a complete Retrofit/OkHttp/Gson client aimed at the Lichess
API plus a binary opening-book file format, but `myEngine`'s only call site for it is commented
out ([`ai/myEngine.java:34-44, 254-265`](src/ai/myEngine.java#L34-L44)) — none of it currently
runs.

## 3. Dead and parallel code left in the tree

These don't affect runtime behavior but they actively mislead anyone reading the codebase,
since nothing distinguishes them from live code:

| Path | Status |
|---|---|
| `player/ai/MoveStrategy.java`, `MiniMax.java`, `BoardEvaluator.java`, `player/Player.java` | A second, complete, unused minimax/evaluator abstraction (interface-based `MoveStrategy`) that nothing constructs or calls. Its class name (`MiniMax`) differs from the live one (`Minimax`) only by capitalization. |
| `pieces/Piece2.java`, `pieces/PieceUT.java` | Unreferenced alternate `Piece` classes. |
| `ai/EvaluationLevel2.java`, `ai/ChessMoveConverter.java` | Unreferenced. |
| `ai/BoardState.getAllPossibleMoves()` / `getAllPossibleMovesForASide()` | Private/public methods with zero call sites outside their own class. |
| `ChessServer/ChessServer.java` | Entirely commented out (a Spark REST stub). |
| `ai/openingBook/*` (Retrofit/Lichess client, binary book reader) | Fully implemented but never invoked. |
| `ai/TranspositionTable.java`, `ai/BitBoard/ZobristHashing.java` | Implemented but wired into `Minimax` only as commented-out lines. |

## 4. How the pieces actually interact today

### 4.1 Package dependency graph is circular, not layered

A "clean" chess engine would layer roughly as `pieces/model → move-gen/rules → engine → UI`,
each layer only depending on the ones below it. Here, every package imports every other
package:

- `pieces.*` imports `ai.BoardState` (its own container) **and** `main.Board`, `main.Move`,
  `main.setting.ChoosePlayFormat` — i.e. a chess piece class depends on the Swing panel and on
  global UI settings just to compute its own pixel position at construction time
  (`Pawn`'s constructor reads `Board.tileSize` and `ChoosePlayFormat.isPlayingWhite` directly,
  [`pieces/Pawn.java:15-26`](src/pieces/Pawn.java#L15-L26)).
- `ai.BoardState` imports `main.CheckScanner`, `main.Move`, `main.setting.ChoosePlayFormat`.
- `main.CheckScanner` imports `ai.BoardState` back, and also reaches into `main.Board`'s
  `public static Piece selectedPiece` field mid-algorithm
  ([`main/CheckScanner.java:48, 64, 117`](src/main/CheckScanner.java#L48)) to skip a piece
  that's mid-drag — rules logic reading live UI selection state.
- `ai.myEngine` imports `main.Board` and `main.Main` (to call `Main.showEndGameMessage`).
- `ai.Minimax` and `ai.BitBoard.BitBoardEvaluate` import `main.setting.ChoosePlayFormat`.

So `main ↔ ai ↔ pieces` form one mutually-dependent cluster — there is no package you could
extract, unit-test, or reuse in isolation without dragging in the other two, and no compiler
boundary is preventing any of this from getting worse.

### 4.2 Walkthrough: a human dragging a piece

1. `Input.mousePressed`/`mouseDragged`/`mouseReleased` (a `MouseAdapter` registered directly on
   the `Board` panel) constructs a `Move` and asks `board.state.isValidMove(move)` — validated
   via the **object-oriented** rules (§2.2/§2.3, path 1).
2. `Board.makeMove(move)` mutates `BoardState` fields directly, plays a sound
   (`AudioPlayer`), starts a `ChessAnimation`, and — as part of building the move's PGN string
   — triggers the **third** check-detection path (§2.3) as a side effect of formatting.
3. `Board.updateGameState` re-derives game-over status **again**, independently, by calling
   `CheckScanner` once more and consulting `SavedStatesForDraws` (a separate static repetition
   tracker) and a 50-move counter kept on `BoardState`.
4. If it's the engine's turn next, `Input.makeEngineMove` spins up a raw `Thread` (Stockfish
   path) or delegates to `myEngine.makeMove` (its own `ExecutorService`), which **itself**
   calls back into `Board.makeMove`, closing a loop where the "AI" layer drives the UI layer
   which drives the "AI" layer.

### 4.3 Walkthrough: the engine's turn (skill level ≥ 13, i.e. Stockfish)

`Input.makeEngineMove` spins a new thread that busy-loops (up to 1200ms, `Thread.sleep`-free
tight loop) calling `StockfishEngine.getBestMove`, which itself restarts the UCI handshake
every call and polls output with `Thread.sleep(180)`. A returned move is validated **again**
through the object-oriented `BoardState.isValidMove` before being applied — so even a trusted
engine's move is re-validated through the slower, brute-force legality path.

## 5. Biggest architectural flaws, ranked

1. **UI and domain/engine logic are the same objects, not merely "coupled".**
   `Board extends JPanel` *is* the rules engine, the animation host, the audio trigger, and the
   dialog launcher all at once (§2.6). There is no `Game`/`Rules` class you could run headless,
   write a unit test against, or reuse for the (currently dead) `ChessServer`. Any change to
   how a move is applied risks breaking painting, sound, and engine turn-taking simultaneously,
   because they're all the same method (`Board.makeMove`).

2. **Two board representations and three independent check/checkmate/stalemate
   implementations, with no single source of truth** (§2.1, §2.3). This is the most direct
   explanation for the bugs called out in the last commit ("doesn't recognize draws",
   "sometimes avoids mate"): a fix applied to `CheckScanner` doesn't touch `BitBoard`'s
   `isCheckOn`, and vice versa, so the UI and the engine can legitimately disagree about
   whether a position is check, mate, or a draw.

3. **Circular package dependencies (`main ↔ ai ↔ pieces`)** with no compiler-enforced boundary
   (§4.1). A `Piece` needing `Board.tileSize` and `ChoosePlayFormat.isPlayingWhite` just to
   exist means the "model" layer cannot be instantiated, tested, or reasoned about without the
   Swing UI and its global settings.

4. **Global mutable static state doubles as an implicit parameter channel into the search
   algorithm.** `ChoosePlayFormat.isPlayingWhite/isOnePlayer/isComputersGame/
   isEnginePlayingBlack` and `SettingPanel.skillLevel` are read from inside `Minimax.minimax`
   itself to decide root-move bookkeeping (§2.4), and are routinely flipped-and-restored as a
   hack to reuse a method meant for "my move" when computing a hint or a forced move for the
   other side (e.g. [`main/Input.java:130-133`](src/main/Input.java#L130-L133),
   [`ai/myEngine.java:100-105`](src/ai/myEngine.java#L100-L105)). This is not thread-safe (the
   `Minimax` class itself is all `static` fields), and makes the search's behavior depend on
   UI state that has nothing to do with the position being searched.

5. **Ad hoc, inconsistent concurrency.** At least four different patterns for "do work off the
   UI thread" coexist: a raw `Thread` in `Input.makeEngineMove`'s Stockfish branch, a
   single-thread `ExecutorService` + `Future` in `myEngine`, a `SwingWorker` +
   `CountDownLatch` + `Thread.sleep(6000)` polling loop in `Main.play()`, and one-off
   `new Thread(() -> { Thread.sleep(500); ... })` calls scattered through `Board`/`Input` purely
   to sequence a sound after an animation. Several of these mutate Swing components
   (`board.repaint()`, `JOptionPane`) from background threads without `invokeLater`
   (§2.4), which is a Swing thread-safety violation that can manifest as intermittent
   UI glitches or hangs.

6. **A formatting method has a game-logic side effect.** `Move.setRepresentation()` — called
   to build a PGN string — triggers a full simulate/check/revert cycle on the live board
   (§2.3, path 3). Generating notation should never be able to affect (or be affected by) game
   rules evaluation; here they're the same call chain.

7. **Expensive resource loading tied to the domain model.** `pieces.Piece` decodes and
   rescales the sprite sheet image (`ImageIO.read` + `getScaledInstance`) in an **instance**
   initializer (§4, cross-referenced in [`pieces/Piece.java:23-31`](src/pieces/Piece.java#L23-L31)),
   so every `Piece` object built anywhere — including every board clone created by
   `BoardState.loadPiecesFromFen`, which runs on every move via the notation side effect above
   — redoes this work. A pure rules/model object is paying an image-decoding cost that only the
   renderer needs.

8. **No layering means no testability.** Because `pieces`, `ai`, and `main` all depend on each
   other, and the one class that holds "the rules" (`Board`) is a `JPanel`, there is currently
   no way to write a headless unit test for "is this checkmate" or "is this move legal" without
   booting Swing and wiring up sound/animation collaborators.

9. **Dead and parallel implementations inflate the codebase with no signal for which path is
   live** (§3): a second minimax engine, two extra `Piece` variants, an unused evaluator
   abstraction, a commented-out server, and a fully-built-but-never-called opening-book/Lichess
   client. Anyone changing "the" engine has to first determine, by grep, that `ai.Minimax` (not
   `player.ai.MiniMax`) is the one actually reachable from the UI.

10. **No structured persistence despite that being a stated goal.** Saved games are flat FEN
    text files (§2.7); there is no schema that could support the "database of past games to
    learn from" goal mentioned for this project — that would need to be designed from scratch,
    not extended from `SaveGame`/`LoadGame`.

## 6. Summary

The engine that actually runs today is: `Board` (Swing) → `Input` (Swing) → either
`StockfishEngine` (process-per-call UCI) or `myEngine` → `Minimax` (bitboard alpha-beta) →
`Move` (translated back to the object model) → `Board.makeMove` (mutates `BoardState`, which
also drives painting/sound/notation). Running in parallel, unreachable, sit a second engine
(`player/ai`), a second piece hierarchy, a REST server, and an opening-book client. Meanwhile
three separate pieces of code — `CheckScanner`, `BitBoard`'s bitwise attack tables, and a
simulate-and-revert triggered from PGN formatting — each independently decide whether a king is
in check, and nothing keeps them in agreement. Any redesign should prioritize collapsing these
into one board representation and one rules engine with a real API boundary, before touching UI
or engine strength.
