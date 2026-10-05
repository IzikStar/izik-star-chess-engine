package ai.board;

import ai.variant.Variant;

/** Where boards come from: the one place that picks the implementation. */
public final class Boards {

    private Boards() {}

    /** A standard-chess position from its FEN. */
    public static Board fromFen(String fen) {
        return GenericBoard.fromFen(BoardRules.CHESS, fen);
    }

    /** A position of {@code variant} from its FEN. */
    public static Board fromFen(Variant variant, String fen) {
        return GenericBoard.fromFen(BoardRules.of(variant), fen);
    }
}
