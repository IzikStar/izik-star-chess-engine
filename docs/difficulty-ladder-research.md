# Difficulty ladder — research

Branch `difficulty-ladder`. The owner asked (2026-10-03) for the built-in engine to play the
lower levels with a search depth that keeps rising "for as long as it still plays at a realistic
speed", then Stockfish, with more levels in total so several are Stockfish, and with the average
Elo rising by roughly the same amount from each level to the next. This note has the
measurements and the proposed ladder; nothing in the code has changed yet.

All numbers come from the cloud container (4 cores, JDK 21, Stockfish 16 from apt). The raw
output and the probe programs are in `docs/ladder-data/`.

## 1. The ladder today

| UI level | Skill (0-18) | Who plays |
|---|---|---|
| 1 | 0 | random legal move |
| 2-7 | 2-12 | built-in engine, depth = level − 1 (+1 with ≤ 12 pieces, +2 with ≤ 8) |
| 8-10 | 14-18 | Stockfish Skill Level 13 / 15 / 17, 0.3 / 0.6 / 1 s a move |

The built-in engine deepens one ply at a time and plays the deepest depth finished within
`MinimaxEngine.TIME_CAP_MS` (5 s).

## 2. How long each depth takes

`DepthTiming` played 8 self-play games (depth 4, 8 openings spread over the suite), took a
position every 14 plies from ply 12 (38 positions measured), and timed
`Minimax.getBestMove` at depths 4-9 with the game's search options (quiescence, transposition
table, move ordering, variety 2). A search was stopped at 40 s.

| Nominal depth | Middlegame (> 12 pieces, 25 pos.) median / p90 | Over 5 s | 9-12 pieces (9 pos.) median / p90 | ≤ 8 pieces (4 pos.) median / p90 |
|---|---|---|---|---|
| 5 | 0.13 / 0.33 s | 0 / 25 | 0.04 / 0.11 s | 0.01 / 0.02 s |
| 6 | 0.62 / 1.7 s | 0 / 25 | 0.14 / 0.43 s | 0.03 / 0.05 s |
| 7 | 2.1 / 9.0 s | 4 / 25 | 0.57 / 2.1 s | 0.09 / 0.25 s |
| 8 | 9.0 / > 40 s | 17 / 25 | 1.9 / 8.0 s | 0.21 / 0.64 s |
| 9 | 32 / > 40 s | 23 / 25 | 6.5 / 28 s | 0.72 / 3.1 s |

A level searches its nominal depth in the middlegame, one more with 9-12 pieces and two more
with ≤ 8, so each level pays roughly the same column across a game. Depth 7 fits the 5 s cap
most of the time; depth 8 misses it in two thirds of middlegame positions.

**Does depth 8 still help with the cap?** A depth-8 player that deepens until 5 s, against plain
depth 7, 20 games: depth 7 scored 11 / 20. With the cap, "depth 8" plays its depth-7 move in most
middlegame positions and gains nothing measurable. The owner's guess was right: two plies above
today's Level 7 is already too slow, and one ply above (depth 7) is the deepest that pays off.

## 3. Elo of each player

`Ladder` plays any two players over the arena's openings, both colours each:

- `D<n>`: the built-in engine exactly as the game runs it (`MinimaxEngine.bestMove`, skill 2n,
  5 s cap, variety 2).
- `R`: random legal moves. `M<p>`: depth 1, but p % of moves random.
- `E<elo>`: Stockfish 16 with `UCI_LimitStrength` and `UCI_Elo`, one thread, fixed move time.

**Stockfish's move time matters.** At 0.1 s a move UCI_Elo plays weaker than at 0.5 s: depth 5
scored 13 / 20 against E1600 at 0.1 s and 4.5 / 20 at 0.5 s. The ratings below are therefore on
the scale of Stockfish at **0.5 s a move**, the time proposed for the Stockfish levels (§4), so the
table describes the levels as they will be played. The 0.1 s results enter the fit with one shared
unknown offset (fitted: Stockfish at 0.1 s plays about 110 Elo below its UCI_Elo).

Matches (score of the first player; 20 games unless noted):

