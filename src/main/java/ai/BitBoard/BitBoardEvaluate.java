package ai.BitBoard;


import main.setting.ChoosePlayFormat;


public class BitBoardEvaluate {
    private static int gameStage;
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

//    private static final long[] BEST_TILES_FOR_WHITE_QUEEN_IN_OPENING = {
//            BoardParts.Tile.D1.position,
//            BoardParts.Tile.C1.position,
//            BoardParts.Tile.E2.position,
//            BoardParts.Tile.D2.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_QUEEN_IN_OPENING = {
//            BoardParts.Tile.D8.position,
//            BoardParts.Tile.C8.position,
//            BoardParts.Tile.E7.position,
//            BoardParts.Tile.D7.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_ROOK_IN_OPENING = {
//            BoardParts.Tile.A1.position,
//            BoardParts.Tile.H1.position,
//            BoardParts.Tile.D1.position,
//            BoardParts.Tile.F1.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_ROOK_IN_OPENING = {
//            BoardParts.Tile.A8.position,
//            BoardParts.Tile.H8.position,
//            BoardParts.Tile.D8.position,
//            BoardParts.Tile.F8.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_BISHOP_IN_OPENING = {
//            BoardParts.Tile.C1.position,
//            BoardParts.Tile.F1.position,
//            BoardParts.Tile.G2.position,
//            BoardParts.Tile.B2.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_BISHOP_IN_OPENING = {
//            BoardParts.Tile.C8.position,
//            BoardParts.Tile.F8.position,
//            BoardParts.Tile.G7.position,
//            BoardParts.Tile.B7.position
//    };

