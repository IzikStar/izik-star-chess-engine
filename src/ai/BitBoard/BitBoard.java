package ai.BitBoard;

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
    public boolean sameTeem(long bitBoard) {
        if (isWhiteToMove) {
            return (bitBoard & whitePieces) != 0;
        }
        else {
            return (bitBoard & blackPieces) != 0;
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
        long newState;
        long king = isWhiteToMove ? whiteKings : blackKings;
        if ((king & BoardParts.EIGHTH_RANK) == 0) {
            newState = king << 8;
            if (!sameTeem(newState)) {
                BitBoard newBoard = getNewBoardFromMove(1, newState);
                if (!newBoard.isSelfCheck()) {
                    nextStates.add(newBoard);
                }
            }
        }
        return nextStates;
    }
    private ArrayList<BitBoard> getQueensMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        return nextStates;
    }
    private ArrayList<BitBoard> getRooksMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        return nextStates;
    }
    private ArrayList<BitBoard> getBishopsMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        return nextStates;
    }
    private ArrayList<BitBoard> getKnightsMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        return nextStates;
    }
    private ArrayList<BitBoard> getPawnsMoves() {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        return nextStates;
    }


    // main for debugging
    public static void main(String[] args) {

    }

}
