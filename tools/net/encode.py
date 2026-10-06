"""A position's inputs for the network, numbered as ai.eval.NetFeatures numbers them.

    inputs = 2 sides x T piece types x S squares      (chess: 2 x 6 x 64 = 768)
    index  = (side * T + type) * S + square

side 0 is the player to move, side 1 the opponent. Squares are a8 = 0 ... h1 = 63 as the player to
move sees them: White as they are, Black with the board mirrored top to bottom (e8 -> e1). Piece
types are the variant's list in order; for chess K Q R B N P.

    python3 tools/net/encode.py "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1"

prints the inputs that are on. Java prints the same list with `lab.Cli net NET.json FEN`.
"""
import sys

CHESS_PIECES = "KQRBNP"


def active(fen, pieces=CHESS_PIECES, width=8, height=8):
    """The inputs that are on in `fen`, ascending, from the side to move."""
    placement, side_to_move = fen.split()[:2]
    viewer = 0 if side_to_move == "w" else 1
    types = len(pieces)
    squares = width * height
    out = []
    square = 0
    for ch in placement.replace("/", ""):
        if ch.isdigit():
            square += int(ch)
            continue
        player = 0 if ch.isupper() else 1
        side = 0 if player == viewer else 1
        piece_type = pieces.index(ch.upper())
        row, col = divmod(square, width)
        if viewer == 1:
            row = height - 1 - row
        seen = row * width + col
        out.append((side * types + piece_type) * squares + seen)
        square += 1
    return sorted(out)


def inputs(pieces=CHESS_PIECES, width=8, height=8):
    return 2 * len(pieces) * width * height


if __name__ == "__main__":
    for fen in sys.argv[1:]:
        print(active(fen))
