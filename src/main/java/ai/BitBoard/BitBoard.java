package ai.BitBoard;

import ai.BitBoard.BitPiece.*;
import ai.board.Board;
import ai.board.Move;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The standard-chess board: twelve bitboards, one per colour and piece type. Implements {@link Board}
 * so the search, the evaluation interface and the rules reach it only through that interface
 * (Phase 6, R1); Phase 6 R3 replaces it with a board whose pieces are data.
 */
public class BitBoard implements Board {
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
        updateOccupancy();
    }
    public long getQueens(int color) {
        return color == 1 ? whiteQueens : blackQueens;
    }
    public void setRooks(int color, long position) {
        if (color == 1) this.whiteRooks = position;
        else this.blackRooks = position;
        updateOccupancy();
    }
    public long getRooks(int color) {
        return color == 1 ? whiteRooks : blackRooks;
    }
    public void setBishops(int color, long position) {
        if (color == 1) this.whiteBishops = position;
        else this.blackBishops = position;
        updateOccupancy();
    }
    public long getBishops(int color) {
        return color == 1 ? whiteBishops : blackBishops;
    }
    public void setKnights(int color, long position) {
        if (color == 1) this.whiteKnights = position;
        else this.blackKnights = position;
        updateOccupancy();
    }
    public long getKnights(int color) {
        return color == 1 ? whiteKnights : blackKnights;
    }
    public void setPawns(int color, long position) {
        if (color == 1) this.whitePawns = position;
        else this.blackPawns = position;
        updateOccupancy();
    }
    public long getPawns(int color) {
        return color == 1 ? whitePawns : blackPawns;
    }

    // every move and attack is computed from these, so each setter keeps them current
    private void updateOccupancy() {
        whitePieces = whiteKings | whiteQueens | whiteRooks | whiteBishops | whiteKnights | whitePawns;
        blackPieces = blackKings | blackQueens | blackRooks | blackBishops | blackKnights | blackPawns;
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
        int opponent = BitBoardOperations.toggleColor(color);
        for (long king = color == 1 ? whiteKings : blackKings; king != 0; king &= king - 1) {
            if (Attacks.attacked(this, Long.numberOfTrailingZeros(king), opponent)) return true;
        }
        return false;
    }

    // making moves:
    private BitBoard getNewBoardFromMove(int numOfPiece, long newPosition) {
        return getNewBoardFromMove(numOfPiece, newPosition, true);
    }
    // scoreCheck: rate a move that gives check first in the move order (skipped when only legality matters)
    private BitBoard getNewBoardFromMove(int numOfPiece, long newPosition, boolean scoreCheck) {
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
                    // the pawn's start and end squares; the en-passant square is the one between them
                    long moved = whitePawns ^ newPosition;
                    ePT = (Long.numberOfTrailingZeros(moved) + 63 - Long.numberOfLeadingZeros(moved)) / 2;
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
                if (K && (BoardParts.Tile.H1.position & newPosition) == 0) {
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
                    // the pawn's start and end squares; the en-passant square is the one between them
                    long moved = blackPawns ^ newPosition;
                    ePT = (Long.numberOfTrailingZeros(moved) + 63 - Long.numberOfLeadingZeros(moved)) / 2;
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
        // a castling right also ends when its rook is captured on its home square
        K &= (wK & BoardParts.Tile.E1.position) != 0 && (wR & BoardParts.Tile.H1.position) != 0;
        Q &= (wK & BoardParts.Tile.E1.position) != 0 && (wR & BoardParts.Tile.A1.position) != 0;
        k &= (bK & BoardParts.Tile.E8.position) != 0 && (bR & BoardParts.Tile.H8.position) != 0;
        q &= (bK & BoardParts.Tile.E8.position) != 0 && (bR & BoardParts.Tile.A8.position) != 0;
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
        if (scoreCheck && bitBoard.isCheckOn(bitBoard.isWhiteToMove ? 1 : 0)) moveValue = Integer.MAX_VALUE;
        bitBoard.setMoveValue(moveValue);
        return bitBoard;
    }

    // get next states:
    public ArrayList<BitBoard> getNextStates() {
        if (this.nextStates == null) {
            int color = isWhiteToMove ? 1 : 0;
            ArrayList<BitBoard> nextMoves = getMovesForColor(color);
            nextStates = new ArrayList<>();
            for (BitBoard state : nextMoves) {
                if (!state.isCheckOn(color)) nextStates.add(state);
            }
        }
        return nextStates;
    }

    /** Drops the cached children, so a searched subtree can be garbage collected. */
    public void releaseNextStates() {
        nextStates = null;
    }

    /**
     * True when this position follows from {@code parent} by a capture or a promotion: the moves
     * the quiescence search keeps looking at after the main search depth runs out.
     */
    public boolean isNoisyChildOf(BitBoard parent) {
        boolean white = parent.isWhiteToMove;
        long victimsBefore = white ? parent.blackPieces : parent.whitePieces;
        long victimsAfter = white ? blackPieces : whitePieces;
        long pawnsBefore = white ? parent.whitePawns : parent.blackPawns;
        long pawnsAfter = white ? whitePawns : blackPawns;
        return Long.bitCount(victimsAfter) < Long.bitCount(victimsBefore)
                || Long.bitCount(pawnsAfter) < Long.bitCount(pawnsBefore);
    }

    public ArrayList<BitBoard> getSortedNextStates() {
        if (nextStates == null) {
            getNextStates();
        }

        ArrayList<BitBoard> sortedNextStates = new ArrayList<>(nextStates);
        sortedNextStates.sort((board1, board2) -> Integer.compare(board2.getMoveValue(), board1.getMoveValue()));

        return sortedNextStates;
    }


    /**
     * The legal captures and queen promotions of the side to move, most valuable victim first and,
     * among equal victims, cheapest attacker first: the moves the quiescence search plays. A capture
     * of a defended piece by a more valuable one is left out, since it loses material. Built
     * without the other moves, and not cached, so it leaves {@link #getNextStates()} untouched.
     */
    public ArrayList<BitBoard> getNoisyNextStates() {
        int color = isWhiteToMove ? 1 : 0;
        long enemy = color == 1 ? blackPieces : whitePieces;
        long occupied = whitePieces | blackPieces;
        ArrayList<BitBoard> noisy = new ArrayList<>();
        addCaptures(noisy, color, 1, color == 1 ? whiteKings : blackKings, enemy, occupied);
        addCaptures(noisy, color, 2, color == 1 ? whiteQueens : blackQueens, enemy, occupied);
        addCaptures(noisy, color, 3, color == 1 ? whiteRooks : blackRooks, enemy, occupied);
        addCaptures(noisy, color, 4, color == 1 ? whiteBishops : blackBishops, enemy, occupied);
        addCaptures(noisy, color, 5, color == 1 ? whiteKnights : blackKnights, enemy, occupied);

        long pawns = color == 1 ? whitePawns : blackPawns;
        long promotionRow = BoardParts.getPromotionRow(color);
        BitPawn pawn = new BitPawn(color, pawns, whitePieces, blackPieces);
        for (long p = pawns; p != 0; p &= p - 1) {
            int from = Long.numberOfTrailingZeros(p);
            int push = color == 1 ? from - 8 : from + 8; // White moves toward a8 = 0
            long targets = Attacks.PAWN[color][from] & enemy;
            if ((promotionRow & (1L << push) & ~occupied) != 0) {
                targets |= 1L << push;
            }
            for (; targets != 0; targets &= targets - 1) {
                long to = Long.lowestOneBit(targets);
                BitBoard child = getNewBoardFromMove(6, pawns & ~(1L << from) | to, false);
                if ((to & promotionRow) != 0) {
                    child = pawn.getPromotions(child, to).getFirst(); // the queen
                }
                addIfLegal(noisy, child, color, 6);
            }
        }
        int colorIndex = color == 1 ? 1 : -1;
        for (long move : pawn.getEnPassantMoves(enPassantTile)) {
            if (move != 0L) {
                BitBoard child = getNewBoardFromMove(6, move, false);
                int opponent = BitBoardOperations.toggleColor(color);
                child.setPawns(opponent, BitOperations.clearBit(child.getPawns(opponent), enPassantTile + 8 * colorIndex));
                child.setMoveValue(child.getMoveValue() + 10);
                addIfLegal(noisy, child, color, 6);
            }
        }
        noisy.sort((a, b) -> Integer.compare(b.getMoveValue(), a.getMoveValue()));
        return noisy;
    }

    /** The captures of one kind of piece, found in the attack tables rather than the move lists. */
    private void addCaptures(ArrayList<BitBoard> noisy, int color, int numOfPiece, long position,
                             long enemy, long occupied) {
        for (long p = position; p != 0; p &= p - 1) {
            int from = Long.numberOfTrailingZeros(p);
            long targets = enemy & switch (numOfPiece) {
                case 1 -> Attacks.KING[from];
                case 2 -> Attacks.rook(from, occupied) | Attacks.bishop(from, occupied);
                case 3 -> Attacks.rook(from, occupied);
                case 4 -> Attacks.bishop(from, occupied);
                default -> Attacks.KNIGHT[from];
            };
            for (; targets != 0; targets &= targets - 1) {
                int to = Long.numberOfTrailingZeros(targets);
                if (ROUGH_VALUE[numOfPiece] > roughValueOn(to) && Attacks.attacked(this, to, 1 - color)) {
                    continue; // a bigger piece takes a smaller, defended one: it loses material
                }
                long move = position & ~(1L << from) | 1L << to;
                addIfLegal(noisy, getNewBoardFromMove(numOfPiece, move, false), color, numOfPiece);
            }
        }
    }

    /**
     * How the search orders this position among {@code parent}'s children: a capture scores its
     * victim first, then the cheaper attacker first (MVV-LVA), and a queen promotion counts as winning
     * a queen. Quiet moves (and under-promotions) score -1. Uses the same fixed values as the
     * quiescence search, whatever the evaluation's weights.
     */
    public int captureScore(BitBoard parent) {
        long both = lastMove.piece.position & lastMove.newPosition;
        long from = lastMove.piece.position & ~both;
        long to = lastMove.newPosition & ~both;
        if (from == 0 || to == 0) {
            return -1;
        }
        long enemy = parent.isWhiteToMove ? parent.blackPieces : parent.whitePieces;
        int toSquare = Long.numberOfTrailingZeros(to);
        int attacker = parent.roughValueOn(Long.numberOfTrailingZeros(from));
        int victim = 0;
        if ((enemy & to) != 0) {
            victim = parent.roughValueOn(toSquare);
        } else if (attacker == ROUGH_VALUE[6] && toSquare == parent.enPassantTile
                && ((parent.whitePawns | parent.blackPawns) & from) != 0) {
            victim = ROUGH_VALUE[6];
        }
        if (attacker == ROUGH_VALUE[6] && (to & (BoardParts.getPromotionRow(0) | BoardParts.getPromotionRow(1))) != 0
                && Character.toLowerCase(lastMove.promotionChoice) == 'q') {
            victim += ROUGH_VALUE[2];
        }
        if (victim == 0) {
            return -1;
        }
        return victim * 16 + 15 - Math.min(attacker, 10); // the king (1000) attacks last
    }

    /**
     * Textbook piece values (king 1 … pawn 6), only for skipping captures that lose material. They
     * are fixed rather than the evaluation's weights, so the search prunes the same way whatever
     * weights it is given.
     */
    private static final int[] ROUGH_VALUE = {0, 1000, 9, 5, 3, 3, 1};

    private int roughValueOn(int square) {
        long b = 1L << square;
        if (((whitePawns | blackPawns) & b) != 0) return ROUGH_VALUE[6];
        if (((whiteKnights | blackKnights) & b) != 0) return ROUGH_VALUE[5];
        if (((whiteBishops | blackBishops) & b) != 0) return ROUGH_VALUE[4];
        if (((whiteRooks | blackRooks) & b) != 0) return ROUGH_VALUE[3];
        if (((whiteQueens | blackQueens) & b) != 0) return ROUGH_VALUE[2];
        return ROUGH_VALUE[1];
    }

    /** Keeps a legal child, its move value turned into victim first, then cheapest attacker. */
    private static void addIfLegal(ArrayList<BitBoard> noisy, BitBoard child, int color, int numOfPiece) {
        if (!child.isCheckOn(color)) {
            child.setMoveValue(child.getMoveValue() * 8 + numOfPiece); // pieces are numbered king 1 … pawn 6
            noisy.add(child);
        }
    }

    // get next moves:
    public ArrayList<BitBoard> getMovesForColor(int color) {
        ArrayList<BitBoard> nextStates = new ArrayList<>(getKingsMoves(color));
        nextStates.addAll(getQueensMoves(color));
        nextStates.addAll(getRooksMoves(color));
        nextStates.addAll(getBishopsMoves(color));
        nextStates.addAll(getKnightsMoves(color));
        nextStates.addAll(getPawnsMoves(color));
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
                castles.add(board);
            }
            if (canWhiteCastleQueenSide && ((whitePieces | blackPieces) & BoardParts.WHITE_QUEEN_SIDE_CASTLE) == 0 && (((king | BoardParts.WHITE_QUEEN_SIDE_CASTLE_PATH) & getAllAttackedTiles(opponentColor)) == 0 && (rooks & BoardParts.Tile.A1.position) != 0)) {
                long kingNewPosition = BoardParts.Tile.C1.position;
                long rooksNewPosition = ((rooks | BoardParts.Tile.D1.position) & ~BoardParts.Tile.A1.position);
                BitBoard board = getNewBoardFromMove(1, kingNewPosition);
                board.setRooks(color, rooksNewPosition);
                board.hasWhiteCastled = true;
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
                castles.add(board);
            }
            if (canBlackCastleQueenSide && ((whitePieces | blackPieces) & BoardParts.BLACK_QUEEN_SIDE_CASTLE) == 0 && (((king | BoardParts.BLACK_QUEEN_SIDE_CASTLE_PATH) & getAllAttackedTiles(opponentColor)) == 0 && (rooks & BoardParts.Tile.A8.position) != 0)) {
                long kingNewPosition = BoardParts.Tile.C8.position;
                long rooksNewPosition = ((rooks | BoardParts.Tile.D8.position) & ~BoardParts.Tile.A8.position);
                BitBoard board = getNewBoardFromMove(1, kingNewPosition);
                board.setRooks(color, rooksNewPosition);
                board.hasBlackCastled = true;
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
        return nextStates;
    }
    // get all attacked tiles:
    public long getAllAttackedTiles(int color) {
        return Attacks.all(this, color);
    }

    /** Package-visible for the Phase 2 {@code rules} adapter: is the side to move in check? */
    public boolean isSideToMoveInCheck() {
        return isCheckOn(isWhiteToMove ? 1 : 0);
    }

    /**
     * Whether the side to move has a legal move: the same answer as {@code !getNextStates().isEmpty()},
     * but it stops at the first legal move and keeps nothing, so a leaf of the search no longer builds
     * all its children. Castling is not tried: whenever castling is legal, so is the king's step
     * to f1/d1 (f8/d8).
     */
    public boolean hasLegalMove() {
        if (nextStates != null) return !nextStates.isEmpty();
        int color = isWhiteToMove ? 1 : 0;
        long kings = color == 1 ? whiteKings : blackKings;
        for (long move : new BitKing(color, kings, whitePieces, blackPieces).validMovements()) {
            if (!sameTeem(move, kings) && !getNewBoardFromMove(1, move, false).isCheckOn(color)) return true;
        }
        long knights = color == 1 ? whiteKnights : blackKnights;
        for (long move : new BitKnight(color, knights, whitePieces, blackPieces).validMovements()) {
            if (!getNewBoardFromMove(5, move, false).isCheckOn(color)) return true;
        }
        long bishops = color == 1 ? whiteBishops : blackBishops;
        for (long move : new BitBishop(color, bishops, whitePieces, blackPieces).validMovements()) {
            if (!getNewBoardFromMove(4, move, false).isCheckOn(color)) return true;
        }
        long rooks = color == 1 ? whiteRooks : blackRooks;
        for (long move : new BitRook(color, rooks, whitePieces, blackPieces).validMovements()) {
            if (!getNewBoardFromMove(3, move, false).isCheckOn(color)) return true;
        }
        long queens = color == 1 ? whiteQueens : blackQueens;
        for (long move : new BitQueen(color, queens, whitePieces, blackPieces).validMovements()) {
            if (!getNewBoardFromMove(2, move, false).isCheckOn(color)) return true;
        }
        long pawns = color == 1 ? whitePawns : blackPawns;
        BitPawn pawn = new BitPawn(color, pawns, whitePieces, blackPieces);
        for (long move : pawn.validMovements()) {
            if (!getNewBoardFromMove(6, move, false).isCheckOn(color)) return true;
        }
        int opponentColor = BitBoardOperations.toggleColor(color);
        int colorIndex = color == 1 ? 1 : -1;
        for (long move : pawn.getEnPassantMoves(enPassantTile)) {
            if (move != 0L) {
                BitBoard child = getNewBoardFromMove(6, move, false);
                child.setPawns(opponentColor, BitOperations.clearBit(child.getPawns(opponentColor), enPassantTile + 8 * colorIndex));
                if (!child.isCheckOn(color)) return true;
            }
        }
        return false;
    }

    public int getStatus() {
        if (!hasLegalMove()) {
            if (isCheckOn(0)) return Integer.MIN_VALUE;
            if (isCheckOn(1)) return Integer.MAX_VALUE;
            // System.out.println("staleMate!!!!!!!!!!!");
            return 0;
        }
        // 50-move rule: 50 full moves without a capture or pawn move. The counter is
        // incremented once per ply (getNewBoardFromMove), so the threshold is 100, not 50.
        if (numOfTurnsWithoutCaptureOrPawnMove >= 100) return 0;
        return 1;
    }

    public boolean getIsWhiteToMove() {
        return isWhiteToMove;
    }

    // ---- Board ------------------------------------------------------------------------------

    /** {@link #lastMove()} once computed; {@link Move#NONE} until then. */
    private int lastMoveCode = Move.NONE;

    @Override
    public int sideToMove() {
        return isWhiteToMove ? 0 : 1;
    }

    @Override
    public List<BitBoard> children() {
        return Collections.unmodifiableList(getNextStates());
    }

    @Override
    public List<Board> orderedChildren() {
        ArrayList<Board> sorted = new ArrayList<>(getNextStates());
        sorted.sort((a, b) -> Integer.compare(((BitBoard) b).moveValue, ((BitBoard) a).moveValue));
        return sorted;
    }

    @Override
    public List<Board> noisyChildren() {
        return new ArrayList<>(getNoisyNextStates());
    }

    @Override
    public void releaseChildren() {
        releaseNextStates();
    }

    /**
     * The move that made this position: the squares the moving piece left and reached (for castling,
     * the king's), and the promotion piece when a pawn reached the last rank.
     */
    @Override
    public int lastMove() {
        if (lastMoveCode == Move.NONE && lastMove != null) {
            long both = lastMove.piece.position & lastMove.newPosition;
            int from = Long.numberOfTrailingZeros(lastMove.piece.position & ~both) & 63;
            int to = Long.numberOfTrailingZeros(lastMove.newPosition & ~both) & 63;
            boolean promotes = lastMove.piece.name == 6 && (to >>> 3) == (lastMove.piece.color == 1 ? 0 : 7);
            lastMoveCode = Move.of(from, to, promotes ? lastMove.promotionChoice : 0);
        }
        return lastMoveCode;
    }

    @Override
    public int captureScore(Board parent) {
        return captureScore((BitBoard) parent);
    }

    @Override
    public boolean inCheck() {
        return isSideToMoveInCheck();
    }

    @Override
    public boolean isOver() {
        return getStatus() != 1;
    }

    @Override
    public long repetitionKey() {
        return ZobristHashing.computeHash(this);
    }

    @Override
    public long searchKey() {
        return ZobristHashing.searchKey(this);
    }

    @Override
    public String toFen() {
        return BitBoardRules.toFen(this);
    }

    @Override
    public String toString() {
        return BitBoardOperations.printBitBoard(this);
    }

}
