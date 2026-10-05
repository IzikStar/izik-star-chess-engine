# Phase 6 — Pieces as data: a board for invented pieces and variants

Branch `phase-6-board-interface` (step R1). The owner approved the direction on 2026-10-05: the
platform should let him invent chess variants, starting with rule switches and **new pieces now**.
For now a piece is only movement and capture, a fixed function of the board; long term the owner
wants "everything possible": more dimensions, more than two players, other board shapes, squares
with their own behaviour, and an experiment platform over all of it with a very good UI.

The project's stated goal is challenge, research and fun, not a product. That decides close calls
below: prefer what keeps experiments trustworthy and the code open to wild ideas.

## 1. Why the current board cannot take a new piece

Read on `main` at the merge of #23:

| Concern | Today | Where |
|---|---|---|
| Piece identity | 12 named `long` fields (`whiteKnights`, `blackPawns`, ...), piece numbers 1-6 hard-coded | `ai.BitBoard.BitBoard` |
| Movement | one class per piece, each direction tested by hand | `ai.BitBoard.BitPiece.*` |
| A move | a whole new `BitBoard` per move, described as "new bitboard of all pieces of this type" | `BitBoard.getNewBoardFromMove`, `BitMove` |
| Special rules | castling squares (E1, H1, A1, G1, C1 ...), en passant and promotion written into move making | `getNewBoardFromMove`, `getCastles`, `BitPawn` |
| End of game | the king is the piece you must not lose; mate is a constant of the evaluation | `getStatus`, `BitBoardEvaluate.MATE` |
| Search | `Minimax` walks `BitBoard` children directly | `ai.Minimax` |
| Hashing | Zobrist keys for exactly 12 piece kinds | `ZobristHashing` |
| Evaluation | ~500 parameters named for the six pieces | `BitBoardEvaluate`, `ParamSchema` |
| Text forms | FEN and SAN use the six standard letters | `rules.Position`, `rules.San` |
| UI | 12 SVGs, material values hard-coded in `chess.ts` | `web/src` |

A seventh piece touches every row. What is already right and stays: `rules` is the only
authority on legality (the browser never computes moves), `Engine` and `Evaluator` are
interfaces, `ParamSchema` is generic, and the safety nets exist (perft in both views, 56-position
`SameMoveTest`, random games against Stockfish, `SearchSpeedTest`).

## 2. Target architecture

```
web UI ──► game / web ──► rules (FEN, legality, status)      engine / arena / lab
                              │                                    │
                              ▼                                    ▼
                         ai.board.Board  ◄──── ai.Minimax ──► ai.eval.Evaluator
                              │
                 ┌────────────┴─────────────┐
            BitBoard (today)        GenericBoard (R3): piece types as data,
                                    one move generator, rules from the variant
```

- **`ai.board.Board`** is the only way into a position: moves, children, check, game over,
  repetition and search keys, FEN. Players are numbered from 0 in turn order and squares are
  plain ids, never "white/black" or files and ranks, so a board for four players or a non-square
  geometry fits the same interface later (the owner's long-term vision).
- **`ai.board.Move`** is one `int`: from, to (8 bits each, boards up to 256 squares) and a
  promotion letter (5 bits). 21 bits, so the transposition table stores it whole.
- **Piece types are data (R2).** A piece is a list of atoms, each marked move, capture or both:
  *leap* (a fixed offset such as (1,2)), *slide* (a direction until blocked, optionally with a
  maximum range), a symmetry (all eight directions, forward only, sideways ...), and *first move
  only*. Properties: royal (losing it loses), promotes on the last rank and to what, a starting
  value for the evaluation, a letter and an icon. The same definition has a text form in Betza
  notation, which Fairy-Stockfish reads, so Fairy-Stockfish can check our perft counts on pieces
  the owner invents.
- **`GenericBoard` (R3):** one bitboard per player and piece type (up to 16 types), a move
  generator compiled from the piece definitions into per-square tables, castling, en passant and
  promotion read from the variant instead of fixed squares.

## 3. Steps

| Step | What | Done when |
|---|---|---|
| **R1** | `Board` interface and `int` move; `BitBoard` implements it; `Minimax`, the `Evaluator` interface, the transposition table, repetition and `rules` go through it | no main class outside `ai.BitBoard` (and the `Boards` factory) names the bitboard; `SameMoveTest` unchanged |
| R2 | piece types as data, the atoms above, a compiler to per-square tables; the six standard pieces defined as data | their tables equal `ai.BitBoard.Attacks` |
| R3 | `GenericBoard` | perft equal to `BitBoard` on every position, equal move lists along random games, `SameMoveTest` unchanged, `SearchSpeedTest` not slower; then `BitBoard` and `BitPiece` are deleted (kept in tests as the oracle until then) |
| R4 | a `Variant` (piece set, start position, the rule switches of the lab plan) through `rules`, the session, the WebSocket protocol and saved games; evaluation parameters generated from the piece set | standard chess is exactly `tuned-v1`, the difficulty ladder does not move |
| R5 | piece designer in the UI (click squares around a piece for move / capture / both, drag for a slide, live preview, Betza text), custom piece rendering, Fairy-Stockfish perft check, the variant health check | the owner invents a piece, plays it against the engine and gets a health report |
| R6 | extension layers, each behind `Board` | see §5 |

## 4. R1 in detail (this branch)

### 4.1 Decisions (defaults taken)

- **Copy-make, not make/unmake.** A move makes a new position; positions keep their children
  until the search releases them. This is how `BitBoard` already works, it keeps searches
  thread-safe with nothing to undo, and R3 can still make children cheaply (a few dozen `long`s).
  A make/unmake board can come later behind the same interface if profiling asks for it.
- **The interface hands out children, not only moves.** `orderedChildren()` keeps the generator's
  own ordering (checks first, then captures), and `captureScore(parent)` feeds MVV-LVA. Both
  were already the search's inputs, so the search's moves do not change.
- **Players are `int`.** `Evaluator.evaluate(Board, int rootPlayer)` replaces
  `evaluate(BitBoard, boolean rootIsBlack)`; `Evaluator.MATE` moves to the interface.
- **The chess evaluation stays chess-specific.** `BitBoardEvaluate` is the bitboard's own
  evaluator and casts to `BitBoard`; R4 generates a schema per piece set. It is the one class
  outside `ai.BitBoard` the layering test allows to be named.
- **The history table stays [2][64][64]** for two players on 64 squares until a board needs more.

### 4.2 What changed

- New `ai.board`: `Board`, `Move`, `Boards` (the one place that picks the implementation).
- `BitBoard implements Board`; `lastMove()` reads the move from `BitMove` (castling as the king's
  two-square step, promotion letter only when a pawn reaches the last rank), `toFen()` writes the
  position with its own en-passant square.
- `Minimax` uses only `Board` and `Move`; it returns an `int` move.
- `rules.Rules` gets moves and next positions from `Board` instead of diffing bitboards.
- `MinimaxEngine`, `DrawOffers`, `BoardStateTracker`, `Texel` go through `Board` / `Boards`.

### 4.3 Checks

- `ai.BitBoard.BoardInterfaceTest`: from every perft position, 20 random games of up to 120
  plies; at every position the `Board` moves equal the old bridge's (`BitBoardRules.legalMoves`,
  same order) and every next FEN equals `BitBoardRules.applyMove`'s.
