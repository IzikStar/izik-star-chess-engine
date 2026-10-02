# Phase 4 — Concurrency and the Stockfish integration

**Status: DONE on this branch (2026-10-02), pending merge — decisions in §8, log in §9.** Branch
`phase-4-concurrency-stockfish`, cut from `phase-3-decouple-ui` (PR #2, not merged yet; this
branch is rebased onto `master` once it is). No production code has been touched.

This is the mandatory research step from [REFACTOR_GUIDE.md](../REFACTOR_GUIDE.md) §Phase 4. It
covers every caller of the engines, what the guide's concurrency problem looks like after Phase 3,
four problems reproduced with measurements, the product questions (strength and time per level),
the design forks, and a checkable definition of done.

All timings below were taken on the cloud container (4 cores, JDK 21, Stockfish 16 from apt). They
are relative evidence, not a promise about the owner's machine. Probes live outside the repo;
the ones worth keeping become tests in the increments.

---

## 1. Where Phase 3 left the concurrency problem

ARCHITECTURE.md §5.5 listed four ad hoc patterns: a raw `Thread` in `Input`, an
`ExecutorService` in `myEngine`, a `SwingWorker` + `CountDownLatch` + `sleep(6000)` loop in
`Main.play()`, and `new Thread(() -> sleep(500) …)` sound sequencing in `Board`/`Input`.

Phase 3 already removed all four (the classes or methods that held them are deleted). Today:

| Where | What | Thread |
|---|---|---|
| `game.GameSession.maybeStartEngine` | engine move: `engineExecutor.submit`, result handed to the dispatcher, stale results dropped by a generation counter | one daemon "engine" thread → EDT |
| `game.GameSession.requestHint` | hint: same executor, same hand-back | same "engine" thread → EDT |
| `GameSession` level 0 | `Thread.sleep(1000)` before a random move, inside the engine job | engine thread |
| `main.Board` | animation (`javax.swing.Timer`, 15 ms) and delayed sounds (`Timer`) | EDT |
| `engine.StockfishEngine.getOutput` | `Thread.sleep(waitTime)` then `reader.ready()` polling | engine thread |

Nothing touches Swing off the EDT any more (checked: every `GameListener` callback is delivered
through the dispatcher, which is `SwingUtilities::invokeLater` in `Main`; `Board`'s timers are
Swing timers). So the guide's "one pattern" goal is **mostly met in shape**. What is still wrong
is the behaviour of that one pattern, and the Stockfish bridge.

## 2. Every caller of an engine (guide research item 1)

| Caller | Path | Level used |
|---|---|---|
| Engine's turn, human vs engine | `GameSession.maybeStartEngine` → `EngineSelector.move(fen, level)` | `GameConfig.skillLevel` (0-18) |
| Computer vs computer ("New computer Game") | same, both sides | skill 6 = UI Level 4 (set by `Main`) |
| Hint button | `GameSession.requestHint` → `EngineSelector.hint(fen)` | Stockfish skill 20; fallback built-in at 10 |
| After undo / new game / config change | `cancelEngine()` then `maybeStartEngine()` | as above |
| Level selection | `EngineSelector`: < 13 → `MinimaxEngine`; ≥ 13 → `StockfishEngine`, fallback built-in at 3 | |

The UI offers Level 1-10 = skill 0, 2, …, 18. Built-in depth = skill / 2 (+1 with ≤ 12 pieces,
+2 with ≤ 8). So Level 1 is random, Levels 2-7 are depth 1-6, Levels 8-10 are Stockfish.

The Phase 3 API already gives the engine side what it needs: FEN (`Game.fen`), legality
(`Rules.isLegal`), the game's history (`Game.history()` FENs, `Game.moves()` UCI moves). One
gap: the `Engine` interface takes only a FEN, so Stockfish cannot be given `position … moves …`
(Fork C).

## 3. Problems reproduced

### 3.1 A hint backlog starves the engine (new)

Hints and engine moves share one single-thread queue, a hint is never cancelled, and nothing
stops a second hint request while one is pending. A headless stress run (human-vs-engine at
level 2 with random moves, undo, colour flips, new games, and hint requests on 10% of 3,000
operations, Stockfish missing so hints fall back to depth 5) ended with **the engine still not
having answered 60 s after the last operation**: its move was queued behind dozens of
5-20 s hint searches. The same run without hint requests ended with the engine answering
(0 errors). In the app this is a user clicking "hint" a few times and the engine going silent for
minutes.

### 3.2 Cancelling does not stop a search (new)

`cancelEngine()` calls `Future.cancel(true)`, but `Minimax` never checks for interruption, so a
cancelled search runs to the end and the next search waits behind it. Probe: level 10 (depth 5),
human plays 1.e4, takes it back after 0.5 s and plays 1.d4: **the engine answered after 48.9 s**,
about two full searches (one 1.e4 search nobody wants, then the 1.d4 one).

