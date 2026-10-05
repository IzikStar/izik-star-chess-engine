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

## 4c. R3 (branches `phase-6-chess-position`, `phase-6-generic-board`)

- **R3a:** `ai.board.ChessPosition` is what the chess evaluation reads (pieces of a type, attacks,
  check, castling); `BitBoardEvaluate` reads through it, so it works on any board that offers it.
- **R3b:** `ai.board.BoardRules` (a piece set, a grid and a start position; castling found from the
  start position, Zobrist keys per player and type) and `ai.board.GenericBoard`, one bitboard per
  player and type and one move generator for every type. `Boards.fromFen` now returns it.
  - Attack test: a piece of player *p* attacks a square exactly when the same piece of the other
    player, standing on that square, attacks it (offsets turned half a circle, slides blocked the
    same both ways). Each piece also has a *reach* (its attacks on an empty board), so most types
    are ruled out with one AND.
  - A move is tested for leaving the king attacked only when it could: a royal piece moved, the
    side was in check, the piece left a line an enemy slider could attack the king along, or the
    move is castling, en passant or a promotion. Gives-check is tested the same way.
  - Slides: per square, the rays that rise and the rays that fall; the nearest blocker is the
    lowest or highest set bit of ray AND occupied, so a slide costs a few bit operations per ray.
- Checks: `ai.BitBoard.GenericBoardTest` (perft on every position; along 10 random games of 150
  plies from each: next FENs, check, game over, attacks, both evaluations, quiescence moves and
  capture scores equal to `BitBoard`'s) and `ai.GenericSearchTest` (search values equal at depths
  1-4, with and without the speed-ups).
- The generic board lists moves square by square, not direction by direction, so among moves of
  exactly equal score the search may now pick another one: 3 of the 224 moves in `same-moves.txt`
  changed (each checked to score the same on both boards) and were re-recorded.
- Speed (`-Pstress`, level 7, ms, old / new): middlegame 1515-1549 / 1329-1407, kiwipete 811-827 /
  830-843, italian 487-510 / 443-507, rook endgame 72 / 109. Per node the new board is faster
  everywhere (rook endgame 1.68 / 1.33 µs); the endgame searches more nodes because equal moves
  come in another order.
- **R3c:** `ai.BitBoard` is deleted (`BitBoard`, `BitPiece`, `BitMove`, `BitBoardRules`,
  `ZobristHashing`, the bit helpers). The evaluation moved to `ai.eval.ChessEvaluate`, with its own
  chess attack tables (`ai.eval.Attacks`) and square masks (`ai.eval.BoardParts`) until R4
  generates the evaluation from the piece set. The board's tests moved to `ai.board`: perft against
  the published counts (`SearchPerftTest`), random games against Stockfish's legal moves
  (`-Pstress`), and `GenericBoardTest`, which checks that a position reached by moves reads as the
  same position parsed from its FEN and that the skipped attack tests agree with the full ones.
  `LayeringTest`: nothing outside `ai.board` names `GenericBoard`, and `ai/BitBoard` stays deleted.

## 4d. R4 — a variant end to end (owner: "start", 2026-10-05)

Steps: **R4a** variant as data and the board honouring it; R4b `rules` (FEN, SAN, status) by
variant; R4c an evaluation built from the piece set (chess keeps `ChessEvaluate`, so the ladder
does not move); R4d the game session, saved games and the protocol carry the variant; R4e the
variant choice in the New game dialog and a drawing for pieces without one.

### R4a (branch `phase-6-variant`)

- `ai.variant.Variant`: id, name, piece types, grid, start FEN, and the rule switches: goal
  (`CHECKMATE`, `LOSE_EVERYTHING` = antichess, `KING_OF_THE_HILL`, `CHECKS` with a count), forced
  capture, castling on or off. Built in (`Variants`): chess, antichess (the king is an ordinary
  piece a pawn may promote to, no castling, captures compulsory), King of the Hill, Three-check.
  `VariantJson` writes and reads a variant with every field spelled out (Gson 2.8 has no records).
- `BoardRules.of(variant)` compiles a variant once; castling is found from the start position only
  when it is on; the hill is the centre 2x2 (or 1x1) of the grid.
