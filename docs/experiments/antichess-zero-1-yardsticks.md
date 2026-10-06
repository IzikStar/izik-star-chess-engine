# Antichess from zero 1: the champions against the random mover and Fairy-Stockfish

A follow-up measurement of the run "Antichess from zero 1" (`FromZero`, population 20, 80
generations, depth 4 with some games at 5, material and mobility evolved from all zeros;
`runs/antichess-zero-1.db`, its main write-up in [antichess-zero-1.md](antichess-zero-1.md)). During the run the
champion was measured only against the all-zero engine, which it beat 95-100% from generation 0
on, so that yardstick could not show progress. Here the champions of generations 0, 10, ..., 70
and 79 play the two new yardsticks of PR #45: the random mover and Fairy-Stockfish 14.0.1 XQ at
full strength with a fixed number of nodes a move.

Setup: `arena.Cli match gN.json OPPONENT --variant antichess --depth 4 --openings 25 --threads 4`,
the same 25 random 4-ply openings for every match, each with both colours (50 games), seed 1.
Champions written with `lab.Cli champion`. Run on 2026-10-06 in a 4-core cloud container, about
25 minutes for all 56 matches. The champion's score, Elo with the 95% interval:

| Champion | vs random | vs fsf 1 node | vs fsf 10 | vs fsf 100 | vs fsf 1,000 | vs fsf 10,000 |
|---|---|---|---|---|---|---|
| gen 0 | 100% | 90% | 88% | 76% (+200) | **15%** (−301 [−501, −194]) | 0% |
| gen 10 | 100% | 94% | 96% | 84% (+288) | 22% (−220 [−372, −119]) | 4% |
| gen 20 | 100% | 94% | 96% | 86% (+315) | 28% (−164 [−294, −67]) | 0% |
| gen 30 | 100% | 93% | 96% | 83% (+275) | **40%** (−70 [−178, +25]) | 2% |
| gen 40 | 100% | 100% | 100% | 90% (+382) | 38% (−85 [−195, +10]) | 0% |
| gen 50 | 100% | 96% | 96% | 90% (+382) | 30% (−147 [−272, −51]) | 2% |
| gen 60 | 100% | 94% | 96% | 90% (+382) | 34% (−115 [−232, −20]) | 4% |
| gen 70 | 100% | 96% | 96% | 92% (+424) | 34% (−115 [−232, −20]) | 4% |
| gen 79 | 100% | 99% | 97% | 83% (+275) | 26% (−182 [−318, −84]) | 2% |

Head to head, same setup:

| Match | Score | Elo |
|---|---|---|
| gen 79 vs gen 0 | 68% (+34 =0 −16) | **+131** [+35, +251] |
| gen 79 vs gen 40 | 56% (+26 =4 −20) | +42 [−50, +141] |

## What it says

1. **The run learned something real, mostly in the first 30-40 generations.** Against
   Fairy-Stockfish at 1,000 nodes the champion went from 15% (generation 0) to 40% (generation 30)
   and the generation-79 champion beats generation 0 by +131 Elo, an interval that excludes zero.
   After generation 40 nothing more is visible: 79 vs 40 is +42 with an interval across zero, and
   the 1,000-node scores of generations 40-79 (26-38%) are within each other's noise. The mutation
   step shrank from 150 to 10 cp over the run, so late generations mostly reshuffled small
   differences.
2. **Where the evolved engine stands.** At depth 4 it is roughly Fairy-Stockfish at a few hundred
   nodes a move: it wins clearly at 100 nodes, loses clearly at 1,000, and is crushed at 10,000.
   This is a strength scale, not an equal-effort comparison: our engine searches to depth 4
   whatever the node count, and the nodes it used were not counted.
3. **The random mover is no yardstick for a depth-4 engine.** Every champion, including generation
   0, wins 100%: in antichess a 4-ply search alone sees the forced captures a random mover walks
   into. It is a useful floor only for shallow searches or for a broken evaluation. The yardstick
   that moved with the run is **Fairy-Stockfish at about 1,000 nodes**; for the next antichess
   run, measure against `fsf:1000` (the Lab now offers Fairy-Stockfish; this node count is
   `--yardsticks fsf:1000` on the command line).
4. For the network question (docs/phase-7-research.md §3): the evolved piece values helped, but
   the gap to Fairy-Stockfish is mostly search, since antichess is decided by long forced capture
   sequences.

Raw lines are below; the PGN of every match was kept outside the repository.

```
random   all generations: +50 =0 -0
fsf:1    g0 +45 -5, g10 +47 -3, g20 +47 -3, g30 +46 =1 -3, g40 +50, g50 +48 -2, g60 +47 -3, g70 +48 -2, g79 +49 =1
fsf:10   g0 +44 -6, g10 +48 -2, g20 +48 -2, g30 +48 -2, g40 +50, g50 +48 -2, g60 +48 -2, g70 +48 -2, g79 +48 =1 -1
fsf:100  g0 +38 -12, g10 +42 -8, g20 +43 -7, g30 +41 =1 -8, g40 +45 -5, g50 +45 -5, g60 +45 -5, g70 +46 -4, g79 +41 =1 -8
fsf:1000 g0 +7 =1 -42, g10 +11 -39, g20 +14 -36, g30 +20 -30, g40 +19 -31, g50 +15 -35, g60 +17 -33, g70 +17 -33, g79 +13 -37
fsf:10000 g0 -50, g10 +2 -48, g20 -50, g30 +1 -49, g40 -50, g50 +1 -49, g60 +2 -48, g70 +2 -48, g79 +1 -49
```
