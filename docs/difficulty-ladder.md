# Difficulty ladder

Levels 0-13, defined in `engine.Levels` (the UI's copy is `LEVELS` in `web/src/chess.ts`). How the
ladder was chosen: `docs/difficulty-ladder-research.md`.

| Level | Name | Who plays | Elo (classic weights) | 95% interval |
|---|---|---|---|---|
| 0 | Random moves | any legal move | -680 | -900 to -510 |
| 1 | Beginner | built-in engine, depth 1, a quarter of its moves random | 120 | -30 to 230 |
| 2 | Learner | built-in engine, depth 1, an eighth of its moves random | 420 | 270 to 520 |
| 3 | Novice | built-in engine, depth 1 | 720 | 590 to 800 |
| 4 | Casual | built-in engine, depth 2 | 1050 | 990 to 1120 |
| 5 | Improving | built-in engine, depth 3 | 1310 | 1270 to 1350 |
| 6 | Club player | built-in engine, depth 4 | 1460 | 1420 to 1500 |
| 7 | Expert | built-in engine, depth 6 | 1680 | 1650 to 1720 |
| 8 | Strong expert | built-in engine, depth 7 (its deepest) | 1920 | 1880 to 1960 |
| 9 | Master | Stockfish, UCI_Elo 2150, 0.5 s a move | 2120 | 2080 to 2160 |
| 10 | International master | Stockfish, UCI_Elo 2400, 0.5 s a move | 2430 | 2400 to 2470 |
| 11 | Grandmaster | Stockfish, UCI_Elo 2650, 0.5 s a move | 2640 | 2610 to 2670 |
| 12 | Super grandmaster | Stockfish, UCI_Elo 2900, 0.5 s a move | 2930 | 2890 to 2970 |
| 13 | Full strength | Stockfish at full strength, 1 s a move | 3500+ | above the highest anchor |

The built-in levels search one ply deeper with 9-12 pieces left and two deeper with 8 or fewer,
and stop deepening at 5 s (`MinimaxEngine.TIME_CAP_MS`). Without Stockfish, Levels 9-13 play
Level 8 and the game says so. Hints are Stockfish at full strength, 4 s a move.

The Elo column comes from `arena.LadderCalibration` with the classic weights (October 2026, about
4,000 games on a 4-core cloud machine): every level against the next one up and against Stockfish
anchors, 100 games a pairing, plus the candidate levels below. It is on the scale of Stockfish's
UCI_Elo, Stockfish's own calibration against the CCRL computer rating list, not a human
federation's rating. Level 13 scored 89% against the highest anchor Stockfish accepts (3190), so
its number (about 3560) is only a lower bound in practice.

Candidates measured for the ladder (50 games a pairing; Level 2 100):

| Candidate | Elo | Used |
|---|---|---|
| depth 1, 15% random | 315 | no |
| depth 1, 12% random | 415 | Level 2 |
| depth 1, 10% random | 477 | no |
| depth 1-7 without the quiescence search | 26, 530, 728, 1223, 1212, 1424, 1490 | no |

Without the quiescence search the engine stops in the middle of exchanges and loses about a ply or
more: depth 4 without it (1223) is weaker than depth 3 with it (1310), and depth 7 without it
(1490) is weaker than depth 5 with it. None of them filled a gap better than the levels above.
Depth 5 (1584) is not a level: it sat only 120 Elo above depth 4 and 100 below depth 6.

## Engine weights

The built-in levels (1-8) play with one of two sets of evaluation weights, picked in the New game
dialog and remembered (`engine.Weights`):

- **Tuned** (the default): `presets/tuned-v1.json`, fitted by Texel tuning to Stockfish self-play
  (`docs/evolution-guide.md`).
- **Classic**: `presets/classic.json`, the hand-written weights the engine always played with.
  The Elo column above was measured with these.

The tuned weights against the classic ones at each level's depth, head to head over all openings
with both colours (102 games; 40 at depth 7):

| Level | Depth | Tuned scored | Elo gain (95% interval) | Shown Elo with tuned |
|---|---|---|---|---|
| 1 | 1 + ¼ random | not measured | +65 (inferred) | 185 |
| 2 | 1 + ⅛ random | not measured | +80 (inferred) | 500 |
| 3 | 1 | 62.3% | +87 [+25, +155] | 810 |
| 4 | 2 | 68.1% | +132 [+72, +201] | 1180 |
| 5 | 3 | 72.1% | +165 [+97, +246] | 1480 |
| 6 | 4 | 67.2% | +124 [+61, +196] | 1580 |
| 7 | 6 | 80.9% | +251 [+179, +347] | 1930 |
| 8 | 7 | 76.3% | +203 [+98, +359] | 2120 |

The UI adds these gains, rounded to 10, to the classic Elo (`TUNED_ELO_GAIN` in
`web/src/chess.ts`). A head-to-head gain can overstate the gain against other opponents: at depth
3 against Stockfish 1320 the tuned weights scored 67.2% and the classic ones 53.9%, about 100 Elo
apart rather than 165. `arena.LadderCalibration` would pin the tuned levels down properly. Saved
games record the weights, and the rating table in My games keeps the two apart; games saved
before the choice existed were played with the classic weights.

## Playing with a clock

In a game with a clock the engine also keeps to a time budget per move (`engine.TimeBudget`):
the time it has left over 25, plus three quarters of the increment, never more than half of what
is left (and about a second kept back). The budget only ever shortens a level's thinking: the
built-in levels stop deepening when it runs out, Stockfish's move time is cut to it, and Level 0's
pause takes at most half of it. With plenty of time on the clock every level plays exactly as
without one, so the Elo numbers above still hold; in time trouble the engine moves faster and
plays weaker. Hints ignore the clock.

## Measuring it again

`arena.LadderCalibration` plays every level against the next one up and, from Level 3, against
Stockfish anchors held to a UCI_Elo; it fits all games at once and prints each level's Elo with a
95% interval. The default, 100 games a pairing, takes many hours (most of it Level 8, which thinks
up to 5 s a move). It writes every finished game to its results file, so an interrupted run
continues where it stopped when started again with the same command.

```
./mvnw package -DskipTests
java -cp target/izikstar-chess-3.1.0.jar arena.LadderCalibration --weights classic --games 100
java -cp target/izikstar-chess-3.1.0.jar arena.LadderCalibration --weights classic --report-only
```

`--weights` picks the built-in levels' weights, `tuned` (the default) or `classic`; each set keeps
its own results file (`ladder-results-tuned.txt`, `ladder-results-classic.txt`). `--extra` adds
pairings, for example a candidate level against its neighbours (`--extra D1r12:L1,D1r12:L2`); a
candidate `D<depth>[nq][r<percent>]` is the built-in engine at that depth, `nq` without the
quiescence search, `r12` with 12% random moves. The October 2026 results file is
`docs/ladder-data/ladder-results-classic.txt`.

Ratings depend on the machine: on a faster computer the built-in engine's deepest level runs
into its 5 s cap less often, so run it on the computer the game is played on.
