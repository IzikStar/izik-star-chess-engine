# Evolution guide

This is the hand-over of Phase 5: everything around the evolution algorithm is built, and the
algorithm itself is yours to write. This page covers what you get, how to write and run an
algorithm, how to read the numbers, and the traps that make evolution look like it works when
it does not.

## 1. What an individual is

An individual is a **parameter vector**: one integer for each of the 499 weights of the
evaluation (`ai.eval.ParamVector`, over the schema `BitBoardEvaluate.SCHEMA`).

- Every parameter has a name, a group, a default, a `min`/`max` range and a description
  (`ParamSpec`). See below for the defaults.
- The evaluation's unit is a centipawn, a hundredth of a pawn: `material.pawn` is 100. (It was a
  tenth of a pawn before; a parameter file without `"unit": "centipawn"` is read the old way and
  converted.)
- `schema.defaults()` is where evolution starts: the Texel-tuned weights, preset `tuned-v1`
  (`src/main/resources/presets/tuned-v1.json`, see section 6). The hand-written weights the
  engine has always played with are kept as the preset `classic` (`BitBoardEvaluate.CLASSIC`,
  `src/main/resources/presets/classic.json`), and the game plays with them. Edit that file to
  improve them by hand; `engine.SameMoveTest` records the moves they play.
- Most terms come in pairs, `.mg` (middlegame) and `.eg` (endgame). The engine blends the two by
  the material left on the board.
- Groups: `material` 5 terms, `pawns` 17, `kingSafety` 8, `king` 5, `development` 5,
  `pieces` 6, `mobility` 4, `activity` 3, `castling` 3. Each term has an mg and an eg weight.
  On top of those come three "until turn N" gates and the piece-square tables `pst.*`: 384
  numbers, 6 pieces × 32 squares × 2 phases. The tables are mirrored, so a–d files only.
- In `classic`, the terms added in Phase 5 are 0, which means "off". Texel tuning gave them
  values in `tuned-v1`.
- `vector.get("material.knight.mg")`, `vector.with(name, value)` and `toArray()` / `new
  ParamVector(schema, int[])` are all you need. Values are clamped to each parameter's range.
- A vector saves as JSON (`toJson()`), and `arena.Cli` can play a JSON file against anything.

## 2. The API you implement

```java
package evolution;

public class MyEvolution implements Evolution {
    public MyEvolution() { }          // the runner creates it by class name

    @Override
    public List<ParamVector> firstGeneration(ParamSchema schema, Random random) { ... }

    @Override   // optional: the default is a round robin
    public List<Pairing> pairings(List<ParamVector> population, Random random) { ... }
    // new Pairing(a, b) lets the run's depth schedule pick the depth; new Pairing(a, b, 4) sets it

    @Override
    public List<ParamVector> nextGeneration(Generation generation, Random random) { ... }
}
```

`Generation` hands you the population and every game it played:

- `points(i)`, `gamesPlayed(i)`, `score(i)` (0 to 1), `pointsAgainst(i, j)`, `ranking()`,
  `champion()`.
- `games()`: the full records, with moves, result and reason.

**Use only the `Random` you are given.** It is seeded from the run's seed and the generation
number, which makes a run repeatable, and a stopped run resumes exactly where it was. Any other
source of chance breaks both.

Put your class in `src/main/java/evolution/` and build with `./mvnw package -DskipTests`.
`RandomMutationExample` is the smallest working example. `MaterialExperiment` is the first
real experiment (2026-10-04): material weights only, generation 0 = 8 mutants each of the tuned
and the classic weights (±100), 40 games a pair at depths 3-6 set through `Pairing`, the best
3 survive, 8 children bred from them with a pull toward the best, a bonus for beating Stockfish
where the parents did not, and a dramatic mutation every fourth generation. Its run:
`--generations 10 --openings-per-pairing 1 --member-stockfish-openings 5 --yardsticks default,classic
--yardstick-every 1 --yardstick-openings 10 --deep-depth 0`. It keeps the better half and refills
with mutated copies. It is naive on purpose, and section 5 explains why it barely improves.

## 3. Running