    private static final long BEST_TILES_FOR_WHITE_KNIGHT_IN_OPENING = (BoardParts.Tile.F3.position | BoardParts.Tile.C3.position);
    private static final long BEST_TILES_FOR_BLACK_KNIGHT_IN_OPENING = (BoardParts.Tile.F6.position | BoardParts.Tile.C6.position);

//    private static final long[] BEST_TILES_FOR_WHITE_PAWN_IN_OPENING = {
//            BoardParts.SECOND_RANK,
//            BoardParts.THIRD_RANK,
//            BoardParts.FOURTH_RANK
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_PAWN_IN_OPENING = {
//            BoardParts.SEVENTH_RANK,
//            BoardParts.SIXTH_RANK,
//            BoardParts.FIFTH_RANK
//    };
//
//    // מערכים לאמצע משחק
//    private static final long[] BEST_TILES_FOR_WHITE_KING_IN_MID_GAME = {
//            BoardParts.Tile.G1.position,
//            BoardParts.Tile.B1.position,
//            BoardParts.Tile.F1.position,
//            BoardParts.Tile.C1.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_KING_IN_MID_GAME = {
//            BoardParts.Tile.G8.position,
//            BoardParts.Tile.B8.position,
//            BoardParts.Tile.F8.position,
//            BoardParts.Tile.C8.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_QUEEN_IN_MID_GAME = {
//            BoardParts.Tile.D1.position,
//            BoardParts.Tile.C1.position,
//            BoardParts.Tile.E4.position,
//            BoardParts.Tile.D4.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_QUEEN_IN_MID_GAME = {
//            BoardParts.Tile.D8.position,
//            BoardParts.Tile.C8.position,
//            BoardParts.Tile.E5.position,
//            BoardParts.Tile.D5.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_ROOK_IN_MID_GAME = {
//            BoardParts.Tile.A1.position,
//            BoardParts.Tile.H1.position,
//            BoardParts.Tile.D1.position,
//            BoardParts.Tile.F1.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_ROOK_IN_MID_GAME = {
//            BoardParts.Tile.A8.position,
//            BoardParts.Tile.H8.position,
//            BoardParts.Tile.D8.position,
//            BoardParts.Tile.F8.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_BISHOP_IN_MID_GAME = {
//            BoardParts.Tile.C1.position,
//            BoardParts.Tile.F1.position,
//            BoardParts.Tile.G4.position,
//            BoardParts.Tile.B4.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_BISHOP_IN_MID_GAME = {
//            BoardParts.Tile.C8.position,
//            BoardParts.Tile.F8.position,
//            BoardParts.Tile.G5.position,
//            BoardParts.Tile.B5.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_KNIGHT_IN_MID_GAME = {
//            BoardParts.Tile.G1.position,
//            BoardParts.Tile.B1.position,
//            BoardParts.Tile.F6.position,
//            BoardParts.Tile.C6.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_KNIGHT_IN_MID_GAME = {
//            BoardParts.Tile.G8.position,
//            BoardParts.Tile.B8.position,
//            BoardParts.Tile.F3.position,
//            BoardParts.Tile.C3.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_PAWN_IN_MID_GAME = {
//            BoardParts.THIRD_RANK,
//            BoardParts.FOURTH_RANK,
//            BoardParts.FIFTH_RANK
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_PAWN_IN_MID_GAME = {
//            BoardParts.SIXTH_RANK,
//            BoardParts.FIFTH_RANK,
//            BoardParts.FOURTH_RANK
//    };
//
//    // מערכים לסיום משחק
//    private static final long[] BEST_TILES_FOR_WHITE_KING_IN_END_GAME = {
//            BoardParts.Tile.G1.position,
//            BoardParts.Tile.B1.position,
//            BoardParts.Tile.F1.position,
//            BoardParts.Tile.C1.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_KING_IN_END_GAME = {
//            BoardParts.Tile.G8.position,
//            BoardParts.Tile.B8.position,
//            BoardParts.Tile.F8.position,
//            BoardParts.Tile.C8.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_QUEEN_IN_END_GAME = {
//            BoardParts.Tile.D1.position,
//            BoardParts.Tile.C1.position,
//            BoardParts.Tile.E3.position,
//            BoardParts.Tile.D3.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_QUEEN_IN_END_GAME = {
//            BoardParts.Tile.D8.position,
//            BoardParts.Tile.C8.position,
//            BoardParts.Tile.E6.position,
//            BoardParts.Tile.D6.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_ROOK_IN_END_GAME = {
//            BoardParts.Tile.A1.position,
//            BoardParts.Tile.H1.position,
//            BoardParts.Tile.D1.position,
//            BoardParts.Tile.F1.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_ROOK_IN_END_GAME = {
//            BoardParts.Tile.A8.position,
//            BoardParts.Tile.H8.position,
//            BoardParts.Tile.D8.position,
//            BoardParts.Tile.F8.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_BISHOP_IN_END_GAME = {
//            BoardParts.Tile.C1.position,
//            BoardParts.Tile.F1.position,
//            BoardParts.Tile.G3.position,
//            BoardParts.Tile.B3.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_BISHOP_IN_END_GAME = {
//            BoardParts.Tile.C8.position,
//            BoardParts.Tile.F8.position,
//            BoardParts.Tile.G6.position,
//            BoardParts.Tile.B6.position
//    };
//
//    private static final long[] BEST_TILES_FOR_WHITE_KNIGHT_IN_END_GAME = {
//            BoardParts.Tile.G1.position,
//            BoardParts.Tile.B1.position,
//            BoardParts.Tile.F4.position,
//            BoardParts.Tile.C4.position
//    };
//
//    private static final long[] BEST_TILES_FOR_BLACK_KNIGHT_IN_END_GAME = {
//            BoardParts.Tile.G8.position,
//            BoardParts.Tile.B8.position,
//            BoardParts.Tile.F5.position,
//            BoardParts.Tile.C5.position
//    };

    private static final long[] BEST_TILES_FOR_WHITE_PAWN_IN_END_GAME = {
            BoardParts.SEVENTH_RANK,
            BoardParts.SIXTH_RANK,
            BoardParts.FIFTH_RANK,
            BoardParts.FOURTH_RANK,
            BoardParts.THIRD_RANK
    };

    private static final long[] BEST_TILES_FOR_BLACK_PAWN_IN_END_GAME = {
            BoardParts.SECOND_RANK,
            BoardParts.THIRD_RANK,
            BoardParts.FOURTH_RANK,
            BoardParts.FIFTH_RANK,
            BoardParts.SIXTH_RANK
    };


    public static int evaluate(BitBoard board) {
        boolean switchSides = ChoosePlayFormat.isComputersGame ? (ChoosePlayFormat.isEnginePlayingBlack) : ChoosePlayFormat.isPlayingWhite;
        int value;
        if (board.whiteKings == 0) return switchSides ? Integer.MAX_VALUE : Integer.MIN_VALUE;
        if (board.blackKings == 0) return switchSides ? Integer.MIN_VALUE : Integer.MAX_VALUE;
        value = board.getStatus();
        if (value != 1) return switchSides ? value : -value;
        value = 0;
        gameStage = getGameStage(board);
        value += getPiecesPureValue(board);
        value += getPawnsProgress(board);
        // value += getPiecesValueForAmount(board, value);
        value += getKingSafety(board);
        value += getCastles(board);
        value += getTargets(board);
        value += getBishopsDevelopment(board);
        value += getQueensOutInTheOpening(board);
        value += getKnightsDevelopment(board);
        return switchSides ? value : -value;
    }

    private static int getGameStage(BitBoard board) {
        int gameStage = 1;
        if (board.numOfTurns < 8) {
            gameStage = 0;
        }
        if (BitOperations.countSetBits(board.whitePieces & board.blackPieces) >= 7) {
            gameStage = 2;
        }
        return gameStage;
    }
    
    private static int getPiecesPureValue(BitBoard board) {
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

    private static int getPiecesValue(BitBoard board) {
        int piecesValue = 0;
        piecesValue -= BitOperations.countSetBits(board.whitePawns) * 9;
        piecesValue -= BitOperations.countSetBits(board.whiteKnights) * 27;
        piecesValue -= BitOperations.countSetBits(board.whiteBishops) * 30;
        piecesValue -= BitOperations.countSetBits(board.whiteRooks) * 45;
        piecesValue -= BitOperations.countSetBits(board.whiteQueens) * 81;

        piecesValue += BitOperations.countSetBits(board.blackPawns) * 9;
        piecesValue += BitOperations.countSetBits(board.blackKnights) * 27;
        piecesValue += BitOperations.countSetBits(board.blackBishops) * 30;
        piecesValue += BitOperations.countSetBits(board.blackRooks) * 45;
        piecesValue += BitOperations.countSetBits(board.blackQueens) * 81;
        return piecesValue;
    }

    private static int getPiecesValueForAmount(BitBoard board, int evaluate) {
        int piecesValue = 0;
        int wNumOfPieces = 16 - BitOperations.countSetBits(board.whitePieces) + evaluate;
        int bNumOfPieces = 16 - BitOperations.countSetBits(board.blackPieces) - evaluate;
        if (wNumOfPieces < 1) wNumOfPieces = 1;
        if (bNumOfPieces < 1) bNumOfPieces = 1;
        piecesValue -= BitOperations.countSetBits(board.whitePawns) * wNumOfPieces;
        piecesValue -= BitOperations.countSetBits(board.whiteKnights) * 3 * wNumOfPieces;
        piecesValue -= BitOperations.countSetBits(board.whiteBishops) * 4 * wNumOfPieces;
        piecesValue -= BitOperations.countSetBits(board.whiteRooks) * 5 * wNumOfPieces;
        piecesValue -= BitOperations.countSetBits(board.whiteQueens) * 9 * wNumOfPieces;

        piecesValue += BitOperations.countSetBits(board.blackPawns) * bNumOfPieces;
        piecesValue += BitOperations.countSetBits(board.blackKnights) * 3 * bNumOfPieces;
        piecesValue += BitOperations.countSetBits(board.blackBishops) * 4 * bNumOfPieces;
        piecesValue += BitOperations.countSetBits(board.blackRooks) * 5 * bNumOfPieces;
        piecesValue += BitOperations.countSetBits(board.blackQueens) * 9 * bNumOfPieces;
        return piecesValue;
    }

    private static int getKingSafety(BitBoard board) {
        int kingSafety = 0;
        if (board.numOfTurns < 20) {
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
        }
        return kingSafety;
    }

    private static int getIsKingsBehindPawns(BitBoard board) {
        int kingSafety = 0;
        if ((board.whiteKings >> 8 & board.whitePawns) == 0) {
            // מוריד את ערך המלך בחייל אם אין לפני המלך חייל
            kingSafety += 10;
            // לתוספת דיוק צריך לבדוק אם המלך נמצא במקום ששיפט 9 או 7 מעלה בעוד שורה. ןצריך גם לבדוק מבני חיילים בטוחים שנפרשים על שתי שורות
            if ((board.whiteKings >> 9 & board.whitePawns) == 0) {
                kingSafety += 4;
            }
            if ((board.whiteKings >> 7 & board.whitePawns) == 0) {
                kingSafety += 4;
            }

        }
        if ((board.blackKings >> 8 & board.blackPawns) == 0) {
            // מוריד את ערך המלך בחייל אם אין לפני המלך חייל
            kingSafety -= 10;
            // לתוספת דיוק צריך לבדוק אם המלך נמצא במקום ששיפט 9 או 7 מעלה בעוד שורה. ןצריך גם לבדוק מבני חיילים בטוחים שנפרשים על שתי שורות
            if ((board.blackKings >> 9 & board.blackPawns) == 0) {
                kingSafety -= 4;
            }
            if ((board.blackKings >> 7 & board.blackPawns) == 0) {
                kingSafety -= 4;
            }

        }

        return kingSafety;
    }

    private static int getCastles(BitBoard board) {
        int castles = 0;
        castles -= board.canWhiteCastleKingSide ? 0 : -6;
        castles -= board.canWhiteCastleQueenSide ? 0 : -4;
        castles -= board.hasWhiteCastled ? 16 : 0;
        castles += board.canBlackCastleKingSide ? 0 : -6;
        castles += board.canBlackCastleQueenSide ? 0 : -4;
        castles += board.hasBlackCastled ? 16 : 0;
        return castles;
    }

    private static int getTargets(BitBoard board) {
        int targets = 0;
        targets -= BitOperations.countSetBits(board.getAllAttackedTiles(1));
        targets += BitOperations.countSetBits(board.getAllAttackedTiles(0));

        targets -= BitOperations.countSetBits((board.getAllAttackedTiles(1) & board.blackPieces)) * 2;
        targets += BitOperations.countSetBits((board.getAllAttackedTiles(0) & board.whitePieces)) * 2;

        targets -= BitOperations.countSetBits((board.getAllAttackedTiles(1) & board.whitePieces));
        targets += BitOperations.countSetBits((board.getAllAttackedTiles(0) & board.blackPieces));
        return targets;
    }

    private static int getPawnsProgress(BitBoard board) {
        int pawnProgress = 0;
        for (int i = 0; i < BEST_TILES_FOR_WHITE_PAWN_IN_END_GAME.length; i++) {
            pawnProgress -= BitOperations.countSetBits((board.whitePawns & BEST_TILES_FOR_WHITE_PAWN_IN_END_GAME[i])) * (10 - i * 2 - 1);
        }
        for (int i = 0; i < BEST_TILES_FOR_BLACK_PAWN_IN_END_GAME.length; i++) {
            pawnProgress += BitOperations.countSetBits((board.blackPawns & BEST_TILES_FOR_BLACK_PAWN_IN_END_GAME[i])) * (10 - i * 2 - 1);
        }
        pawnProgress -= BitOperations.countSetBits(board.whitePawns & BoardParts.CENTER) * 5;
        pawnProgress += BitOperations.countSetBits(board.blackPawns & BoardParts.CENTER) * 5;
        return pawnProgress;
    }

    private static int getPassedPawnsAndBlockedPawns(BitBoard board) {
        int passedPawnsAndBlockedPawns = 0;
        
        return passedPawnsAndBlockedPawns;
    }

    private static int getQueensOutInTheOpening(BitBoard board) {
        int queensOutInTheOpening = 0;
        if (gameStage == 0) {
            queensOutInTheOpening -= (board.whiteQueens & BoardParts.Tile.D1.position) == 0 ? -15 : 0;
            queensOutInTheOpening += (board.blackQueens & BoardParts.Tile.D8.position) == 0 ? -15 : 0;
        }
        // System.out.println(queensOutInTheOpening);
        return queensOutInTheOpening;
    }

    private static int getKnightsDevelopment(BitBoard board) {
        int knightsDevelopment = 0;
        knightsDevelopment -= (board.whiteKnights & BoardParts.FIRST_RANK) != 0 ? -10 : 0;
        knightsDevelopment += (board.blackKnights & BoardParts.EIGHTH_RANK) != 0 ? -10 : 0;

        knightsDevelopment -= BitOperations.countSetBits(board.whiteKnights & BEST_TILES_FOR_WHITE_KNIGHT_IN_OPENING) * 2;
        knightsDevelopment += BitOperations.countSetBits(board.blackKnights & BEST_TILES_FOR_BLACK_KNIGHT_IN_OPENING) * 2;


        knightsDevelopment -= (board.whiteKnights & BoardParts.BACK_FIVE_RANKS) != 0 && board.numOfTurns < 9 ? -5 : 0;
        knightsDevelopment += (board.blackKnights & BoardParts.FIRST_FIVE_RANKS) != 0 && board.numOfTurns < 9 ? -5 : 0;
        return knightsDevelopment;
    }

    private static int getPawnsInTheCenter(BitBoard board) {
        int pawnsDevelopment = 0;
        pawnsDevelopment -= (board.whitePawns & (BoardParts.Tile.E2.position | BoardParts.Tile.D2.position)) != 0 ? - 2 : 0;
        pawnsDevelopment += (board.blackPawns & (BoardParts.Tile.E7.position | BoardParts.Tile.D7.position)) != 0 ? - 2 : 0;
//        for (int i = 0; i < BEST_TILES_FOR_WHITE_PAWN_IN_END_GAME.length; i++) {
//            pawnsDevelopment -= BitOperations.countSetBits((board.whitePawns & BEST_TILES_FOR_WHITE_PAWN_IN_END_GAME[i])) * (10 - i * 2 - 1);
//        }
//        for (int i = 0; i < BEST_TILES_FOR_BLACK_PAWN_IN_END_GAME.length; i++) {
//            pawnsDevelopment += BitOperations.countSetBits((board.blackPawns & BEST_TILES_FOR_BLACK_PAWN_IN_END_GAME[i])) * (10 - i * 2 - 1);
//        }
        return pawnsDevelopment;
    }

    private static int getBishopsDevelopment(BitBoard board) {
        int bishopsDevelopment = 0;
        bishopsDevelopment -= (board.whiteBishops & BoardParts.FIRST_RANK) != 0 ? -15 : 0;
        bishopsDevelopment += (board.blackBishops & BoardParts.EIGHTH_RANK) != 0 ? -15 : 0;
        return bishopsDevelopment;
    }

}
