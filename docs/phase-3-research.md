# Phase 3 — Extract a headless rules API and decouple the UI

**Status: RESEARCH — awaiting the owner's decisions in §4 before any production code changes.**
Baseline: `master` @ `5d3d3bd` (Phase 2 merge). `mvn test` → **49 green** (built with
`-Dmaven.compiler.release=21`, see §4 Fork 0). No production code has been touched.

This is the mandatory research artifact from [REFACTOR_GUIDE.md](../REFACTOR_GUIDE.md) §Phase 3:
a call-site audit of every guide research item, two bugs reproduced while auditing, the
behaviours that must survive, the open design forks, a rollback plan, and a concrete definition
of done. Forward context carried from Phase 2: **the long-term goal is a micro-services backend
with a React UI**, so forks are biased toward a headless, event-driven core.

---

## 1. Call-site audit

### 1.1 `ChoosePlayFormat.*` and `SettingPanel.skillLevel` — every read and write

`main.setting.ChoosePlayFormat` is five `public static` fields and nothing else:
`isPlayingWhite`, `isEnginePlayingBlack`, `isOnePlayer`, `setSkillLevel`, `isComputersGame`.
`SettingPanel.skillLevel` is a sixth (`public static int`, values `0,2,…,18`).

| Field | Writers | Readers outside the UI layer |
|---|---|---|
| `isPlayingWhite` | `SettingPanel` button (37); `Board.restart` (495); **flip-and-restore hacks** `Input` 102/104, 130/132, 182/184; two-player auto-flip `Input` 257, 330 | `Minimax.minimax` (97); `BitBoardEvaluate.evaluate` (253); every `pieces/*` constructor (pixel position); `BoardState.movePawnForClone` (540) |
| `isOnePlayer` | `SettingPanel` button (24) | `BoardState.movePawnForClone` (540) |
| `isComputersGame` | `Main.play` 132, 153, 158, 169, 174, 181 | `Minimax.minimax` (97); `BitBoardEvaluate.evaluate` (253) |
| `isEnginePlayingBlack` | `Main.play` 146, 162 | `Minimax.minimax` (97); `BitBoardEvaluate.evaluate` (253) |
| `setSkillLevel` | **nobody** — always `0` | `Input.makeEngineMove` (74) |
| `SettingPanel.skillLevel` | `SettingPanel` level buttons (55); **flip-and-restore hacks** `Input` 101/105, 129/133, 181/185, `myEngine` 92/114; `Main.play` 148, 164 | `myEngine` 63, 127, 161, 245-251 (search depth); `StockfishEngine` field initialiser (22) |

UI-only readers (fine to keep as UI state, but they should read one config object, not statics):
`Board` 115, 138, 184-220 (board orientation), 262, 299, 366, 372, 452, 466, 487-506, 533;
`Input` 38, 47, 53, 213, 245, 318; `Main.updateScores` 204, 217.

**What the statics are really encoding** — four independent concepts are folded into them:

1. **Game mode**: human-vs-engine / human-vs-human / engine-vs-engine
   (`isOnePlayer`, `isComputersGame`).
2. **Which side the human plays** (`isPlayingWhite` in one-player mode).
3. **Board orientation** (`isPlayingWhite` again — in two-player mode it is *rewritten after
   every move* (`Input` 257/330) purely so the board flips to face the side to move).
4. **"Whose move is the search choosing?"** (`isPlayingWhite` / `isEnginePlayingBlack` read
   inside `Minimax` and `BitBoardEvaluate`). This is the one that must not be UI state at all.

**The flip-and-restore pattern, all four instances.** Each flips `isPlayingWhite` (and overrides
`skillLevel`) so that a search written for "the engine's move" runs for the *other* side, then
flips back:

| Site | Purpose | Why it is broken |
|---|---|---|
| `Input.makeEngineMove` 100-105 | Stockfish gave no legal move in 1200 ms → fall back to `myEngine` at level 3 | `myEngine.makeMove` is **asynchronous** (returns a `Future`), so the flags are restored *before* the search runs; the search then sees whichever state the flags are in when it reaches line 97 |
| `Input.takeEngineHint` 128-133 | `myEngine` hint (dead: `takeMyEngineHints = false`) | same |
| `Input.takeEngineHint` 180-185 | Stockfish hint timed out → `myEngine` hint at level 10 | same |
| `myEngine.makeMove` 91-114 | retry with a random move at level 0 | runs on the executor thread, races the UI thread's own reads |

