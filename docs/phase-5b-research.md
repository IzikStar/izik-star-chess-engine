# Phase 5b — Transposition table and move ordering

Branch `phase-5b-search-tt`. The owner asked (2026-10-03) to start straight away, so the
decisions below are the defaults this phase went with, listed so they can be changed, not
questions waiting for an answer.

## 1. Why

Phase 5's quiescence search made every leaf dearer. On the cloud container (4 cores, JDK 21)
Level 7 (depth 6) took 6.2 s in the middlegame benchmark, over the engine's 5 s cap, so it played
its depth-5 move there. `engine.SearchSpeedTest` had been loosened to twice the cap. Phase 4b had
left two known speedups for later (docs/phase-4b-research.md §4, Fork B2): the transposition
table, which existed but was commented out and unsound as written, and better move ordering.

## 2. What was there

- `ai.TranspositionTable`: a `HashMap<Long, Entry>`, created new for every depth, never used (the
  calls were comments). Its entries had no bound type, so an alpha-beta cut-off would have been
  stored as an exact score.
- `ZobristHashing.computeHash`: pieces and side to move only. Good enough for the search's
  repetition check; not for a table, because castling rights, the en-passant square, whether a
  side has castled and the move number all change the evaluation.
- Move ordering: the move generator's `moveValue`. Checks first (`Integer.MAX_VALUE`), then
  captures by the victim's value, minus a little for giving up castling rights; the sort is
  stable, so ties keep generation order (king, queen, rook, bishop, knight, pawn).
- Each depth of the iterative deepening was an independent search: nothing learned at depth 5
  helped depth 6.

## 3. Decisions (defaults taken)

### 3.1 One table per `getBestMove` call, shared by its depths

The table, the killers and the history live in a `SearchState` created by `getBestMove` and
handed to each depth. Depth 6 starts by trying, in every position, the move depth 5 found best.

- Not kept between moves of a game. Keeping it would make the engine a stateful object and the
  arena's games depend on what was searched before; one table per call keeps every search
  independent, thread-safe (nothing static, nothing shared between threads) and repeatable with a
  seed, which the evolution arena relies on.
- Not used in the quiescence search. It would save less (quiescence nodes are cheap and mostly
  unique) and its bounds interact with standing pat.

### 3.2 Size and replacement

- Two plain arrays (`long` key, `long` packed entry: score 32 bits, depth 8, bound 2, move 22),
  16 bytes a slot, allocated once per call and never grown.
- `2^(14 + depth)` slots, at most `2^20`: 1 MB at depth 2 up to 16 MB from depth 6. The
  busiest benchmark visits about 1.1 M nodes at depth 6, most of them quiescence nodes that never
  enter the table, so 1 M slots is plenty. Four arena threads at depth 6 use 64 MB; the 300 MB
  heap of `SearchSpeedTest` has room to spare.
- Slots come in pairs. The first keeps the deeper entry; the second always takes the newest. A
  deeper entry pushed out of the first slot moves to the second. A shallower visit that has no best
  move of its own (a fail-low) keeps the move an earlier visit stored.

### 3.3 What the key covers, and what it leaves out

`ZobristHashing.searchKey` = `computeHash` (pieces, side to move) XOR castling rights, the
en-passant square, has-castled flags and the move number (the evaluation has opening-only terms
that read it). So two positions sharing a key are scored the same by the evaluation, short of a
64-bit collision. The repetition check keeps using `computeHash` unchanged.

Left out, as engines usually do:
- **The 50-move counter.** A position 2 plies from the 50-move draw and the same position 40
  plies from it share a key. It matters only within a few plies of the draw.
- **Repetition history.** The search's threefold check sees only the current branch, and a
  position needs to appear three times on it, at least 8 plies, which Levels 1-7 never search.
  So in practice no repetition draw reaches the table.

### 3.4 Bounds and mate scores

- Fail-soft alpha-beta, as before. A result at or below the window's lower edge is stored as an
  upper bound, at or above the upper edge as a lower bound, otherwise exact. A stored entry is used
  only if it was searched at least as deep, and a bound only if it settles the window.
