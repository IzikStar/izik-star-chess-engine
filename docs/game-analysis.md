# Game analysis

The **Analyse** button (and **Analyse game** under a finished game) sends the game to the local
server, which asks Stockfish about every position and sends back a report. The browser shows an
evaluation bar beside the board, an evaluation graph, a mark on every move and, for each side, an
accuracy, the average centipawn loss and a rough Elo estimate.

- Server: [`analysis.GameAnalyzer`](../src/main/java/analysis/GameAnalyzer.java) (the numbers),
  [`analysis.UciEvaluator`](../src/main/java/analysis/UciEvaluator.java) (Stockfish as a judge, its
  own process at full strength), [`web.AnalysisApi`](../src/main/java/web/AnalysisApi.java)
  (`POST /api/analysis`, then poll `GET /api/analysis/{id}`).
- Browser: [`web/src/Analysis.tsx`](../web/src/Analysis.tsx).
- The rules stay in Java: the moves are checked with `rules.Rules` before Stockfish sees them.
- Stockfish searches each position to depth 12 (6-22 can be asked for). A 40-move game takes
  about ten seconds. Without Stockfish the server answers 503 and the page says so.

## The live evaluation bar

Without a full analysis the bar still follows the game: after every move (and for every position
you step back to) the browser asks [`web.EvalApi`](../src/main/java/web/EvalApi.java)
(`POST /api/eval {startFen?, moves}` → `{score}`) for the position's score. It is the same judge as
the analysis (Stockfish at full strength, depth 12, its own process, kept between requests), so the
bar does not jump when a full analysis arrives; positions an analysis already covered are not asked
again. A finished position (mate, stalemate) is scored without the engine. Without Stockfish the
endpoint answers 503 and the bar stays hidden.

Whether the bar shows is a setting per kind of game (**Settings**, kept in the browser): on against
the computer and when watching the engine, off between two people. With a full analysis it always
shows.

## The numbers

Every score is from White's side: centipawns, or moves to mate. A finished game scores
±10000 (shown as 1-0 / 0-1).

| What | Formula | Source |
|---|---|---|
| Winning chances (the bar, the graph) | `50 + 50 · (2 / (1 + e^(−0.00368208 · cp)) − 1)` | Lichess |
| A move's centipawn loss | mover's score before − mover's score after, at least 0; scores capped at ±1000, a mate counts as ±1000 | Lichess |
| A move's accuracy | `103.1668 · e^(−0.04354 · Δ) − 3.1669`, 0-100, where Δ is the winning chances the move gave away | Lichess |
| Side accuracy | the average of its moves' accuracies | simplified from Lichess (which also weights by volatility) |
| Mark | the engine's own move: **best ★**; otherwise by Δ: under 2 excellent, under 10 good, under 20 inaccuracy ?!, under 30 mistake ?, else blunder ?? | Lichess thresholds, chess.com names |
| Elo estimate | `3100 · e^(−0.01 · ACPL)`, between 400 and 3000 (20 → 2540, 50 → 1880, 100 → 1140) | a rough fit of published average-centipawn-loss figures by rating |

The Elo estimate is the weakest of these: a short game, a quiet game where nobody can go wrong,
or a game already lost all make it swing by hundreds of points. Treat it as a hint about the
quality of one game, not a rating.
