# Variant rules: the building blocks

A variant (`ai.variant.Variant`) is pieces, a board, a start position and rules. The rules used to be one
`Goal` (checkmate, lose everything, king of the hill, N checks). They are now building blocks that can be
combined. The four built-in variants are presets of these blocks, and they play exactly as before.
`PresetPinTest` pins their perft counts and the engine's moves at depths 1 to 4, recorded with the old model.

## Win conditions (`WinCondition`)

A variant lists one or more ways to win. After every move the board checks them in the listed order, and
the first one met decides the game (`GenericBoard.goalMet`).

| Kind | Parameters | Met when |
|---|---|---|
| `CHECKMATE` | none | the side to move is in check and has no legal move. Leave it out to turn checkmate off (antichess) |
| `REACH_SQUARES` | `squares`, `pieces` (letters; `""` = the royal pieces) | the side that just moved has a piece of those types on one of the squares. King of the Hill is this with d4, e4, d5, e5 |
| `CHECKS` | `count` | the side that just moved has given check `count` times. The FEN carries the checks still to give (`3+3`) |
| `CAPTURE_ALL_OF` | `pieces` (letters) | the side to move has no piece of those types left |
| `BARE_ROYAL` | none | the side to move has nothing left but royal pieces |
| `LOSE_EVERYTHING` | none | the side to move has no pieces left, or no legal move: it **wins** (antichess) |

Checkmate and "no legal move" are judged only once the position goals are checked
(`GenericBoard.outcome`):

1. The goals in their order. All of them except checkmate are decided by the position alone.
2. If the side to move has no legal move: with `LOSE_EVERYTHING` it wins. If it is in check and `CHECKMATE`
   is a goal, it is checkmated. Otherwise the stalemate setting decides.
3. Then the move limit.

`CHECKMATE`, `CHECKS`, `BARE_ROYAL` and a `REACH_SQUARES` goal counting royal pieces need a royal piece.
A kind may be listed once, except `REACH_SQUARES` and `CAPTURE_ALL_OF`.

## Settings

- **`royalMode`**
  - `ALL_SAFE` (the default) means every royal piece must stay safe. A move may not leave any of them
    attacked, and an attack on any one is check.
  - `LAST_STANDING` means a side with two or more royal pieces has no check. Its royal pieces may be left
    attacked and captured. Once it has one left, that piece is held to check as in chess. A side with none
    left has lost (`GameStatus.ROYALS_LOST`). In play that only happens from a set-up position, because the
    last royal piece is protected by the check rule. The game is lost by checkmate instead.
- **`stalemate`**: `DRAW`, `WIN` or `LOSS` for the side that has no legal move and is not checkmated.
- **`repetition`**: threefold repetition draws (`Game.status`), and the search treats a repeated position
  as a draw (`Board.repetitionDraws`). Off, repeating never ends the game.
- **`moveLimit`**: the N-move rule. After N moves by each side with no capture and no move of a piece that
  promotes, the game is a draw. 0 turns the rule off.
- **`forcedCapture`**: as before.
- **`castling`** (`CastlingRule`):
  - `enabled`.
  - `steps`: how far the castling piece moves toward its partner. 0 is the chess way, onto the g-file or
    the c-file.
  - `partner`: where the partner lands. `INSIDE` puts it on the square the castling piece crossed last;
    `OUTSIDE` puts it next to the castling piece, toward the edge.
  - `sides`: `BOTH`, `KING_SIDE` or `QUEEN_SIDE`.
  - `safePassage`: the castling piece may not castle out of check, through an attacked square or onto one.
    With it off, only the landing square counts, as for any move.

  Which pieces castle is still set on the pieces themselves. Any type with the `KING` role is a castling
  piece, and any type with the `ROOK` role is a partner. There may be several of each. Each
  `BoardRules.Castling` records its own castling-piece type and partner type. This fixes a bug: with two
  `ROOK`-role types, castlings were found for both, but play moved the last type's piece. A castling whose
  landing square the piece also reaches by an ordinary move is not generated, because both moves would have
  the same from and to squares. The ordinary move is the one played.

## JSON and old files

`VariantJson` writes `goals`, `royalMode`, `stalemate`, `repetition`, `moveLimit`, `forcedCapture`,
`castling` (a boolean) and `castlingRule` (`steps`, `partner`, `sides`, `safePassage`). Older files, and
saved games that embed one, have a single `goal` with `checksToWin` and none of the newer fields. Such a
file reads as the preset its goal stood for (`Variant.Goal`, the old constructor):

| Old goal | Goals | Stalemate | Other settings |
|---|---|---|---|
| `CHECKMATE` | checkmate | draw | all safe, repetition, 50 moves, chess castling (if `castling`) |
| `LOSE_EVERYTHING` | lose everything | win | the same |
| `KING_OF_THE_HILL` | checkmate, then the centre squares (royal pieces) | draw | the same |
| `CHECKS` | checkmate, then `checksToWin` checks | draw | the same |

So an old file plays as it always did. `Variant.legacyGoal()` goes the other way: it names the preset a
variant's rules equal, if any.

## Fairy-Stockfish (`arena.FairyConfig`)

Fairy-Stockfish can only be the opponent in a health check, or a yardstick in the Lab, for a variant it can
be told about. A variant whose goals and settings equal a preset inherits that game (chess, antichess,
kingofthehill or 3check). For any other mix, Fairy-Stockfish inherits chess and is given these options:

| Our rule | Fairy-Stockfish option |
|---|---|
| N checks | `checkCounting`, with the count in the start FEN |
| one squares goal on one piece type | `flagPiece`, `flagRegionWhite`, `flagRegionBlack` |
| capturing all of one type | `extinctionValue = loss`, `extinctionPieceTypes` |
| stalemate win or loss | `stalemateValue` |
| move limit | `nMoveRule` |
| castling landing file | `castlingKingsideFile`, `castlingQueensideFile` |
| castling partner types | `castlingRookPieces` |
| castling sides | the start FEN's castling rights |

Everything else is refused with a reason (`FairyConfig.refusal`): checkmate turned off, the bare-royal goal,
two kings a side or a royal piece other than the king, repetition off, castling through check or with the
partner outside, a castling piece other than the king, and goals that cannot be said together. The variant
is then "our engine only". `/api/variants` rows carry `fairy` and `fairyReason`, and the designer shows the
reason. Health checks and Lab runs still work with the built-in engine, just without Fairy-Stockfish. Lab's
New run already greys out the `fsf` yardstick by the `fairy` flag. Fairy-Stockfish is not installed where
this was written, so the new option lines are checked by their text (`FairyStockfishTest`), not by playing.

## Where the rules show

- **Game status** (`rules.GameStatus`): new values `ALL_CAPTURED`, `BARE_ROYAL`, `ROYALS_LOST` and
  `STALEMATE_LOSS`. `HILL_REACHED` now stands for any squares goal, and `NO_MOVES_LEFT` also for a
  stalemate that wins. The PGN `Termination` names the goal ("won by reaching d5", "won by capturing every
  Queen"), and the game page says the same.
- **Designer** (Overview): an ordered, editable list of ways to win, each explained in one sentence. Then
  the royal mode, stalemate, repetition, move limit, forced capture and castling settings, each explained,
  and whether Fairy-Stockfish plays the variant. `POST /api/variant-rules` returns the engine's view of the
  edited variant: whether it can be played, its Fairy-Stockfish status and its castlings. The Board tab
  marks where a castling piece lands (a ring) and where its partner lands (a dashed ring).
