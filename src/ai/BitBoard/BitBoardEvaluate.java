package ai.BitBoard;

import java.util.Random;

public class BitBoardEvaluate {
    private static int maximizer, minimizer;
    // מערכים לפתיחה
    private static final long[] BEST_TILES_FOR_WHITE_KING_IN_OPENING = {
            (BoardParts.Tile.G1.position | BoardParts.Tile.B1.position),
            (BoardParts.Tile.F1.position | BoardParts.Tile.C1.position),
            (BoardParts.FIRST_RANK),
            (BoardParts.SECOND_RANK)
    };

    private static final long[] BEST_TILES_FOR_BLACK_KING_IN_OPENING = {
            (BoardParts.Tile.G8.position | BoardParts.Tile.B8.position),
            (BoardParts.Tile.F8.position | BoardParts.Tile.C8.position),
            (BoardParts.EIGHTH_RANK),
            (BoardParts.SEVENTH_RANK)
    };

    private static final long[] BEST_TILES_FOR_WHITE_QUEEN_IN_OPENING = {
            BoardParts.Tile.D1.position,
            BoardParts.Tile.C1.position,
            BoardParts.Tile.E2.position,
            BoardParts.Tile.D2.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_QUEEN_IN_OPENING = {
            BoardParts.Tile.D8.position,
            BoardParts.Tile.C8.position,
            BoardParts.Tile.E7.position,
            BoardParts.Tile.D7.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_ROOK_IN_OPENING = {
            BoardParts.Tile.A1.position,
            BoardParts.Tile.H1.position,
            BoardParts.Tile.D1.position,
            BoardParts.Tile.F1.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_ROOK_IN_OPENING = {
            BoardParts.Tile.A8.position,
            BoardParts.Tile.H8.position,
            BoardParts.Tile.D8.position,
            BoardParts.Tile.F8.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_BISHOP_IN_OPENING = {
            BoardParts.Tile.C1.position,
            BoardParts.Tile.F1.position,
            BoardParts.Tile.G2.position,
            BoardParts.Tile.B2.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_BISHOP_IN_OPENING = {
            BoardParts.Tile.C8.position,
            BoardParts.Tile.F8.position,
            BoardParts.Tile.G7.position,
            BoardParts.Tile.B7.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_KNIGHT_IN_OPENING = {
            BoardParts.Tile.G1.position,
            BoardParts.Tile.B1.position,
            BoardParts.Tile.F3.position,
            BoardParts.Tile.C3.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_KNIGHT_IN_OPENING = {
            BoardParts.Tile.G8.position,
            BoardParts.Tile.B8.position,
            BoardParts.Tile.F6.position,
            BoardParts.Tile.C6.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_PAWN_IN_OPENING = {
            BoardParts.SECOND_RANK,
            BoardParts.THIRD_RANK,
            BoardParts.FOURTH_RANK
    };

    private static final long[] BEST_TILES_FOR_BLACK_PAWN_IN_OPENING = {
            BoardParts.SEVENTH_RANK,
            BoardParts.SIXTH_RANK,
            BoardParts.FIFTH_RANK
    };

    // מערכים לאמצע משחק
    private static final long[] BEST_TILES_FOR_WHITE_KING_IN_MID_GAME = {
            BoardParts.Tile.G1.position,
            BoardParts.Tile.B1.position,
            BoardParts.Tile.F1.position,
            BoardParts.Tile.C1.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_KING_IN_MID_GAME = {
            BoardParts.Tile.G8.position,
            BoardParts.Tile.B8.position,
            BoardParts.Tile.F8.position,
            BoardParts.Tile.C8.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_QUEEN_IN_MID_GAME = {
            BoardParts.Tile.D1.position,
            BoardParts.Tile.C1.position,
            BoardParts.Tile.E4.position,
            BoardParts.Tile.D4.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_QUEEN_IN_MID_GAME = {
            BoardParts.Tile.D8.position,
            BoardParts.Tile.C8.position,
            BoardParts.Tile.E5.position,
            BoardParts.Tile.D5.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_ROOK_IN_MID_GAME = {
            BoardParts.Tile.A1.position,
            BoardParts.Tile.H1.position,
            BoardParts.Tile.D1.position,
            BoardParts.Tile.F1.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_ROOK_IN_MID_GAME = {
            BoardParts.Tile.A8.position,
            BoardParts.Tile.H8.position,
            BoardParts.Tile.D8.position,
            BoardParts.Tile.F8.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_BISHOP_IN_MID_GAME = {
            BoardParts.Tile.C1.position,
            BoardParts.Tile.F1.position,
            BoardParts.Tile.G4.position,
            BoardParts.Tile.B4.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_BISHOP_IN_MID_GAME = {
            BoardParts.Tile.C8.position,
            BoardParts.Tile.F8.position,
            BoardParts.Tile.G5.position,
            BoardParts.Tile.B5.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_KNIGHT_IN_MID_GAME = {
            BoardParts.Tile.G1.position,
            BoardParts.Tile.B1.position,
            BoardParts.Tile.F6.position,
            BoardParts.Tile.C6.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_KNIGHT_IN_MID_GAME = {
            BoardParts.Tile.G8.position,
            BoardParts.Tile.B8.position,
            BoardParts.Tile.F3.position,
            BoardParts.Tile.C3.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_PAWN_IN_MID_GAME = {
            BoardParts.THIRD_RANK,
            BoardParts.FOURTH_RANK,
            BoardParts.FIFTH_RANK
    };

    private static final long[] BEST_TILES_FOR_BLACK_PAWN_IN_MID_GAME = {
            BoardParts.SIXTH_RANK,
            BoardParts.FIFTH_RANK,
            BoardParts.FOURTH_RANK
    };

    // מערכים לסיום משחק
    private static final long[] BEST_TILES_FOR_WHITE_KING_IN_END_GAME = {
            BoardParts.Tile.G1.position,
            BoardParts.Tile.B1.position,
            BoardParts.Tile.F1.position,
            BoardParts.Tile.C1.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_KING_IN_END_GAME = {
            BoardParts.Tile.G8.position,
            BoardParts.Tile.B8.position,
            BoardParts.Tile.F8.position,
            BoardParts.Tile.C8.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_QUEEN_IN_END_GAME = {
            BoardParts.Tile.D1.position,
            BoardParts.Tile.C1.position,
            BoardParts.Tile.E3.position,
            BoardParts.Tile.D3.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_QUEEN_IN_END_GAME = {
            BoardParts.Tile.D8.position,
            BoardParts.Tile.C8.position,
            BoardParts.Tile.E6.position,
            BoardParts.Tile.D6.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_ROOK_IN_END_GAME = {
            BoardParts.Tile.A1.position,
            BoardParts.Tile.H1.position,
            BoardParts.Tile.D1.position,
            BoardParts.Tile.F1.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_ROOK_IN_END_GAME = {
            BoardParts.Tile.A8.position,
            BoardParts.Tile.H8.position,
            BoardParts.Tile.D8.position,
            BoardParts.Tile.F8.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_BISHOP_IN_END_GAME = {
            BoardParts.Tile.C1.position,
            BoardParts.Tile.F1.position,
            BoardParts.Tile.G3.position,
            BoardParts.Tile.B3.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_BISHOP_IN_END_GAME = {
            BoardParts.Tile.C8.position,
            BoardParts.Tile.F8.position,
            BoardParts.Tile.G6.position,
            BoardParts.Tile.B6.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_KNIGHT_IN_END_GAME = {
            BoardParts.Tile.G1.position,
            BoardParts.Tile.B1.position,
            BoardParts.Tile.F4.position,
            BoardParts.Tile.C4.position
    };

    private static final long[] BEST_TILES_FOR_BLACK_KNIGHT_IN_END_GAME = {
            BoardParts.Tile.G8.position,
            BoardParts.Tile.B8.position,
            BoardParts.Tile.F5.position,
            BoardParts.Tile.C5.position
    };

    private static final long[] BEST_TILES_FOR_WHITE_PAWN_IN_END_GAME = {
            BoardParts.SEVENTH_RANK,
            BoardParts.SIXTH_RANK,
            BoardParts.FIFTH_RANK,
            BoardParts.FOURTH_RANK
    };

    private static final long[] BEST_TILES_FOR_BLACK_PAWN_IN_END_GAME = {
            BoardParts.SECOND_RANK,
            BoardParts.THIRD_RANK,
            BoardParts.FOURTH_RANK,
            BoardParts.FIFTH_RANK
    };


    // for now the function duos nothing. I just want to check the run time.
    public static int evaluate(BitBoard board) {
        int value;
        maximizer = board.isWhiteToMove ? 1 : -1;
        minimizer = board.isWhiteToMove ? -1 : 1;
        if (board.whiteKings == 0) return Integer.MAX_VALUE;
        if (board.blackKings == 0) return Integer.MIN_VALUE;
        value = board.getStatus();
        if (value != 1) return value;
        value = 0;
        value += getPiecesValue(board);
        value += getKingSafety(board);


        return value;
    }

    private static int getPiecesValue(BitBoard board) {
        int piecesValue = 0;
        piecesValue -= BitOperations.countSetBits(board.whitePawns) * 10;
        piecesValue -= BitOperations.countSetBits(board.whiteKnights) * 30;
        piecesValue -= BitOperations.countSetBits(board.whiteBishops) * 33;
        piecesValue -= BitOperations.countSetBits(board.whiteRooks) * 50;
        piecesValue -= BitOperations.countSetBits(board.whiteQueens) * 90;

        piecesValue += BitOperations.countSetBits(board.blackPawns) * 10;
        piecesValue += BitOperations.countSetBits(board.blackKnights) * 30;
        piecesValue += BitOperations.countSetBits(board.blackBishops) * 33;
        piecesValue += BitOperations.countSetBits(board.blackRooks) * 50;
        piecesValue += BitOperations.countSetBits(board.blackQueens) * 90;
        return piecesValue;
    }

    private static int getKingSafety(BitBoard board) {
        int kingSafety = 0;
        int[] iValues = { 10, 5, 0, -7, -25 };
        for (int i = 0; i < BEST_TILES_FOR_WHITE_KING_IN_OPENING.length; i++) {
            if ((board.whiteKings & BEST_TILES_FOR_WHITE_KING_IN_OPENING[i]) != 0) {
                kingSafety -= iValues[i];
                break;
            }
            if (i == BEST_TILES_FOR_WHITE_KING_IN_OPENING.length - 1) kingSafety -= iValues[i + 1];
        }
        for (int i = 0; i < BEST_TILES_FOR_BLACK_KING_IN_OPENING.length; i++) {
            if ((board.blackKings & BEST_TILES_FOR_BLACK_KING_IN_OPENING[i]) != 0) {
                kingSafety += iValues[i];
                break;
            }
            if (i == BEST_TILES_FOR_BLACK_KING_IN_OPENING.length - 1) kingSafety += iValues[i + 1];
        }
        return kingSafety;
    }






}