- `Board.outcome()`: ONGOING, WIN, LOSS or DRAW for the player to move, from the goal first, then
  "no legal move" (antichess: a win; otherwise mate or stalemate), then the 50-move rule. A
  position whose game is over by the goal has no moves. `isOver()` is now `outcome() != ONGOING`.
- Three-check counts checks in the position (in the FEN as Fairy-Stockfish writes it, checks
  still to give: `3+3`) and in its hash keys.
- Oracle: Fairy-Stockfish's Python build (`pip install pyffish`) runs here. `tools/variant_oracle.py`
  writes `src/test/resources/variants/oracle.txt` (6 seeded random games of up to 120 plies per
  variant with its legal moves at every ply, and perft to depth 3 from 7 positions per variant);
  `ai.board.VariantOracleTest` checks our board against all of it (2,394 checks), without Python.

### R4b (branch `phase-6-variant-rules`)

- `rules.Rules`, `San` and `Game` take a `Variant` (the old methods are chess). `Game(variant)`
  starts from the variant's position; a move to the last row without a piece named promotes to
  the first piece the mover lists.
- `GameStatus` gains the variant endings: `HILL_REACHED` and `CHECKS_GIVEN` (the side to move
  lost), `NO_PIECES_LEFT` and `NO_MOVES_LEFT` (antichess: the side to move won), with
  `sideToMoveLost()`, `sideToMoveWon()` and `result(whiteToMove)`. The session, the web state,
  the arena and the ladder calibration read results through `result` instead of "checkmate means
  the mover won". Status comes from `Board.outcome()` and the new `Board.goalReached()`.
- Insufficient material is a chess rule only (goal checkmate with the standard pieces): a lone
  king can still win King of the Hill.
- SAN: a check that wins the game (the third check) is written `#`, as Fairy-Stockfish does.
- `Position` keeps three-check's `3+3` field (and counts it in the repetition key).
- Checks: `rules.RulesVariantTest` compares SAN of every played move (2,298) and how each finished
  game ends with Fairy-Stockfish (`oracle.txt` now has `san` and `end` lines), plus hand-made
  positions for each goal.

### R4c (branch `phase-6-variant-eval`)

- `ai.eval.Evaluators.forVariant(variant)` picks the evaluation: the chess pieces on the chess
  board with a royal king to lose (chess, King of the Hill, Three-check) keep `ChessEvaluate`, so
  the difficulty ladder and `tuned-v1` do not move; any other variant gets a `PieceSetEvaluate`.
- `PieceSetEvaluate` builds its `ParamSchema` from the piece set: per piece `material.<piece>`,
  `mobility.<piece>` and `square.<piece>.<square>` (64 on the chess board, seen from the owner's
  side). Material starts at the piece's value (royal pieces 0), the rest at 0; in antichess every
  weight starts at 0, so an evolution run there starts from nothing. Mobility is counted only when
  some mobility weight is not 0, so a fresh evaluation costs no move generation.
- It reads the board through `ai.board.PieceBoard` (pieces and mobility by type), which
  `GenericBoard` implements.
- Game ends are scored from `Board.outcome()` in both evaluations, so a reached hill or a third
  check is a mate for the search, as checkmate is.
- Checks: `ai.eval.PieceSetEvaluateTest` (colour symmetry with random weights, antichess's zero
  start, a forced antichess win found with all weights at 0, variant goals as mate in
  `ChessEvaluate`). `SameMoveTest` is unchanged.

### R4d (branch `phase-6-variant-game`)

- `SearchRequest` carries the variant. `MinimaxEngine` plays it (its evaluator for chess-piece
  variants, `Evaluators.forVariant` for the rest); `EngineSelector` never asks Stockfish outside
  chess, so its levels and hints play the built-in engine's top level there. The web hub also
  caps the level below Stockfish's in a variant, so the PGN never names a level that did not play.
- `GameSession` keeps the variant (`newGame(variant, fen, control)`, `resume(variant, ...)`,
  PGN loading). Running out of time loses in every variant; only chess has material that can
  never win, so only chess turns a flag into a draw.
