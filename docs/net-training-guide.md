# Training the small network (Phase 7, step 1)

The owner decided on 2026-10-06 (docs/phase-7-research.md §5): chess first, positions from engine
self-play, labels mixing the game's result and the engine's score, a PyTorch trainer, a
768 → 32 → 1 network, gradient training first and evolution over the recipes later, and the
**owner writes the trainer** with Claude's guidance. This page is that guidance: what the
repository already does for you, the exact formats at each hand-over point, and what the trainer
has to do. Everything in Java and `tools/net` is tested; the trainer is the part you write.

## 1. The pipeline

```
Stockfish self-play ──> positions.csv ──> positions.bin ──> train.py (yours) ──> nets/first.json ──> the engine
     lab.Cli selfplay     fen,result,score   lab.Cli features    PyTorch, CPU        net-v1 file        net:nets/first.json
```

Every arrow is a file, so each step can be checked on its own and re-run alone.

### 1.1 Positions with results and scores

```bash
java -cp target/izikstar-chess-3.1.0.jar lab.Cli selfplay positions.csv --games 10000 --nodes 5000 --threads 4 --seed 1
```

Stockfish plays itself at 5,000 nodes a move from the arena's openings plus 2-6 random moves
(`lab.Texel.selfPlay`). Every quiet position is one line of `fen,result,score`
(`lab.TrainingExport`):

| column | meaning |
|---|---|
| `fen` | the position before a move, as the variant writes it |
| `result` | how the game ended, from White's side: `1`, `0.5`, `0` |
| `score` | Stockfish's search score for the position in centipawns from White's side, as it reported it while choosing its move (a mate in N as `±(10000 − N)`); empty when the player gave none (the built-in engine does not report scores yet) |

*Quiet* means: after the first 12 plies, the side to move is not in check, and the move it played
was neither a capture nor a promotion. A search resolves captures first, so a static evaluation
is not expected to match the result in the middle of one. Measured on 4 cores: about 85 quiet
positions a game, 1,000 games in 6-7 minutes, so 1M positions is about 80 minutes.

Any other game works the same way with Fairy-Stockfish:
`lab.Cli selfplay anti.csv --variant antichess --player fsf:5000 --games 10000`. A run's games
(`lab.Cli export runs/anti.db anti.csv`) give the same columns, without scores.

### 1.2 The training file

```bash
java -cp target/izikstar-chess-3.1.0.jar lab.Cli features positions.csv positions.bin            # chess
java -cp target/izikstar-chess-3.1.0.jar lab.Cli features anti.csv anti.bin --variant antichess
```

`lab.NetData` encodes every line as the network's inputs, **everything from the side to move**:

```
header   "NETDATA1" (8 bytes), int32 inputs, int32 slots, int32 rows, int32 0            24 bytes
row      int16[slots]  the inputs that are on, ascending, then -1 up to the slot count
         float32       result for the side to move: 1, 0.5 or 0
         float32       score for the side to move in centipawns, NaN when the line had none
         uint8         side to move (0 White, 1 Black), then 3 bytes of 0
```

Little-endian; `slots` is the piece count of the variant's start position (chess: 32), so every
row is the same length (chess: 76 bytes, 1M positions = 76 MB) and Python maps the file as one
array. `tools/net/data.py` reads it:

```python
import sys; sys.path.insert(0, "tools/net")
import data
d = data.load("positions.bin")
d["active"]   # int16 [rows, 32]: the inputs that are on, -1 = no piece
d["result"]   # float32 [rows]
d["score"]    # float32 [rows], NaN = none
d["inputs"]   # 768
```

`python3 tools/net/data.py positions.bin` prints a summary (how many positions, wins and draws,
score range). Flipping to the side to move halves what the network must learn: a position and
its colour-swapped mirror are the same row.

### 1.3 The inputs (`ai.eval.NetFeatures`, `tools/net/encode.py`)

One input per (side, piece type, square), on when such a piece stands there:

```
index = (side * T + type) * S + square          T = 6 piece types (K Q R B N P), S = 64 squares
side    0 = the player to move, 1 = the opponent
square  a8 = 0 ... h1 = 63 as the player to move sees it: White as it is, Black mirrored top to
        bottom (Black's e8 is input square 60, "e1")
```

Chess: 768 inputs, about 30 on. `python3 tools/net/encode.py "FEN"` prints a position's inputs;
`lab.Cli net NET.json "FEN"` prints the same list from Java. The two agree (checked on the
`NetFeaturesTest` positions and the 425 positions of a smoke run); any new encoding must keep
them agreeing, or the trained network scores other positions than it was trained on.

## 2. The model