### 3.3 Search time is unbounded and climbs steeply with level

Built-in engine, one search per position (ms; a second run in parallel gave similar numbers):

| UI level (skill) | depth | start | after 1.e4 | Italian | middlegame | rook endgame |
|---|---|---|---|---|---|---|
| 2 (2) | 1 | 84 | 13 | 20 | 19 | 39 |
| 3 (4) | 2 | 176 | 56 | 46 | 31 | 81 |
| 4 (6) | 3 | 579 | 1026 | 620 | 1299 | 432 |
| 5 (8) | 4 | 4150 | 4624 | 3730 | 3450 | 318 |
| 6 (10) | 5 | 9267 | 21744 | 21426 | 34696 | 8987 |
| 7 (12) | 6 | > 90 s | | | | |

So UI Level 6 thinks 10-35 s a move and Level 7 is unplayable. A JFR profile of a depth-5
search: **68% of samples are inside `BitBoard.isCheckOn` / `getAllAttackedTiles`**, called while
building every child board to filter illegal moves; 3% are `BitBoardOperations.printBitBoard`
(debug output still running inside the search). The `getNumOfNodes` pre-walk in
`Minimax.search` is wasted work but not the main cost (removing it changed little). The
transposition table exists but its use is commented out.

### 3.4 The Stockfish bridge spends most of its time on the handshake

With Stockfish installed, `StockfishEngine.bestMove` on three positions at levels 13/15/17/21:
**~685 ms per move, of which 150 ms is Stockfish thinking** (`go movetime 150`). The rest is
`uci` / `isready` / `ucinewgame` / `setoption` / `position` resent on every move, each followed by
a `sleep(100)`-and-poll wait. The first call took 1,646 ms (one retry: `bestmove` had not arrived
within the fixed 180 ms read window). `ucinewgame` every move also throws away Stockfish's hash,
and a FEN-only `position` hides the game history, so Stockfish cannot see repetitions. All levels
think for the same 150 ms; only "Skill Level" differs.

### 3.5 Unattended computer-vs-computer

20 games at level 2 (both sides), headless `GameSession`: **20 finished, 0 hangs, 0 exceptions**,
1,540 plies in 15 s. The app's "New computer Game" plays skill 6 (depth 3, 0.4-1.3 s a ply), so a
50-game run there takes about an hour; it was not attempted yet and is part of the definition of
done.

## 4. Design forks — need a decision before implementation

### Fork A — strength and time per level (product decision)

The guide asks for this to be written down rather than inherited.

- **A1 (recommended): keep depth as the strength knob, add a hard time cap.** The built-in engine
  searches with iterative deepening up to its level's depth and stops at a cap (proposal: 5 s for
  every level), playing the best move of the last finished depth. Levels 2-5 play as today
  (all under 5 s in §3.3); Levels 6-7 stop taking 10-90 s and get stronger as the search gets
  faster.
  Stockfish: Skill Level as today (13/15/17) plus a move time per level (proposal: 300 / 600 /
  1000 ms); hints: skill 20, 1000 ms.
- A2: time is the only knob (each level = a time budget). Simpler to explain, but low levels
  would play much stronger on a fast machine and weaker on a slow one.
- A3: leave times as they are. Rejected by the measurements.

### Fork B — the concurrency primitive

- **B1 (recommended): keep the one engine thread from Phase 3, and make every job cancellable.**
  A search gets a cancel token/deadline that `Minimax` checks every N nodes and that
  `StockfishEngine` turns into UCI `stop`. At most one hint is pending: a new hint request
  replaces it, and the engine's own move cancels it. Undo / new game / config change cancel the
  running job for real, so the next one starts at once. Results still come back through the
  dispatcher with the generation check.
- B2: separate threads for hints and moves. Two built-in searches could run at once on a
  multi-core machine, but Stockfish is one process that searches one position at a time, so it
  needs a queue anyway. More states for little gain.
- B3: `CompletableFuture` chains. Same semantics as B1 with a different API; no behaviour gain.

### Fork C — Stockfish session

- **C1 (recommended): one Stockfish process per `GameSession`**, started on first use, with a
  reader thread feeding a queue (no `sleep` polling). Handshake (`uci`, options) once;
  `ucinewgame` only on a new game; each move sends `position fen <start> moves <uci…>` and
  `go movetime <ms>` and waits for `bestmove` with a timeout; cancellation sends `stop`. A
  crashed or missing process is reported once and the built-in engine takes over, as today. The
  `Engine` interface takes a small request (start FEN + moves + level + cancel token) instead of a
  bare FEN; `MinimaxEngine` uses its final FEN.
- C2: persistent process but keep sending a bare FEN. Smaller change; Stockfish stays blind to
  repetitions in the game.