**Key finding — the search never actually needs to be told whose move it is.** In every caller,
the side the search is choosing for is *the side to move at the root*. `Minimax` line 97's
condition (`isPlayingWhite == !whiteToMove`, i.e. "the side to move is not the human") is just
an indirect way of saying `depth == maxDepth` (root), and the flips exist only to keep that
indirect test true when the search runs for the human's side (hints). Likewise
`BitBoardEvaluate.evaluate`'s `switchSides` is "the root side is Black". Passing the root side
explicitly (or deriving it from the root position) deletes all four hacks rather than relocating
them.

### 1.2 Two bugs reproduced during the audit

Both reproduced with a throw-away probe calling `Minimax.getBestMove` directly (not committed;
will become characterization tests in increment 1).

**Bug A — "the engine stops playing" (ARCHITECTURE §2.4), confirmed.** With the statics in the
torn state (engine to move as White while `isPlayingWhite == true`), `Minimax.getBestMove`
throws `NullPointerException: Cannot invoke "ArrayList.isEmpty()" because "Minimax.bestMoves" is
null` at depth 2 and 4. In the app that exception is swallowed by `Input.makeEngineMove`'s
`catch (Exception e) {}` (`Input` 59) and the turn ends with no move. Root cause is exactly §1.1's flip pattern
plus `bestMoves` being a `static` that is only initialised when the static-derived `lastDepth`
holds. Fix in this phase: root-side as a parameter + `bestMoves` local to one search call. The
swallowed exception / concurrency is Phase 4, but nothing will be throwing it any more.