```bash
java -cp target/izikstar-chess-3.1.0.jar lab.Cli run runs/try1.db --algorithm evolution.MyEvolution \
     --generations 30 --depth 3 --openings-per-pairing 2 --yardstick-every 5 --yardstick-openings 25
java -cp target/izikstar-chess-3.1.0.jar lab.Cli resume runs/try1.db     # after Ctrl+C
java -cp target/izikstar-chess-3.1.0.jar lab.Cli show runs/try1.db
java -cp target/izikstar-chess-3.1.0.jar lab.Cli champion runs/try1.db 29 champion.json   # its weights
java -jar target/izikstar-chess-3.1.0.jar                                  # the app: Lab tab
```

| Option | Meaning | Default |
|---|---|---|
| `--generations` | how many generations | 20 |
| `--depth` | search depth of the games (the deep ones aside) | 3 |
| `--deep-depth` | depth of the deep games; 0 = none | 4 |
| `--deep-share` | percent of games played deep, first generation-last generation; grows evenly in between | 10-40 |
| `--openings-per-pairing` | each pairing plays this many openings, each with both colours | 2 |
| `--variety` | moves within this much of the best may be played (pawn = 100) | 20 |
| `--max-plies` | a longer game is scored a draw | 300 |
| `--threads` | games at once | cores − 1 |
| `--seed` | same seed, same run | 1 |
| `--yardstick-every` | the champion plays the yardsticks every N generations (and after the last) | 5 |
| `--yardstick-openings` | openings of each yardstick match, each with both colours | 20 |
| `--yardsticks` | who the champion is measured against, comma-separated (see below) | default,classic,sf:auto |
| `--stockfish-from` | Stockfish yardsticks only from this generation on | 10 |
| `--member-stockfish-openings` | every member (not only the champion) plays Stockfish over this many openings, both colours, at `--depth`; `Generation.stockfishScore(i)` hands the results to the algorithm. The level moves like `sf:auto`, by the population's average | 0 (off) |

**Yardsticks** are players that never change. Each can be `default` (the schema defaults),
`classic` (the hand-written weights), any parameter file, `sf:1500` (Stockfish held to UCI_Elo
1500, 20,000 nodes a move; `sf:1500@50000` for 50,000), or `sf:auto`: Stockfish at the level where
the champion scores between 30% and 70%. It starts at 1320, the lowest Stockfish allows, and moves
one level up after a match above 70% and one down below 30%. Against an opponent that wins every
game there is no signal. Yardsticks with the same weights (today `default` and `classic`) play
once and report the same result. Yardstick matches use the generation's share of deep games too,
and `show` and the Lab tab give the score at each depth, so you can see whether a gain at depth 3
holds at depth 4.

Stockfish held to 1320 at 20,000 nodes a move is weaker than Stockfish's 1320 label, which is
measured at long time controls. The classic weights at depth 3 score about 55% against it. So read
`sf1500` as a fixed opponent to beat, not as a rating.

**Cost.** Games per generation = pairings × openings-per-pairing × 2. A round robin of 8 has 28
pairings, so 2 openings each means 112 games. Rough speed on a 4-core machine with 3 threads:

- 100 games at depth 2: a few seconds.
- 100 games at depth 3: about 15 s.
- 100 games at depth 4: about 100 s.
- A deep share of 10%-40% at depth 4 makes the average game about 1.6 to 3.4 times slower than all at
  depth 3.

Depth 3 is the sweet spot for evolving. The weights you find there carry over to deeper search
well enough.

**Games and the hall of fame.** Every game is kept in the run file with its depth.
`lab.Cli pgn runs/try1.db games.pgn` writes them as PGN (`--generation N`, `--member M` to narrow
it), and the Lab tab has a download button. `arena.Cli match` writes its games to
`runs/arena/*.pgn` (or `--pgn FILE`).

The **hall of fame** (`runs/hall-of-fame/`, one JSON file per entry) keeps every individual worth
keeping, from every run: its weights, where it came from, its yardstick results and its games. The
runner keeps each run's last champion and every champion whose whole interval against a yardstick
is above 0. Keep any member by hand with `lab.Cli keep runs/try1.db GENERATION MEMBER NAME --note
"..."` or the button in the Lab tab. `lab.Cli fame` lists them. Anywhere a player is named
(`arena.Cli match`, `--yardsticks`), `hof:NAME` is that entry, and the Lab tab plays against it.

`lab.Cli export runs/try1.db positions.csv` writes every quiet position of every game with its
result (`fen,result`), for fitting weights directly (section 6).

## 4. Reading the numbers

The only numbers that mean "the engine got better" are **the champion against the yardsticks**.
The first yardstick in the list is the chart in the Lab tab; `show` and the Lab tab list them all.

