# Experiment "Material 1" (2026-10-04)

`evolution.MaterialExperiment`, 10 generations, seed 1. The run file is `runs/material-1.db` (open
the Lab tab); its kept champions are in `runs/hall-of-fame/`. Command:

```bash
java -cp target/izikstar-chess-3.1.0.jar lab.Cli run runs/material-1.db --name "Material 1" \
     --algorithm evolution.MaterialExperiment --generations 10 --depth 3 --openings-per-pairing 1 \
     --threads 3 --seed 1 --yardstick-every 1 --yardstick-openings 10 --yardsticks default,classic \
     --deep-depth 0 --member-stockfish-openings 5
```

Time: 11 h 25 min on 3 threads (generation 0, 16 members: 1 h 48 min; then about 1 h each).
26,150 games.

## Per generation

| Gen | Champion | Its round-robin score | Champion vs tuned (20 games) | vs classic | Stockfish level | Members' average vs Stockfish |
|---|---|---|---|---|---|---|
| 0 | #1 | 70% | 50% | 57.5% | 1320 | 48% |
| 1 | #4 | 56% | 45% | 75% | 1320 | 63% |
| 2 | #6 | 53% | 40% | 72.5% | 1320 | 75% |
| 3 | #8 | 54% | 57.5% | 67.5% | 1500 | 40% |
| 4 | #1 | 70% | 45% | 70% | 1500 | 34% |
| 5 | #2 | 57% | 60% | 62.5% | 1500 | 31% |
| 6 | #9 | 55% | 50% | 77.5% | 1500 | 47% |
| 7 | #8 | 55% | 45% | 82.5% | 1500 | 50% |
| 8 | #0 | 67% | 67.5% | 62.5% | 1500 | 38% |
| 9 | #1 | 54% | 57.5% | 75% | 1500 | 50% |

Generation 4 was born with the dramatic mutation (±200); generation 8 too.

## Findings

- **Every tuned mutant beat every classic mutant in generation 0** (54-70% against 30-48%): the
  tuned non-material weights are worth more than any material change tried. From generation 1 on
  the whole population descends from tuned.
- **The material drifted the same way in every generation:** the middlegame pawn down, the
  endgame pawn up, the rook and the endgame knight up.

  | | pawn mg | pawn eg | knight mg | knight eg | bishop mg | bishop eg | rook mg | rook eg | queen mg | queen eg |
  |---|---|---|---|---|---|---|---|---|---|---|
  | tuned-v1 | 98 | 114 | 326 | 308 | 369 | 348 | 499 | 505 | 908 | 906 |
  | gen 8 champion | 65 | 165 | 327 | 367 | 347 | 399 | 554 | 566 | 936 | 891 |
  | gen 9 champion | 70 | 146 | 342 | 395 | 379 | 385 | 559 | 550 | 925 | 878 |

- **No confirmed gain over tuned-v1.** Checked over all 51 openings with both colours (102 games):

  | | depth 3 | depth 4 |
  |---|---|---|
  | gen 9 champion vs tuned-v1 | 43.1%, Elo −48 [−116, +16] | 47.5%, Elo −17 [−81, +45] |
  | gen 8 champion vs tuned-v1 | 49.0%, Elo −7 [−73, +58] | 54.9%, Elo +34 [−30, +100] |

  The 20-game yardstick results above (up to +127 for generation 8) were noise, as the guide
  warns. tuned-v1's material was fitted together with all its other weights, so it sits close to
  the best material for them; selection on ~400 games a member could not find a difference that
  large matches confirm.