### Fork D — how much search speed belongs in this phase

- **D1 (recommended): only what the concurrency work needs, plus free wins.** Cancellation,
  iterative deepening with the time cap, remove the `getNumOfNodes` pre-walk and the debug
  printing inside the search. Leave the move generator alone. Switching on the transposition
  table and making legality checks cheap (68% of the time) becomes its own phase ("4b"), with
  perft tests as its safety net, because it changes the rules authority from Phase 2.
- D2: also do the move-generator speed-up and the transposition table here. Faster engine
  sooner, but one PR that changes threading, Stockfish and the rules core at once.

### Fork E — how "unattended computer-vs-computer" is proven

- **E1 (recommended): a stress test tagged `stress`** (run with `-Pstress`, not on every build):
  many engine-vs-engine games at a capped level, plus the random undo/new-game/hint churn from
  §3.1, asserting every game ends and the engine always answers. `-Psmoke` runs a short version.
- E2: manual runs only.

## 5. Definition of done (guide exit criteria, made checkable)

1. One documented concurrency pattern computes every engine move and hint (B1), and the threading
   contract is written in `GameSession`'s Javadoc and ARCHITECTURE.md.
2. Undo / new game / config change during a search: the next engine move starts within a few
   hundred ms (test, replacing the 48.9 s of §3.2).
3. Repeated hint requests never delay the engine's move by more than one hint (test, from §3.1).
4. Stockfish keeps one process per game; no `Thread.sleep` remains in `StockfishEngine`; a
   missing or crashed Stockfish falls back to the built-in engine (tests with a fake UCI
   process, so they run without Stockfish installed).
5. No search exceeds its level's time cap by more than a small margin (test).
6. No Swing component is touched off the EDT (`LayeringTest` already keeps Swing out of
   `game`/`engine`; plus a review of `main`).
7. `-Pstress`: 50 engine-vs-engine games plus the churn run finish with no hang and no exception.
8. `mvn test` and `-Psmoke` green; the app driven under Xvfb at Levels 1, 6, 7 and 9 (with
   Stockfish), hint, undo during engine thought, computer-vs-computer.

## 6. Proposed increments (each its own commit, green and playable)

1. Safety net: tests for §3.1 and §3.2 (red, tagged `known-bugs`), a fake UCI engine for tests.
2. Cancellable searches: cancel token + deadline in `Minimax`, iterative deepening, time caps
   per level (Fork A), remove the pre-walk and debug printing.
3. `GameSession` job policy: real cancellation, one pending hint, engine move cancels a hint.
4. Stockfish session (Fork C): persistent process, reader thread, `position … moves`, move time
   per level, `stop`, fallback; `Engine` takes a request object.
5. Stress test (Fork E) and the Xvfb run; docs (ARCHITECTURE §2.5/§5.5, README, guide status).

## 7. Rollback

Each increment is one commit on this branch; reverting one restores the previous green state.
The branch is merged with `--no-ff` like the earlier phases.

## 8. Decisions (locked 2026-10-02, project owner)

All recommendations approved: **A1** (depth per level + 5 s cap; Stockfish 300 / 600 / 1000 ms,
hints 1000 ms), **B1** (one engine thread, cancellable jobs, at most one pending hint),
**C1** (one Stockfish process per session, `position fen … moves …`), **D1** (cancellation, time
cap and free wins only; move-generator speed and the transposition table become Phase 4b),
**E1** (`-Pstress` test).

## 9. Increment log

### Increment 1 — safety net (2026-10-02)

`game.EngineJobsTest`, on real threads, tagged `known-bug` (red, excluded from `mvn test`):
`undoCancelsTheRunningSearch` (take back during a depth-6 search, the engine must answer the new
move within 10 s) and `hintsDoNotStarveTheEngine` (five hint requests, then a human move: the
depth-1 reply must come within 10 s). Both fail on this commit (`-Pknown-bugs`).

### Increments 2 + 3 — cancellable searches, a time cap, one pending hint (2026-10-02)

- `engine.SearchRequest` (position, start FEN + moves, level, `Cancellation`) replaces the bare
  FEN in `Engine` / `EngineSelector`; `bestMove(fen, level)` stays as a convenience.
- `ai.Minimax.getBestMove(board, maxDepth, stop)`: iterative deepening, each depth an independent
  search, the stop signal looked at every 256 nodes; depth 1 always finishes. Without a stop it
  plays the same move as one fixed-depth search (tested). `MinimaxEngine` stops at
  `TIME_CAP_MS = 5000` or on cancellation.
- `GameSession`: every job carries a `Cancellation`; take-back / new game / config change cancel
  the engine and the hint; the engine's own move cancels a pending hint; a hint for a position
  that already has one pending is ignored. Threading contract written in its Javadoc.