```
hidden[h] = relu( b1[h] + Σ_{i on} w1[h][i] )        h = 0..31      relu(x) = max(x, 0)
score     = b2 + Σ_h w2[h] · hidden[h]                in centipawns, for the side to move
```

In PyTorch this is `nn.Sequential(nn.Linear(768, 32), nn.ReLU(), nn.Linear(32, 1))` applied to a
0/1 vector of the inputs; `data.dense(d["active"][i:j], 768)` makes that vector for a batch. (An
`nn.EmbeddingBag(768, 32, mode="sum")` over the index rows is the same first layer without
building the 0/1 matrix, and much faster; try the dense version first, it is the one you can see.)

Weights: 768 × 32 + 32 + 32 + 1 = **24,641 numbers**. Java evaluates a position by adding one
column of `w1` per piece on the board (about 30 × 32 additions) plus the output layer: the same
cost as the hand-written evaluation, so search depth stays. Incremental updates (NNUE) are not
needed at this size and are not implemented.

### 2.1 The loss

The score is read as centipawns on the Texel curve the project already uses:

```
expected(score) = 1 / (1 + 10^(-score / 400))          0 cp -> 0.5, +400 cp -> 0.91, -400 cp -> 0.09
```

The label is a mix of the game's result and the engine's score (N3, λ = 0.5 to start):

```
target = λ · expected(score_label) + (1 − λ) · result          score_label clamped to ±2000 cp first
loss   = mean over the batch of (expected(net(position)) − target)²
```

Clamp the engine score before mixing: mate scores are ±9,990 and would otherwise dominate the
mean. Rows whose score is NaN (no engine score) use `result` alone. `tools/net/forward.py
NET.json positions.bin` computes this error against the result for any net-v1 file, so you have a
number to compare your trainer's output with; a network that always says 0 scores about 0.19 on
self-play data, the Texel-tuned weights do noticeably better, and your first network should beat
both.

### 2.2 What the trainer does (the part you write)

1. Load `positions.bin` with `data.load`; shuffle once with a fixed seed; keep the last 10% as the
   validation set and never train on them.
2. Build the model; `torch.manual_seed(seed)` so a run can be repeated.
3. For each epoch (10-50): for each batch of 4,096-16,384 rows: build the input, compute the
   loss above, `loss.backward()`, `optimizer.step()`. Adam with learning rate 1e-3 is the standard
   start.
4. After each epoch, compute the loss on the validation set. Keep the weights of the best epoch;
   stop when it has not improved for a few epochs (early stopping).
5. Write the best weights as a net-v1 file (§3).

Things worth printing: the validation loss per epoch, how many hidden units are 0 for every
validation position (dead units), and the score the network gives the start position.

## 3. The network file (`net-v1`)

```json
{ "format": "net-v1", "variant": "chess", "inputs": 768, "hidden": 32, "activation": "relu",
  "w1": [[768 numbers], ... 32 rows], "b1": [32 numbers], "w2": [32 numbers], "b2": 0.0 }
```

`w1` is `Linear(768, 32).weight` as PyTorch holds it (`[32][768]`, `.tolist()` writes it);
`b1` its bias; `w2` is `Linear(32, 1).weight[0]`; `b2` the output bias. Plain JSON, about 400 KB
for 32 units; keep them in `nets/` (not committed until one is worth keeping).

`ai.eval.NetEvaluate` reads the file and refuses another format, variant, input count or
activation. It is an `Evaluator` like the hand-written ones, so the usual search plays with it.

## 4. Checking and measuring

```bash
# the same position in Java and in numpy, scores must agree to a few hundredths
java -cp target/izikstar-chess-3.1.0.jar lab.Cli net nets/first.json "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"
python3 tools/net/forward.py nets/first.json "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

# the network in the arena: against the tuned chess weights (default) and against Stockfish
java -cp target/izikstar-chess-3.1.0.jar arena.Cli match net:nets/first.json default --depth 4 --openings 50
java -cp target/izikstar-chess-3.1.0.jar arena.Cli match net:nets/first.json sf:1500 --depth 4 --openings 50
```

`net:FILE.json` is a player spec wherever one is accepted (`arena.Cli`, `lab.Cli selfplay
--player`, the yardsticks of a run). Elo with its 95% interval comes out as for any match; 100
games is about ±100 Elo, so compare networks over several hundred.

## 5. Not built yet, on purpose

- The Lab's forms do not offer `net:` yet, and the web game cannot play a network (the engine
  falls back to the variant's evaluation when the weights are not of its schema). Both come once a
  network is worth playing against.
- The built-in engine reports no score during self-play, so our own games give `result` only;
  labelling them with Stockfish's score afterwards (positions from our games, labels from a strong
  engine) is the planned next step of the data loop.
- Evolution over the recipes (λ, learning rate, width, data) is yours, after the first network
  works (N6).
