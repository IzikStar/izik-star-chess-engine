package ai.BitBoard;

import ai.BitBoard.BitPiece.*;
import ai.BoardState;
import pieces.Piece;
import main.Debug;

import java.util.ArrayList;

public class BitBoard {
    // state arguments
    protected long whiteKings = 0x0, whiteQueens = 0x0, whiteRooks = 0x0, whiteBishops = 0x0, whiteKnights = 0x0, whitePawns = 0x0;
    protected long blackKings = 0x0, blackQueens = 0x0, blackRooks = 0x0, blackBishops = 0x0, blackKnights = 0x0, blackPawns = 0x0;
    protected long whitePieces, blackPieces;
    protected long enPassantTile;
    private boolean canWhiteCastleKingSide, canWhiteCastleQueenSide, canBlackCastleKingSide, canBlackCastleQueenSide;
    protected boolean isWhiteToMove;
    protected int numOfTurns, numOfTurnsWithoutCaptureOrPawnMove;

    // constructors
    public BitBoard(BoardState boardState) {
        for (Piece piece : boardState.getAllPieces()) {
            int tile = boardState.getTileNum(piece.col, piece.row);
            long tileNum = 0x1;
            tileNum <<= tile;
            if (piece.isWhite) {
                switch (piece.name.charAt(0)) {
                    case 'K' :
                        if (piece.name.equals("King")) {
                            whiteKings = whiteKings | tileNum;
                            if (!piece.isFirstMove) {
                                canWhiteCastleQueenSide = false;
                                canWhiteCastleKingSide = false;
                            }
                        }
                        else whiteKnights = whiteKnights | tileNum;
                        break;
                    case 'Q' :
                        whiteQueens = whiteQueens | tileNum;
                        break;
                    case 'R' :
                        whiteRooks = whiteRooks | tileNum;
                        if (piece.col == 0) {
                            if (!piece.isFirstMove) {
                                canWhiteCastleQueenSide = false;
                            }
                        }
                        else {
                            if (!piece.isFirstMove) {
                                canWhiteCastleKingSide = false;
                            }
                        }
                        break;
                    case 'B' :
                        whiteBishops = whiteBishops | tileNum;
                        break;
                    case 'P' :
                        whitePawns = whitePawns | tileNum;
                        break;
                    default: Debug.log("unfamiliar piece.");
                    break;
                }
            }
            else {
                switch (piece.name.charAt(0)) {
                    case 'K' :
                        if (piece.name.equals("King")) {
                            blackKings = blackKings | tileNum;
                            if (!piece.isFirstMove) {
                                canBlackCastleQueenSide = false;
                                canBlackCastleKingSide = false;
                            }
                        }
                        else blackKnights = blackKnights | tileNum;
                        break;
                    case 'Q' :
                        blackQueens = blackQueens | tileNum;
                        break;
                    case 'R' :
                        blackRooks = blackRooks | tileNum;
                        if (piece.col == 0) {
                            if (!piece.isFirstMove) {
                                canBlackCastleQueenSide = false;
                            }
                        }
                        else {
                            if (!piece.isFirstMove) {
                                canBlackCastleQueenSide = false;
                            }
                        }
                        break;
                    case 'B' :
                        blackBishops = blackBishops | tileNum;
                        break;
                    case 'P' :
                        blackPawns = blackPawns | tileNum;
                        break;
                    default: Debug.log("unfamiliar piece.");
                        break;
                }
            }
        }
        whitePieces = whiteKings | whiteQueens | whiteRooks | whiteBishops | whiteKnights | whitePawns;
        blackPieces = blackKings | blackQueens | blackRooks | blackBishops | blackKnights | blackPawns;
        isWhiteToMove = boardState.getIsWhiteToMove();
        numOfTurns = boardState.numOfTurns;
        numOfTurnsWithoutCaptureOrPawnMove = boardState.numOfTurnWithoutCaptureOrPawnMove;
        enPassantTile = boardState.enPassantTile;
    }
    public BitBoard(long whiteKings, long whiteQueens, long whiteRooks, long whiteBishops, long whiteKnights, long whitePawns,
                    long blackKings, long blackQueens, long blackRooks, long blackBishops, long blackKnights, long blackPawns,
                    boolean isWhiteToMove,
                    boolean canWhiteCastleKingSide, boolean canWhiteCastleQueenSide,
                    boolean canBlackCastleKingSide, boolean canBlackCastleQueenSide,
                    long enPassantTile,
                    int numOfTurns, int numOfTurnsWithoutCaptureOrPawnMove) {
        this.whiteKings = whiteKings;
        this.whiteQueens = whiteQueens;
        this.whiteRooks = whiteRooks;
        this.whiteBishops = whiteBishops;
        this.whiteKnights = whiteKnights;
        this.whitePawns = whitePawns;
        this.blackKings = blackKings;
        this.blackQueens = blackQueens;
        this.blackRooks = blackRooks;
        this.blackBishops = blackBishops;
        this.blackKnights = blackKnights;
        this.blackPawns = blackPawns;
        this.enPassantTile = enPassantTile;
        this.whitePieces = whiteKings | whiteQueens | whiteRooks | whiteBishops | whiteKnights | whitePawns;
        this.blackPieces = blackKings | blackQueens | blackRooks | blackBishops | blackKnights | blackPawns;
        this.isWhiteToMove = isWhiteToMove;
        this.numOfTurns = numOfTurns;
        this.numOfTurnsWithoutCaptureOrPawnMove = numOfTurnsWithoutCaptureOrPawnMove;
        this.canWhiteCastleKingSide = canWhiteCastleKingSide;
        this.canWhiteCastleQueenSide = canWhiteCastleQueenSide;
        this.canBlackCastleKingSide = canBlackCastleKingSide;
        this.canBlackCastleQueenSide = canBlackCastleQueenSide;
    }
    public BitBoard(BitBoard board) {
        this.whiteKings = board.whiteKings;
        this.whiteQueens = board.whiteQueens;
        this.whiteRooks = board.whiteRooks;
        this.whiteBishops = board.whiteBishops;
        this.whiteKnights = board.whiteKnights;
        this.whitePawns = board.whitePawns;
        this.blackKings = board.blackKings;
        this.blackQueens = board.blackQueens;
        this.blackRooks = board.blackRooks;
        this.blackBishops = board.blackBishops;
        this.blackKnights = board.blackKnights;
        this.blackPawns = board.blackPawns;
        this.enPassantTile = board.enPassantTile;
        this.whitePieces = whiteKings | whiteQueens | whiteRooks | whiteBishops | whiteKnights | whitePawns;
        this.blackPieces = blackKings | blackQueens | blackRooks | blackBishops | blackKnights | blackPawns;
        this.isWhiteToMove = board.isWhiteToMove;
        this.numOfTurns = board.numOfTurns;
        this.numOfTurnsWithoutCaptureOrPawnMove = board.numOfTurnsWithoutCaptureOrPawnMove;
        this.canWhiteCastleKingSide = board.canWhiteCastleKingSide;
        this.canWhiteCastleQueenSide = board.canWhiteCastleQueenSide;
        this.canBlackCastleKingSide = board.canBlackCastleKingSide;
        this.canBlackCastleQueenSide = board.canBlackCastleQueenSide;
    }