- PGN: a `Variant` tag with Lichess's names ("Antichess", "King of the Hill", "Three-check");
  reading accepts a name or an id in any case, "Standard" or no tag is chess, an unknown one is
  refused. Moves are read by the variant's rules, promotion to a king included.
- Saved games keep a `variant` id; files from before have none and load as chess.
- Protocol: `newGame` takes `variant` (an id, absent is chess); a champion needs the chess pieces.
  The state carries `variant` {id, name, goal, checksToWin?}; `/api/games` summaries carry the id.
  `/api/eval` and `/api/analysis` answer 422 `unsupportedVariant` outside chess (Stockfish).
- Checks: `engine.VariantEngineTest`, the variant cases in `PgnTest`, `GameArchiveTest`,
  `GameEndingsTest`, `EvalApiTest`, and two socket games in `WebServerTest` (antichess's forced
  capture and PGN tag, King of the Hill against the engine, a three-check game saved and carried on).

### R4e (branch `phase-6-variant-ui`)

- The New game dialog has a *Game* choice (Chess, Antichess, King of the Hill, Three-check) with
  the variant's rule under it. Outside chess the strength slider stops at Level 8, the Elo next
  to a level is hidden (it is a chess Elo), and antichess hides the weights choice (it plays with
  its own evaluation). Rematch and *Play Level n* keep the variant; a lab champion starts chess.
- The game screen names the variant and its rule under the status line; Three-check shows each
  side's checks on its card (`✚ 1/3`); antichess shows no material lead. The evaluation bar and
  *Analyse* are for chess only. The result line, sounds and *My games* (variant in the opponent
  column, kept out of the per-level Elo table) read the variant endings.
- The promotion picker offers the pieces the legal moves name (an antichess pawn may become a
  king). A piece letter with no drawing is drawn as its letter in a circle (`LetterPiece`), so a
  variant with new pieces (R5) can be shown before it has art.
- Checks: `web/e2e/variants.spec.ts` (antichess forced capture and promotion to king, the
  three-check counter, King of the Hill against the engine with the ladder capped).

## 4e. R5 — the piece designer and the health check (owner: "go", 2026-10-05)

Steps: **R5a** Betza text and the player's variants as files; R5b Fairy-Stockfish checks invented
pieces; R5c the designer in the UI (a *Variants* tab); R5d the variant health check (self-play
statistics); R5e made variants in the New game dialog, and one browser test through the whole
path. Defaults taken: the designer is its own tab; pieces are what the atoms already say (leaps up
to 3 squares, slides with a range, symmetry, first move only, moves and captures apart) on 8x8; a
piece without art is its letter in a circle.

### R5a (branch `phase-6-made-variants`)

- `ai.piece.Betza` writes atoms as Betza text and reads it back, in Fairy-Stockfish's dialect
  (checked by hand against `pyffish`: `rfN` is two forward and one right, `frN` one forward and
  two right, `ff`/`fs`/`bb`/`bs` the knight's pairs, `rN` both moves with the long leg to the
  right, `W2` a rider of range 2). Leapers `W F D N A H C Z G`, riders as a doubled letter or
  `R B Q`, `K`, modifiers `m c i` and `f b l r v s`. The pawn is `mfWimfW2cfF`.
- `VariantJson` writes each piece's Betza next to its atoms, and reads a piece from Betza alone.
- `game.VariantStore`: the player's variants, one file each in `variants/` (`--variants DIR`),
  after the built-ins. A save is refused with the reason when the id is a built-in's, a piece
  promotes to a piece the variant lacks, the start position does not read, or the game is over at
  the start; two plies of every line are generated as a smoke test.
- A game of a made variant keeps a copy of the variant (`SavedGame.variantDef`), so editing or
  deleting the variant never breaks its games; PGN reads a made variant's name too.
- HTTP: `GET/PUT/DELETE /api/variants[/{id}]`, `POST /api/betza` (text to atoms and back). The
  state's `variant` carries `custom` and the pieces' letters and names.
- Checks: `ai.piece.BetzaTest` (the chess pieces, Fairy's direction pairs, 400 random atoms written
  and read back to the same moves on random boards), `game.VariantStoreTest`, and an Amazon-chess
  game in `WebServerTest` (saved over HTTP, played over the socket, the variant deleted, the game
  still carried on).

