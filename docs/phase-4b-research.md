# Phase 4b — Make the built-in search fast enough for its levels

**Status: RESEARCH, waiting for the owner's decisions (§5).** Branch `phase-4b-search-speed`,
cut from `master` after PRs #2 and #3 were merged (2026-10-02). No production code has been
touched.

This is the mandatory research step from [REFACTOR_GUIDE.md](../REFACTOR_GUIDE.md) §Phase 4b.
The guide asks for a perft suite as the safety net before any move-generator change. Building
that suite found nine bugs in the move generator, which has been the game's single rules
authority since Phase 2. Most of this document is about those bugs, because the speed work cannot
be proven safe against a wrong baseline.

All timings were taken on the cloud container (4 cores, JDK 21, Stockfish 16 from apt). They are
relative evidence, not a promise about the owner's machine. The probes live outside the repo; the
ones worth keeping become tests in the increments.

---

## 1. Summary

- **Nine rules bugs.** Eight of them can be seen by a player. For example, the king can step next
  to the other king, or onto a square that a knight or a white pawn attacks. Some legal castling
  moves are refused, and one illegal en-passant capture is allowed. The ninth bug only blinds the
  search to en passant.
- **The fixes are small.** All nine fit in about 30 changed lines. With them, every standard perft
  position matches the published counts. 1,000 random games also match Stockfish's legal moves at
  every ply. The fixed engine plays differently, and better: it beat master 65% to 35% at depth 3
  (§2.5).
- **Speed today.** Master needs 6–23 s to finish depth 5 in openings and middlegames, so
  Levels 6 and 7 play their depth-4 move, the same move as Level 5. One 5 s search holds about
  1 GB of heap, and depth 6 runs out of a 3.4 GB heap.
- **Speed with the prototype.** The speed changes alone do not change which move the
  rules-fixed engine picks. Depth 5 finishes in 0.2–0.5 s, depth 6 in 1.1–3.5 s, and peak heap is
  about 0.1–0.2 GB.
- **No test changes.** The existing tests all pass with the rules fixes and the speed changes
  together. The one flaky Phase 4 test is fixed separately in PR #4.

## 2. The perft safety net

### 2.1 How it was measured

