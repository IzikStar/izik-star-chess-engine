# Evolution guide

This is the hand-over of Phase 5: everything around the evolution algorithm is built, and the
algorithm itself is yours to write. This page covers what you get, how to write and run an
algorithm, how to read the numbers, and the traps that make evolution look like it works when
it does not.

## 1. What an individual is

An individual is a **parameter vector**: one integer for each of the 499 weights of the
evaluation (`ai.eval.ParamVector`, over the schema `BitBoardEvaluate.SCHEMA`).

- Every parameter has a name, a group, a default, a `min`/`max` range and a description
  (`ParamSpec`). `schema.defaults()` is today's hand-tuned engine.
- The evaluation's unit is a tenth of a pawn: `material.pawn` defaults to 10.
- Most terms come in pairs, `.mg` (middlegame) and `.eg` (endgame). The engine blends the two by
  the material left on the board.
- Groups: `material` 5 terms, `pawns` 17, `kingSafety` 8, `king` 5, `development` 5,
  `pieces` 6, `mobility` 4, `activity` 3, `castling` 3. Each term has an mg and an eg weight.
  On top of those come three "until turn N" gates and the piece-square tables `pst.*`: 384
  numbers, 6 pieces × 32 squares × 2 phases. The tables are mirrored, so a–d files only.
- Terms added in Phase 5 start at 0, which means "off". Evolution can switch them on.
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
`RandomMutationExample` is the smallest working example. It keeps the better half and refills
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
| `--depth` | fixed search depth of every game | 3 |
| `--openings-per-pairing` | each pairing plays this many openings, each with both colours | 2 |
| `--variety` | moves within this much of the best may be played (pawn = 10) | 2 |
| `--max-plies` | a longer game is scored a draw | 300 |
| `--threads` | games at once | cores − 1 |
| `--seed` | same seed, same run | 1 |
| `--yardstick-every` | the champion plays the default weights every N generations (and after the last) | 5 |
| `--yardstick-openings` | openings of that match, each with both colours | 20 |

**Cost.** Games per generation = pairings × openings-per-pairing × 2. A round robin of 8 has 28
pairings, so 2 openings each means 112 games. Rough speed on a 4-core machine with 3 threads:

- 100 games at depth 2: a few seconds.
- 100 games at depth 3: about 15 s.
- 100 games at depth 4: about 100 s.

Depth 3 is the sweet spot for evolving. The weights you find there carry over to deeper search
well enough.

`lab.Cli export runs/try1.db positions.csv` writes every quiet position of every game with its
result (`fen,result`), for fitting weights directly (section 6).

## 4. Reading the numbers

The only number that means "the engine got better" is **the champion against the default
weights**, the yardstick. It is the chart in the Lab tab and the `vs default weights` line in
`show`.

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
   compete. Ranges are wide on purpose: a queen can be worth 0 to 2700. Step sizes should
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
- **Texel tuning** (not evolution, but a great starting point): fit the weights so that
  `sigmoid(eval)` predicts the results in `export`'s positions. It runs in minutes on a few
  hundred thousand positions, and evolution can then refine the result.

## 7. Where things are

| What | Where |
|---|---|
| Your API | `src/main/java/evolution/` (`Evolution`, `Generation`, `Pairing`, the example) |
| Runner, record, CLI, export | `src/main/java/lab/` |
| Matches, tournaments, Elo | `src/main/java/arena/` (`arena.Cli match` for head-to-head checks) |
| Parameters and evaluation | `src/main/java/ai/eval/`, `src/main/java/ai/BitBoard/BitBoardEvaluate.java` |
| Lab page | `web/src/Lab.tsx`, served by `web.LabApi` |
| Design and decisions | `docs/phase-5-research.md` (§9 is the log of what was built) |
