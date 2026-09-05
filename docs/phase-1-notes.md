# Phase 1 — Remove dead and parallel code

Status: **DONE and verified** (Maven 3.9.11 + OpenJDK 26, 2026-09-05).
Branch: `phase-1-remove-dead-code`. One revertable unit; `master` untouched.

Verified results (identical to the Phase 0 baseline — nothing reachable was touched):

| command | result |
|---|---|
| `./mvnw test` | **23 pass, 0 fail** — the "do not regress" suite |
| `./mvnw test -Pknown-bugs` | **4 fail (on purpose), build SUCCESS** — the documented bugs |
| `./mvnw package` | builds `target/izikstar-chess-3.1.0.jar` (8.7 MB) |

This is the written record the [refactor guide](../REFACTOR_GUIDE.md) requires at the end of
every phase: what the research found, what was decided and why, what is deferred.

---

## 1. What was deleted

9 files, 2 whole packages, ~250 lines. Every one confirmed to have **zero** live references
(see §2 for the audit method).

| Path | Lines | Why it was dead |
|---|---|---|
| `player/Player.java` | 4 | Empty class. |
| `player/ai/MoveStrategy.java` | 9 | Interface stub for an engine abstraction nothing implements in the live path. |
| `player/ai/BoardEvaluator.java` | 7 | Interface stub, same abstraction. |
| `player/ai/MiniMax.java` | 32 | Skeleton `implements MoveStrategy`; `execute()` returns `null`, `min()` half-written. Class name differs from the live `ai.Minimax` only by capitalisation. |
| `pieces/Piece2.java` | 4 | Empty class. |
| `pieces/PieceUT.java` | 4 | Empty class. |
| `ai/EvaluationLevel2.java` | 137 | A second, more detailed static evaluation function. No callers. Ideas preserved below (§3). |
| `ai/ChessMoveConverter.java` | 37 | `e2e4`-style string → `Move` converter. No callers. Approach preserved below (§3). |
| `ChessServer/ChessServer.java` | 22 | 100 % commented out (a Spark REST stub); `spark.*` is not even a declared dependency. |

The `player/` and `ChessServer/` directories are now gone entirely.

## 2. Research method / audit

- **Import + identifier grep** across `src/` for every class name, package name, and the two
  `BoardState` method names, excluding each symbol's own defining file. Result: no hits outside
  the deleted set and the test suite (see §4).
- **Dynamic-reference sweep:** `grep -E "forName|newInstance|loadClass|getDeclaredConstructor"`
  over `src/` → none anywhere in the project. String-literal sweep for the deleted class names
  → none. So nothing is loaded by reflection or by name.
- **Build / IDE config:** `pom.xml`, `.run/*.xml`, `.idea/*.xml` (excluding the SonarLint issue
  cache) grepped for the deleted names → none. The `out/` tree is stale, git-ignored IDE output
  and targets nothing directly.
- **Baseline captured before deletion:** `./mvnw test` = 23 green, `-Pknown-bugs` = 4 red,
  `package` = jar. **Re-run after deletion: byte-for-byte the same outcomes.**

## 3. Ideas preserved before deletion (per the guide's research step, item 3)

### 3.1 `EvaluationLevel2` — heuristic evaluation ideas

`EvaluationLevel2.evaluate(BoardState)` returned a `double` from White/Black's perspective via
`ChoosePlayFormat.isPlayingWhite` (itself a smell — see ARCHITECTURE §5.4). Stripped of that
coupling, the heuristic terms it computed, for a future evaluator (Phase 2/4):

- **Material:** sum of `piece.value` (cast to int), signed by side.
- **King safety:** king still on its home rank gets a bonus that is *larger* when castled
  (files g/b `+1.25`, file c `+0.75`, on top of a `+1.25` base).
- **Minor-piece development:** knight/bishop still on the home rank is penalised `~1.05`.
- **Mobility:** `0.02 * pieceValue` per legal move of that piece (king excluded).
- **Tactical scan (per candidate move):** looks at `move.captured` —
  - capturing an equal-named piece is treated as "this piece will probably be traded next
    turn" and scored `pieceValue * 0.8`;
  - otherwise a threat term `capturedValue*0.1 - pieceValue*0.05` (only if positive);
  - a defended-friendly term with the same formula.
- **Pawn structure (`evaluatePawn`):** advancement `(7 - dist_to_promotion) * 0.3`; open
  promotion path `+0.6`; central files (d/e) advancement `* 0.1`; phalanx/protected pawn
  (friendly pawn on an adjacent file, same rank) `+0.2`.
- **Terminal:** `status == 0 && isCheck` ⇒ ±∞ by side; stalemate ⇒ 0.

