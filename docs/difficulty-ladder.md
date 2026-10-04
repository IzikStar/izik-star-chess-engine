# Difficulty ladder

Levels 0-13, defined in `engine.Levels` (the UI's copy is `LEVELS` in `web/src/chess.ts`). How the
ladder was chosen: `docs/difficulty-ladder-research.md`.

| Level | Name | Who plays | Elo (≈, classic weights) |
|---|---|---|---|
| 0 | Random moves | any legal move | below the ladder |
| 1 | Beginner | built-in engine, depth 1, a quarter of its moves random | 100 |
| 2 | Novice | built-in engine, depth 1 | 460 |
| 3 | Casual | built-in engine, depth 2 | 750 |
| 4 | Improving | built-in engine, depth 3 | 1070 |
| 5 | Club player | built-in engine, depth 4 | 1260 |
| 6 | Strong club player | built-in engine, depth 5 | 1530 |
| 7 | Expert | built-in engine, depth 6 | 1690 |
| 8 | Strong expert | built-in engine, depth 7 (its deepest) | 1910 |
| 9 | Master | Stockfish, UCI_Elo 2150, 0.5 s a move | 2150 |
| 10 | International master | Stockfish, UCI_Elo 2400, 0.5 s a move | 2400 |
| 11 | Grandmaster | Stockfish, UCI_Elo 2650, 0.5 s a move | 2650 |
| 12 | Super grandmaster | Stockfish, UCI_Elo 2900, 0.5 s a move | 2900 |
| 13 | Full strength | Stockfish at full strength, 1 s a move | 3190+ |

The built-in levels search one ply deeper with 9-12 pieces left and two deeper with 8 or fewer,
and stop deepening at 5 s (`MinimaxEngine.TIME_CAP_MS`). Without Stockfish, Levels 9-13 play
Level 8 and the game says so. Hints are Stockfish at full strength.

The Elo column is the research measurement (20-100 games a pairing, about ±100 per level), on
the scale of Stockfish's UCI_Elo: Stockfish's own calibration against the CCRL computer rating
list, not a human federation's rating.

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
| 1 | 1 + random | not measured | +65 (¾ of Level 2's) | 165 |
| 2 | 1 | 62.3% | +87 [+25, +155] | 550 |
| 3 | 2 | 68.1% | +132 [+72, +201] | 880 |
| 4 | 3 | 72.1% | +165 [+97, +246] | 1240 |
| 5 | 4 | 67.2% | +124 [+61, +196] | 1380 |
| 6 | 5 | 76.5% | +205 [+138, +290] | 1740 |
| 7 | 6 | 80.9% | +251 [+179, +347] | 1940 |
| 8 | 7 | 76.3% | +203 [+98, +359] | 2110 |

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
mvn package -DskipTests
java -cp target/izikstar-chess-3.1.0.jar arena.LadderCalibration --weights tuned --games 100
java -cp target/izikstar-chess-3.1.0.jar arena.LadderCalibration --weights tuned --report-only
```

`--weights` picks the built-in levels' weights, `tuned` (the default) or `classic`; each set keeps
its own results file (`ladder-results-tuned.txt`, `ladder-results-classic.txt`).

Ratings depend on the machine: on a faster computer the built-in engine's deepest level runs
into its 5 s cap less often, so run it on the computer the game is played on.