| Match | Score | | Match | Score |
|---|---|---|---|---|
| R – D1 | 0 / 100 | | D3 – E1320 @0.5 s | 7 / 20 |
| D1 – D2 | 15.5 / 100 | | D4 – E1320 @0.5 s | 11.5 / 20 |
| D2 – D3 | 13.5 / 100 | | D5 – E1320 @0.5 s | 11.5 / 20 |
| D3 – D4 | 20 / 100 | | D5 – E1600 @0.5 s | 4.5 / 20 |
| D4 – D5 | 9.5 / 100 | | D6 – E1600 @0.5 s | 8 / 20 |
| D5 – D6 | 4 / 20 | | D6 – E1900 @0.5 s | 7 / 20 |
| D6 – D7 | 6 / 20 | | D7 – E2000 @0.5 s | 6 / 20 |
| D7 – D8 (5 s cap) | 11 / 20 | | D7 – E2300 @0.5 s | 5 / 20 |
| R – M75 | 3 / 40 | | M75 – M50 | 1 / 40 |
| M50 – M25 | 2.5 / 40 | | M25 – D1 | 3.5 / 40 |

plus six matches against Stockfish at 0.1 s (`anchor1.txt`). `fit.py` finds the ratings that best
explain all results together (Bradley-Terry maximum likelihood, half a draw added to each
pairing so 0 % scores stay finite), with Stockfish's UCI_Elo fixed:

| Player | Elo (≈) | Step from the one below |
|---|---|---|
| random | −1150 | |
| depth 1, 75 % random | −780 | +370 |
| depth 1, 50 % random | −290 | +490 |
| depth 1, 25 % random | 110 | +400 |
| depth 1 | 460 | +350 |
| depth 2 | 750 | +290 |
| depth 3 | 1070 | +320 |
| depth 4 | 1260 | +190 |
| depth 5 | 1530 | +270 |
| depth 6 | 1690 | +160 |
| depth 7 | 1910 | +220 |

Caveats: 20-game matches give about ±150 Elo per pairing (95 %); the fit across many pairings is
tighter, but treat each figure as ±100. Ratings below Stockfish's minimum UCI_Elo (1320) are
extrapolated through the built-in chain, and the bottom of the scale is not comparable with
human ratings. UCI_Elo itself is calibrated against computer play (CCRL), not FIDE or chess.com.

## 4. Proposed ladder

Thirteen levels, built-in engine for 1-8, Stockfish for 9-13. The average step is about 255 Elo.

| Level | Who plays | Elo (≈) | Step |
|---|---|---|---|
| 1 | built-in, depth 1, a quarter of its moves random | 100 | |
| 2 | built-in, depth 1 | 460 | +360 |
| 3 | built-in, depth 2 | 750 | +290 |
| 4 | built-in, depth 3 | 1070 | +320 |
| 5 | built-in, depth 4 | 1260 | +190 |
| 6 | built-in, depth 5 | 1530 | +270 |
| 7 | built-in, depth 6 | 1690 | +160 |
| 8 | built-in, depth 7 (5 s cap) | 1910 | +220 |
| 9 | Stockfish UCI_Elo 2150, 0.5 s | 2150 | +240 |
| 10 | Stockfish UCI_Elo 2400, 0.5 s | 2400 | +250 |
| 11 | Stockfish UCI_Elo 2650, 0.5 s | 2650 | +250 |
| 12 | Stockfish UCI_Elo 2900, 0.5 s | 2900 | +250 |
| 13 | Stockfish full strength, 1 s | 3190+ | +290 |

Decisions for the owner:

1. **Top built-in level = depth 7** (Level 8). Depth 8 is too slow and, with the cap, no stronger.
2. **Stockfish levels use UCI_Elo instead of Skill Level**, at 0.5 s a move, so each level has a
   known target Elo and the steps can be set to match the built-in ones. Level 13 is full
   strength. Hints stay full strength as today.
3. **Level 1 is no longer pure random moves.** Random play sits about 1100 Elo below depth 1, a
   gap no single level can bridge evenly. Level 1 becomes depth 1 with 25 % random moves (a few
   real blunders, but it captures what is hanging). Alternative: keep random moves as a "Level 0"
   below the ladder, outside the even steps.
4. **13 levels** (today 10). Without Stockfish installed, Levels 9-13 play Level 8 and the UI
   says so, as today. The evolved-champion games keep using the built-in levels (2-8).
5. **The Elo table appears in the UI** next to the level slider ("Level 6 · ≈ 1530") and in
   `docs/difficulty-ladder.md`.
6. The steps at Levels 5 and 7 are smaller (~170) and at Level 2 larger (~360). Evening them out
   would need in-between built-in players (for example depth n with some random moves); after
   implementation the whole ladder is re-measured with adjacent-level matches and Stockfish
   targets for 9-12 adjusted if a gap is off.