Caveats: weights are un-tuned guesses; "protected pawn" checks the same rank, not the rank
behind, so it detects phalanxes, not true defenders; relies on the slow list-based
`piece.getValidMoves`. Treat as a checklist of *what to score*, not *how much*.

### 3.2 `ChessMoveConverter` — UCI-string → `Move`

`convertChessNotationToMove(BoardState, Piece, String)` parsed a 4-char string (`"e2e4"`):
file `- 'a'` → col 0-7; rank → row via `8 - numericValue(rank)` (board is rank-inverted);
validated the passed `Piece` actually sits on the from-square; then
`new Move(board, piece, endX, endY)`. No promotion-suffix handling (a 5th char like `e7e8q`).

**Phase 4 relevance:** Stockfish/UCI hands back exactly this string format, so Phase 4 needs
this from-square→`Piece`→`Move` conversion. Check whether `main/Move.java` already covers it
before re-authoring; if not, this is the shape to rebuild (with promotion-suffix support) on
the headless API from Phase 3, not against `BoardState` + a caller-supplied `Piece`.

## 4. Deviation from the guide's Phase 1 scope — `BoardState` move-gen methods NOT deleted

The guide's scope names *"the unused `BoardState` methods with no external callers"*
(`getAllPossibleMoves()` / `getAllPossibleMovesForASide()`, from ARCHITECTURE §3). **That
assumption is now stale — Phase 0 invalidated it:**

- `BoardState.getAllPossibleMovesForASide()` (public) is now called from **6 places in the
  Phase 0 characterization suite** — `CharacterizationTestBase.legalMoveCount()`,
  `CheckmateStalemateTest` (×3), `SpecialMovesTest`, `StartingPositionTest` — where its
  *currently-buggy* output (12 moves from the start, `ConcurrentModificationException` when a
  capture exists) is deliberately pinned. Phase 0's notes: *"Phase 2 replaces this with the
  unified move generator; it is pinned, not fixed."*
- `BoardState.getAllPossibleMoves()` (private) has one caller: `BoardState.main()`, a smoke
  test. Phase 0 valued those `main()` smoke tests as fixture seeds.

Deleting either now would break the safety net for zero surface-area gain, since Phase 2 is
already committed to removing this whole move-gen family. **Decision: leave both methods +
`BoardState.main()` for Phase 2 to delete as one unit with its move-generator rewrite.**
Per the guide's cross-phase note ("if a phase's research turns up something that invalidates
this guide's assumptions, that's a reason to update this guide"), the Phase 1 and Phase 2
entries in `REFACTOR_GUIDE.md` have been amended to say so.

## 5. Deliberately out of Phase 1 scope (found during the audit, NOT touched)

| Item | Why left |
|---|---|
| `ai/openingBook/*` (Retrofit/Lichess client, binary book reader) | Guide explicitly reserves it for **Phase 5** (completion, not removal). Still unreferenced today. |
| `ai/TranspositionTable.java`, `ai/BitBoard/ZobristHashing.java` | Implemented; wired into `Minimax` only as commented-out lines. ARCHITECTURE §3 lists them but the guide's Phase 1 scope paragraph does **not** — they are plausibly wanted for Phase 2/4 search work. Leave the decision to that phase. |
| `GUI/SoundPlayer.java` (dead per Phase 0 §1.3), `main/Debug.java`, and other possibly-idle classes not in any list | Not named in the guide's Phase 1 scope. A broader "unused class" sweep can happen later; Phase 1 stays limited to the ARCHITECTURE §3 parallel-implementation set so it remains an obviously-safe, mechanical change. |
| `ai/BoardState` two move-gen methods | See §4. |
| `.idea/flatlaf-3.0.jar`, `chessGame_3.iml` remnants | Phase 0 follow-up, not dead *code*. |

## 6. Exit criteria (from the guide) — met

- [x] The dead classes are gone.
- [x] The project still builds (`./mvnw package`).
- [x] Phase 0's test suite still passes unchanged (23 green; 4 known-bug reds unchanged).
- [ ] **Still owed:** a manual playthrough — human vs computer, computer vs computer,
  save/load — to confirm parity with `master`. (Same manual smoke test Phase 0 also left owed;
  the packaged jar builds and the automated suite is unaffected, but a human play-through has
  not been done on this branch.)

## 7. Rollback

Everything is on `phase-1-remove-dead-code`; `master` is untouched. Abandon:
`git checkout master`. Undo after merge: revert the merge/squash commit. Every deleted file is
recoverable from git history (`git show master~1:src/main/java/ai/EvaluationLevel2.java`, etc.).