- Free wins: the `getNumOfNodes` pre-walk, the per-search `System.out` lines, and a leftover debug
  check in `BitBoard.getMovesForColor` (it rendered every generated board to a string and compared
  it with one position) are gone.
- The §3.1 / §3.2 tests are green and untagged; new: `cancellationReachesTheEngine`,
  `builtInRespectsTimeCap`, `cancelledRequestGetsNoMove`, `deepeningMatchesFixedDepth`.
  `mvn test` **84 green**, `-Psmoke` green.

Search times after this commit (ms, same positions as §3.3):

| UI level (skill) | start | after 1.e4 | Italian | middlegame | rook endgame |
|---|---|---|---|---|---|
| 2 (2) | 52 | 8 | 15 | 17 | 20 |
| 3 (4) | 108 | 47 | 27 | 19 | 15 |
| 4 (6) | 316 | 243 | 275 | 260 | 98 |
| 5 (8) | 1463 | 1016 | 2328 | 1533 | 173 |
| 6 (10) | 5042 (cap) | 5002 (cap) | 5004 (cap) | 5048 (cap) | 2205 |
| 7 (12) | 5041 (cap) | 5021 (cap) | 5012 (cap) | 5010 (cap) | 5004 (cap) |

Removing the debug check alone roughly halved every search. Levels 6 and 7 now answer in 5 s, but
in the middlegame they usually finish only depth 4, so they play like Level 5 until the move
generator gets faster (Phase 4b).

### Increment 4 — one Stockfish session per game (2026-10-02)

`engine.StockfishEngine` rewritten: the process starts on first use, the UCI handshake runs once,
a reader thread feeds a queue (no `sleep` polling), `isready`/`readyok` before each search drops
any stale output, `ucinewgame` only when the start position changes or the move list gets shorter
(take-back), `position fen <start> moves …` every move, Skill Level resent only when it changes,
`go movetime` 300 / 600 / 1000 ms for Levels 8 / 9 / 10 and 1000 ms for hints. Cancel sends
`stop`; an interrupt of the engine thread is not treated as a failure. A crash, a silent process or
an illegal answer kills it and the next request restarts it; after 3 failures in a row, or if the
executable cannot be launched or does not speak UCI, it reports unavailable and the built-in engine
answers.

Tests: `engine.StockfishSessionTest` against `engine.FakeUci` (a fake UCI engine run as its own
process, modes normal / hang / crash / illegal), plus one test against a real Stockfish that runs
when `/usr/games/stockfish` (or `-Dstockfish.path`) exists. `mvn test` **90 green**, `-Psmoke` 3
green.

Same probe as §3.4 with Stockfish 16: the first move pays the process start (~1 s), after that
each move takes its move time plus ~5 ms (Level 8: 309 ms, Level 9: 606 ms, Level 10 and hints:
~1,005 ms); a forced mate comes back in 8-25 ms. Before: ~685 ms for every move, 150 ms of it
thinking.

### Increment 5 — stress test, the real app, docs (2026-10-02)

- `game.StressTest`, tagged `stress` (`mvn test -Pstress`, new profile): 50 engine-vs-engine
  games at Level 2, 5 at the app's computer-game skill 6, 3 Stockfish-vs-Stockfish at Level 8
  (when installed), and 3,000 random moves / take-backs / colour flips / new games / hint
  requests ending with the engine answering. **4 green in 136 s.** A short version
  (`stressSmoke`: 5 games + 300 operations) runs with `-Psmoke`.
- Driven the real app under Xvfb with Stockfish 16 and a `RepaintManager` that flags any repaint
  off the event thread: engine reply after 1.e4 at UI Level 1: 1.0 s (the cosmetic pause),
  Level 6: 5.0 s, Level 7: 5.0 s (cap), Level 8: 1.4 s (first Stockfish move, process start),
  Levels 9 / 10: 1.0 s; a new game during a Level 7 search then 1.e4: reply in 5.05 s (the stale
  search was dropped at once); five hint requests then a move at Level 3: reply in 65 ms;
  computer-vs-computer: 94 plies in 40 s; **0 off-EDT repaints**.
- Found while driving it: the move times were keyed to Stockfish's skill (13/15/17) instead of the
  UI's skill (14/16/18), so Level 8 thought 600 ms; fixed (300 / 600 / 1000) and pinned in
  `StockfishSessionTest`.
- In the app "go back" is accepted only on a human's turn (unchanged from Phase 3), so a
  take-back during the engine's thought cannot happen from the UI; the session handles it anyway
  (tested).
- ARCHITECTURE.md (Phase 4 banner, §5.5, §6), README (search, Stockfish, roadmap) and
  REFACTOR_GUIDE.md (Phase 4 status; Phase 4b split out) re-synced. `mvn test` **90 green**,
  `-Psmoke` 4 green, `-Pstress` 4 green.

