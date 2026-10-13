# Phase 8 — Portal chess: squares that change the geometry (research; awaiting decisions)

The owner asked on 2026-10-13 for a variant whose rules the current variant model cannot say, so
that building it forces the model to grow. Of four candidates (portals, cannons and archers,
prisoners with drops, duck chess) he picked **portals**, and added that all four will be needed
eventually, and that in the end building a variant should be done with **functions, not dry
data**. This document is the design for portals and how it starts the road to functions. §6 lists
the decisions, each with a recommendation; no code is written before the owner answers.

It is the first of the extension layers in [phase-6-research.md §5](phase-6-research.md): layer 4
(geometry), in its smallest form that stays inside 64 squares.

## 1. The game

Portal chess is chess with two pairs of linked squares. A piece that **slides into** a portal square
goes on from its twin, in the same direction:

```
  8 . . . . . . . .        a rook on a3 sliding toward the h-file:
  7 . . . . . . . .          b3, c3 (portal A), f6 (its twin), g6, h6
  6 . . B . A . . .
  5 . . . . . . . .        a bishop on a1 sliding up the long diagonal:
  4 . . . . . . . .          b2, c3 (portal A), f6, g7, h8
  3 R . A . . B . .
  2 . . . . . . . .        A = c3 <-> f6     B = f3 <-> c6
  1 . . . . . . . .
    a b c d e f g h
```

Checks and pins go through portals as well, so a king on h6 is in check from the rook on a3. The
pairs are placed so the board looks the same to both players (each pair is its own image when the
board turns half a circle) and so each pair joins two squares of one colour, so bishops stay on
their colour. Otherwise it is chess: same pieces, same start, checkmate, castling.

Nobody has an engine for this: Fairy-Stockfish has no portals. What a bishop or a rook is worth
here, which squares near a portal matter, whether the centre still counts: the Lab will be the
first to find out.

## 2. The rule, exactly

**P-rule.** Walking a slide step by step: when the step lands on a portal square that is not the
piece's own square, that square is on the path (the piece may stop there, capture there, or be
blocked there), and the **next** square of the path is its twin. From the twin the slide goes on
in the same direction. Arriving at a twin this way does not jump again.

Why this rule and not a simpler one: the move generator and the check test rely on a slide being
**blocked the same both ways**. `GenericBoard.attacked` finds attackers of a square by sliding
*from* that square with the other player's pieces (the "mirror" test), and `GenericBoard.exposure`
finds pins along the lines a royal piece could be attacked on. Under the P-rule a path read
backwards is the backwards path: a3 → b3, c3, f6, g6, h6 and h6 → g6, f6, c3, b3, a3 visit the
same squares. Two simpler rules break this:

- "Stepping out of a portal square continues from the twin" (the piece on c3 goes to g6): the path
  from b3 visits c3 and g6, the path back from g6 visits f6 and b3. A piece on f6 would block one
  direction and not the other.
- "Portal squares are holes you pass through": nobody may stand on them, so the twin pairs are
  dead squares; legal but dull, and still has to decide where a slide stops.

Corners of the rule (each is a decision in §6 with a default):

- **A piece standing on a portal** slides normally from it (the rook on c3 goes d3, e3, ... and is
  not sent to f6). Only entering a portal jumps.
- **Leaps do not use portals.** A knight landing on c3 is on c3. A king's step is a leap, so the
  king never jumps. (Leaps through portals have no path to follow, so the rule has no meaning
  for them.)
- **Limited slides count the jump as a step.** f6 is the step after c3. A pawn's double step from
  c2 is a slide of range 2, so it may end on f6. Funny, and consistent: the pawn on c2 sees c3 and
  "the square after it". The alternative (the jump is free, so the pawn lands on f7) is §6 D3.
- **A path that comes back** (portals placed so a line returns to itself) ends before it would
  visit a square twice, or its own start square.
- **Castling** passes through squares on the first rank; portals there would make "the squares
  between king and rook" ambiguous, so the designer refuses a portal on a square castling crosses.
- **En passant** follows the pawn's path: the square passed over is the one before the landing
  square on the path (c3 for c2→f6).

## 3. The engine

### 3.1 Where portals live

`Grid` is the board's geometry, so the pairs go there: `Grid(width, height, portals)`, with
`Grid.CHESS` keeping no portals. The variant JSON gets `"portals": [["c3","f6"],["f3","c6"]]`,
left out when empty, so every existing file reads as before.

That has a free consequence worth keeping: `Evaluators.usesChessEvaluation` asks
`variant.grid().equals(Grid.CHESS)`, so a variant with portals is evaluated by the evaluation
built from its pieces (`PieceSetEvaluate`), not by the tuned chess evaluation whose square tables
and king safety assume an ordinary board. Evolution then learns its values (§5).

### 3.2 Slides as chains of segments

`CompiledPiece` stores each slide from each square as one bitboard ray and finds the first piece in
the way with one bit trick: on a rising ray the blocker is the lowest set bit, on a falling ray the
highest. That needs the squares of a ray to be in index order, and a path through a portal is not:
b3, c3 are 41, 42 and f6, g6, h6 are 21, 22, 23.

