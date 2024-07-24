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


    // move details:
    public boolean sameTeem(long newPosition, long prevPosition) {
        if (isWhiteToMove) {
            return ((newPosition & ~prevPosition) & whitePieces) != 0;
        }
        else {
            return ((newPosition & ~prevPosition) & blackPieces) != 0;
        }
    }
    private boolean isSelfCheck() {
        return false;
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
            // capture:
            if((newPosition & blackPieces) != 0) {
                nOTWCOPM = 0;
                // -logic to remove the capture piece-
            }

            // pawn:
            if (numOfPiece == 6 /*&& (whitePawns ^ newPosition) != 0*/) {
                nOTWCOPM = 0;
                // pawn 2 square move:
                if(BitOperations.isShiftBy16(whitePawns ^ newPosition)) {
                    // "whitePawns ^ newPosition" is the pawn how moved in the start and the end location, ">>8" move it to first row and third row, then we get only third row.
                    ePT = BoardParts.FIRST_RANK ^ (whitePawns ^ newPosition >> 8);
                }
                wP = newPosition;
            }
            // knight:
            else if (numOfPiece == 5) {wN = newPosition;}
            // bishop:
            else if (numOfPiece == 4) {wB = newPosition;}
            // rook:
            else if (numOfPiece == 3) {
                wR = newPosition;
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
            else if (numOfPiece == 2) {wQ = newPosition;}
            // king:
            else if (numOfPiece == 1) {
                K = false;
                Q = false;
                wK = newPosition;
            }
        }
        else {
            // update name of turn because the black turn just ended:
            nOT++;
            // capture:
            if((newPosition & whitePieces) != 0) {
                nOTWCOPM = 0;
                // -logic to remove the capture piece-
            }

            // pawn:
            if (numOfPiece == 6 /*&& (whitePawns ^ newPosition) != 0*/) {
                nOTWCOPM = 0;
                // pawn 2 square move:
                if(BitOperations.isShiftBy16(blackPawns ^ newPosition)) {
                    // "whitePawns ^ newPosition" is the pawn how moved in the start and the end location, ">>8" move it to first row and third row, then we get only third row.
                    ePT = BoardParts.EIGHTH_RANK ^ (blackPawns ^ newPosition << 8);
                }
                bP = newPosition;
            }
            // knight:
            else if (numOfPiece == 5) {bN = newPosition;}
            // bishop:
            else if (numOfPiece == 4) {bB = newPosition;}
            // rook:
            else if (numOfPiece == 3) {
                // checking if kingSide rook left its origin tile and cancel castling rights for that side:
                if (k && (BoardParts.Tile.H8.position & newPosition) == 0) {
                    k = false;
                }
                // checking if queenSide rook left its origin tile and cancel castling rights for that side:
                else if (q && (BoardParts.Tile.H1.position & newPosition) == 0) {
                    q = false;
                }
                bR = newPosition;
            }
            // queen:
            else if (numOfPiece == 2) {bQ = newPosition;}
            // king:
            else if (numOfPiece == 1) {
                k = false;
                q = false;
                bK = newPosition;
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

    public ArrayList<BitBoard> getNextStates() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        nextStates.addAll(getKingsMoves());
        nextStates.addAll(getQueensMoves());
        nextStates.addAll(getRooksMoves());
        nextStates.addAll(getBishopsMoves());
        nextStates.addAll(getKnightsMoves());
        nextStates.addAll(getPawnsMoves());
        return nextStates;
    }
    // get next state for any piece
    private ArrayList<BitBoard> getKingsMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        int color = isWhiteToMove ? 1 : 0;
        long position = isWhiteToMove ? whiteKings : blackKings;
        BitKing king = new BitKing(color, position);
        for (long move : king.validMovements()) {
            if (!sameTeem(move, position)) {
                BitBoard newBoard = getNewBoardFromMove(1, move);
                if (!newBoard.isSelfCheck() || true) {
                    nextStates.add(newBoard);
                }
            }
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getQueensMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        int color = isWhiteToMove ? 1 : 0;
        long position = isWhiteToMove ? whiteQueens : blackQueens;
        BitQueen queen = new BitQueen(color, position);
        for (long move : queen.validMovements()) {
            if (!sameTeem(move, position)) {
                BitBoard newBoard = getNewBoardFromMove(2, move);
                if (!newBoard.isSelfCheck() || true) {
                    nextStates.add(newBoard);
                }
            }
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getRooksMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        int color = isWhiteToMove ? 1 : 0;
        long position = isWhiteToMove ? whiteRooks : blackRooks;
        BitRook rook = new BitRook(color, position);
        for (long move : rook.validMovements()) {
            if (!sameTeem(move, position)) {
                BitBoard newBoard = getNewBoardFromMove(3, move);
                if (!newBoard.isSelfCheck() || true) {
                    nextStates.add(newBoard);
                }
            }
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getBishopsMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        int color = isWhiteToMove ? 1 : 0;
        long position = isWhiteToMove ? whiteBishops : blackBishops;
        BitBishop bishop = new BitBishop(color, position);
        for (long move : bishop.validMovements()) {
            if (!sameTeem(move, position)) {
                BitBoard newBoard = getNewBoardFromMove(4, move);
                if (!newBoard.isSelfCheck() || true) {
                    nextStates.add(newBoard);
                }
            }
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getKnightsMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        int color = isWhiteToMove ? 1 : 0;
        long position = isWhiteToMove ? whiteKnights : blackKnights;
        BitKnight knight = new BitKnight(color, position);
        for (long move : knight.validMovements()) {
            if (!sameTeem(move, position)) {
                BitBoard newBoard = getNewBoardFromMove(5, move);
                if (!newBoard.isSelfCheck() || true) {
                    nextStates.add(newBoard);
                }
            }
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getPawnsMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        int color = isWhiteToMove ? 1 : 0;
        long position = isWhiteToMove ? whitePawns : blackPawns;
        BitPawn pawn = new BitPawn(color, position);
        for (long move : pawn.validMovements()) {
            if (!sameTeem(move, position)) {
                BitBoard newBoard = getNewBoardFromMove(6, move);
                if (!newBoard.isSelfCheck() || true) {
                    nextStates.add(newBoard);
                }
            }
        }
        return nextStates;
    }

    // main for debugging
    public static void main(String[] args) {
        String fen = "rnbqkbnr/8/8/8/8/8/8/RNBQKBNR w KQkq - 0 1\n";
        BoardState boardState = new BoardState(fen, null);
        BitBoard board = new BitBoard(boardState);

        System.out.println("Initial Board:");
        System.out.println(BitBoardOperations.printBitBoard(board));

        // בדיקת המהלכים של המלכים
        ArrayList<BitBoard> kingMoves = board.getKingsMoves();
        System.out.println("King Moves:");
        System.out.println("size:" + kingMoves.size());
        for (BitBoard move : kingMoves) {
            System.out.println(BitBoardOperations.printBitBoard(move));
        }

        // בדיקת המהלכים של המלכות
        ArrayList<BitBoard> queenMoves = board.getQueensMoves();
        System.out.println("Queen Moves:");
        System.out.println("size:" + queenMoves.size());
        for (BitBoard move : queenMoves) {
            System.out.println(BitBoardOperations.printBitBoard(move));
        }

        // בדיקת המהלכים של הצריחים
        ArrayList<BitBoard> rookMoves = board.getRooksMoves();
        System.out.println("Rook Moves:");
        System.out.println("size:" + rookMoves.size());
        for (BitBoard move : rookMoves) {
            System.out.println(BitBoardOperations.printBitBoard(move));
        }

        // בדיקת המהלכים של הרצים
        ArrayList<BitBoard> bishopMoves = board.getBishopsMoves();
        System.out.println("Bishop Moves:");
        System.out.println("size:" + bishopMoves.size());
        for (BitBoard move : bishopMoves) {
            System.out.println(BitBoardOperations.printBitBoard(move));
        }

        // בדיקת המהלכים של הפרשים
        ArrayList<BitBoard> knightMoves = board.getKnightsMoves();
        System.out.println("Knight Moves:");
        System.out.println("size:" + knightMoves.size());
        for (BitBoard move : knightMoves) {
            System.out.println(BitBoardOperations.printBitBoard(move));
        }

        // בדיקת המהלכים של הרגלים
        ArrayList<BitBoard> pawnMoves = board.getPawnsMoves();
        System.out.println("Pawn Moves:");
        System.out.println("size:" + pawnMoves.size());
        for (BitBoard move : pawnMoves) {
            System.out.println(BitBoardOperations.printBitBoard(move));
        }
    }



}