- The root never takes a score from the table (it always searches its moves, so `variety` still
  sees every candidate's score), but it does take the table's move first.
- Mate scores are counted from the root (`MATE + depth to go`). The table stores them counted
  from the position (`score - depth to go`) and adds the depth back when read, so a mate stored at
  one ply reads right at another.

### 3.5 Move ordering

In every main-search position, after the generator's sort:
1. the table's move;
2. captures and queen promotions, most valuable victim first, then cheapest attacker
   (`BitBoard.captureScore`, with the quiescence search's fixed piece values);
3. the two killer moves of this ply (quiet moves that caused a cut-off at the same ply);
4. the other quiet moves by history (a cut-off adds depth² for that from/to square pair and side;
   the table halves when one entry passes 2^20).

Ties keep the generator's order, so quiet checks still come first among equal quiet moves.

### 3.6 Switch

`Minimax.Options` has two new flags, `transpositionTable` and `moveOrdering`, both on by default
(the three-argument constructor turns them on). `Options.withSpeedups(false)` searches exactly as
before this phase. The arena exposes it as `--no-speedups A|B`, and `--move-ms N` plays an
equal-time match instead of fixed depth.

## 4. Speed

`Minimax.getBestMove` at Level 6 and 7 depths (the rook endgame has 8 pieces, so 2 plies deeper),
the faster of two runs after a JIT warm-up, nodes = main search + quiescence:

| Position | Depth | Before | After | Nodes before | Nodes after |
|---|---|---|---|---|---|
| start | 5 / 6 | 80 / 726 ms | 35 / 168 ms | 0.10 M / 0.99 M | 0.03 M / 0.12 M |
| after 1.e4 | 5 / 6 | 308 / 3072 ms | 46 / 175 ms | 0.53 M / 4.67 M | 0.05 M / 0.14 M |
| italian | 5 / 6 | 308 / 3638 ms | 56 / 400 ms | 0.40 M / 4.88 M | 0.05 M / 0.31 M |
| middlegame | 5 / 6 | 600 / 6158 ms | 161 / 1227 ms | 0.96 M / 8.03 M | 0.25 M / 1.06 M |
| kiwipete | 5 / 6 | 218 / 1275 ms | 149 / 690 ms | 0.28 M / 1.03 M | 0.15 M / 0.48 M |
| rook endgame | 7 / 8 | 48 / 260 ms | 23 / 58 ms | 0.13 M / 0.48 M | 0.04 M / 0.07 M |

Each part alone, middlegame depth 6: table only 2281 ms (2.59 M nodes), ordering only 1650 ms
(1.45 M nodes), both 1227 ms (1.06 M nodes). A node costs a little more (about 1.1 µs against
0.8 µs) because every main-search node computes a key and scores its moves, but there are 8 times
fewer of them.

Level 7 is back under the 5 s cap with room to spare (worst 1.2 s), so `SearchSpeedTest`'s Level 7
limit is the cap again instead of twice the cap.

## 5. Does it change the moves?

**The score never changes.** `ai.TranspositionTableTest` searches the 56 positions of
`same-moves.txt` at depths 1-4, and four mate positions at depths 1-5, with and without the
speedups, and requires the same root score. A one-off run also matched all 56 at depth 5 and the
six benchmark positions at depth 6 (0 differences).

**The move can change, only between moves of exactly the same score.** Alpha-beta keeps the first
move that reaches the best score, so a different order picks a different one of the tied moves.
Six of the 224 recorded moves in `same-moves.txt` changed (4 of 56 positions), each to a move of
the same score, so the file was re-recorded and its header says why. The changes:

| Position | Before (depths 1-4) | After |
|---|---|---|
| middlegame | d1a4 d1a4 d1a4 d1b3 | d1a4 d1a4 d1a4 d1a4 |
| `2b1kbnr/1rq1np1p/…` | b7b5 b7b5 a6b5 b7b5 | a6b5 ×4 |
| `r1bqkbnr/pppppppp/2n5/…` | g8f6 g8f6 e7e5 g8f6 | g8f6 g8f6 e7e5 d7d5 |
| `1nb1k2r/rpppnppp/…` | e6f7 ×4 | h1h6 ×4 (takes the queen first) |

## 6. Strength

Arena (`arena.Cli`), default weights against themselves, the 51 openings of the suite with both
colours (102 games), variety 2, 3 games at a time:

- **Same depth (4):** with speedups +42 =30 -30, 55.9%, Elo +41 [-15, +100]. Within the noise of
  equal strength, as expected: same scores, only different picks among ties. 86 s in all.
- **Same time (300 ms a move, deepening as far as the time allows):** RESULT_TIMED

## 7. Tests

- `ai.TranspositionTableTest` (new, 62 cases): same score with and without, at depths 1-4 for
  the 56 positions and 1-5 for the mates; slot-pair replacement; same seed, same move.
- `engine.SameMoveTest`: re-recorded (§5).
- `engine.SearchSpeedTest` (`-Pstress`): Level 7 limit back to the 5 s cap.
- `mvn test`: TESTS_MAIN. `mvn test -Pstress`: TESTS_STRESS.