### R5b (branch `phase-6-fairy-pieces`)

- Five made-up variants live in `src/test/resources/variants/made/*.json`: Amazon chess (`QN`
  for the queen), Archbishop chess (`BN` for the knights), Knightrider chess (`NN` for the
  bishops), Forward chess (a Scout `fW3bW` and a Guard `mWcFffN`, no castling) and Amazon
  antichess (forced captures, the king an ordinary piece).
- `tools/variant_oracle.py` gives each one to Fairy-Stockfish as a config whose invented pieces
  are `customPieceN = <letter>:<Betza>` with the Betza text **our writer** put in the file, then
  records random games and perft like the built-ins. `ai.board.VariantOracleTest` reads the
  variants from the same files (`TestVariants.made`) with the atoms removed; `rules.RulesVariantTest` checks the SAN and game ends, so our Betza parser, our writer and the
  move generator and SAN are all checked against Fairy-Stockfish: 3,600 more positions and 105 perft
  counts, all equal on the first run (promotions to the new pieces and antichess game ends
  included).
- Known difference, not covered: our "first move only" means the piece has not left its start
  square; Fairy-Stockfish allows `i` moves only from the double-step rank. The two agree for pawns,
  so no test variant gives `i` to another piece.

### R5c (branch `phase-6-designer`)

- A *Variants* tab (`web/src/Variants.tsx`, `#variants`): the variants in a list, built-ins
  read-only with *Make a copy*. A variant's name, goal (checks to win for N-check), forced
  captures and castling; its start position painted on a board (pick a piece, click squares) or
  typed as FEN; its pieces as chips with their Betza.
- The piece editor: name, letter (the start position and promotions follow a new letter), value,
  royal, promotes to, en passant, castling role. A 7x7 grid around the piece (up is forward) takes
  clicks with the current tools: jump or slide (a slide's steps up to the edge or 2-6), move +
  capture / move only / capture only, all 8 ways / mirrored left-right / just that one, first move
  only. A click on a lit square takes that move away. Beside it an 8x8 board shows where the piece
  goes from the square you click, before or after its first move. The Betza field follows the grid
  (`POST /api/betza`) and the grid follows the field (Enter or leaving it).
- Default taken: the plan said "drag for a slide"; a *Slide* tool is clearer on a phone, so
  dragging is not used.
- The server checks a variant on *Save* and its reason shows on the page (`VariantStore.check`).
- Checks: `web/e2e/designer.spec.ts` (copy chess, make a knight by one click and the Amazon by
  text, 8 and 35 squares on the preview, put it on d1/d8, save, reload, delete; an empty board is
  refused with a reason).

### R5d (branch `phase-6-health`)

- `lab.VariantHealth`: the built-in engine plays a variant against itself on every core but one.
  Each game opens with 4 random plies so the games differ. Both sides then search to the same depth
  (with the app's usual variety, and the variant's own evaluation), and a game is stopped as a draw
  at 300 plies. The report gives white/black/draw counts, white's score, the decisive share, average,
  shortest and longest length, the average number of legal moves per turn, the endings by kind,
  and plain-word notes: one side scores 65% or more, 70% draws, 30% still going at the cap, under 10
  moves a game, or fewer than 8 moves to choose from. The same settings always give the same report.
- `MinimaxEngine.searchAtDepth(variant, gameFens, depth, options, stop)` is the variant search it
  plays with.
- HTTP (`web.HealthApi`): `POST /api/health` {variant, games, depth} starts a check of the variant
  as sent, so unsaved changes are checked too. Only one check runs at a time, and a new one stops
  the last. `GET /api/health` returns progress and the report; `DELETE` stops the check.
- UI: a *Health check* panel in the designer (games 20-200, depth 1-3), with a progress bar, a
  white/draw/black bar, a table and the notes.
- Speed on 4 cores, 40 games at depth 3: chess 15 s, King of the Hill 8 s, three-check 5 s,
  antichess 2 s. The built-ins come out balanced (white 43-58%), and none gets a note.
- Checks: `lab.VariantHealthTest` (counts, progress, same report twice, antichess and Amazon
  chess, cancel, the notes), `WebServerTest.healthCheck`, and `e2e/designer.spec.ts`.

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