Perft counts the positions reached by every sequence of legal moves to a fixed depth. Published
counts exist for standard test positions ([chessprogramming.org, "Perft
Results"](https://www.chessprogramming.org/Perft_Results)). Stockfish's `go perft N` prints the
count under each first move ("divide"), which pins down the first wrong move.

Two views were checked:

- **The search's view:** `BitBoard.getNextStates()`, the boards `Minimax` actually walks.
- **The game's view:** `rules.Rules.legalMoves` / `applyMove`, which re-reads every position from
  FEN.

Three kinds of input were used:

- **Seven standard positions:** the start position, Kiwipete, positions 3–6, and position 4
  mirrored.
- **Sixteen edge-case positions:** fourteen from the TalkChess perft suite (en-passant pins,
  castling rights, promotions, stalemate) and two of our own (pawn checks).
- **Random games:** a differential check against Stockfish at every ply.

### 2.2 Master today

Search view, first depth that differs (the start position and position 6 are right through
depth 4):

| Position | Depth | Master | Correct |
|---|---|---|---|
| Kiwipete | 2 | 2,037 | 2,039 |
| Position 3 | 2 | 214 | 191 |
| Position 4 | 3 | 9,443 | 9,467 |
| Position 4 mirrored | 2 | 259 | 264 |
| Position 5 | 2 | 1,525 | 1,486 |

The game's view at depth 3 is wrong for the same five positions, for example Kiwipete 97,696
instead of 97,862 and position 3 3,210 instead of 2,812. Of the 16 edge-case positions, 14 are
wrong. Only the two "castling gives check" positions are right.

### 2.3 The nine bugs

Line numbers are on `master` at `a49fcb0`. Each "what a player sees" example was reproduced
through `rules.Rules` and checked against Stockfish's move list.

**1. Two knight jumps are not counted as attacks.**
- Where: `BitPiece/BitKnight.java:118` and `:134`, in `getAttackedTiles`.
- Cause: two of the eight jumps are computed but never added (`downRightMove(i);` with no
  `attackedTile |=`).
- What a player sees: a knight check from those two directions is missed. In
  `4k3/8/8/8/8/3n4/8/4K3 w`, the game allows `Kf2`, a square the knight attacks.

**2. Pawn attacks use the wrong edge test.**
- Where: `BitPiece/BitPawn.java:86-87`.
- Cause: the edge test gets the square's number instead of its bit. White pawns never attack.
  Black pawns miss most left-side attacks and wrap around the h-file.
- What a player sees: kings walk into pawn attacks, and checks by white pawns are not seen. In
  `8/8/3k4/8/2P5/8/8/4K3 b`, the game allows `Kd5`.

**3. The setters don't update which squares are occupied.**
- Where: `BitBoard.java:102-136`, the setters `setQueens` … `setPawns`.
- Cause: they change a piece set but not `whitePieces` / `blackPieces`, which every move and
  attack is computed from. The en-passant, castling and promotion children are built with these
  setters.
- What a player sees: an en-passant capture that exposes the player's own king is allowed. In
  `8/2p5/8/KP5r/8/8/8/7k b`, after `...c5`, the game allows `bxc6`. After castling, the search
  still sees the rook on its old square.

**4. The wrong corner square for White's king-side rook.**
- Where: `BitBoard.java:204`, the white rook branch.
- Cause: it tests `A8` where it means `H1`. Any white rook move removes the right to castle king
  side, and moving the a1 rook keeps the queen-side right.
- What a player sees: White can never castle king side after moving the a1 rook. In
  `4k3/8/8/8/8/8/8/R3K2R w KQ`, after `Rb1 Kd8`, `O-O` is refused.

**5. Queen-side castling requires b1/b8 to be safe.**
- Where: `BoardParts.java:59` and `:61`, used at `BitBoard.java:444` and `:464`.
- Cause: one mask is used for both "must be empty" and "must not be attacked". b1 and b8 must be
  empty but may be attacked.
- What a player sees: a legal `O-O-O` is refused whenever b1 or b8 is attacked, for example in
  `1r2k3/8/8/8/8/8/8/R3K3 w Q`.

**6. Sideways queen moves drop the side's other queens.**
- Where: `BitPiece/BitQueen.java:55` and `:68`.
- Cause: quiet sideways moves are added without the side's other queens.
- What a player sees: with two queens, sideways queen moves are missing. In
  `4k3/8/8/8/8/8/8/Q2QK3 w`, both queens lack their moves to b1 and c1. In the search, these
  moves produce a board with a queen missing.

**7. The search gets the wrong en-passant square.**
- Where: `BitBoard.java:180` and `:262`.
- Cause: after a double pawn push, the en-passant square comes from the wrong expression and is
  nearly always a8.
- What a player sees: nothing, because the rules facade recomputes the square from the move. The
  search never sees an en-passant capture after its first ply. It can also see phantom "en
  passant" captures onto the eighth rank. One example is `bxa8` in position 4 mirrored after
  `...d5`.

**8. One king neighbour is not counted as attacked.**
- Where: `BitPiece/BitKing.java:130`.
- Cause: the same slip as #1. The down-right neighbour is computed but not added.
- What a player sees: the kings can stand next to each other. In `1k6/8/1K6/8/8/8/8/8 w`, the game
  allows `Kc7`.

**9. Capturing a rook on its corner keeps the castling right.**
- Where: `BitBoard.java:234` and `:316`, the capture branches.
- Cause: capturing a rook on its home square doesn't remove that side's castling right.
- What a player sees: a side can later castle with a different rook. In
  `r3k2r/7r/8/8/8/8/1B6/4K3 w kq`, after `Bxh8 Rxh8 Ke2`, Black may play `O-O`. For White, bug #4
  hides this today, so fixing #4 alone would expose it.

### 2.4 With all nine fixed (prototype)

The fixes add 32 lines and remove 12, in six files. Each fix is the obvious one: add the missing
`attackedTile |=`, pass the bit, recompute occupancy in the setters, and test `H1`. They also add a
separate "must not be attacked" mask for the queen side, keep the other queens, and compute the
en-passant square as the midpoint of the push. Finally, a castling right ends when its king or
rook is no longer on its home square.

**Results:**
- All seven standard positions match to depth 4, and the start position and position 3 also
  match to depth 5.
- All sixteen edge-case positions match at their published depths.
- 1,000 random games from five starting positions match Stockfish's legal moves at all 278,894
  positions. These games included 269 castlings, 2,823 promotions and 32 en-passant captures.
- All existing tests pass. The one Phase 4 test that fails is flaky on master too and is fixed by
  PR #4.

**How the engine's play changes.** At depths 1–3, the fixed engine picks a different move in 313
of 1,003 sampled positions. Most of this comes from the evaluation: its "targets" term counts
attacked squares using the same attack maps. Once white pawns and all knight jumps count, many
positions score differently.

In a 60-game match at depth 3 against master, the fixed engine scored 65%, and 58% without the
games master forfeited by playing illegal moves (§2.5).

### 2.5 Match: rules-fixed engine vs master, depth 3

**Setup.**
- 60 games at depth 3: 30 random four-ply openings, each played with both colours.
- The referee uses the fixed rules.
- Games past 160 plies are scored by material. No game got that far.

**Result.** The rules-fixed engine scored **+29 =20 −11 (65%)**.

**Master's forfeits.** Ten of master's losses were forfeits for an illegal move, each one bug #1,
#2 or #8 in action. Six ignored a check by a knight or a pawn. Four moved the king onto a square
that a pawn or the other king attacks. In the real game, master's own rules accept those moves.

**Without the forfeits**, the score is +19 =20 −11 (58%). The fixed engine is not weaker, and it no
longer plays illegal moves.

## 3. Where the time goes

### 3.1 Cost model on master

- **Every child board pays for two full attack maps.** One is for "does this move give check" (the
  move-ordering bonus, `BitBoard.java:368`). The other is for "does it leave my own king in check"
  (legality, `:381`). Each attack map builds six `BitPiece` objects that each scan all 64 squares.
  The Phase 4 profile put 68% of search time here.
- **Leaves build all their children.** `getStatus()` builds every child of a leaf to spot mate
  and stalemate (`Minimax.java:97`, `BitBoardEvaluate.java:267`). So the search builds one ply
  more than its depth, and that ply is the largest.
- **Evaluation repeats work.** It builds six attack maps per leaf (`getTargets`), three per side,
  all with the same values. `countSetBits` counts bits one at a time.
- **Smaller costs:**
  - The Zobrist hash is recomputed square by square at every node. At leaves it is added and then
    removed without being used.
  - Children are sorted twice per node.
  - Debug strings are built for every generated piece list, even with logging off.
- **Memory.** Every board keeps its children (`nextStates`), and the root lives for the whole
  search. So the whole searched tree stays reachable.
- **A crash stops the engine silently.** `GameSession.java:296` catches `RuntimeException` only.
  If an engine job dies with an `Error`, for example `OutOfMemoryError`, the job ends silently and
  `engineBusy` stays set. The engine then stops moving. This is the Phase 3 "engine stops
  playing" symptom again, through a different cause.

### 3.2 Measurements

**Time to finish depth 5**, as the engine runs it (iterative deepening from a fresh position), with
peak heap:

| Position | Master | Rules fixed | Rules fixed + speed prototype |
|---|---|---|---|
| Start | 7,685 ms / 1,138 MB | 4,014 ms / 738 MB | 385 ms / 67 MB |
| After 1.e4 | 15,357 ms / 1,804 MB | 10,987 ms / 1,634 MB | 367 ms / 75 MB |
| Italian | 15,775 ms / 1,666 MB | 7,041 ms / 1,213 MB | 290 ms / 107 MB |
| Middlegame | 23,183 ms / 2,571 MB | 19,863 ms / 2,421 MB | 526 ms / 133 MB |
| Kiwipete | 6,381 ms / 1,184 MB | 5,793 ms / 1,157 MB | 172 ms / 65 MB |
| Rook endgame | 93 ms / 30 MB | 96 ms / 22 MB | 9 ms / 5 MB |

**Depth 6.** With the prototype, depth 6 finishes in 1,614 / 2,916 / 2,827 / 3,523 / 1,059 / 44 ms
for the same six positions, with a peak heap of 107–161 MB. On master, the start position runs out
of a 3.4 GB heap.

**The levels in a real engine call** (with the 5 s cap):

| UI level (depth) | Master | Prototype |
|---|---|---|
| 5 (4) | 0.2–1.8 s | 0.05–0.23 s |
| 6 (5) | Hits the cap in 5 of 6 positions and plays its depth-4 move, the same move as Level 5 | 0.15–0.56 s, finishes depth 5 |
| 7 (6) | Hits the cap in 5 of 6 positions and plays its depth-4 move | 0.5–3.9 s, finishes depth 6 (depth 8 in the endgame) |
| Peak heap, Levels 6–7 | 0.5–1.1 GB | under 0.2 GB |

### 3.3 The speed prototype: same moves, less work

1. **Attack lookups.** Use precomputed knight, king and pawn tables and ray walks from the
   square. "Is the king attacked" now looks at one square instead of building every attack. This
   is a new class of about 80 lines.
2. **Lazy legal-move check.** At a leaf, "has a legal move" stops at the first legal move instead
   of building every child. Mate and stalemate detection give exactly the same answer.
3. **Free searched subtrees.** A subtree is released once it has been searched. The root keeps its
   children between depths.
4. **Fewer attack maps in evaluation.** The two maps are built once instead of six times.
5. **Cheaper hashing.** The Zobrist hash is computed over the piece bitboards, which gives the same
   values. There is no hashing at leaves, where the hash was never used.
6. **Small cleanups.** Children are sorted once, `Long.bitCount` is used, and no debug strings are
   built on the hot path.

In total: +165 / −49 lines on top of the rules fixes.

**Evidence that the moves are identical:**
- Perft is unchanged (§2.4).
- The prototype picks the same move as the rules-fixed engine on 1,003 positions at depths 1–3,
  on 300 positions at depth 4, and on the six benchmark positions at depths 1–6.
- The new attack sets equal the old ones on about 960,000 boards. The lazy legal-move check gives
  the same answer as building every child on the same boards.
- The full test suite passes.

**Profile after the prototype** (JFR, depth 6): ray walks take 21%, building child boards 18%, bit
counting 11% (since replaced), list copies 7% and sorting 5%. No single hotspot is left. The next
big step would be a different board design (make/unmake on one board), which the exit criteria do
not need.

## 4. Not covered by the speed work

- **The transposition table.** It is commented out in `Minimax` and is unsound as written. It
  stores an alpha-beta cut-off bound as if it were an exact score, and its key ignores castling
  rights and the en-passant square. Switching it on means rewriting it, and it changes the
  engine's moves.
- **Better move ordering.** Searching the previous depth's best move first means fewer nodes, but a
  different choice among equally scored moves.

Both are real speedups for a later phase, and both need their own way of testing (re-recorded
expected moves or a strength match).

## 5. Design forks — need a decision before implementation

### Fork A — the rules bugs

- **A1 (recommended): fix all nine first, in this phase, test first.**
  - Each player-visible bug gets a test through `rules.Rules`, red at first under `-Pknown-bugs`
    as in Phase 2, and the perft suite is added alongside.
  - Then come the fixes, one commit per root cause.
  - The engine will play differently in about a third of positions, because it now sees pawn and
    knight attacks correctly. In §2.5 it beats master 65% to 35%, and it no longer plays illegal
    moves.
- **A2: the same fixes in their own PR, before the speed work.** That is one more merge for you,
  but you could play-test the rules change on its own.
- **A3: speed first, rules later.** Not recommended. The speed work would have to keep the bugs to
  prove "same moves", and then everything would change again.

### Fork B — how much speed

- **B1 (recommended): only changes that keep the engine's moves identical** (§3.3).
  - Depth 5 takes under 0.6 s and depth 6 under 4 s here.
  - Memory drops from about 1 GB to under 0.2 GB.
  - Proven by perft and a "same move at fixed depth" test.
- **B2: B1 plus a rewritten transposition table and best-move-first ordering.** This is likely
  2–4x faster again, but the engine's moves change and need a strength test. It fits better as a
  later phase, if Level 7 still feels slow on your machine.

### Fork C — where the safety net runs

- **C1 (recommended): in every `mvn test`.** This covers:
  - perft for the seven standard positions at depth 3;
  - the sixteen edge-case positions at depths that keep the whole set under about 2 s;
  - one test per player-visible bug;
  - a "same move at depths 1–4" test on about 50 positions, recorded right after the rules fixes.

  Deeper perft and the random-games check against Stockfish run under `-Pstress`. The Stockfish
  check is skipped when Stockfish is not installed.
- **C2: perft only under a profile.** The default build is faster, but a rules regression could
  pass a normal `mvn test`.

## 6. Definition of done (guide exit criteria, made checkable)

1. **Perft matches the published counts.**
   - The seven standard positions at depth 3 in `mvn test`.
   - Depth 4 under `-Pstress`, with the start position and position 3 at depth 5.
   - The sixteen edge-case positions.
2. **Regression tests.** Every player-visible bug in §2.3 has a test through `rules.Rules`.
3. **Speed.** In the six benchmark positions on the cloud container, depth 5 finishes within 1 s
   and depth 6 within 5 s (an `-Pstress` benchmark). On the owner's machine, Levels 6 and 7 answer
   without hitting the cap in typical middlegames (the owner's play test).
4. **Memory.** A Level 7 search peaks under about 300 MB of heap (benchmark).
5. **Same moves.** The engine picks the same moves at depths 1–4 as the rules-fixed engine on the
   recorded set (a test).
6. **No silent hang.** An engine job that fails with an `Error` is reported, and the game does not
   hang (a test).
7. **Green runs.** `mvn test`, `-Psmoke` and `-Pstress` are green. The app is driven under Xvfb at
   Levels 6 and 7, with a hint and an undo while the engine thinks.

## 7. Proposed increments (each its own commit, green and playable)

1. **Safety net.** Perft tests for both views, plus one test per player-visible bug (red, tagged
   `known-bug`).
2. **Rules fixes, one commit per root cause:**
   - #1 and #8, the missing attacks;
   - #2, pawn attacks;
   - #3, occupancy;
   - #4 and #9, castling rights;
   - #5, the castling path;
   - #6, the queen;
   - #7, the en-passant square.

   The `known-bug` tests turn green and join the default run. The expected moves for the
   "same move" test are recorded at the end.
3. **Speed** (§3.3 items 1, 2, 4, 5 and 6). The "same move" test stays green.
4. **Memory and docs.**
   - Release searched subtrees.
   - Report engine-job `Error`s.
   - Add the `-Pstress` benchmark.
   - Update the docs: ARCHITECTURE, the README levels, and the guide status.

## 8. Rollback

Each increment is one or more commits on this branch, and reverting one restores the previous
green state. The branch is merged with a merge commit like the earlier phases.

## 9. Noticed, not in this phase

- **Weak fallback without Stockfish.** Levels 8–10 fall back to the built-in engine at skill 3,
  which is depth 1 and Level 2's strength (`EngineSelector.MOVE_FALLBACK_LEVEL`). With a fast
  search, the fallback could play at Level 7 strength. That is a product decision for later.
- **Castling checks are ordered as quiet moves.** When castling, the "gives check" bonus is
  computed before the rook moves (`BitBoard.java:437-468`). This affects move ordering only, and
  fixing it would change tie-breaks.
- **The endgame stage is never detected.** `BitBoardEvaluate.getGameStage` tests
  `whitePieces & blackPieces`, which is always empty. Nothing reads that stage today. Also,
  `gameStage` is a static field written by every evaluation, which is safe only while one thread
  searches.
