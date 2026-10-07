# The blind fun test

Does the health check tell which variants people enjoy? If it does, the Lab can sift many invented
variants and only the promising ones need people to try them. If it does not, the Lab is a research toy
and not a design tool. This test answers that once, cheaply. It was chosen on 2026-10-07 after the second
product check (the riskiest assumption behind the "a game found by the Lab" idea).

## The variants

`lab.FunTest` made 40 random variants and gave each a health check (80 games, both sides at depth 2,
4 random opening moves, at most 300 plies, seed 20261007):

```
java -cp target/izikstar-chess-3.1.0.jar lab.FunTest playtest/fun-test-1 --candidates 40 --games 80 --depth 2
```

A random variant is the 8x8 board, a king (royal, except in give-away), a full row of pawns on the second
or third row, two to four piece types for the back row drawn from Knight, Bishop, Rook, Queen, Mann (WF),
Champion (WAD), Wizard (FC), Archbishop (BN), Chancellor (RN), Camel (C) and Elephant (FA), set out
symmetrically around the king, and one of five ways to win: checkmate, king of the hill, three checks,
capture every enemy pawn, or give everything away. No castling. Pawns promote to the back-row types.

Each gets a score from 0 to 100 from its health report, fixed before anyone plays (`FunTest.score`):

| Part | Points | Full marks when |
|---|---|---|
| Balance | 30 | White scores 50%; nothing at 25% or 75% |
| Decisive | 25 | every game has a winner |
| Length | 20 | 30 to 100 plies on average; nothing below 10 or above 200 |
| Choice | 15 | 20 or more legal moves a turn on average |
| Ends | 10 | no game reaches the 300-ply cap |

The "top" group is the best four with four different ways to win: the score favours the rules with short
games (three checks, give-away), and without this the top group would be mostly one kind of rule, so the
test would compare rules rather than scores. Four more are drawn at random from the other 36: the
"random" group.
The eight are written to `playtest/fun-test-1/variants/` as **Fun test A** to **Fun test H**, in a
shuffled order. Which is which (`KEY-do-not-open.md`) and every candidate's score (`ranking.csv`) are
kept out of this public repository, in the project's private files (`chess-fun-test/`), so a player
cannot look. Neither is to be opened before the last rating is in.

## Playing

1. Copy the eight files from `playtest/fun-test-1/variants/` into the app's variants folder (`variants/`
   next to where the app runs, unless `--variants` says otherwise).
2. Each player plays **two games of every variant**, against the engine or a friend, in any order. Nobody
   is told which variant came from where.
3. After every game of a Fun test variant the game screen asks for the player's name and **"Would you play
   this again tomorrow?"** from 1 (no) to 5 (gladly). The answers go to `fun-test-ratings.jsonl` next to
   the games folder.
4. After the two rounds, let people play whatever they like. A third game of a variant that nobody asked
   for is the strongest signal there is; the app counts it from the ratings.

`GET /api/fun-test/summary` shows, per variant, the games, players, mean rating and the players who played
it three times or more.

## Reading the result

Open the key only now.

- **Pass**: the top group's mean rating is at least 1 point above the random group's, **and** at least one
  variant was played a third time by half of its players or more. The Lab predicts fun; the product idea
  "a game found by the Lab" stays open, and its next assumption is tested.
- **Fail**: no real difference between the groups. The health check does not predict fun. The product
  question is closed; the project goes on for research and fun.
- **In between**: a difference but no variant people came back to. One more round with eight new
  variants (another seed) before deciding.

With six to eight players the numbers are small. A 1-point gap on a 1-5 scale is large on purpose, so that
a pass is not luck.
