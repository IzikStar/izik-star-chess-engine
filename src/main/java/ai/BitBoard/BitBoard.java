package ai.BitBoard;

import ai.BitBoard.BitPiece.*;
import ai.BoardState;
import ai.Minimax;
import main.Move;
import pieces.*;
import main.Debug;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;

public class BitBoard {
    // state arguments
    protected long whiteKings = 0x0, whiteQueens = 0x0, whiteRooks = 0x0, whiteBishops = 0x0, whiteKnights = 0x0, whitePawns = 0x0;
    protected long blackKings = 0x0, blackQueens = 0x0, blackRooks = 0x0, blackBishops = 0x0, blackKnights = 0x0, blackPawns = 0x0;
    protected long whitePieces, blackPieces;
    protected int enPassantTile;
    protected boolean canWhiteCastleKingSide, canWhiteCastleQueenSide, canBlackCastleKingSide, canBlackCastleQueenSide;
    public boolean hasWhiteCastled = false;
    public boolean hasBlackCastled = false;
    protected boolean isWhiteToMove;
    protected int numOfTurns, numOfTurnsWithoutCaptureOrPawnMove;
    protected ArrayList<BitBoard> nextStates;
    public BitMove lastMove;

    private int moveValue = 0; // ערך ברירת מחדל הוא 0

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
        canWhiteCastleKingSide = boardState.canWhiteCastleKingSide;
        canWhiteCastleQueenSide = boardState.canWhiteCastleQueenSide;
        canBlackCastleKingSide = boardState.canBlackCastleKingSide;
        canBlackCastleQueenSide = boardState.canBlackCastleQueenSide;
        if (boardState.getLastMove() != null) {
            long prevPosition = switch (boardState.getLastMove().piece.type) {
                case 'K' -> whiteKings;
                case 'Q' -> whiteQueens;
                case 'R' -> whiteRooks;
                case 'B' -> whiteBishops;
                case 'N' -> whiteKnights;
                case 'P' -> whitePawns;
                case 'k' -> blackKings;
                case 'q' -> blackQueens;
                case 'r' -> blackRooks;
                case 'b' -> blackBishops;
                case 'n' -> blackKnights;
                case 'p' -> blackPawns;
                default -> 0L;
            };
            this.lastMove = new BitMove(prevPosition, boardState.getLastMove(), boardState.fromC, boardState.fromR, boardState.isLastMoveCastling, boardState.isLastMovePawn);
        }
    }
    public BitBoard(long whiteKings, long whiteQueens, long whiteRooks, long whiteBishops, long whiteKnights, long whitePawns,
                    long blackKings, long blackQueens, long blackRooks, long blackBishops, long blackKnights, long blackPawns,
                    boolean isWhiteToMove,
                    boolean canWhiteCastleKingSide, boolean canWhiteCastleQueenSide,
                    boolean canBlackCastleKingSide, boolean canBlackCastleQueenSide,
                    boolean hasWhiteCastled, boolean hasBlackCastled,
                    int enPassantTile,
                    int numOfTurns, int numOfTurnsWithoutCaptureOrPawnMove, BitMove lastMove) {
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
        this.hasWhiteCastled = hasWhiteCastled;
        this.hasBlackCastled = hasBlackCastled;
        this.lastMove = lastMove;
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
        this.hasWhiteCastled = board.hasWhiteCastled;
        this.hasBlackCastled = board.hasBlackCastled;
        this.lastMove = new BitMove(board.lastMove);
        if (board.nextStates != null) {
            this.nextStates = new ArrayList<>();
            this.nextStates.addAll(board.nextStates);
        }
    }


    public int getMoveValue() {
        return moveValue;
    }
    public void setMoveValue(int moveValue) {
        this.moveValue = moveValue;
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

    public void setPromotionChoice(char promotionChoice) {
        lastMove.setPromotionChoice(promotionChoice);
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
        // Debug.log("Attacked tiles: " + BitOperations.printBitboard(attackedTiles));
        long king = color == 1 ? whiteKings : blackKings;
        return (king & attackedTiles) != 0;
    }

    // making moves:
    private BitBoard getNewBoardFromMove(int numOfPiece, long newPosition) {
        long wK = whiteKings, wQ = whiteQueens, wR = whiteRooks, wB = whiteBishops, wN = whiteKnights, wP = whitePawns;
        long bK = blackKings, bQ = blackQueens, bR = blackRooks, bB = blackBishops, bN = blackKnights, bP = blackPawns;
        int ePT = -1;
        boolean K = canWhiteCastleKingSide, Q = canWhiteCastleQueenSide, k = canBlackCastleKingSide, q = canBlackCastleQueenSide;
        boolean WTM = !isWhiteToMove;
        int nOT = numOfTurns, nOTWCOPM = numOfTurnsWithoutCaptureOrPawnMove + 1;
        BitMove lastMove;
        long lastToMove = 0;
        BitPiece lastPieceToMove;
        int moveValue = 0;

        if (isWhiteToMove) {
            long target;
            // pawn:
            if (numOfPiece == 6) {
                nOTWCOPM = 0;
                // pawn 2 square move:
                if(BitOperations.isShiftBy16(whitePawns ^ newPosition)) {
                    // "whitePawns ^ newPosition" is the pawn how moved in the start and the end location, ">>8" move it to first row and third row, then we get only third row.
                    ePT = BitOperations.getColIndexFromBit(BoardParts.FIRST_RANK ^ (whitePawns ^ newPosition >>> 8));
                }
                lastToMove = whitePawns;
                wP = newPosition;
                target = wP & ~whitePawns;
            }
            // knight:
            else if (numOfPiece == 5) {
                lastToMove = whiteKnights;
                wN = newPosition;
                target = wN & ~whiteKnights;
            }
            // bishop:
            else if (numOfPiece == 4) {
                lastToMove = whiteBishops;
                wB = newPosition;
                target = wB & ~whiteBishops;
            }
            // rook:
            else if (numOfPiece == 3) {
                lastToMove = whiteRooks;
                wR = newPosition;
                target = wR & ~whiteRooks;
                // checking if kingSide rook left its origin tile and cancel castling rights for that side:
                if (K && (BoardParts.Tile.A8.position & newPosition) == 0) {
                    K = false;
                    moveValue -= 7;
                }
                // checking if queenSide rook left its origin tile and cancel castling rights for that side:
                else if (Q && (BoardParts.Tile.A1.position & newPosition) == 0) {
                    Q = false;
                    moveValue -= 5;
                }
            }
            // queen:
            else if (numOfPiece == 2) {
                lastToMove = whiteQueens;
                wQ = newPosition;
                target = wQ & ~whiteQueens;
            }
            // king:
            else if (numOfPiece == 1) {
                lastToMove = whiteKings;
                K = false;
                Q = false;
                moveValue -= 12;
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
                if (bK != blackKings) moveValue = Integer.MAX_VALUE;
                if (bQ != blackQueens) moveValue += 90;
                if (bR != blackRooks) moveValue += 50;
                if (bB != blackBishops) moveValue += 33;
                if (bN != blackKnights) moveValue += 30;
                if (bP != blackPawns) moveValue += 10;
            }
        }
        else {
            // update name of turn because the black turn just ended:
            nOT++;
            long target;
            // pawn:
            if (numOfPiece == 6) {
                nOTWCOPM = 0;
                // pawn 2 square move:
                if(BitOperations.isShiftBy16(blackPawns ^ newPosition)) {
                    // "blackPawns ^ newPosition" is the pawn how moved in the start and the end location, ">>8" move it to first row and third row, then we get only third row.
                    ePT = BitOperations.getColIndexFromBit(BoardParts.EIGHTH_RANK ^ (blackPawns ^ newPosition << 8));
                }
                lastToMove = blackPawns;
                bP = newPosition;
                target = bP & ~blackPawns;
            }
            // knight:
            else if (numOfPiece == 5) {
                lastToMove = blackKnights;
                bN = newPosition;
                target = bN & ~blackKnights;
            }
            // bishop:
            else if (numOfPiece == 4) {
                lastToMove = blackBishops;
                bB = newPosition;
                target = bB & ~blackBishops;
            }
            // rook:
            else if (numOfPiece == 3) {
                lastToMove = blackRooks;
                bR = newPosition;
                target = bR & ~blackRooks;
                // checking if kingSide rook left its origin tile and cancel castling rights for that side:
                if (k && (BoardParts.Tile.H8.position & newPosition) == 0) {
                    k = false;
                    moveValue -= 7;
                }
                // checking if queenSide rook left its origin tile and cancel castling rights for that side:
                else if (q && (BoardParts.Tile.A8.position & newPosition) == 0) {
                    q = false;
                    moveValue -= 5;
                }
            }
            // queen:
            else if (numOfPiece == 2) {
                lastToMove = blackQueens;
                bQ = newPosition;
                target = bQ & ~blackQueens;
            }
            // king:
            else if (numOfPiece == 1) {
                lastToMove = blackKings;
                k = false;
                q = false;
                moveValue -= 12;
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
                if (wK != whiteKings) moveValue = Integer.MAX_VALUE;
                if (wQ != whiteQueens) moveValue += 90;
                if (wR != whiteRooks) moveValue += 50;
                if (wB != whiteBishops) moveValue += 33;
                if (wN != whiteKnights) moveValue += 30;
                if (wP != whitePawns) moveValue += 10;
            }
        }
        lastPieceToMove = switch (numOfPiece) {
            case 1 -> new BitKing(isWhiteToMove ? 1 : 0, lastToMove, 0L, 0L);
            case 2 -> new BitQueen(isWhiteToMove ? 1 : 0, lastToMove, 0L, 0L);
            case 3 -> new BitRook(isWhiteToMove ? 1 : 0, lastToMove, 0L, 0L);
            case 4 -> new BitBishop(isWhiteToMove ? 1 : 0, lastToMove, 0L, 0L);
            case 5 -> new BitKnight(isWhiteToMove ? 1 : 0, lastToMove, 0L, 0L);
            case 6 -> new BitPawn(isWhiteToMove ? 1 : 0, lastToMove, 0L, 0L);
            default -> null;
        };
        BitBoard bitBoard = new BitBoard(
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
                hasWhiteCastled, // has white castled
                hasBlackCastled, // has black castled
                ePT,       // enPassantTile
                nOT,   // numOfTurns
                nOTWCOPM,   // numOfTurnsWithoutCaptureOrPawnMove
                new BitMove(lastPieceToMove, newPosition)
        );
        if (bitBoard.isCheckOn(bitBoard.isWhiteToMove ? 1 : 0)) moveValue = Integer.MAX_VALUE;
        bitBoard.setMoveValue(moveValue);
        return bitBoard;
    }

    // get next states:
    public ArrayList<BitBoard> getNextStates() {
        if (this.nextStates == null) {
            int color = isWhiteToMove ? 1 : 0;
            ArrayList<BitBoard> nextMoves = getMovesForColor(color);
            nextStates = new ArrayList<>();
            int counter = 0;
            for (BitBoard state : nextMoves) {
                if (state.isCheckOn(color)) counter++;
                else nextStates.add(state);
            }
            Debug.log("move that causes check: " + counter);
        }
        return nextStates;
    }

    public ArrayList<BitBoard> getSortedNextStates() {
        if (nextStates == null) {
            getNextStates();
        }

        ArrayList<BitBoard> sortedNextStates = new ArrayList<>(nextStates);
        sortedNextStates.sort((board1, board2) -> Integer.compare(board2.getMoveValue(), board1.getMoveValue()));

        return sortedNextStates;
    }


    // get next moves:
    public ArrayList<BitBoard> getMovesForColor(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>(getKingsMoves(color));
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
        for (BitBoard board : nextStates) {
            if (BitBoardOperations.printBitBoard(board).equals("\n[ r n b q k b n r ]\n" +
                    "[ - p - p - p p p ]\n" +
                    "[ - - p - - - - - ]\n" +
                    "[ p - - - p - - - ]\n" +
                    "[ - - B - P - - - ]\n" +
                    "[ - - - P - - - - ]\n" +
                    "[ - P P - - P P P ]\n" +
                    "[ R N B Q K - N R ]\n")) {
                System.out.println("found the problem! its move " + nextStates.indexOf(board) + ", and it is a " + board.lastMove);
            }
        }
        return nextStates;
    }
    // pieces moves:
    private ArrayList<BitBoard> getKingsMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteKings : blackKings;
        BitKing king = new BitKing(color, position, whitePieces, blackPieces);
        ArrayList<Long> kingMoves = king.validMovements();
        ArrayList<BitBoard> castles = getCastles(color);
        for (long move : kingMoves) {
            if (!sameTeem(move, position)) {
                BitBoard newBoard = getNewBoardFromMove(1, move);
                nextStates.add(newBoard);
            }
        }
        nextStates.addAll(castles);
        Debug.log("king moves: " + kingMoves.size());
        Debug.log("castle moves: " + castles.size());
        return nextStates;
    }
    private ArrayList<BitBoard> getCastles(int color) {
        ArrayList<BitBoard> castles = new ArrayList<>();
        long king = color == 1 ? whiteKings : blackKings;
        long rooks = color == 1 ? whiteRooks : blackRooks;
        int opponentColor = BitBoardOperations.toggleColor(color);
        if (color == 1) {
            if (canWhiteCastleKingSide && ((whitePieces | blackPieces) & BoardParts.WHITE_KING_SIDE_CASTLE) == 0 && (((king | BoardParts.WHITE_KING_SIDE_CASTLE) & getAllAttackedTiles(opponentColor)) == 0 && (rooks & BoardParts.Tile.H1.position) != 0)) {
                long kingNewPosition = BoardParts.Tile.G1.position;
                long rooksNewPosition = ((rooks | BoardParts.Tile.F1.position) & ~BoardParts.Tile.H1.position);
                BitBoard board = getNewBoardFromMove(1, kingNewPosition);
                board.setRooks(color, rooksNewPosition);
                board.hasWhiteCastled = true;
                Debug.log("white king side castle!");
                castles.add(board);
            }
            if (canWhiteCastleQueenSide && ((whitePieces | blackPieces) & BoardParts.WHITE_QUEEN_SIDE_CASTLE) == 0 && (((king | BoardParts.WHITE_QUEEN_SIDE_CASTLE) & getAllAttackedTiles(opponentColor)) == 0 && (rooks & BoardParts.Tile.A1.position) != 0)) {
                long kingNewPosition = BoardParts.Tile.C1.position;
                long rooksNewPosition = ((rooks | BoardParts.Tile.D1.position) & ~BoardParts.Tile.A1.position);
                BitBoard board = getNewBoardFromMove(1, kingNewPosition);
                board.setRooks(color, rooksNewPosition);
                board.hasWhiteCastled = true;
                Debug.log("white queen side castle!");
                castles.add(board);
            }
        }
        else {
            if (canBlackCastleKingSide && ((whitePieces | blackPieces) & BoardParts.BLACK_KING_SIDE_CASTLE) == 0 && (((king | BoardParts.BLACK_KING_SIDE_CASTLE) & getAllAttackedTiles(opponentColor)) == 0 && (rooks & BoardParts.Tile.H8.position) != 0)) {
                long kingNewPosition = BoardParts.Tile.G8.position;
                long rooksNewPosition = ((rooks | BoardParts.Tile.F8.position) & ~BoardParts.Tile.H8.position);
                BitBoard board = getNewBoardFromMove(1, kingNewPosition);
                board.setRooks(color, rooksNewPosition);
                board.hasBlackCastled = true;
                Debug.log("black king side castle!");
                castles.add(board);
            }
            if (canBlackCastleQueenSide && ((whitePieces | blackPieces) & BoardParts.BLACK_QUEEN_SIDE_CASTLE) == 0 && (((king | BoardParts.BLACK_QUEEN_SIDE_CASTLE) & getAllAttackedTiles(opponentColor)) == 0 && (rooks & BoardParts.Tile.A8.position) != 0)) {
                long kingNewPosition = BoardParts.Tile.C8.position;
                long rooksNewPosition = ((rooks | BoardParts.Tile.D8.position) & ~BoardParts.Tile.A8.position);
                BitBoard board = getNewBoardFromMove(1, kingNewPosition);
                board.setRooks(color, rooksNewPosition);
                board.hasBlackCastled = true;
                Debug.log("black queen side castle!");
                castles.add(board);
            }
        }
        return castles;
    }
    private ArrayList<BitBoard> getQueensMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteQueens : blackQueens;
        BitQueen queen = new BitQueen(color, position, whitePieces, blackPieces);
        ArrayList<Long> moves = queen.validMovements();
        for (long move : moves) {
            BitBoard newBoard = getNewBoardFromMove(2, move);
            nextStates.add(newBoard);
        }
        Debug.log("queen moves: " + moves.size());
        return nextStates;
    }
    private ArrayList<BitBoard> getRooksMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteRooks : blackRooks;
        BitRook rook = new BitRook(color, position, whitePieces, blackPieces);
        ArrayList<Long> moves = rook.validMovements();
        for (long move : moves) {
            BitBoard newBoard = getNewBoardFromMove(3, move);
            nextStates.add(newBoard);
        }
        Debug.log("rook moves: " + moves.size());
        return nextStates;
    }
    private ArrayList<BitBoard> getBishopsMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteBishops : blackBishops;
        BitBishop bishop = new BitBishop(color, position, whitePieces, blackPieces);
        ArrayList<Long> moves = bishop.validMovements();
        for (long move : moves) {
            BitBoard newBoard = getNewBoardFromMove(4, move);
            nextStates.add(newBoard);
        }
        Debug.log("bishop moves: " + moves.size());
        return nextStates;
    }
    private ArrayList<BitBoard> getKnightsMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whiteKnights : blackKnights;
        BitKnight knight = new BitKnight(color, position, whitePieces, blackPieces);
        ArrayList<Long> moves = knight.validMovements();
        for (long move : moves) {
            BitBoard newBoard = getNewBoardFromMove(5, move);
            nextStates.add(newBoard);
        }
        Debug.log("knight moves: " + moves.size());
        return nextStates;
    }
    private ArrayList<BitBoard> getPawnsMoves(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>();
        long position = color == 1 ? whitePawns : blackPawns;
        int opponentColor = BitBoardOperations.toggleColor(color);
        int colorIndex = color == 1 ? 1 : -1;
        BitPawn pawn = new BitPawn(color, position, whitePieces, blackPieces);
        ArrayList<Long> moves = pawn.validMovements();
        long[] enPassantMoves = pawn.getEnPassantMoves(enPassantTile);
        int EnPassantCounter = 0;//, promotionCounter = 0;
        for (long move : moves) {
            BitBoard newBoard = getNewBoardFromMove(6, move);
            long promotionTile = (move & BoardParts.getPromotionRow(color));
            if (promotionTile != 0) {
                // promotionCounter++;
                nextStates.addAll(pawn.getPromotions(newBoard, promotionTile));
            }
            else nextStates.add(newBoard);
        }
        for (long move : enPassantMoves) {
            if (move != 0L) {
                BitBoard newBoard = getNewBoardFromMove(6, move);
                newBoard.setPawns(opponentColor, BitOperations.clearBit(newBoard.getPawns(opponentColor), enPassantTile + 8 * colorIndex));
                nextStates.add(newBoard);
            }
        }
        Debug.log("pawn moves: " + moves.size());
        //Debug.log("promotion possibilities: " + promotionCounter * 4);
        Debug.log("En Passant moves: ");
        for (long enPassantMove : enPassantMoves) {
            if (enPassantMove != 0) EnPassantCounter++;
        }
        Debug.log(EnPassantCounter + "");
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

    public int getPieceAt(int square) {
        long tile = BitOperations.setBit(0L, square);
        if ((tile & (whiteKings | blackKings)) != 0) return 1;
        if ((tile & (whiteQueens | blackQueens)) != 0) return 2;
        if ((tile & (whiteRooks | blackRooks)) != 0) return 3;
        if ((tile & (whiteBishops | blackBishops)) != 0) return 4;
        if ((tile & (whiteKnights | blackKnights)) != 0) return 5;
        if ((tile & (whitePawns | blackPawns)) != 0) return 0;
        return 0;
    }

    public int getColorAt(int square) {
        long tile = BitOperations.setBit(0L, square);
        if ((tile & whitePieces) != 0) return 1;
        // if ((tile & blackPieces) != 0) return 0;
        return 0;
    }

    public int getStatus() {
        if (nextStates == null) getNextStates();
        if (nextStates.isEmpty()) {
            if (isCheckOn(0)) return Integer.MIN_VALUE;
            if (isCheckOn(1)) return Integer.MAX_VALUE;
            // System.out.println("staleMate!!!!!!!!!!!");
            return 0;
        }
        if (numOfTurnsWithoutCaptureOrPawnMove >= 50) return 0;
        return 1;
    }

    public BitMove getRandomPossibleMove() {
        BitBoard state = getSortedNextStates().getFirst();
        return state.lastMove;
    }

    public boolean getIsWhiteToMove() {
        return isWhiteToMove;
    }

    @Override
    public String toString() {
        return BitBoardOperations.printBitBoard(this);
    }

    // main for debugging
    public static void main(String[] args) {
        //Debug.debugging = true;
        // String fen = "rnbqkbn1/8/8/8/6r1/p5pP/8/RNBQK2R w KQkq - 0 1";
        //String fen = "rnbqkb1r/pppppppp/5n2/8/8/5N2/PPPPPPPP/RNBQKB1R b KQkq - 0 1";
        String prev = "rnbqkbnr/1p1p1ppp/2p5/p3p3/2B1P3/3P4/PPP2PPP/RNBQK1NR w qkQK a6 0 3";
        String fen = "rnbqkbnr/1p1p1ppp/2p5/p3p3/P1B1P3/3P4/1PP2PPP/RNBQK1NR b qkQK a3 0 4";
        // String fen = "r3k2r/8/8/8/8/8/8/R3K2R b KQkq - 0 1";
        //String fen = "rk6/p1p5/B4p2/1q2bP2/3N4/2K5/8/1R6 b - - 0 1";
        //String fen = "1k4r1/7P/8/8/8/8/8/7K w - - 0 1";
        BoardState tempState = new BoardState(prev, null);
        BoardState boardState = new BoardState(fen, new Move(tempState, tempState.getPiece(0,6), 0, 4));
        BitBoard board = new BitBoard(boardState);
        long timeElapsed;
        Instant start, end;
        start = Instant.now(); // התחלת מדידת זמן

        System.out.println("Initial Board:");
        System.out.println(BitBoardOperations.printBitBoard(board));


        // ArrayList<BitBoard> states = board.getMovesForColor(1);
        ArrayList<BitBoard> states = board.getNextStates();
        System.out.println("Moves:");
        System.out.println("size:" + states.size());
        for (BitBoard move : states) {
            System.out.println(BitBoardOperations.printBitBoard(move));
        }
        // checking minimax:
        Minimax.maxDepth = 4;
        System.out.println(Minimax.getBestMove(boardState));
        end = Instant.now(); // סיום מדידת זמן
        timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
        System.out.println("time spend: " + timeElapsed);
    }

}