    // Getters and Setters to the relevant pieces
    public void setQueens(int color, long position) {
        if (color == 1) this.whiteQueens = position;
        else this.blackQueens = position;
    }
    public long getQueens(int color) {
        return color == 1 ? whiteQueens : blackQueens;
    }
    public void setRooks(int color, long position) {
        if (color == 1) this.whiteRooks = position;
        else this.blackRooks = position;
    }
    public long getRooks(int color) {
        return color == 1 ? whiteRooks : blackRooks;
    }
    public void setBishops(int color, long position) {
        if (color == 1) this.whiteBishops = position;
        else this.blackBishops = position;
    }
    public long getBishops(int color) {
        return color == 1 ? whiteBishops : blackBishops;
    }
    public void setKnights(int color, long position) {
        if (color == 1) this.whiteKnights = position;
        else this.blackKnights = position;
    }
    public long getKnights(int color) {
        return color == 1 ? whiteKnights : blackKnights;
    }
    public void setPawns(int color, long position) {
        if (color == 1) this.whitePawns = position;
        else this.blackPawns = position;
    }
    public long getPawns(int color) {
        return color == 1 ? whitePawns : blackPawns;
    }

    // move details:
    public boolean sameTeem(long newPosition, long prevPosition) {
        if (isWhiteToMove) {
            return ((newPosition & ~prevPosition) & whitePieces) != 0;
        }
        else {
            return ((newPosition & ~prevPosition) & blackPieces) != 0;
        }
    }

    private boolean isCheckOn(int color) {
        long attackedTiles = getAllAttackedTiles(BitBoardOperations.toggleColor(color));
        Debug.log("Attacked tiles: " + BitOperations.printBitboard(attackedTiles));
        long king = color == 1 ? whiteKings : blackKings;
        return (king & attackedTiles) != 0;
    }