A generation's own scores only say who beat whom inside the population. A population can get
better at beating itself while getting worse at chess.

Every Elo comes with a 95% interval, and **the interval is the real result**:

- Over 100 games, a 55% score is +35 Elo, but anything from about −35 to +105 is just as
  likely.
- In a trial run, generation 0's champion *was the default weights*. Over 8 games against the
  default weights it scored 31%, which reads as −137 Elo, with an interval of −446 to +37.
  Nothing had changed: that was pure noise.
- An improvement is real only when the whole interval sits above 0.

Before believing a winner, confirm it with a bigger match:

```bash
java -cp target/izikstar-chess-3.1.0.jar arena.Cli match champion.json default --depth 3
```

That is about 100 games, with both colours for every opening.

## 5. Traps

1. **Noise beats signal.** A short match picks a lucky individual far more often than a better
   one. Selection on noise drifts randomly: that is what `RandomMutationExample` does. More
   games per pairing, keeping parents that won many generations in a row, or averaging scores
   across generations all help.
2. **Mutation size.** At 5% of each parameter's range, every mutant was worse than the
   defaults, so the defaults stayed champion forever. At 1%, the mutants were close enough to
   compete. Ranges are wide on purpose: a queen can be worth 0 to 27000. Step sizes should
   follow how sensitive a parameter is, not its range.
3. **Too many parameters at once.** 499 numbers evolved together with a few hundred games each
   generation is hopeless. Evolve one group at a time (`spec.group()`), or start with
   `material` and `pawns`. The piece-square tables are 384 numbers on their own.
4. **Overfitting to the population.** Individuals learn to exploit each other's weaknesses.
   The yardstick catches this. Also keep the defaults or an old champion in the population
   now and then.
5. **Same openings.** With 1 opening per pairing, everyone plays the same few lines that
   generation. The runner rotates through the 51 openings of the suite across generations and
   pairings, but more openings per pairing gives a fairer score.
6. **Depth as a hidden parameter.** Every game uses the same fixed depth. Don't evolve depth
   or time: deeper always wins, and that says nothing about the weights.

## 6. Directions worth trying

These are well-known methods that suit this setup:

- **(1+1) or (μ+λ) evolution strategy** with a step size that adapts: grow it when children
  win, shrink it when they lose (the "1/5 success rule").
- **SPSA**: nudge all weights by ±δ at once, play the + version against the − version, and
  move toward the winner. It is the method Stockfish's tuning uses. It needs no population,
  just two players per step.
- **CMA-ES**: learns which parameters move together. It is the strongest general method,
  but needs many more games per generation.
- **Texel tuning** (not evolution, but the starting point it refines; see below).

### Texel tuning

Texel tuning fits the weights so that `sigmoid(K · eval)` predicts each position's game result.
The evaluation is a sum of weights times feature counts, so the error has a single minimum: the
fit lands in the same place whatever weights it starts from. This is why the defaults were
fitted directly rather than first copying another engine's tables (PeSTO): those would only be a
different starting point for the same answer.

```bash
# Stockfish against itself from randomized openings; keeps quiet positions (no capture, no check)
java -cp target/izikstar-chess-3.1.0.jar lab.Cli selfplay positions.csv --games 3000 --nodes 5000
# fit; 10% of the positions are held out to check it is not memorizing
java -cp target/izikstar-chess-3.1.0.jar lab.Cli tune positions.csv tuned.json --iterations 1000
java -cp target/izikstar-chess-3.1.0.jar arena.Cli match tuned.json classic --depth 3
```

`--from` sets the starting weights (default `classic`), `--regularization` how strongly the
weights are pulled back toward them. `lab.Cli export` positions from a run's games work too.
`tuned-v1` came from this recipe; the numbers are in `docs/phase-5-research.md`.

## 7. Where things are

| What | Where |
|---|---|
| Your API | `src/main/java/evolution/` (`Evolution`, `Generation`, `Pairing`, the example) |
| Runner, record, CLI, export | `src/main/java/lab/` |
| Matches, tournaments, Elo | `src/main/java/arena/` (`arena.Cli match` for head-to-head checks) |
| Parameters and evaluation | `src/main/java/ai/eval/`, `src/main/java/ai/BitBoard/BitBoardEvaluate.java` |
| Lab page | `web/src/Lab.tsx`, served by `web.LabApi` |
| Design and decisions | `docs/phase-5-research.md` (§9 is the log of what was built) |