- `architecture.LayeringTest`: no main class outside `ai.BitBoard` and `ai.board.Boards` names
  `ai.BitBoard.*` except `BitBoardEvaluate`.
- `engine.SameMoveTest` unchanged (56 positions, depths 1-4), the whole suite green, `-Pstress`
  (`SearchSpeedTest`) within its limits.

## 4b. R2 (branch `phase-6-pieces-as-data`)

New package `ai.piece`, not yet used by the board (R3 uses it):

- `Atom`: leap or slide, an offset seen from the owner (forward toward the opponent, right to
  the owner's right), a symmetry (`ONE`, `SIDEWAYS` = left-right mirror, `ALL` = all eight),
  a mode (`MOVE`, `CAPTURE`, `BOTH`), a range for slides (0 = no limit) and first-move-only.
- `PieceType`: name, letter, atoms, royal, promotes-to letters, en passant, castling role
  (`KING` / `ROOK`), starting value in centipawns.
- `StandardPieces`: the six chess pieces as data. The pawn is a forward slide of range 1, a
  first-move forward slide of range 2 (so the double step cannot jump a piece), and a sideways
  pair of forward diagonal capture leaps.
- `Grid`: a rectangle of at most 64 squares, numbered from the top row; player 1 sees it turned
  half a circle, so an asymmetric invented piece points toward its opponent for both players.
- `CompiledPiece`: a piece for one player on one grid, as per-square leap bitboards and slide
  rays. `quietTargets` (empty squares it can move to) and `captureTargets` (its attack set).

Checks: `ai.BitBoard.StandardPiecesTest` compares the six pieces with `Attacks` on every square,
for both players, over 300 random occupancies (attacks, quiet moves, pawn pushes);
`ai.piece.CompiledPieceTest` covers symmetries, an invented piece, the player-dependent forward
direction and a 5x5 grid.

Decisions taken: castling, en passant and promotion stay rules of the board (R3), with the piece
only saying which role it plays; first-move-only is a property of the atom, and the board (R3)
tracks which pieces have not moved.

## 5. Towards "everything possible"

Each layer is more flexible and slower or riskier than the one before, so it is added only when
an experiment needs it. All of them sit behind `Board`, so the search, the evaluation interface
and the UI protocol do not change when they arrive.

1. **Now:** leaps and slides, move and capture separately, directions, first move only.
2. **Conditional atoms:** hoppers (the xiangqi cannon), lame leapers (blocked by a piece in the
   way), capture at a distance without moving.
3. **Rule events:** what happens after a capture (explosion, conversion), drops of captured
   pieces, several moves per turn, win conditions as predicates on the board.
4. **Geometry:** boards beyond 64 squares, holes, other shapes and dimensions: a second board
   implementation (wider bitboards or arrays). The browser needs its own board renderer before
   this, since `react-chessboard` draws only 8x8.
5. **Players:** more than two. Needs another search (max^n or paranoid), not only another board.
6. **Code:** pieces or squares whose behaviour is a function someone writes, sandboxed and
   time-limited. Full freedom, at the price of speed and of the perft oracle.