    // making moves:
    private BitBoard getNewBoardFromMove(int numOfPiece, long newPosition) {
        long wK = whiteKings, wQ = whiteQueens, wR = whiteRooks, wB = whiteBishops, wN = whiteKnights, wP = whitePawns;
        long bK = blackKings, bQ = blackQueens, bR = blackRooks, bB = blackBishops, bN = blackKnights, bP = blackPawns;
        long ePT = -1;
        boolean K = canWhiteCastleKingSide, Q = canWhiteCastleQueenSide, k = canBlackCastleKingSide, q = canBlackCastleQueenSide;
        boolean WTM = !isWhiteToMove;
        int nOT = numOfTurns, nOTWCOPM = numOfTurnsWithoutCaptureOrPawnMove + 1;

        if (isWhiteToMove) {
            long target;
            // pawn:
            if (numOfPiece == 6 /*&& (whitePawns ^ newPosition) != 0*/) {
                nOTWCOPM = 0;
                // pawn 2 square move:
                if(BitOperations.isShiftBy16(whitePawns ^ newPosition)) {
                    // "whitePawns ^ newPosition" is the pawn how moved in the start and the end location, ">>8" move it to first row and third row, then we get only third row.
                    ePT = BoardParts.FIRST_RANK ^ (whitePawns ^ newPosition >>> 8);
                }
                wP = newPosition;
                target = wP & ~whitePawns;
            }
            // knight:
            else if (numOfPiece == 5) {
                wN = newPosition;
                target = wN & ~whiteKnights;
            }
            // bishop:
            else if (numOfPiece == 4) {
                wB = newPosition;
                target = wB & ~whiteBishops;
            }
            // rook:
            else if (numOfPiece == 3) {
                wR = newPosition;
                target = wR & ~whiteRooks;
                // checking if kingSide rook left its origin tile and cancel castling rights for that side:
                if (K && (BoardParts.Tile.A8.position & newPosition) == 0) {
                    K = false;
                }
                // checking if queenSide rook left its origin tile and cancel castling rights for that side:
                else if (Q && (BoardParts.Tile.A1.position & newPosition) == 0) {
                    Q = false;
                }
            }
            // queen:
            else if (numOfPiece == 2) {
                wQ = newPosition;
                target = wQ & ~whiteQueens;
            }
            // king:
            else if (numOfPiece == 1) {
                K = false;
                Q = false;
                wK = newPosition;
                target = wK & ~whiteKings;
            }
            // default:
            else {
                target = 0x0L;
            }
            // capture:
            if((newPosition & blackPieces) != 0) {
                nOTWCOPM = 0;
                // -logic to remove the captured piece-
                int capturedPiece = BitOperations.getPositionFromBit(target);
                bK = BitOperations.clearBit(bK, capturedPiece);
                bQ = BitOperations.clearBit(bQ, capturedPiece);
                bR = BitOperations.clearBit(bR, capturedPiece);
                bB = BitOperations.clearBit(bB, capturedPiece);
                bN = BitOperations.clearBit(bN, capturedPiece);
                bP = BitOperations.clearBit(bP, capturedPiece);
            }
        }
        else {
            // update name of turn because the black turn just ended:
            nOT++;
            long target;
            // pawn:
            if (numOfPiece == 6 /*&& (whitePawns ^ newPosition) != 0*/) {
                nOTWCOPM = 0;
                // pawn 2 square move:
                if(BitOperations.isShiftBy16(blackPawns ^ newPosition)) {
                    // "blackPawns ^ newPosition" is the pawn how moved in the start and the end location, ">>8" move it to first row and third row, then we get only third row.
                    ePT = BoardParts.EIGHTH_RANK ^ (blackPawns ^ newPosition << 8);
                }
                bP = newPosition;
                target = bP & ~blackPawns;
            }
            // knight:
            else if (numOfPiece == 5) {
                bN = newPosition;
                target = bN & ~blackKnights;
            }
            // bishop:
            else if (numOfPiece == 4) {
                bB = newPosition;
                target = bB & ~blackBishops;
            }
            // rook:
            else if (numOfPiece == 3) {
                bR = newPosition;
                target = bR & ~blackRooks;
                // checking if kingSide rook left its origin tile and cancel castling rights for that side:
                if (k && (BoardParts.Tile.H8.position & newPosition) == 0) {
                    k = false;
                }
                // checking if queenSide rook left its origin tile and cancel castling rights for that side:
                else if (q && (BoardParts.Tile.A8.position & newPosition) == 0) {
                    q = false;
                }
            }
            // queen:
            else if (numOfPiece == 2) {
                bQ = newPosition;
                target = bQ & ~blackQueens;
            }
            // king:
            else if (numOfPiece == 1) {
                k = false;
                q = false;
                bK = newPosition;
                target = bK & ~blackKings;
            }
            // default:
            else {
                target = 0x0L;
            }
            // capture:
            if((newPosition & whitePieces) != 0) {
                nOTWCOPM = 0;
                // -logic to remove the captured piece-
                int capturedPiece = BitOperations.getPositionFromBit(target);
                wK = BitOperations.clearBit(wK, capturedPiece);
                wQ = BitOperations.clearBit(wQ, capturedPiece);
                wR = BitOperations.clearBit(wR, capturedPiece);
                wB = BitOperations.clearBit(wB, capturedPiece);
                wN = BitOperations.clearBit(wN, capturedPiece);
                wP = BitOperations.clearBit(wP, capturedPiece);
            }
        }
        return new BitBoard(
                wK,        // whiteKings
                wQ,        // whiteQueens
                wR,        // whiteRooks
                wB,        // whiteBishops
                wN,        // whiteKnights
                wP,        // whitePawns
                bK,        // blackKings
                bQ,        // blackQueens
                bR,        // blackRooks
                bB,        // blackBishops
                bN,        // blackKnights
                bP,        // blackPawns
                WTM,     // isWhiteToMove
                K,         // canWhiteCastleKingSide
                Q,         // canWhiteCastleQueenSide
                k,         // canBlackCastleKingSide
                q,         // canBlackCastleQueenSide
                ePT,       // enPassantTile
                nOT,   // numOfTurns
                nOTWCOPM   // numOfTurnsWithoutCaptureOrPawnMove
        );
    }

