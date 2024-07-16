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
    // rows and cols
    public static final long BACK_RANK = 0xFF00000000000000L;      // השורה השמינית (אחורית)
    public static final long FIRST_RANK = 0x00000000000000FFL;     // השורה הראשונה (קדמית)
    public static final long LEFT_FILE = 0x8080808080808080L;      // העמודה השמאלית
    public static final long RIGHT_FILE = 0x0101010101010101L;     // העמודה הימנית
    // חצאי מסגרות של הלוח
    public static final long BACK_LEFT_CORNER = BACK_RANK | LEFT_FILE;  // שורה שמינית ועמודה שמאלית
    public static final long BACK_RIGHT_CORNER = BACK_RANK | RIGHT_FILE; // שורה שמינית ועמודה ימנית
    public static final long FIRST_LEFT_CORNER = FIRST_RANK | LEFT_FILE; // שורה ראשונה ועמודה שמאלית
    public static final long FIRST_RIGHT_CORNER = FIRST_RANK | RIGHT_FILE; // שורה ראשונה ועמודה ימנית
    // diagonals
    public static final long BACK_LEFT_DIAGONAL = 0x8040201008040201L;  // אלכסון אחורי שמאלי (A8-H1)
    public static final long BACK_RIGHT_DIAGONAL = 0x0102040810204080L; // אלכסון אחורי ימני (A1-H8)
    public static final long FIRST_LEFT_DIAGONAL = 0x0102040810204080L;  // אלכסון קדמי שמאלי (A1-H8)
    public static final long FIRST_RIGHT_DIAGONAL = 0x8040201008040201L; // אלכסון קדמי ימני (H1-A8)


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

    // operations:
    // Set a bit at a specific position
    public void setBit(long bitboard, int position) {
        bitboard |= (1L << position);
    }
    // Clear a bit at a specific position
    public void clearBit(long bitboard, int position) {
        bitboard &= ~(1L << position);
    }
    // Toggle a bit at a specific position
    public void toggleBit(long bitboard, int position) {
        bitboard ^= (1L << position);
    }
    // Check if a bit at a specific position is set
    public boolean isBitSet(long bitboard, int position) {
        return (bitboard & (1L << position)) != 0;
    }
    // Method to check if the result has exactly two 1-bits that are 16 positions apart
    public static boolean isShiftBy16(long bitboard) {
        // Check if the number has exactly two 1-bits
        if (Long.bitCount(bitboard) != 2) {
            return false;
        }

        // Check if the number is of the form 1 << x | 1 << (x + 16)
        while (bitboard != 0 && (bitboard & 1) == 0) {
            bitboard >>= 1;
        }

        // Now the least significant bit is set, shift right by 16 and check the next bit
        return (bitboard >> 1) == (1L << 15);
    }
    // Print the bitboard as a binary string
    public void printBitboard(long bitboard) {
        System.out.println(Long.toBinaryString(bitboard));
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
                if(isShiftBy16(whitePawns ^ newPosition)) {
                    // "whitePawns ^ newPosition" is the pawn how moved in the start and the end location, ">>8" move it to first row and third row, then we get only third row.
                    ePT = FIRST_RANK ^ (whitePawns ^ newPosition >> 8);
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
                if (K && (0x1L & newPosition) == 0) {
                    K = false;
                }
                // checking if queenSide rook left its origin tile and cancel castling rights for that side:
                else if (Q && (0x80L & newPosition) == 0) {
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
            // update num of turn because the black turn just ended:
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
                if(isShiftBy16(blackPawns ^ newPosition)) {
                    // "whitePawns ^ newPosition" is the pawn how moved in the start and the end location, ">>8" move it to first row and third row, then we get only third row.
                    ePT = BACK_RANK ^ (blackPawns ^ newPosition << 8);
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
                if (k && (0x0100000000000000L & newPosition) == 0) {
                    k = false;
                }
                // checking if queenSide rook left its origin tile and cancel castling rights for that side:
                else if (q && (0x8000000000000000L & newPosition) == 0) {
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
        if ((king & BACK_RANK) == 0) {
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
        System.out.println("BACK_RANK: " + Long.toBinaryString(BACK_RANK));
        System.out.println("FIRST_RANK: " + Long.toBinaryString(FIRST_RANK));
        System.out.println("LEFT_FILE: " + Long.toBinaryString(LEFT_FILE));
        System.out.println("RIGHT_FILE: " + Long.toBinaryString(RIGHT_FILE));
        System.out.println("BACK_LEFT_CORNER: " + Long.toBinaryString(BACK_LEFT_CORNER));
        System.out.println("BACK_RIGHT_CORNER: " + Long.toBinaryString(BACK_RIGHT_CORNER));
        System.out.println("FIRST_LEFT_CORNER: " + Long.toBinaryString(FIRST_LEFT_CORNER));
        System.out.println("FIRST_RIGHT_CORNER: " + Long.toBinaryString(FIRST_RIGHT_CORNER));
    }

}
