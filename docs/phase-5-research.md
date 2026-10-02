# Phase 5 research — groundwork for an engine that learns by self-play evolution

Status: **all decisions (E1-E12) approved by the owner 2026-10-02**, E9-E12 after he asked for
"dozens more parameters" and research into machine learning setting them or a neural network
that reads the board (§8). Implementation follows §7.
Written 2026-10-02 on top of Phase 4c (`phase-4c-web-ui`, PR #6).

## 1. What the owner asked for

> "I'd like an evolution flow: the computer makes several versions with random values for all
> kinds of parameters, lets them play each other, and the values are corrected in favour of the
> winners. The game should learn the more it plays. That is the part I want to work on myself,
> it's the cool part. Everything needed to get us there I'd rather you do, to save time."

This answers the question REFACTOR_GUIDE's Phase 5 left open ("define what *learning from past
games* means before designing the schema"): **learning = evolving the engine's parameters
through self-play.** So Phase 5 is re-scoped around that goal. The split of work:

- **The owner writes the evolution itself**: how a population is created, how individuals are
  selected, mutated and crossed, how fitness is computed from results. That is one Java
  interface (§5.4) and whatever he builds behind it.
- **Claude builds everything that leads there**: an engine whose parameters can be set from
  outside, a fast and fair self-play arena, a record of every generation and game, and a page in
  the web app to watch evolution happen and play against the champion.

## 2. What the engine looks like today (measured, not assumed)

### 2.1 The evaluation is ~30 hard-coded numbers

`ai.BitBoard.BitBoardEvaluate.evaluate` sums nine terms, every weight a literal in the code:

| Term | Numbers in it today |
|---|---|
| Material | P 10, N 30, B 33, R 50, Q 90 |
| Pawn advance | 9 / 7 / 5 / 3 / 1 for ranks 7..3 (White), mirrored for Black; +5 per centre pawn |
| King placement (first 20 plies) | 10 / 5 / 0 / -7 / -25 by square group (g1/b1, f1/c1, back rank, 2nd rank, elsewhere) |
| Castling | -6 / -4 for losing king/queen-side rights, +16 for having castled |
| Activity ("targets") | +1 per attacked square, +2 per attacked enemy piece, +1 per defended own piece |
| Bishops | -15 if any bishop is still on the back rank |
| Queen | -15 if the queen left d1/d8 in the opening (first 8 plies) |
| Knights | -10 undeveloped, +2 on c3/f3 (c6/f6), -5 if out early before ply 9 |

Plus thresholds: "opening" = before ply 8, king table applies before ply 20, knight rule before
ply 9. Three more terms are written but switched off (`getIsKingsBehindPawns`,
`getPiecesValueForAmount`, `getPawnsInTheCenter`). These are exactly the "parameters" the
owner wants to evolve; today none can be changed without editing code.

### 2.2 Problems that would break or poison evolution

1. **Shared mutable state.** `BitBoardEvaluate.gameStage` is a `static` field written on every
   evaluation. Two searches on two threads overwrite each other's game stage, so **games cannot
   run in parallel** until it goes. (`ai.Debug.debugging` is also a mutable static, but read-only
   in practice.)
2. **The endgame stage never triggers.** `getGameStage` counts
   `whitePieces & blackPieces`, which is always empty (a square can't hold both colours), so
   stage 2 is unreachable. Harmless today because nothing reads stage 2, but an evolved
   "endgame weight" would be dead.
3. **Self-play is deterministic.** Measured: three depth-3 games from the start position are
   the same 82-ply game, three depth-4 games the same 125-ply threefold draw. `Minimax` has a
   random tie-break, but it never holds more than one move, so the same two engines always play
   the same game. Evolution would learn from one game repeated, not from many.
4. **No quiescence search.** The search stops at a fixed depth in the middle of captures, so
   the score at the leaves is often "I just took a queen" before the recapture. At low depth
   this noise dominates the evaluation, so a weight change may win or lose for reasons that have
   nothing to do with the weight. Fitness signal gets much cleaner with a capture-only
   extension at the leaves.
5. **The parameters reach the search through statics only.** `Minimax` calls
   `BitBoardEvaluate.evaluate(board, rootIsBlack)`; there is no way to say "evaluate with *these*
   weights". Move ordering (`getSortedNextStates` by `moveValue`) does not use the evaluation, so
   only the leaf evaluation needs the parameters.

### 2.3 Speed (measured in this container, 4 cores, one thread per game)

| Fixed depth | Average game | Games per hour on 4 cores |
|---|---|---|
| 3 | ~0.7 s, 82 plies | ~20,000 |
| 4 | ~2 s, 125 plies | ~7,000 |

So a generation of, say, 16 individuals × 16 games each (128 games) takes about 5 s at depth 3
or 40 s at depth 4 on four cores; a few hundred generations fit in an evening. The owner's PC
will differ; the arena reports its own numbers. Faster search (the transposition table and move
ordering deferred in Phase 4b) would let evolution run deeper, but is not needed to start.

### 2.4 What already exists and can be reused

- `rules.Game` plays and adjudicates a full game (mate, stalemate, 50-move, threefold,
  insufficient material). The arena can use it as the referee.
- `engine.Engine` / `SearchRequest` is the move-picker interface; a parameterised
  `MinimaxEngine` fits it.
- `engine.SameMoveTest` pins the engine's moves at depths 1-4 on 56 positions. It proves that
  moving the weights out of the code changes nothing when the defaults are used.
- `game.StressTest` already runs unattended engine-vs-engine games.
- The opening-book code (`ai.openingBook`, Lichess over the network) was never wired in. For
  self-play an **offline** opening suite is what matters (§5.2); the online book can wait.

## 3. What evolution needs from the infrastructure

1. A **parameter vector** with a name, default, minimum and maximum for each entry, so any
   algorithm can mutate it without knowing chess.
2. An engine that **plays with a given vector**, thread-safe, so many games run at once.
3. **Fair, varied games**: each pairing plays a set of different openings, each opening with
   both colours, at a fixed depth (fixed depth is CPU-independent and reproducible; fixed time
   would make results depend on the machine's load).
4. **Adjudication** so games don't run forever: the normal rules, plus a ply cap and optional
   "resign when the score is hopeless for N moves".
5. **Statistics** that don't fool you: wins/draws/losses, an Elo difference with an error bar,
   and a fixed yardstick (the current default weights, and optionally Stockfish at a low skill
   level) so "the population got better" is measured against something that does not move.
6. **A record** of every generation, individual, match and game, so a run can be stopped,
   resumed, compared and replayed.
7. **Something to look at**: progress over generations, the current champion's weights, any game
   replayed on the board, and "play against the champion" in the normal game screen.

## 4. Options

### Option A — Evolution-ready engine, arena, record and lab page (recommended)

Build items 1-7 above in four steps (§7), each a green commit, before the owner writes any
evolution code. The owner then works only in `evolution/` against a stable API, and every run
he does is recorded and visible.

### Option B — Minimal: parameters + arena, no record or UI

Items 1-5 only, results printed to the console. Faster to deliver, but the owner would end up
building the record and the visualisation himself, which is the opposite of what he asked.

### Option C — Opening book and game database first, as the old Phase 5 said

Builds things evolution does not need yet (the online Lichess book, storing human games).
Recommended against: it delays the part the owner cares about.

## 5. Design of Option A

### 5.1 Parameters (`engine.params`)

- `ParamSpec(name, defaultValue, min, max, description)` and `EvalParams` = an immutable
  `int[]` plus the shared list of specs. Integers, in the evaluation's own units
  (pawn = 10 today).
- Every literal in §2.1 becomes a named parameter whose default is today's value, so
  `EvalParams.defaults()` plays **exactly** today's moves (`SameMoveTest` stays green, unchanged).
- The three switched-off terms become parameters with default weight 0, so evolution can turn
  them on.
- Saved and loaded as JSON (`{"pawn": 10, "knight": 30, ...}`), readable and hand-editable.
- `BitBoardEvaluate` becomes an instance holding its `EvalParams` (no statics), created once per
  search and passed to `Minimax`.

### 5.2 Arena (`arena` package)

- `Match.play(EvalParams white, EvalParams black, Opening opening, MatchRules rules)` → a
  finished game (moves, result, reason, ply count).
- `MatchRules`: fixed depth, ply cap (default 300 → draw), optional resign threshold.
- An **opening suite**: ~50 short, balanced opening lines (2-6 moves each, e.g. Italian,
  Sicilian, Queen's Gambit, King's Indian...) as a resource file in UCI, every line checked by
  `rules.Rules` in a test. Each opening is played twice with colours swapped, so neither side
  gets the better opening.
- `Tournament` runs a list of pairings on a thread pool (default: number of cores - 1) and
  reports progress.
- Statistics: score, W/D/L, Elo difference with a 95% interval; a gauntlet mode
  "candidate vs yardstick" for honest progress checks.
- Command line: `java -cp <jar> arena.Cli match a.json b.json --depth 3 --games 100`.

### 5.3 Record (`lab` package, SQLite)

Tables: `run` (name, settings, start time), `generation`, `individual` (params JSON, parent ids,
fitness), `game` (white, black, opening, moves in UCI, result, reason, plies). SQLite through
`org.xerial:sqlite-jdbc`: one file per run, nothing to install, opens in any SQLite browser and
supports the queries the owner will want ("which weights rose over the last 50 generations?").

### 5.4 The owner's part (`evolution` package)

One interface, the only thing he has to implement:

```java
public interface Evolution {
    /** The first generation. */
    List<EvalParams> firstGeneration(ParamSchema schema, Random random);

    /** Which games to play this generation (default: round robin). */
    default List<Pairing> pairings(List<EvalParams> population) { ... }

    /** The next generation, from this one's game results. */
    List<EvalParams> nextGeneration(List<EvalParams> population, Results results, Random random);
}
```

The runner (`lab.EvolutionRunner`) does the rest: plays the pairings, records everything,
checks the champion against the yardstick every N generations, and can stop and resume.
To prove the plumbing works there will be a deliberately naive `RandomMutationExample`
(mutate everyone, keep the top half) used by the tests. The owner can read it, delete it or
ignore it; the real algorithm is his.

### 5.5 Lab page in the web app

A second screen next to the game: pick a run, a chart of champion strength vs the yardstick per
generation, a table of how each weight moved, the latest games (click one to replay it on the
board), and a **"Play the champion"** option in the New game dialog. Runs start from the command
line or from the page.

### 5.6 Search changes

- **Quiescence search** (captures only, with stand-pat) at the leaves: cleaner fitness signal
  and a stronger engine at every level. It changes the engine's moves, so `same-moves.txt` is
  re-recorded once, in its own commit, with the reason in the header.
- **Variety**: the opening suite does this for self-play. In normal play nothing changes.
- **Transposition table and move ordering** (deferred from Phase 4b): not in this phase. They
  only make the arena faster; revisit if depth 3-4 turns out too shallow.

## 6. Decisions for the owner

| # | Question | Options | Recommendation |
|---|---|---|---|
| E1 | Direction | **A full groundwork** / B parameters + arena only / C old Phase 5 (opening book + game DB) | **A** |
| E2 | What evolves | **Evaluation weights only** / also search settings (depth, quiescence on/off) | **Weights only.** Search settings change speed, so a "deeper" individual would win by thinking longer, not by judging better |
| E3 | Fix the endgame-stage bug and add quiescence before evolving | **Yes** (moves change once, re-recorded) / no, evolve today's engine as is | **Yes** |
| E4 | Game format | **Fixed depth, ~50 openings × both colours** / fixed time per move | **Fixed depth** (reproducible, independent of the PC's load) |
| E5 | Where results live | **SQLite file per run** / JSON files | **SQLite** |
| E6 | Lab page in the web app | **Yes, with "play the champion"** / command line only for now | **Yes** |
| E7 | Example algorithm | **A naive one for the tests only, the real one is yours** / none / a full reference genetic algorithm | **Naive one for tests only** |
| E8 | Online Lichess opening book | **Later** (after evolution works) / now | **Later** |

## 7. Plan

Each step on branch `phase-5-evolution`, each commit green.

1. **Parameters.** `EvalParams` + specs for every literal, evaluation as an instance, no
   statics, behind the `Evaluator` interface (§8.4). `SameMoveTest` unchanged and green (proves
   defaults = today), plus a test that two different parameter sets running on two threads
   don't affect each other.
1b. **More parameters (E9).** The new terms of §8.2, each with default weight 0 (and tapered
   pairs whose both halves start at today's value), so the defaults still play today's moves
   and `SameMoveTest` stays green; evolution decides which ones matter.
2. **Search fixes (E3).** Endgame-stage fix, quiescence search; re-record `same-moves.txt`;
   a gauntlet showing the new engine beats the old at the same depth.
3. **Arena.** Opening suite (each line validated), `Match`, `Tournament` on a thread pool,
   adjudication, Elo with error bars, CLI. Tests: a match is reproducible, colours are swapped,
   a stronger depth beats a weaker one.
4. **Record and runner.** SQLite schema, `EvolutionRunner`, the `Evolution` interface, the naive
   example, stop/resume, and the training-data export of §8.3 (E12). Test: a 3-generation run
   on a tiny population records everything.
5. **Lab page.** Runs list, progress chart, weight table, game replay, "play the champion".
   API tests and a Playwright test.
6. **Hand-over.** A short `docs/evolution-guide.md` for the owner: the API, how to start a run,
   what the numbers mean, and pitfalls (noise, overfitting to the population, why the yardstick
   matters).

**Exit criteria.** `EvalParams.defaults()` reproduces the engine; parameter sets play in
parallel without interfering; a tournament of N games with colours swapped runs from the CLI
and reports Elo ± error; an evolution run with the example algorithm is recorded in SQLite and
visible on the lab page; the owner can play the champion; all suites green.

**Rollback.** Steps 1 and 3-6 don't change how the engine plays; step 2 does and is one commit
that can be reverted on its own.

## 8. Beyond ~30 hand-written weights: more parameters, learned parameters, neural networks

The owner's follow-up: "we need dozens more parameters. Maybe even let machine learning set
parameters somehow. Or the input could just be the screen, although that moves towards a neural
network. Let's research it."

### 8.1 What has been done before

- **Evolving a hand-written evaluation works.** David, Koppel and Netanyahu evolved the weights
  of a full evaluation function (first by imitating grandmaster games, then by coevolution) and
  the search's settings, and report a program on par with leading engines of the time
  ([arXiv 1711.08337](https://arxiv.org/abs/1711.08337)).
- **Evolving piece values, piece-square tables and small neural networks by self-play works
  too.** Fogel's Blondie25 played variations of itself for over 8,000 generations and improved
  by almost 400 rating points ([chessprogramming: Blondie25](https://www.chessprogramming.org/Blondie25)).
- **The usual "machine learning sets the weights" in chess is Texel's method**: treat the
  evaluation as a single logistic neuron, and fit the weights so the evaluation of positions
  from real games predicts how those games ended (win/draw/loss), by minimising the prediction
  error. Other families: temporal-difference learning (TD-Leaf, KnightCap), SPSA, CLOP, and
  evolutionary methods ([chessprogramming: Automated Tuning](https://www.chessprogramming.org/Automated_Tuning)).
- **Modern engines read the board with a small neural network (NNUE)**: 768 on/off inputs (one
  per piece type × colour × square), one hidden layer, one output. It is fast because a move
  flips only a few inputs, so the hidden layer is updated, not recomputed. It is trained on
  positions labelled with game results and engine scores
  ([chessprogramming: NNUE](https://www.chessprogramming.org/NNUE)).

### 8.2 Level 1: many more hand-written parameters (dozens to hundreds)

The standard vocabulary of chess evaluation, none of which the engine has today:

| Group | Terms | Count |
|---|---|---|
| Game phase | every weight split into a middlegame and an endgame value, blended by the material left ("tapered") | ×2 |
| Piece-square tables | a value for each piece on each square (left-right mirrored: 32 squares) | 6 × 32 × 2 phases = 384 |
| Mobility | value per legal move, per piece type | 4 × 2 |
| Pawn structure | doubled, isolated, backward, connected, passed pawn by rank (6), protected passer, blocked passer | ~12 × 2 |
| King safety | pawn shield in front of the castled king, open/half-open files near the king, attackers near the king by piece type, safe checks | ~10 × 2 |
| Pieces | bishop pair, rook on open/half-open file, rook on 7th rank, knight outpost, bad bishop, queen early | ~8 × 2 |
| Other | tempo (side to move), trade-down bonus when ahead | ~3 |

That is about 70 named terms (~140 with the two phases) plus 384 table entries: "dozens more
parameters" several times over. Each new term starts at weight 0 (so today's play is unchanged
until evolution or tuning gives it a value). All of it is still fast integer arithmetic.

What changes for evolution: with ~500 numbers, a population that only learns from game results
needs many more games per step to tell good from lucky, because the effect of one table entry on
a game's result is tiny. That is the owner's part to solve; options the literature uses are
evolving groups (e.g. only the 30 most important terms first, tables later), methods that move all
numbers at once from few games (SPSA, CMA-ES), or seeding the population from a Texel fit.

### 8.3 Machine learning that sets the parameters

This needs no new machinery beyond what the arena already produces: every arena game is
recorded, so its positions and final results are a training set. A Texel-style fit over a few
hundred thousand quiet positions from those games (plus, optionally, positions scored by
Stockfish) sets all ~500 numbers in minutes, by gradient descent. It combines well with
evolution: the fit gives a strong starting population, evolution refines it by actual play, and
the new games feed the next fit, so "the more it plays, the more it learns" in both senses.

The groundwork for this is small: an export of `(position, result)` rows from the record (E12),
and the evaluation expressed as features × weights so a gradient can be taken. Whether to write
the fitter, and how it mixes with evolution, is the owner's call; it is a natural second half of
"the cool part".

### 8.4 Level 2: a neural network that reads the board ("the input is the screen")

- **The screen and the board carry the same information.** A network reading pixels would first
  have to learn to recognise the pieces (a vision problem) before it can learn chess. Giving it
  the board directly as 768 on/off inputs is the "screen" without the rendering, and is what
  NNUE does. Recommended over pixels.
- **Size.** 768 → 32 → 1 is about 25,000 weights; 768 → 256 → 1 about 200,000. In Java, without
  incremental updates, a 32-wide net costs roughly 32 × 30 additions per evaluation (only the ~30
  occupied squares are "on"), comparable to today's attack-map evaluation, so search speed stays
  in the same range. Wider nets need the incremental update.
- **Who sets the weights.** Evolution alone can (Blondie25), but it scales badly with tens of
  thousands of weights. Gradient training on recorded positions (like §8.3, with a hidden layer)
  scales well and is how NNUE nets are made. A natural split for this project: training sets the
  weights, evolution chooses what is trained (hidden size, learning rate, which games are used)
  and keeps a population of nets that play each other.
- **Level 3, AlphaZero-style** (a deep network that also proposes moves, guided tree search,
  millions of self-play games on GPUs) is out of reach on a home PC for real strength. Not
  recommended.

**Design consequence for this phase:** the arena, the record, the lab page and the `Evolution`
interface must not assume "~30 integer weights". So the evaluator becomes an interface,
`Evaluator`, built from a flat **parameter vector + schema** (names, ranges, groups). The
hand-written evaluation (Level 1) is one implementation; a neural network (Level 2) is another
whose vector is its weights. The owner's evolution code works on vectors and never needs to
know which kind it is evolving.

### 8.5 Decisions

| # | Question | Options | Recommendation |
|---|---|---|---|
| E9 | More parameters | **Level 1 now: the §8.2 terms + piece-square tables, tapered, new terms starting at 0** / fewer, only named terms (~70) / none | **Level 1 now** |
| E10 | Generic evaluator | **`Evaluator` over a parameter vector + schema, so a neural net fits later without changing the arena or the owner's code** / hand-written evaluation only | **Generic** |
| E11 | Neural network | **Next phase (Phase 6), after evolution works on Level 1: 768 board inputs → small hidden layer → 1, trained and/or evolved** / in this phase / never | **Next phase** |
| E12 | Training data | **Export `(position, result)` from every recorded game, for Texel-style fitting** / not now | **Export now** (cheap; the fitter itself is the owner's or a later step) |

## 9. Increment log

1. **Step 1 — parameters (2026-10-02).** `ai.eval`: `ParamSpec`, `ParamSchema`, `ParamVector`
   (JSON in and out) and the `Evaluator` interface. `BitBoardEvaluate` is an instance built from a
   vector; the static game stage is gone, so searches with different weights run side by side
   (`EvaluatorTest`: two weight sets on four threads score exactly as alone). `Minimax` and
   `MinimaxEngine` take an `Evaluator`; without one they use `BitBoardEvaluate.DEFAULT`.
   `SameMoveTest` unchanged and green.
2. **Step 1b — 499 parameters (2026-10-02).** The evaluation is now features × weights, tapered
   between a middlegame and an endgame weight by the material left (phase 24 → 0). 56 named
   features (27 from the old code, 29 new: pawn structure 11, king safety 8, mobility 4, pieces 6),
   each with an mg and an eg weight; 3 gates (the old "before turn N" thresholds); piece-square
   tables for 6 piece types × 32 mirrored squares × 2 phases. Old weights keep their values in
   both phases (penalties are now negative bonuses, e.g. `development.bishopsHome = -15`); new ones
   start at 0, and a group whose weights are all 0 is not computed. `BitBoardEvaluate.features()`
   returns the full feature vector and phase for fitting (§8.3). Measured: the defaults play the
   same games at the same speed as before; with every feature switched on, a depth-4 game takes
   about 20-30% longer (20 ms per move instead of ~15). Tests: `EvaluatorFeaturesTest` (each new
   feature on a hand-made position; every feature flips sign when the colours are swapped, on 400
   random positions; the score equals the tapered sum of features × weights for random weights).
3. **Step 2 — quiescence search (2026-10-02).** When the depth runs out, `Minimax` no longer
   scores the position on the spot: it plays on through captures and queen promotions until the
   position is quiet, and the side to move may "stand pat" on the static score instead of taking.
   At the first extra ply a side in check tries every move (so mates are still seen); deeper,
   checks are scored as they stand, and the extra plies stop at 8. A capture of a defended piece by
   a more valuable one is skipped; that test uses fixed textbook values (1/3/3/5/9), not the
   evaluation's weights, so it prunes the same way for every candidate. The captures come from a
   new generator, `BitBoard.getNoisyNextStates()`, that reads the attack tables instead of building
   every move (`NoisyMovesTest` checks it against the full move list in the perft trees); the king
   moves were rewritten from the same tables, since they were a sixth of the time. The endgame
   stage bug from §2 was already gone with tapering in step 1b. Effect: at depth 1 the queen no
   longer grabs a pawn a pawn defends (`QuiescenceTest`; the old engine did). Cost: about 1.5-2×
   the old search time (56 positions at depth 5: 7.7 s, was 5.3 s). Level 6 still finishes depth 5
   within 1 s in every benchmark; Level 7 finishes depth 6 within its 5 s cap in five of six, but
   the middlegame benchmark takes about 8 s, so there the cap plays the depth-5 move
   (`SearchSpeedTest` now allows Level 7 twice the cap; a transposition table and better move
   ordering are the way back). `same-moves.txt` re-recorded, as this change is meant to alter play.
4. **Variety (2026-10-02, the owner's request "the game shouldn't be deterministic").** The search
   used to play the same game every time. Now `Minimax.getBestMove` takes a `variety` margin and a
   `Random`: at the root the window is kept open by the margin, so every move within it of the best
   gets an exact score, and one of them is picked at random. A forced mate is never traded away.
   The game engine uses 0.2 pawn (`MinimaxEngine.DEFAULT_VARIETY = 2`): in 20 self-play games from
   the start, 19-20 of the first eight moves differ, and it still never leaves a piece hanging
   (`VarietyTest`). A seeded `Random` repeats a game exactly, which the arena will use: varied but
   reproducible. `searchAtDepth` (and so `SameMoveTest`) stays deterministic.
5. **Step 3 — arena (2026-10-02).** Package `arena`: `Opening` (a suite of 51 balanced lines in
   `arena/openings.txt`, each legal and ending with White to move, `OpeningsTest`), `Player`
   (evaluator, fixed depth, variety, quiescence on/off), `Match` (plays one game; ply cap 300 is
   a draw with reason `PLY_CAP`; the same players, opening and seed give the same game),
   `Tournament` (every pairing × every opening × both colours on a thread pool; results come back
   in a fixed order and do not depend on the number of threads), `Score` (W/D/L, Elo and a 95%
   interval from the per-game spread) and `Cli` (`arena.Cli match A B --depth 3 …`). The search
   gained `Minimax.Options(variety, random, quiescence)`. Gauntlet of step 2, 102 games each,
   default weights with quiescence against the same weights without it: depth 3 +90 =10 -2
   (93%, Elo +453 [+368, +603]) in 15 s; depth 4 +83 =13 -6 (88%, Elo +342 [+268, +452]) in 99 s,
   on 3 threads. Tests: a seed repeats a game, another seed differs, the ply cap, a mate is scored
   for the right side, colours swap, thread count does not change results, depth 3 beats depth 1.
6. **Step 4 — record and runner (2026-10-02).** Package `evolution` is the owner's: the
   `Evolution` interface (`firstGeneration`, `pairings` with a round-robin default,
   `nextGeneration`), `Generation` (the population with its games: points, score, head-to-head,
   ranking, champion), `Pairing`, and `RandomMutationExample` (keep the better half, refill with
   mutated copies; for the tests and as an example only). Package `lab`: `RunStore` (one SQLite
   file per run through `sqlite-jdbc`: tables `run`, `member`, `game`, `generation`),
   `EvolutionRunner` (plays each generation's pairings over rotating openings with both colours,
   stores every game, picks the champion, plays it against the default weights every N
   generations, stores the next population and the generation row in one transaction),
   `TrainingExport` (E12: `fen,result` per quiet position after ply 10) and `Cli`
   (`run`, `resume`, `show`, `export`; Ctrl+C stops after the current generation). Every random
   choice comes from the run's seed and the generation number, so a stopped and resumed run
   records exactly what an uninterrupted one does (`EvolutionRunnerTest`, which also cuts a
   generation off half way). A trial run, 3 generations of 8 at depth 2 with one opening per
   pairing, took 13 s on 3 threads. It also showed how noisy short matches are: generation 0's
   champion was the default weights themselves, and over 8 games against the defaults it scored
   31% (Elo -137, interval -446 to +37). The guide (step 6) has to say this plainly.