    // get next states:
    public ArrayList<BitBoard> getNextStates() {
        int color = isWhiteToMove ? 1 : 0;
        ArrayList<BitBoard> nextMoves = getMovesForColor(color);
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        for (BitBoard state : nextMoves) {
            if (!state.isCheckOn(color)) {
                nextStates.add(state);
            }
        }
        return nextStates;
    }
    // get next moves:
    public ArrayList<BitBoard> getMovesForColor(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        nextStates.addAll(getKingsMoves(color));
        Debug.log("size = " + nextStates.size());
        nextStates.addAll(getQueensMoves(color));
        Debug.log("size = " + nextStates.size());
        nextStates.addAll(getRooksMoves(color));
        Debug.log("size = " + nextStates.size());
        nextStates.addAll(getBishopsMoves(color));
        Debug.log("size = " + nextStates.size());
        nextStates.addAll(getKnightsMoves(color));
        Debug.log("size = " + nextStates.size());
        nextStates.addAll(getPawnsMoves(color));
        Debug.log("size = " + nextStates.size());
        return nextStates;
    }
    // pieces moves:
    private ArrayList<BitBoard> getKingsMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteKings : blackKings;
        BitKing king = new BitKing(color, position, whitePieces, blackPieces);
        for (long move : king.validMovements()) {
            if (!sameTeem(move, position)) {
                BitBoard newBoard = getNewBoardFromMove(1, move);
                nextStates.add(newBoard);
            }
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getQueensMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteQueens : blackQueens;
        BitQueen queen = new BitQueen(color, position, whitePieces, blackPieces);
        for (long move : queen.validMovements()) {
            BitBoard newBoard = getNewBoardFromMove(2, move);
            nextStates.add(newBoard);
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getRooksMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteRooks : blackRooks;
        BitRook rook = new BitRook(color, position, whitePieces, blackPieces);
        for (long move : rook.validMovements()) {
            BitBoard newBoard = getNewBoardFromMove(3, move);
            nextStates.add(newBoard);
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getBishopsMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteBishops : blackBishops;
        BitBishop bishop = new BitBishop(color, position, whitePieces, blackPieces);
        for (long move : bishop.validMovements()) {
            BitBoard newBoard = getNewBoardFromMove(4, move);
            nextStates.add(newBoard);
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getKnightsMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteKnights : blackKnights;
        BitKnight knight = new BitKnight(color, position, whitePieces, blackPieces);
        for (long move : knight.validMovements()) {
            BitBoard newBoard = getNewBoardFromMove(5, move);
            nextStates.add(newBoard);
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getPawnsMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whitePawns : blackPawns;
        BitPawn pawn = new BitPawn(color, position, whitePieces, blackPieces);
        for (long move : pawn.validMovements()) {
            BitBoard newBoard = getNewBoardFromMove(6, move);
            long promotionTile = (move & BoardParts.getPromotionRow(color));
            if (promotionTile != 0) {
                nextStates.addAll(pawn.getPromotions(newBoard, promotionTile));
            }
            else nextStates.add(newBoard);
        }
        return nextStates;
    }
    // get all attacked tiles:
    public long getAllAttackedTiles(int color) {
        BitKing king; BitQueen queen; BitRook rook; BitBishop bishop; BitKnight knight; BitPawn pawn;
        long kings = color == 1 ? whiteKings : blackKings;
        long queens = color == 1 ? whiteQueens : blackQueens;
        long rooks = color == 1 ? whiteRooks : blackRooks;
        long bishops = color == 1 ? whiteBishops : blackBishops;
        long knights = color == 1 ? whiteKnights : blackKnights;
        long pawns = color == 1 ? whitePawns : blackPawns;

        king = new BitKing(color, kings, whitePieces, blackPieces);
        queen = new BitQueen(color, queens, whitePieces, blackPieces);
        rook = new BitRook(color, rooks, whitePieces, blackPieces);
        bishop = new BitBishop(color, bishops, whitePieces, blackPieces);
        knight = new BitKnight(color, knights, whitePieces, blackPieces);
        pawn = new BitPawn(color, pawns, whitePieces, blackPieces);
        return king.getAttackedTiles() | queen.getAttackedTiles() | rook.getAttackedTiles() | bishop.getAttackedTiles() | knight.getAttackedTiles() | pawn.getAttackedTiles();
    }


    // main for debugging
    public static void main(String[] args) {
        String fen = "rnbqkbnr/8/8/8/8/p5pP/8/RNBQKBNR w KQkq - 0 1";
        //String fen = "rk6/p1p5/B4p2/1q2bP2/3N4/2K5/8/1R6 b - - 0 1";
        BoardState boardState = new BoardState(fen, null);
        BitBoard board = new BitBoard(boardState);

        System.out.println("Initial Board:");
        System.out.println(BitBoardOperations.printBitBoard(board));


        // ArrayList<BitBoard> states = board.getMovesForColor(1);
        ArrayList<BitBoard> states = board.getNextStates();
        System.out.println("Moves:");
        System.out.println("size:" + states.size());
        for (BitBoard move : states) {
            System.out.println(BitBoardOperations.printBitBoard(move));
        }

    }

//        // בדיקת המהלכים של המלכים
//        ArrayList<BitBoard> kingMoves = board.getKingsMoves();
//        System.out.println("King Moves:");
//        System.out.println("size:" + kingMoves.size());
//        for (BitBoard move : kingMoves) {
//            System.out.println(BitBoardOperations.printBitBoard(move));
//        }
//
//        // בדיקת המהלכים של המלכות
//        ArrayList<BitBoard> queenMoves = board.getQueensMoves();
//        System.out.println("Queen Moves:");
//        System.out.println("size:" + queenMoves.size());
//        for (BitBoard move : queenMoves) {
//            System.out.println(BitBoardOperations.printBitBoard(move));
//        }
//
//        // בדיקת המהלכים של הצריחים
//        ArrayList<BitBoard> rookMoves = board.getRooksMoves();
//        System.out.println("Rook Moves:");
//        System.out.println("size:" + rookMoves.size());
//        for (BitBoard move : rookMoves) {
//            System.out.println(BitBoardOperations.printBitBoard(move));
//        }
//
//        // בדיקת המהלכים של הרצים
//        ArrayList<BitBoard> bishopMoves = board.getBishopsMoves();
//        System.out.println("Bishop Moves:");
//        System.out.println("size:" + bishopMoves.size());
//        for (BitBoard move : bishopMoves) {
//            System.out.println(BitBoardOperations.printBitBoard(move));
//        }
//
//        // בדיקת המהלכים של הפרשים
//        ArrayList<BitBoard> knightMoves = board.getKnightsMoves();
//        System.out.println("Knight Moves:");
//        System.out.println("size:" + knightMoves.size());
//        for (BitBoard move : knightMoves) {
//            System.out.println(BitBoardOperations.printBitBoard(move));
//        }
//
//        // בדיקת המהלכים של הרגלים
//        ArrayList<BitBoard> pawnMoves = board.getPawnsMoves();
//        System.out.println("Pawn Moves:");
//        System.out.println("size:" + pawnMoves.size());
//        for (BitBoard move : pawnMoves) {
//            System.out.println(BitBoardOperations.printBitBoard(move));
//        }

    //ArrayList<BitBoard> states = board.getMovesForColor(1);

}
