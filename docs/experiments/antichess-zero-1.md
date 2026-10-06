# Experiment "Antichess from zero 1" (2026-10-05/06)

The first experiment on a game other than chess (the owner's decision D2): can evolution, starting
from an engine that knows nothing but the rules, discover how to value the pieces in antichess,
where the goal is to lose all your pieces and captures are forced?

`evolution.FromZero`, 80 generations, seed 7. The run file is `runs/antichess-zero-1.db` (open the
Lab tab). Started from the Lab's command line:

```bash
java -cp target/izikstar-chess-3.1.0.jar lab.Cli run runs/antichess-zero-1.db --name "Antichess from zero 1" \
     --algorithm evolution.FromZero --variant antichess --generations 80 --depth 4 --deep-depth 5 \
     --deep-share 10-40 --openings-per-pairing 2 --opening-plies 4 --random-openings 60 --max-plies 300 \
     --threads 3 --seed 7 --yardstick-every 5 --yardstick-openings 25 --yardsticks zero \
     --options population=20,survivors=4,immigrants=2,start=zero,startSpread=200,evolve=material+mobility,crossover=uniform,rate=0.5,stepFirst=150,stepLast=10
```

The settings: 20 members, each pair plays 2 random 4-ply openings with both colours (760 games a
generation), at depth 4, with 10% of games at depth 5 at first rising to 40% by the end. Member 0
of generation 0 is the all-zero engine; the others start around it (spread 200). Only material
and mobility evolve (12 numbers); the per-square values stay at 0. The best 4 survive, 2 random
immigrants join each generation, children by uniform crossover and a Gaussian step that shrinks
from 150 to 10 centipawns over the run.

Time: 1 h 04 min on 3 threads (23:01 to 00:05 UTC), 61,650 games (60,800 inside the population,
850 against the yardstick).

## What the champions learned

Material and mobility of each tenth generation's champion, in centipawns (the piece's value to
its owner; negative means "I would rather not have it"):

| Gen | Champion | Round-robin score | king | queen | rook | bishop | knight | pawn | mobility (most) |
|---|---|---|---|---|---|---|---|---|---|
| 0 | #13 | 84% | +30 | −111 | −218 | −277 | +82 | −102 | bishop −11 |
| 10 | #16 | 66% | +1 | −265 | −67 | −444 | −273 | −295 | rook −11 |
| 20 | #1 | 64% | +6 | −372 | −216 | −591 | −104 | −332 | pawn −13 |
| 30 | #2 | 60% | −111 | −483 | −528 | −758 | −304 | −463 | rook −14 |
| 40 | #16 | 65% | −250 | −438 | −789 | −828 | −373 | −410 | rook −18 |
| 50 | #0 | 65% | −176 | −449 | −858 | −772 | −411 | −448 | rook −19 |
| 60 | #19 | 68% | −198 | −359 | −486 | −733 | −284 | −478 | rook −21 |
| 70 | #13 | 65% | −222 | −429 | −515 | −747 | −280 | −566 | rook −15 |
| 79 | #1 | 63% | −236 | −360 | −543 | −732 | −282 | −490 | rook −18 |

- **Every piece became a burden.** By generation 30 all six values were negative and stayed so.
  The ordering settled early: the bishop is the piece the champions most want to be rid of
  (about −750), then the rook and the pawn (about −500), the queen (about −400), the knight and
  the king (about −250). The rook's value wandered most (−528 to −858 and back to −543).
- **Mobility learned one thing:** a mobile rook is bad (about −18 per move), the rest stays near
  zero. A rook that sees many squares is a rook that will be forced to capture.
- In antichess the king is an ordinary piece (no check, it can be captured) and the evolution
  treats it like one: a little less of a burden than most.

## How the games changed

Population games of each tenth generation:

| Gen | Average length (plies) | Decisive | Draws (of 760) |
|---|---|---|---|
| 0 | 56 | 98% | 16 |
| 10 | 67 | 92% | 63 |
| 20 | 62 | 93% | 50 |
| 30 | 70 | 89% | 83 |
| 40 | 73 | 88% | 95 |
| 50 | 67 | 89% | 83 |
| 60 | 73 | 87% | 97 |
| 70 | 70 | 88% | 94 |
| 79 | 65 | 91% | 70 |

Games got longer and more often drawn as the members learned to defend; both settle after
generation 40.

## Did it get stronger?

The yardstick chosen for the run, the all-zero engine, measured nothing: the champion beat it
95-100% in every measurement from generation 0 on (generation 0: 48 wins, 2 losses; generation 79:
50 wins). So after the run its champions were measured against the two yardsticks added that night
(PR #45), the random mover and Fairy-Stockfish at a fixed number of nodes a move:
**[antichess-zero-1-yardsticks.md](antichess-zero-1-yardsticks.md)**. In short:

- yes: generation 79 beats generation 0 by **+131 Elo** [+35, +251] (68% over 50 games), and
  against Fairy-Stockfish at 1,000 nodes a move the champion rose from 15% (generation 0) to 40%
  (generation 30);
- the gain was made by generation 30-40; generation 79 against generation 40 is +42 [−50, +141],
  no visible progress in the second half;
- the evolved engine at depth 4 is about as strong as Fairy-Stockfish at a few hundred nodes a
  move: it wins at 100 nodes, loses at 1,000, and is crushed at 10,000.

## For the next run

- Measure against **`fsf:1000`** (and the generation-0 champion via the hall of fame), not `zero`
  or `random`; both are beaten 100% by any depth-4 champion.
- 40 generations would have been enough at these settings; or keep the step larger for longer
  (`stepLast` 30-50) so late generations still explore.
- Evolve the per-square values too (`evolve=all`): with material settled by generation 30, they
  are the next thing an antichess evaluation can learn.
