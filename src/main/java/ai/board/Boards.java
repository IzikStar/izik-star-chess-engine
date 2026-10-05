package ai.board;

import ai.BitBoard.BitBoardRules;

/** Where boards come from: the one place that picks the implementation. */
public final class Boards {

    private Boards() {}

    /** A standard-chess position from its FEN. */
    public static Board fromFen(String fen) {
        return BitBoardRules.fromFen(fen);
    }
}