But it is in order **piece by piece**: before the portal and after it. So a ray that crosses a
portal becomes a short chain of segments, each one an ordinary ray with its own rising or falling
flag. The generator runs the trick on the first segment; only if the segment is empty all the way
to its end (the portal square included) does it go on to the next. Rays that cross no portal stay
exactly as they are, so chess and every variant without portals run the same code at the same
speed. `reach` and `slideReach` take the union of a chain's segments, so the attack test and the
exposure test work unchanged.

Rough cost: one extra array per square for the chains (empty without portals) and one branch per
ray in `slides`. Zobrist keys, the move encoding (from, to), the transposition table, the search
and the evaluation do not change.

### 3.3 Proving it right

There is no Fairy-Stockfish to compare perft counts with, so the tests bring their own oracle:

- **A slow reference generator** that walks every slide one square at a time by the P-rule, with
  none of the bit tricks. Perft to depth 4 from the start position and from a few hundred random
  game positions must equal `GenericBoard`'s, on the default layout and on random layouts.
- **Symmetry property:** for random positions and layouts, a piece on X attacks Y exactly when the
  path from Y with the opposite direction reaches X. This is what the mirror test assumes.
- **Nothing else moves:** `SameMoveTest`, `PresetPinTest` and the perft suites stay green, and the
  speed bench on chess shows no change.
- `FairyConfig.refusal` gains "portals": the variant is "our engine only", as the designer already
  shows for other mixes.

## 4. The browser

- **Board:** portal squares get a coloured ring and a small letter (A, B), one colour per pair, on
  every board (game, designer, Lab replays, analysis).
- **Designer, Board tab:** a "portal pair" tool: click two squares to link them, click a linked
  square to remove its pair. Refused with a reason on a square castling crosses or one already in a
  pair.
- **Where a piece can go** (`web/src/reach.ts`, the hover and designer preview) follows the
  P-rule, so hovering the rook on a3 shows b3, c3, f6, g6, h6.
- **Moves:** the move animates in a straight line from a3 to h6 for now. Animating through the
  portal (into c3, out of f6) is a later nicety.
- **Notation:** unchanged. "Ra3-h6" is a legal move and SAN still names it uniquely.

## 5. First experiment

A built-in preset "Portal chess" (chess plus the two pairs above), a health check (white score,
draws, game length against plain chess), then a Lab run with the recipe that won the KQ Royal
runs: `FromZero`, start from the piece values, material and mobility, population 16, 4 survivors,
2 immigrants, 60 generations, depth 3 with 10% → 40% of games at depth 4, measured against the
generation-0 champion. The questions: does the bishop gain on the rook (a diagonal through a portal
reaches two corners of the board)? Does mobility weigh more than in chess? Then square values, so
we see whether the squares in front of a portal are worth more.

## 6. Decisions for the owner

| # | Question | Recommendation | Alternative |
|---|---|---|---|
| D1 | The path rule | The P-rule (§2): entering a portal puts you on it, the twin is the next square | Holes you pass through and cannot stand on |
| D2 | Which moves use portals | Slides only; leaps (knight, king, pawn's single step) land normally | Leaps too, if they land on a portal they come out on the twin |
| D3 | Does the jump count as a step for a limited slide | Yes: the pawn's double step from c2 may end on f6 | No: the jump is free, the pawn ends on f7 |
| D4 | Default layout | Two pairs, c3↔f6 and f3↔c6, same colour, symmetric for both players | One pair; or pairs of opposite colours (bishops change colour) |
| D5 | Evaluation | The one built from the pieces, learned by the Lab | The chess evaluation as it is |
| D6 | Order of work | 3 PRs: engine + tests; browser; preset + health check + first Lab run | Engine and preset first, browser after |
| D7 | Functions (§7) | Build portals on a "next square" function, so the next layer is a function written by hand | Portals as plain data now, functions later |

## 7. Towards functions instead of data

The owner's goal: in the end, a variant is built from functions, not only from tables of numbers.
Portals are a good place to start, because they show there are **two kinds** of function and they
cost very differently:

- **Functions of the geometry**: "from this square, going this way, what is the next square?" They
  do not depend on where the pieces stand, so they can be run once when the variant loads and
  turned into the same tables the engine already uses. They cost nothing during the search.
  Portals are one; a board whose edges wrap around (a cylinder), holes in the board, and other
  board shapes are others.
- **Functions of the position**: "what may this piece do here", "what happens after this capture",
  "who has won". They have to run on every move during the search, so they are slower (how much
  depends on the language they are written in), need a time limit and a sandbox, and no outside
  engine can check them.

The proposal (D7) is to build portals on the first kind from the start: inside `Grid`, slides are
walked with a `next(square, direction, state)` function, and the portal pairs are just one
implementation of it. The compiler of §3.2 does not care which function it walks, as long as the
function passes the symmetry test of §3.3. The next geometry (a cylinder, holes) is then a new
function, not a new engine, and when a scripting layer comes, the first thing a person writes is
such a function. The second kind is a separate design for later (layer 6), with its own
decisions: the script language, the sandbox, and how much speed we give up.