**Bug B — new: when the engine plays White it cannot deliver checkmate.** In
`7k/1R6/6K1/8/8/8/8/R7 w - - 0 1` (Ra8# and Rb8# both mate in one) the engine as White plays
**Kf7, stalemating Black**, at depth 2 and depth 4. The mirrored position with the engine as
Black (`r7/8/8/8/8/6k1/1r6/7K b`) finds the mate. Cause: `BitBoard.getStatus()` returns
`Integer.MIN_VALUE` when Black is mated (black-positive scale), and `BitBoardEvaluate.evaluate`
returns `switchSides ? value : -value`. For a White engine that is `-Integer.MIN_VALUE`, which
**overflows back to `Integer.MIN_VALUE`** — the search scores "I deliver mate" as the worst
possible outcome and prefers a draw. This only shows when the human plays Black (or in
computer-vs-computer), which is likely why it went unnoticed. It lives in the exact lines this
phase rewrites (the `switchSides` read of `ChoosePlayFormat`), so the fix (mate scores as
`±MATE` constants well inside `int` range, never negating `MIN_VALUE`) belongs here — see Fork 5.

Secondary observation (not a bug to fix now): mate-in-1 and mate-in-N score identically, so at
depth 4 the Black engine in the mirrored position played a non-mating rook move it judged
equivalent. Preferring shorter mates is Phase 4 / search-quality work.

**Bug C — Stockfish always plays at Skill Level 0.** `Input.makeEngineMove` sets
`engine.setSkillLevel(SettingPanel.skillLevel - 1)` (70), then inside the retry loop immediately
overwrites it with `engine.setSkillLevel(ChoosePlayFormat.setSkillLevel)` (74) — a static nobody
ever writes. So levels 8, 9, 10 (the Stockfish levels) all play at Stockfish skill 0. Retiring
`ChoosePlayFormat` forces a decision on this line — see Fork 6.

### 1.3 Reaching into `Board` (and `Main`) from outside the UI

| From | Reach | Purpose |
|---|---|---|
| `ai.myEngine.makeMove` 77-87, 99-109 | `realBoard.makeMove(move)`, `realBoard.input.isStatusChanged/isCheckMate/isWhiteTurn/isStaleMate`, `Board.selectedPiece = null`, `realBoard.repaint()`, `realBoard.updateGameState`, `Main.showEndGameMessage` → `JOptionPane` | the engine *plays* its move into the Swing board and pops the game-over dialog itself — `makeMove`/`repaint` from the executor thread, not the EDT |
| `ai.myEngine.giveHint` 140-143 | writes `realBoard.hintFromC/R`, `hintToC/R` | hint display |
| `pieces.*` constructors | `Board.tileSize`, `ChoosePlayFormat.isPlayingWhite` | pixel position at construction |
| `main.PromotionDialog` | `Board.tileSize` | icon size (UI→UI, fine) |
| `main.setting.SettingPanel` | `Main.board.refresh()` (static `Main.board`) | apply settings |
| `main.ShowScore` | `Main.updateScores` (static labels) | material score |
| `Board.makeMove` → `input.engine.promotionChoice` / `input.myEngine.promotionChoice` (372-377) | reads the engine's last promotion choice through `Input` | engine promotions travel by side channel, not in the move |

`Board.selectedPiece` is `public static`; since Phase 2 no rules code reads it — its only
non-UI writer left is `myEngine` (above). `Board.state` (the live `BoardState`) is
package-private but is handed to `myEngine` at construction (`Input` 36, `Board` 65), so the
engine searches the *live* object the UI mutates.

`Input` is a fourth role on top of mouse handling: it owns **both engines**, the Stockfish retry
loop, the hint logic, the game-over flags (`isStatusChanged`, `isCheckMate`, …) that `Board`,
`myEngine` and `Main.play` all read, and a `CountDownLatch` that `Main.play` swaps by replacing
`board.input` wholesale (`Main` 137-138, 179).

### 1.4 Who mutates game state

There is no single "apply a move" path. `Board.makeMove` (231-304) hand-applies the move to the
`BoardState` object model — en passant (`movePawn`), castling rook (`moveKing`), promotion
(`promotePawn` / `promotePawnTo`, part of it on a `new Thread` 401-410), clocks, side to move,
`SavedStatesForDraws` (a second, static threefold tracker), the move list panel, the score — and
*then* consults `rules.Rules` for status. Phase 2 made `rules.Rules` the authority for
*questions*; the *answer to "what is the position after this move"* is still computed twice
(`Board.makeMove`'s hand-application vs. `Rules.applyMove`), with nothing checking they agree.
`BoardState.makeMove` (599) is a third copy, used only by tests.

`BoardState` also keeps two FEN serializers (`convertPiecesToFEN`, with a broken castling-`-`
condition at 383 and an en-passant field derived from "last mover" bookkeeping, and the clean
`toRulesFen`). `Board` stores and restores positions with the broken one (`savedStates`,
`goBack`, `refresh`, `fenCurrentPosition`) and hands it to Stockfish and `myEngine`.

### 1.5 Animation and audio — how they are actually triggered

- **Animation**: `GUI.ChessAnimation` holds a `Piece` and **writes `piece.xPos/yPos`** each paint.
  Created from `Board.makeMove` (264-270), `moveKing` (331-333), `movePawn`/`promotePawn` (380,
  400), `updateGameState` (539, a zero-distance "pulse" on the mated king). A 1 ms Swing `Timer`
  (`Board` 68-77) repaints continuously. Drag-and-drop bypasses the animation via
  `input.isDraggingMove` (a flag set around the `makeMove` call, `Input` 305-307).
- **Audio**: `GUI.AudioPlayer` instances in `Board`, `Input`; 20 call sites, all synchronous in
  the move path, plus three `new Thread(() -> { sleep(500); play…(); })` sequencers
  (`Board` 381, 401; `Input` 249, 322) to sound *after* an animation.
- Both are pure functions of "a move happened (with these properties: capture / castle /
  promotion / check / mate / draw, who moved, was it the human)". That is exactly the payload of a
  `MoveMade` / `GameOver` event, which confirms the guide's suggested event/listener direction.

### 1.6 Coupling that remains on the rules side

- `rules` → `ai.BitBoard.BitBoardRules` only (clean).
- `ai.BitBoard.BitBoard` still imports `ai.BoardState`, `pieces.*`, `main.Move`, `ai.Minimax`,
  `main.Debug` — via the `BitBoard(BoardState)` adapter constructor and a `main()`. `BitMove`
  imports `main.Move`. `ZobristHashing` builds `BoardState`s in a `main()`.
- `ai.BoardState` imports `main.Move`, `ChoosePlayFormat`, `pieces.*`; `pieces.*` import
  `main.Board`, `main.Move`, `ChoosePlayFormat`, `ai.BoardState`. This is the `main ↔ ai ↔ pieces`
  cycle the exit criteria require gone.

### 1.7 Tests that pin the object model

All 8 characterization test classes drive `ai.BoardState` / `main.Move` / `pieces.Piece` (the
"OO path"), plus `rules.RulesTest`. Deleting the object model means re-pointing those tests at
the headless API first, in the same increment, never deleting assertions — §2.

---

## 2. Behaviours that must be preserved

### 2.1 Automated (must stay green at every increment)

- `mvn test` (49) and `mvn test -Psmoke` (3). Re-pointed, not removed, when their subject moves.
- New in increment 1 (search characterization, currently **untested**): engine finds mate-in-1
  as Black; engine finds mate-in-1 as White (**red today — Bug B**, lands in `-Pknown-bugs`
  and flips green in increment 2); `getBestMove` never throws for either side regardless of UI
  settings (**red today — Bug A**); the engine returns a legal move in a set of fixed positions.

### 2.2 Manual (no automated UI tests exist; script to be walked at the end of the phase)

1. Click-select / click-move and drag-and-drop moves; legal-move dots; capture rings; red check
   border; last-move and hint highlights.
2. 500 ms move animation (click), no animation for drag; castling animates both pieces;
   promotion dialog for the human; engine promotions auto-applied.
3. Sounds: select, move, capture, castle, check, mate (losing sound when the human is mated),
   draw, invalid move, switch (two-player), go-back, hint.
4. Two-player mode: board flips to the side to move after each move, after a short delay.
5. "Play as black": board flips, engine moves first. Level buttons change engine strength;
   levels 8-10 use Stockfish (with myEngine fallback when Stockfish is missing — the
   `showcase-polish` branch adds that fallback, Fork 0).
6. Go back (undoes the engine's and the human's move), New game, Hint, New computer game.
7. Game-over dialog texts (Hebrew) for mate / stalemate / other draws; score panel.

---

## 3. Rollback plan

Branch `phase-3-decouple-ui`, one commit per increment, each green and playable. Any increment
can be reverted alone because the order is "add the new path → move callers → delete the old
path" inside each increment. Merge to `master` `--no-ff` only after the §2.2 script.

---

## 4. Design forks — need a decision before implementation

### Fork 0 — build target and the unmerged `claude/showcase-polish` branch

`master`'s `pom.xml` targets `maven.compiler.release` **26**, which the current LTS (21) cannot
build; the `claude/showcase-polish` branch (5 commits: Java 21 target, Stockfish path config +
graceful fallback when missing, `AudioPlayer` no-device fix, README, untracking IDE files and
binaries) fixes that but was never merged.
- **A (recommended):** merge `showcase-polish` into `master` first, then branch Phase 3 from it.
- **B:** branch from `master` as is and only cherry-pick the Java 21 commit.

### Fork 1 — shape of the headless API

- **A (recommended):** grow the existing `rules.Game` (already FEN history + threefold + UCI)
  into the session API: `play(ChessMove)` returning a `MoveResult` (the SAN, what was captured,
  castling/promotion/en-passant flags, the resulting status), `undo()`, `legalMoves()`,
  `status()`, `fen()`, `sanHistory()`. Add a thin `GameSession` beside it that owns a `Game` +
  `GameConfig` + listeners and decides whose turn it is (human / engine). All Swing-free.
- **B:** keep `ai.BoardState` as the API and strip its Swing dependencies. Rejected-by-default:
  it is mutable, its FEN serializer is buggy (§1.4), and it is the object the cycle runs through.

### Fork 2 — what happens to `ai.BoardState` and `pieces/*`

- **A (recommended):** retire both. `Board` paints from `rules.Position` plus a small UI-only
  render state (selected square, hint, last move, active animations keyed by square). Sprites are
  decoded once into a `PieceSprites` cache (fixes ARCHITECTURE §5.7). Animations move a sprite,
  not a model object. SAN generation moves into `rules` (headless). Tests re-pointed at
  `rules.Game`.
- **B:** keep `BoardState`/`Piece` as a render-only view-model rebuilt from FEN after each move.
  Smaller step, but the cycle and the duplicate move-application survive in weaker form.

### Fork 3 — what replaces `ChoosePlayFormat` / `SettingPanel.skillLevel`

- **A (recommended):** an immutable `GameConfig` record — `mode` (`HUMAN_VS_ENGINE`,
  `HUMAN_VS_HUMAN`, `ENGINE_VS_ENGINE`), `humanColor`, `skillLevel` — owned by `GameSession`,
  changed only via the session (the settings panel calls `session.updateConfig(...)`). **Board
  orientation becomes UI state** derived from config (human color, or side to move in two-player
  mode) — no more rewriting "which side the human plays" after every two-player move. Engines get
  their parameters per call (§Fork 4), so nothing in `ai.*` reads config at all.
- **B:** keep a mutable settings singleton but make it non-static. Smaller diff, keeps the
  "anyone can flip it" problem.

### Fork 4 — engine boundary

- **A (recommended):** one interface, `Engine { ChessMove bestMove(String fen, EngineOptions) }`
  with `MinimaxEngine` (level 0 = random move, else minimax at the current depth formula) and the
  Stockfish adapter behind it, plus a `FallbackEngine` that tries Stockfish then Minimax.
  Engines never touch `Board`, `Main`, `JOptionPane` or config; the session applies the returned
  move and fires events. The search takes the root side from the position (§1.1 key finding),
  `bestMoves`/counters become per-call locals. Hints are just `bestMove` for the side to move.
  Threading stays roughly as is (one background executor in the session, results handed to the
  EDT with `invokeLater`); the real single-concurrency-model work stays **Phase 4**.
- **B:** keep `myEngine`/`Input` orchestration and only remove the statics from the search.
  Fixes Bug A but leaves the engine driving the UI (exit criteria not met).

### Fork 5 — fix Bug B (White engine cannot mate) in this phase?

- **A (recommended):** yes, in the same increment that parameterises `BitBoardEvaluate`. Mate
  scores become `±MATE` constants inside `int` range; the search never negates
  `Integer.MIN_VALUE`. Pinned by a test that is red before, green after.
- **B:** characterize it as a known bug and leave it for Phase 4.

### Fork 6 — Stockfish skill level (Bug C)

- **A (recommended):** wire the level the UI intends (`skillLevel - 1` per the existing line 70)
  — Stockfish levels 8-10 will play noticeably stronger than today.
- **B:** strict parity: keep sending Skill Level 0 explicitly and leave strength to Phase 4.

---

## 5. Micro-services / React implications

`GameSession` + `Game` + `Engine` are exactly the server-side pieces a REST/WebSocket backend
needs: config in, UCI moves in, `MoveResult` / `GameOver` events out. The Swing `Board` becomes
one client of those events; a React client later subscribes to the same events over a socket.
Constraint carried forward: **`rules`, the session and the engines ship with zero
`javax.swing` / `java.awt` / `main.*` / `GUI.*` imports**, enforced by a test that scans imports.

---

## 6. Definition of done (from REFACTOR_GUIDE exit criteria, made checkable)

- No class outside the UI packages imports `javax.swing`, `java.awt`, `main.*` or `GUI.*`
  (import-scanning test). The `main ↔ ai ↔ pieces` cycle is gone.
- `ChoosePlayFormat` is deleted; `SettingPanel.skillLevel` is no longer static; no
  flip-and-restore remains (`grep` clean).
- `Board` contains no chess rules: it never applies a move to a model itself; it renders a
  `Position` and forwards clicks to the session.
- Engines return moves and never call into the UI.
- Bug A and (per Fork 5) Bug B pinned by tests and green; `mvn test`, `-Psmoke` green.
- `ARCHITECTURE.md` re-synced; §2.2 manual script walked (or waived by the owner, as in Phase 2).

---

## 7. Proposed increments (each its own commit, green and playable)

1. **Search safety net.** Characterization tests for `Minimax` (§2.1); Bugs A/B land red in
   `-Pknown-bugs`.
2. **Search takes no UI state.** Root side from the position, per-call search state, `±MATE`
   scores (Fork 5). Delete the four flip-and-restore sites. Bugs A/B green.
3. **`Engine` interface.** `MinimaxEngine`, Stockfish adapter, fallback; engines stop calling
   `Board`/`Main`; promotion travels inside the returned `ChessMove`. Fork 6 applied.
4. **`GameSession` + `GameConfig` + events.** `rules.Game` gains `MoveResult`, `undo`, SAN.
   `Input`/`Board`/`Main.play` drive the session; audio and game-over dialog move to listeners.
   `ChoosePlayFormat` deleted; `SavedStatesForDraws` replaced by `Game`'s threefold.
5. **`Board` becomes a renderer.** Paint from `Position` + render state + sprite cache;
   animations on sprites. Re-point the characterization suite at `rules.Game`; delete
   `BoardState`, `pieces/*`, `main.Move`, and the `BitBoard(BoardState)` adapter.
6. **Boundary test + `ARCHITECTURE.md` re-sync + manual play-through.**

## 8. Decisions

_Pending the project owner._
