# Phase 2 — Unify the board representation and the rules engine

**Status: RESEARCH pass complete (2026-09-05). Awaiting design-fork decisions before implementation.**
Branch not yet cut. Baseline: `master` @ `3a01bc8` (post Phase 1 merge). `./mvnw test` → 23 green,
`-Psmoke` → 3 green, `-Pknown-bugs` → 4 red.

This is the mandatory research artifact from [REFACTOR_GUIDE.md](../REFACTOR_GUIDE.md) — a full
call-site audit, the behaviours that must be preserved, the open design forks, a rollback plan,
and a concrete definition of done. No production code has been touched.

Forward context from the project owner: **the long-term goal is a micro-services backend with a
React UI.** That does not change Phase 2's scope (still "make there be one rules authority"), but
it does bias the fork decisions toward a headless, network-serialisable core — see §5.

---

## 1. Call-site audit — the current state, verified against the tree today

### 1.1 Three check / mate / draw implementations (ARCHITECTURE §2.3, re-verified)

**Path 1 — `main.CheckScanner`, ray-casting against the OO `BoardState`.**

| Method | What it does | Live callers |
|---|---|---|
| `isMoveCausesCheck(Move)` | ray-cast from the king after a hypothetical move | `BoardState.isValidMove` ([BoardState.java:241](../src/main/java/ai/BoardState.java#L241)); `King.canCastle` ([King.java:46,54](../src/main/java/pieces/King.java#L46)) |
| `isGameOver(Piece king)` | brute force: every friendly piece × every square → build `Move` → `isValidMove`; **writes `Board.selectedPiece` mid-loop** ([CheckScanner.java:117](../src/main/java/main/CheckScanner.java#L117)) | `BoardState.getAccurateStatus` (525), `BoardState.getStatus` (757), `Board.updateGameState` (522) |
| `isChecking(BoardState)` | "is the side to move in check" | `Board.paintComponent` (105,120), `Board.updateGameState` (523,560), `myEngine.getRandomMove` (182) |
| `isCheckingForClone(BoardState)` | "did the side that just moved leave its own king in check" | `BoardState.makeMoveToCheckIt` (463) |
| `isCheckingForEvaluation(BoardState)` | same as `isChecking` with a null-guard + debug print | `BoardState.getIsCheck` (764) → `getAccurateStatus` |

The three `isChecking*` variants differ only by which colour/turn they test. `hitByRook` /
`hitByBishop` also skip `Board.selectedPiece` — rules code reading live Swing selection state.

**Path 2 — `ai.BitBoard`, bitwise attack tables, inside the search only.**

| Method | What it does | Live callers |
|---|---|---|
| `isCheckOn(int color)` | `getAllAttackedTiles(other) & kingBB` | `getNewBoardFromMove` (483), `getNextStates` (496, filters self-check), `getStatus` (731-732) |
| `getStatus()` | no legal moves → `MIN`/`MAX`/`0`; `numOfTurnsWithoutCaptureOrPawnMove >= 50` → `0`; else `1` | `Minimax.minimax` (84), `BitBoardEvaluate.evaluate` (257) |
| `getNextStates()` | per-piece pseudo-legal generation, then drop moves that leave own king in check | search; characterization suite |

This is the **more correct** of the two paths: 20 moves from the start, castling / en-passant /
promotion all handled in generation, mate as `MAX`/`MIN`, stalemate as `0`.

**Path 3 — simulate-and-revert, triggered by PGN string formatting.**

`Board.makeMove` → `Move.setRepresentation()` ([Move.java:61](../src/main/java/main/Move.java#L61))
→ `getStatusString()` (135) → `BoardState.makeMoveAndGetStatus(this)`
([BoardState.java:481](../src/main/java/ai/BoardState.java#L481)) mutates the live board (moves the
piece, toggles the turn, removes any capture), calls `getAccurateStatus()` (Path 1), then
`loadPiecesFromFen(tempFen)` to roll back. Formatting a move re-derives check/mate/draw a third
time, on the real board object.

### 1.2 `BoardState` mutate-then-revert method family

All of these snapshot a FEN, mutate `pieceList` in place, then `loadPiecesFromFen` to restore:

| Method | Callers |
|---|---|
| `makeMoveToCheckIt` (436) | `getAllPossibleMovesForASide` (147), `getAllPossibleMoves` (160), `myEngine.makeMove`/`giveHint` (76, 98, 139) |
| `makeMoveAndGetStatus` (481) | Path 3 only |
| `makeMoveAndGetValue` (542) | **none — dead** |
| `makeMoveAndGetFen` (580) | **none — dead** |
| `makeMove` (698) — real apply | **none in the app** (`Board.makeMove` reimplements it); `AppSmokeTest` uses it |
| `cancelMove` (735) | **none — dead** |
| `getAllPossibleMoves` (156, private) | `BoardState.main` (775) only |
| `getAllPossibleMovesForASide` (142) | **the characterization suite only** (6 sites) — the Phase 1 carry-over |

`makeMoveToCheckIt` calls `capture()` / `loadPiecesFromFen()` which structurally mutate `pieceList`
while `getAllPossibleMovesForASide()` iterates it → the characterized `ConcurrentModificationException`.

### 1.3 Move generation — two implementations, different quality

- **OO list-based:** `Piece.getValidMoves` brute-forces 64 squares through `isValidMove`.
  `Pawn.getValidMoves` overrides it and only ever offers the three squares one rank ahead — **no
  two-square push**, and its `colorIndex` sign is the opposite of `Pawn.isValidMovement`'s. This
  is why `getAllPossibleMovesForASide()` returns 12 from the start, not 20.
  Consumers: `myEngine.getRandomMove` (skill 0), `BoardState.getAllPossibleMoves*`,
  `Move.getPieceString` (SAN disambiguation).
- **Bitboard:** `BitBoard.getMovesForColor` + `BitPiece` subclasses. Consumer: `Minimax`.

**Rules that live in only one model:**
- *Castling legality:* bitboard `getCastles` does the full "squares empty + not castling through an
  attacked square + rook home" test. The OO model splits it — `King.canCastle` checks emptiness
  and one transit square (kingside also checks `isChecking`; **queenside does not check the current
  square for check**), and `Board.moveKing` / `BoardState.moveKingForClone` execute the rook hop.
- *Promotion:* bitboard generates all four promotion pieces. The OO clone path hard-codes `"q"`
  ([BoardState.java:642](../src/main/java/ai/BoardState.java#L642)) and the live path defers to a
  Swing dialog or an engine field.
- *En passant:* both models implement it; the OO FEN writer's target-square math
  (`fromR + colorIndex % 8`, [BoardState.java:328](../src/main/java/ai/BoardState.java#L328)) has an
  operator-precedence bug.

### 1.4 Consumers that must be re-pointed at the unified authority

| Consumer | Uses today | Needs |
|---|---|---|
| `Board.paintComponent` | `state.checkScanner.isChecking`, `state.isValidMove` (move dots), `state.getAllPieces` | query API: is-check, legal-targets-for-piece |
| `Board.updateGameState` | `checkScanner.isGameOver` + `isChecking` + `SavedStatesForDraws.isRepetition` + `numOfTurnWithoutCaptureOrPawnMove` + `insufficientMaterial` | one `status()` call |
| `Board.makeMove` | reimplements apply + `Move.setRepresentation` side-effect | apply via the core; SAN from the core without a live-board simulate |
| `Input` (3 sites) | `board.state.isValidMove`, `board.makeMove` | unchanged surface, new impl underneath |
| `King.canCastle` | `checkScanner.isMoveCausesCheck` / `isChecking` | core legal-move list already includes/excludes castling |
| `myEngine.getRandomMove` | `checkScanner.isChecking`, `Piece.getValidMoves` | core legal-move list |
| `Minimax` / `BitBoardEvaluate` | `BitBoard.getStatus`, `getNextStates`, `ChoosePlayFormat.*` | core status/movegen; perspective as a parameter (see §4 fork 5) |
| characterization suite | `getAllPossibleMovesForASide`, `getAccurateStatus`, `getIsCheck`, `BitBoard.getNextStates`/`getStatus` | re-point the OO assertions at the unified generator |

### 1.5 Object model ↔ rendering entanglement (guide research item: "how deeply is `Piece` relied on for rendering vs rules")

`pieces.Piece` carries `col,row` **and** `xPos,yPos` **and** `sprite`, and decodes
`/pieces.png` in an instance initialiser ([Piece.java:24-31](../src/main/java/pieces/Piece.java#L24)).
`King`/`Pawn`/etc. constructors read `Board.tileSize` and `ChoosePlayFormat.isPlayingWhite`. So
every `BoardState` built from a FEN — including every clone in the mutate-revert paths — decodes
and rescales an image six-plus times. Rules never touch `xPos/yPos/sprite`; the renderer never
needs `getValidMoves`. They are separable, but the split is **Phase 3's** job (UI decoupling).
Phase 2 needs the rules core to not require any of the rendering fields; it can leave the OO
`Piece` objects alive as view-models.

---

## 2. Behaviours that must be preserved (Phase 0 characterization suite = the contract)

### 2.1 Pinned CORRECT — must stay green

- Bitboard: 20 moves from start; start status `1`; fool's-mate → `MAX_VALUE` (no moves);
  back-rank mate → `MIN_VALUE`; K+Q-vs-K stalemate → `0`.
- OO: start `getAccurateStatus`==1 / `getStatus`==1 / not check; fool's-mate → 0 moves + check +
  `getAccurateStatus`==`MAX_VALUE` + `getStatus`==0; back-rank mate likewise; stalemate → 0 moves +
  not check + `getAccurateStatus`==0; plain check → `getAccurateStatus`==2 with moves > 0.
- OO `isValidMove` accepts O-O, O-O-O (`CASTLING_OPEN`), e5xf6 e.p. (`EN_PASSANT`), a7-a8 push
  (`PROMOTION`).
- `SavedStatesForDraws`: key seen 3× ⇒ repetition; `removeLastState` undoes it.
- `insufficientMaterial`: lone K true; K+B true; **K+B+B false** (characterized quirk — decide in
  fork 4 whether to keep); any pawn false.
- FEN piece-placement field round-trips.
- `AppSmokeTest` (`-Psmoke`): Scholar's-mate status transitions through the OO path; a full seeded
  random bitboard game terminates in `{0, MAX, MIN}`; `SaveGame`→`LoadGame` FEN round-trip.

### 2.2 Pinned BUGGY — Phase 2 is expected to flip these to correct

- `getAllPossibleMovesForASide()` returns **12, not 20**, from the start
  ([StartingPositionTest.java:18](../src/test/java/characterization/StartingPositionTest.java#L18)).
- `getAllPossibleMovesForASide()` throws **`ConcurrentModificationException`** when the side to move
  has a capture ([SpecialMovesTest.java:49](../src/test/java/characterization/SpecialMovesTest.java#L49)).

The guide's Phase 2 text: these assertions "must be re-pointed at the unified generator and
flipped from 'characterized bug' to 'correct' as part of this phase."

### 2.3 KNOWN-BUG suite (`-Pknown-bugs`, 4 red) — draw detection

- FEN half-move clock truncated to its first digit on load
  (`Character.getNumericValue(parts[4].charAt(0))`).
- OO status path never applies the 50-move rule.
- Bitboard declares the 50-move draw at half the count (counter is per-ply, test threshold is
  per-move-pair).
- OO status path ignores insufficient material (K-vs-K not reported as a draw).

### 2.4 Divergences with no fixture yet — add tests during implementation

- **`BitQueen.getAttackedTiles()` misses the up-left diagonal** — CONFIRMED during increment 1.
  The method is missing the `counter = 1;` reset before its up-left diagonal loop (every sibling
  loop and the whole `validMovements()` have it), so a queen's up-left attacks are computed with
  a stale step counter and land on garbage squares. Effect: an adjacent up-left check is not
  seen — e.g. **Qf7 vs Ke8 (Scholar's Mate) was reported as "not check"** by the bitboard.
  Only affects attack/check/castling-through-check detection, not move generation (which uses
  the correct `validMovements()`). This is a very plausible face of the historical
  "engine avoids / misses mate" complaint. **Fixed in increment 1** (one line); regression-pinned
  by `rules.RulesTest.scriptedGame` (a full game to Scholar's mate through the new facade).
- Threefold repetition at the top level: only `SavedStatesForDraws` (UI, keyed on
  `convertPiecesToDrawFEN`) and `BoardStateTracker` (search, keyed on Zobrist) track it, with
  different keys and neither wired into a `status()` the app trusts.
- **`BitPawn.getEnPassantMoves()` dropped the capturing pawn** — CONFIRMED increment 1, **FIXED
  increment 2.** It returned `position & ~capturingPawnBit` (the pawn removed) with nothing added
  on the e.p. target, so `getNewBoardFromMove` produced a child with one square vacated and none
  filled — the capturing pawn vanished and, via the follow-up `setPawns`, so did the captured
  pawn. Fix: `(position & ~capturingPawnBit) | targetBit`, and the "no e.p." guard is now
  `enPassantIndex >= 0` (was `!= 0`, which let the `-1` sentinel through). Pinned by
  `rules.RulesTest.enPassant` / `enPassantBlack` (generate + apply, both colours).
- Queenside castling through an attacked square, OO path (`King.canCastle` skips the check).
  Still open — increment 3 (when the OO path is re-pointed at the facade).
- `BitBoard(BoardState)` black-kingside-rook branch set `canBlackCastleQueenSide = false` instead
  of `canBlackCastleKingSide` ([BitBoard.java:96-99](../src/main/java/ai/BitBoard/BitBoard.java#L96)).
  **Corrected increment 2**, but note this is currently a *latent* typo: lines 119-122 of the same
  constructor unconditionally copy `boardState.canWhite/BlackCastle*` afterwards, overriding the
  whole per-piece castling block. No behavioural change today; the redundant per-piece block goes
  when `BitBoard(BoardState)` is retired in Phase 3.
- `BitMove(long, Move, int, int, boolean, boolean)` is an empty stub
  ([BitMove.java:39](../src/main/java/ai/BitBoard/BitMove.java#L39)); `new BitBoard(BoardState)`
  builds `lastMove` from it, so a bitboard made from a real `BoardState` has a hollow `lastMove`.
  Currently benign — the search reads child `lastMove`s (set correctly by `getNewBoardFromMove`),
  never the root's. Deferred to Phase 3 with the rest of the `BoardState → BitBoard` bridge; the
  `rules` adapter never creates the stub (it uses the 23-arg constructor with `lastMove = null`).
- `Move.setRepresentation` mutating the live board (Path 3) can interleave with anything else
  holding that `BoardState`.

---

## 3. Rollback plan

- Branch `phase-2-unify-rules-engine` off `master` @ `3a01bc8`. One revertable unit; `master`
  untouched until merge, same as Phases 0–1.
- Land in increments, each its own commit, each leaving `./mvnw test`, `-Psmoke` green and the app
  playable:
  1. New rules core in parallel, nothing calls it; characterization tests added against it.
  2. Point OO `isValidMove` + the `Board`/`Input` status calls at the core (adapter).
  3. Point the search (`Minimax`) at the core's generator/status.
  4. Delete `CheckScanner`, the `BitBoard` status/movegen duplication or the OO one (whichever
     loses fork 1), Path 3's live-board simulate, and the dead `BoardState` method family.
- Abandon = `git checkout master`. Post-merge undo = revert the merge commit. Every deleted file
  stays in git history.

---

## 4. Design forks — need a decision before implementation

### Fork 1 — which representation becomes the single canonical rules authority?

| Option | For | Against |
|---|---|---|
| **A. Bitboard becomes the one engine**, wrapped behind a new Swing-free API; the OO `BoardState`/`Piece` become a thin view-model that delegates every rules/status call to it | Already headless & AWT-free; already the more-correct generator (20 from start, mate/stalemate right); already what the search runs on; least new rules code to write | Bitboard code quality is rough (700-line `getNewBoardFromMove`, `Debug.log` spam, a hard-coded `printBitBoard().equals("…")` probe in `getMovesForColor`, the castling-rights constructor bug); no make/unmake (full field copy per move); no SAN/PGN, no full move history; `BitMove` is half-built |
| **B. Build a fresh clean core** (immutable `Position`, `Move` value type, one `LegalMoveGenerator`, one `GameStatus`), port the bitboard's attack logic into it, retire both current models | Best foundation for the micro-services / React future; one place for the draw rules; can design make/unmake, history, SAN, FEN, UCI in from the start | The most work; highest risk in the highest-risk phase; bitboard attack code has to be re-hosted carefully to not regress the one path that currently works |
| **C. Keep the OO model, rebuild its correctness** | The UI, `Input`, SAN, hints, skill-0 engine already speak it; smallest change to consumers | It's the substantially-broken path (pawn movegen, CME, castling-through-check, no draw rules, reads `Board.selectedPiece` and `ChoosePlayFormat`); carries rendering state and image decode; keeping it means re-deriving all the rules the bitboard already has right |

Recommendation: **A**, with the API surface from Fork 3 — it removes the most duplication for the
least risk and the bitboard is already the closest thing to a correct headless engine. B is the
"right" long answer; it can be a later phase if A's wrapper proves too thin.

### Fork 2 — how far does Phase 2 go on the OO `BoardState` / `Piece` model?

| Option | Meaning |
|---|---|
| **A. Leave a thin render-only adapter** — `BoardState`/`Piece` keep positions for the renderer, forward every `isValidMove` / status / movegen call to the core; delete `CheckScanner`, Path 3, the dead `BoardState` methods. Full OO removal is **Phase 3**. | Keeps Phase 2 focused on "one rules authority", app stays playable throughout, no UI rewrite |
| **B. Fully remove `BoardState` + `pieces/*` + `CheckScanner` now**, UI switches to the core's types this phase | Fewer total phases; but this is Phase 3's decouple-the-UI work pulled forward into the riskiest phase, and the guide explicitly sequences it after |

Recommendation: **A**.

### Fork 3 — canonical API interchange format (matters for the micro-services / React goal)

| Option | Notes |
|---|---|
| **A. FEN in / FEN + status + legal-move list out; moves as UCI strings** (`e2e4`, `e7e8q`) | Already the Stockfish contract, the `SaveGame` format, and trivially JSON for React; a network boundary would need this flattening anyway |
| **B. Bespoke structured `Position` / `Move` objects only** | Nicer in-process ergonomics; has to be re-serialised to cross a service boundary later |
| **C. Both — value objects in-process, FEN/UCI at the edge** | A bit more surface to keep in sync; most flexible |

Recommendation: **A** for the boundary (with plain internal value objects as an implementation
detail). It also directly feeds Phase 4 (Stockfish) and Phase 5 (opening book / game DB).

### Fork 4 — is draw-rule *correctness* in scope for Phase 2, or only de-duplication?

| Option | Meaning |
|---|---|
| **A. Yes — Phase 2 fixes it.** The unified `status()` implements 50-move (correct threshold), threefold repetition, and insufficient material; the 4 known-bug reds go green; K+N+N / K+B+B decided deliberately | Matches the guide's exit criterion ("previously-triplicated logic deleted, not just deprecated" + "expanded to cover every divergence"); this is the phase that targets the "doesn't recognise draws" complaint |
| **B. No — Phase 2 only unifies**, correctness fixes tracked as a follow-on | Smaller phase; but leaves the headline bug the refactor exists to fix for "later", and a later fix then has to re-touch the same code |

Recommendation: **A**.

### Fork 5 — does Phase 2 sever the rules core's reads of `ChoosePlayFormat` / `SettingPanel`?

`BoardState.movePawnForClone` (639), `BitBoardEvaluate.evaluate` (253), `Minimax.minimax` (97) all
read those Swing statics. The guide assigns the **full** `ChoosePlayFormat` retirement to Phase 3.

| Option | Meaning |
|---|---|
| **A. Sever only the rules reads now** (promotion logic, any movegen/status read); pass side-to-move / perspective as parameters. Leave the *search bookkeeping* (`Minimax.lastDepth`, evaluate perspective) for Phase 3 | The rules authority comes out of Phase 2 genuinely headless; search cleanup stays with the rest of the static-global work in Phase 3 |
| **B. Strictly Phase 3** — Phase 2 leaves every static read in place | Cleaner phase boundary on paper; but ships a "unified" core that still can't run without the UI's globals, which defeats the micro-services intent |

Recommendation: **A**.

---

## 5. Micro-services / React implications (owner's stated end goal)

- The bitboard core is already `javax.swing`- and `java.awt`-free. Its only `main.*` touch points
  are the `BoardState → BitBoard` adapter constructor and `main()`. Parameterising the
  `ChoosePlayFormat` reads (Fork 5A) makes it runnable server-side essentially as-is.
- `BoardState` + `pieces/*` **cannot** go server-side cleanly: `Piece` decodes a sprite sheet in an
  instance initialiser and the subclasses read `Board.tileSize` + `ChoosePlayFormat` in their
  constructors. Headless use boots AWT and loads image resources per piece.
- Recommended service boundary shape: stateless-friendly, FEN in / (FEN + status + legal moves)
  out, moves as UCI — the same contract Stockfish, `SaveGame`, and a React client all already want.
- Concrete Phase 2 constraint that keeps the door open: **the rules module ships with zero
  `main.*` / `GUI.*` / `javax.swing` / `java.awt` imports.** The actual network split stays out of
  scope until after Phase 5; Phase 3 points the Swing UI at this module, a future REST service
  points at the same module.

---

## 6. Definition of done (concrete, checkable)

- [ ] Exactly one code path answers: legal moves for the side to move · is it check · is it
      checkmate · is it stalemate · is it a draw (50-move / threefold / insufficient material).
- [ ] `main.CheckScanner` deleted. The losing side of Fork 1's status/movegen duplication deleted,
      not deprecated.
- [ ] `Move.setRepresentation` / SAN generation no longer simulates on the live `BoardState`.
- [ ] `BoardState.makeMoveAndGetStatus` / `makeMoveAndGetValue` / `makeMoveAndGetFen` /
      `cancelMove` / `getAllPossibleMoves` / `getAllPossibleMovesForASide` / `BoardState.main`
      deleted.
- [ ] No rules code reads `Board.selectedPiece`.
- [ ] Characterization suite: the two "characterized bug" move-count assertions re-pointed at the
      unified generator and green; the 4 `known-bug` reds green (or explicitly re-deferred with a
      written reason); a fixture exists for every §2.4 divergence.
- [ ] `./mvnw test` and `./mvnw test -Psmoke` green; `-Pknown-bugs` count updated in
      `docs/phase-0-notes.md` and `phase-1-notes.md`.
- [ ] Rules module has no `main.*` / `GUI` / `javax.swing` / `java.awt` imports (Fork 5A).
- [ ] App plays human-v-computer, computer-v-computer, save/load — parity with `master` or a
      documented intended change.
- [ ] `ARCHITECTURE.md` re-synced (the guide mandates it after Phase 2).

---

## 7. Decisions (locked 2026-09-05, project owner)

| Fork | Decision |
|---|---|
| **1. Canonical core** | **A — the bitboard becomes the one rules engine**, wrapped behind a new Swing-free `rules` package. The OO `BoardState`/`Piece` are demoted to a view-model that delegates every rules/status/movegen call to it. |
| **2. OO model scope** | **A — thin adapter now, full removal in Phase 3.** Phase 2 deletes `CheckScanner`, the PGN simulate-and-revert (Path 3), and the dead `BoardState` method family. `BoardState`/`Piece` survive as render-only until Phase 3 decouples the UI. |
| **3. API interchange** | **A — FEN in / (FEN + status + legal moves) out; moves as UCI strings.** Plain value objects internally; FEN/UCI is the committed boundary contract (feeds Phase 4 Stockfish + Phase 5 game DB + a future REST service + React). |
| **4. Draw correctness** | **A — in scope.** The unified `status()` implements the correct 50-move threshold, threefold repetition, and insufficient material; the 4 `-Pknown-bugs` reds are expected to go green this phase. |
| **5. Static-global reads** | **A** (recommendation, not separately polled) — sever the *rules* reads of `ChoosePlayFormat`/`SettingPanel` now (promotion logic, any movegen/status read); leave the *search bookkeeping* cleanup (`Minimax.lastDepth`, evaluate perspective) for Phase 3. Rules module ships with **no `main.*` / `GUI` / Swing / AWT imports**. |

### Implementation increments (each its own commit, each green + playable)

1. **New `rules` package as a working vertical slice** — `Square`, `ChessMove` (UCI), `Position`
   (FEN, correct half-move clock), `GameStatus`, `Rules` facade + `Game` (history for threefold).
   Movegen / check / mate / stalemate delegate to `BitBoard` through the package-private
   `ai.BitBoard.BitBoardRules` adapter (`fromFen`, parent→child move diff, `toFen`); draw rules
   implemented in the facade. New `rules.RulesTest` (18) pins the *target* behaviour.
   **Nothing re-pointed — the old suite is still 23 + 3 green, `-Pknown-bugs` still 4 red.**
   Two source changes outside the new files: `BitBoard.isSideToMoveInCheck()` (one public accessor
   for the adapter) and the `BitQueen.getAttackedTiles()` one-line fix (see §2.4). ✅ _done_
2. Fix the bitboard bugs the facade exposes: en-passant generation
   (`BitPawn.getEnPassantMoves` dropped the pawn) — **done**; the `BitBoard(BoardState)`
   castling-rights typo corrected (latent); `BitMove` stub + queenside-castling-through-check
   re-scoped (see §2.4). ✅ _done_
3. Route `BoardState`'s public rules API (`isValidMove`, `getAccurateStatus`, `getStatus`,
   `getIsCheck`) and `Board`'s status/paint checks through `rules.Rules`, behind a per-position
   cache. `CheckScanner` stays (now reached only by `makeMoveToCheckIt` + `myEngine` + a dead
   `King.canCastle` fallback) — deleted in increment 5. ✅ _done_ — also fixed all four
   draw-detection bugs early (the `-Pknown-bugs` suite is now empty; its tests moved to
   `characterization.DrawDetectionTest`, green in the default run).
4. Point `Move` SAN generation at the facade; remove the live-board simulate-and-revert (Path 3).
5. Point `myEngine` (skill-0 random) + the `Minimax` entry at the facade's generator/status;
   delete `main.CheckScanner`, `BoardState.getAllPossibleMoves*`, `makeMoveAndGet*`,
   `cancelMove`, `BoardState.main`.
6. Re-point the two "characterized bug" assertions at the unified generator; add a fixture for
   every remaining §2.4 divergence. Re-sync `ARCHITECTURE.md`.
7. Manual play-through (human-v-computer, computer-v-computer, save/load) for parity.

---

## 8. Increment log

### Increment 1 — new `rules` package as a working slice (2026-09-06)

New, headless, no `main.*` / `GUI` / Swing / AWT:

| File | Role |
|---|---|
| `src/main/java/rules/Square.java` | square-index helpers (a8 == 0, h1 == 63) + algebraic names |
| `src/main/java/rules/ChessMove.java` | immutable from/to/promotion; UCI parse & format |
| `src/main/java/rules/Position.java` | immutable FEN value type; **reads the half-move clock in full** |
| `src/main/java/rules/GameStatus.java` | the single status enum (in-progress / check / mate / stalemate / 3 draw kinds) |
| `src/main/java/rules/Rules.java` | the facade: `legalMoves` / `isCheck` / `isCheckmate` / `isStalemate` / `status` / `applyMove` / `isInsufficientMaterial` |
| `src/main/java/rules/Game.java` | current FEN + history; adds threefold repetition on top of `Rules` |
| `src/main/java/ai/BitBoard/BitBoardRules.java` | package-private bridge: `fromFen` (→ `BitBoard`, castling parsed from the FEN), parent→child move diff → `{from,to,promo}`, `toFen` |
| `src/test/java/rules/RulesTest.java` | 18 tests pinning the target behaviour |

Changes to existing engine code (minimal, additive):
- `BitBoard.isSideToMoveInCheck()` — one public accessor so the adapter can ask "is the side to
  move in check" without reaching into `private isCheckOn`.
- `BitQueen.getAttackedTiles()` — added the missing `counter = 1;` before the up-left diagonal
  loop (§2.4). Fixes missed up-left checks/mates (Scholar's Mate).

Verification: `./mvnw test` → **41 green** (23 legacy characterization + 18 new); `-Psmoke` → 3
green; `-Pknown-bugs` → 4 red (unchanged — they exercise the not-yet-re-pointed OO path);
`./mvnw package` → jar builds. Nothing in the running app calls the new package yet, so gameplay
is unchanged.

What the new facade already gets right that neither legacy path does:
- 20 legal moves from the start (OO path: 12).
- The half-move clock read in full (OO path truncates to one digit).
- 50-move rule at 100 plies, not 50 and not per-ply.
- Insufficient material: K vs K, K+minor vs K, K+B vs K+B (same colour).
- Threefold repetition with one consistent key.
- One `GameStatus` for every "is the game over / how" question.

Known gaps carried to increment 2: en-passant generation (bitboard bug — the capture is absent
from `legalMoves` for now), the `BitBoard` castling-rights constructor bug.

### Increment 2 — fix the bitboard bugs the facade exposed (2026-09-06)

- `BitPawn.getEnPassantMoves()` — the capturing pawn now lands on the e.p. target instead of
  vanishing; "no e.p." guard tightened to `>= 0`. The `rules` facade now generates and applies
  e.p. for both colours (`rules.RulesTest.enPassant`, `enPassantBlack`). This also improves the
  live search — `getNextStates()` now includes e.p. captures.
- `BitBoard(BoardState)` — black-kingside-rook branch corrected
  (`canBlackCastleKingSide`, was `...QueenSide`). Latent: overridden by the bulk copy at
  lines 119-122; no behavioural change (see §2.4).

Verification: `./mvnw test` → **42 green** (23 legacy + 19 new); `-Psmoke` → 3 green
(`randomGameInBitboardEngine` still terminates cleanly with e.p. now in the move set);
`-Pknown-bugs` → 4 red (unchanged); `./mvnw package` → jar builds.

### Increment 3 — route the OO path through the facade + fix all draw detection (2026-09-06)

`BoardState`'s public rules API now delegates to `rules.Rules`:
- `toRulesFen()` — a correct FEN from the live fields (piece squares, turn, the four king/rook
  first-move flags, `enPassantTile`, the clocks), independent of the quirky
  `convertPiecesToFEN` / `loadPiecesFromFen`.
- `ensureRulesCache()` — one `Rules.evaluate(fen)` per distinct position, memoised on the FEN
  string, feeding both the legal-move set and the status. Keeps the 64-square
  `Board.paintComponent` sweeps at one generation, not 64.
- `isValidMove(Move)` → membership test in the cached legal-move set (`from*64+to`). The old
  `isValidMovement` / `moveCollidesWithPiece` / `CheckScanner.isMoveCausesCheck` pipeline is
  bypassed.
- `getAccurateStatus()` / `getStatus()` / `getIsCheck()` → derived from the cached
  `rules.GameStatus`. New `getRulesStatus()` exposes the enum.
- `loadPiecesFromFen` — half-move clock and full-move number now `Integer.parseInt(parts[…])`
  (were `Character.getNumericValue(parts[4].charAt(0))` — one digit only).

`Board`:
- `paintComponent` — `checkScanner.isChecking(state)` → `state.getIsCheck()` (2 sites).
- `updateGameState` — the `CheckScanner.isGameOver` + `isChecking` + `insufficientMaterial`
  ladder replaced by a `switch (state.getRulesStatus())`. Threefold still consulted via
  `SavedStatesForDraws.isRepetition()` (position history, not in the stateless status).

`BitBoard.getStatus()` — 50-move threshold `>= 50` → `>= 100` (the counter is per-ply).

**All four draw-detection bugs are now fixed** (the "engine doesn't recognize draws" complaint):
the `-Pknown-bugs` suite is empty; its four tests moved to
`src/test/java/characterization/DrawDetectionTest.java`, asserting the correct answers, green in
the default run.

`CheckScanner` is still compiled and used by `makeMoveToCheckIt` (→ `getAllPossibleMovesForASide`
and `myEngine`) and a now-unreachable `King.canCastle` fallback — deleted in increment 5.

Verification: `./mvnw test` → **46 green** (was 42 + the 4 ex-known-bug tests); `-Psmoke` → 3
green; `-Pknown-bugs` → 0 tests, BUILD SUCCESS; `./mvnw package` → jar builds. The rules routing
is covered by `CheckmateStalemateTest` / `SpecialMovesTest` / `StartingPositionTest` (which call
the now-delegating `getAccurateStatus` / `getIsCheck` / `isValidMove`) plus
`AppSmokeTest.scriptedGameToCheckmate`. The Swing glue in `Board` is a mechanical translation of
test-covered values; a human play-through is still owed at increment 7.
