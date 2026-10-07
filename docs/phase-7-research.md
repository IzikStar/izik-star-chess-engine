# Phase 7 — A small neural network that reads the board (research; decided 2026-10-06)

Roadmap stage 3 (docs/phase-6-research.md, owner's roadmap of 2026-10-05): "a small NNUE-style
value net, Python training on CPU, Java inference, on one variant's self-play". Written on the
night of 2026-10-05/06 while the owner slept, as choices with a recommendation each (§5) and only
the groundwork every option needs (§4). **The owner decided on 2026-10-06** (§5): every
recommendation, except that he writes the trainer himself. Step 1 is merged (PR #62); §6 tracks
what is left.

## 1. What a "small net" means here

The evaluation today is a sum: for every piece, a value for its type, for its mobility and for its
square (`ai.eval.PieceSetEvaluate`, for any variant; chess has its own `ChessEvaluate`). Seen as
a network, that is **zero hidden layers**: one input per (side, piece type, square) that is on or
off, multiplied by a weight, summed. Everything the evolution has done so far moved those weights
(only material and mobility in "Antichess from zero 1"; the square table stayed at 0).

A small net puts one hidden layer between the same inputs and the score:

```
inputs  2 sides x T piece types x 64 squares, on/off   (chess and antichess: 768; up to 2048 for 16 types)
hidden  H units, clipped ReLU (min(max(x, 0), 1))       H = 32 ... 256
output  1 score (centipawns, from the side to move)
```

What the hidden layer adds is **combinations**: "a bishop here *and* my king there" is worth
something neither is alone. That is what the linear table cannot say, and why NNUE nets beat
hand-written evaluations in chess.

Size: 768 x 32 + 32 = about 25,000 weights; 768 x 256 about 200,000. Evaluating without
incremental updates costs one column add per piece on the board (about 30) times H, plus H for the
output: for H = 32 about 1,000 additions, the same order as today's evaluation, so search depth
stays. Wider nets need the NNUE trick (keep the hidden sums and update them by the 2-4 inputs a
move changes); the board's copy-make design (R1) makes that a later step, not a first one.

## 2. Where the weights come from

| Way | How | Fits this project because | Costs |
|---|---|---|---|
| Gradient training on positions | Many `(position, label)` rows; minimise the error of `sigmoid(net(position))` against the label (Texel's method with a hidden layer) | Scales to 200k weights; how every NNUE net is made; the data now exists for any variant (§4) | Needs a trainer (Python + PyTorch, or numpy, or Java) |
| Evolution of the weights | The Lab as it is, with the net's weights as the parameter vector | Uses the owner's evolution code unchanged (`ParamSchema`/`ParamVector`) | 25,000 weights learn very slowly from game results alone; Blondie25 needed 8,000 generations for a much smaller net |
| Both | Gradient training sets the weights; evolution chooses what is trained (H, learning rate, which data, the mix of labels) and keeps a population of nets that play each other | Each does what it is good at; the owner's evolution stays the "outer loop" | Two moving parts |

Labels: the **game's result** (what the side to move eventually scored: 1, 0.5, 0) is free and
honest but noisy; a **search score** from a strong engine (Fairy-Stockfish's `info score` at the
position) is sharp but teaches the net to imitate that engine; NNUE trainers mix the two
(`lambda` x score + (1 - lambda) x result).

## 3. What the first experiment told us (antichess, 2026-10-06)

See [experiments/antichess-zero-1-yardsticks.md](experiments/antichess-zero-1-yardsticks.md):
evolution from zero found that every piece is a burden (all values negative); its champions beat
a random mover 100% and Fairy-Stockfish at 100 nodes a move 76-92%, score 15% (generation 0) to
40% (generation 30) against it at 1,000 nodes, and almost nothing at 10,000. The gain (+131 Elo,
generation 79 over 0) came in the first 30-40 generations. Antichess is decided by long forced-capture sequences, so a
better evaluation helps less than a deeper search there; that is a point *against* choosing
antichess for the first net (N1), and for measuring any net at equal nodes, not equal depth.

## 4. Groundwork built tonight (needed by every option)

- **Yardsticks for any game** (PR #45, #46): Fairy-Stockfish (`fsf`, `fsf:NODES`) for the
  built-in variants and most made ones (`arena.FairyConfig`), the random mover (`random`), in the
  Lab, `lab.Cli` and `arena.Cli --variant`. A net is judged by these.
- **Training data for any game** (this branch): `TrainingExport` replays a run's games by its own
  variant's rules (before, `lab.Cli export` on an antichess run replayed chess and failed), and
  `lab.SelfPlayData` / `lab.Cli selfplay OUT.csv --variant antichess --player fsf:5000` writes
  `fen,result` rows from any player's self-play in any game.

Not built, because each depends on a decision below: the input encoding class, the trainer, the
net file format, the Java `NetEvaluate`.

## 5. Decisions for the owner

**Decided 2026-10-06:** the owner took every recommendation below, with one change to N7 made for
his goal of learning from the process: **the owner writes the trainer** (with guidance), Claude
writes the data, the encoding and the Java inference. Done so far (PR "Phase 7 step 1"): the
engine's score per position in `fen,result,score`, `NetFeatures`, `NetData` and `lab.Cli
features`, `NetEvaluate` and the `net:FILE.json` player, `tools/net/` (reader, encoder, forward
pass), and [net-training-guide.md](net-training-guide.md) with the formats and the recipe.

| # | Question | Options | Recommendation |
|---|---|---|---|
| N1 | Which game first | **Chess**: Stockfish data, and tuned-v1 is a strong, known baseline to beat / **Antichess**: continues tonight's experiment, but search dominates there (§3) / a made variant: no baseline at all | **Chess** first, antichess second (same code, other data) |
| N2 | Where positions come from | **Engine self-play** (Stockfish or Fairy-Stockfish at a few thousand nodes, `lab.Cli selfplay`; measured on 4 cores at 5,000 nodes: chess 17,106 quiet positions from 200 games in 79 s, antichess 7,862 positions from 200 games in 58 s, so 1M positions is about 1.3 h of chess or 2 h of antichess) / our own engine's self-play (weaker labels, but "learns from itself") / the Lab's run games | **Engine self-play**, then add our own games later |
| N3 | Labels | result only / engine score only / **mix (lambda = 0.5)** | **Mix**; it needs one small addition: record the engine's score per position during self-play |
| N4 | Trainer | **Python + PyTorch on CPU** (standard, a few hundred lines, fast enough for 25k-200k weights) / numpy only (no install, slower, all by hand) / Java (one language, more code) | **PyTorch** (`pip install torch`, CPU build); weights exported as JSON or a small binary that Java reads |
| N5 | First size | **768 -> 32 -> 1**, no incremental updates (search speed about as today) / 768 -> 256 -> 1 with incremental updates (stronger, more work) | **768 -> 32 -> 1**; widen when it works |
| N6 | Gradient vs evolution | gradient only / evolution only / **gradient trains, evolution picks the hyper-parameters and keeps a population of nets** | **Both**, as in phase-5 §8.4 |
| N7 | Who writes what | Claude writes data, encoding, Java inference and the trainer; the owner writes the evolution over nets (as with the Lab) / the owner writes the trainer too / Claude writes all of it | **Claude: data, inference, trainer; owner: the evolution** (keeps "the cool part" his) |

## 6. After the decisions

1. ~~Self-play data with scores: 1M positions of chess, Stockfish 5k nodes, `fen,result,score`.~~ Done (2026-10-06).
2. The trainer: PyTorch, 768 -> 32 -> 1, loss on mixed labels, writes `nets/NAME.json`. **The owner's**, per
   [net-training-guide.md](net-training-guide.md).
3. ~~`ai.eval.NetEvaluate implements Evaluator`, reading that file; `Players` spec `net:NAME`; Java and Python
   agree on the inputs and the score.~~ Done.
4. Arena: net vs tuned-v1 at equal depth and at equal nodes, and vs Stockfish levels; a report in
   `docs/experiments/`.
5. The Lab offers "net" as a player and as a yardstick, the web game plays it; the data loop over
   the engine's own games (positions from our games, labels from Stockfish); the owner's evolution
   over the recipes follows.
