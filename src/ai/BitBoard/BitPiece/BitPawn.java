package ai.BitBoard.BitPiece;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitOperations;
import ai.BitBoard.BoardParts;

import java.util.ArrayList;


public class BitPawn extends BitPiece {

    public int colorIndex;
    public BitPawn(int color, long position, long wPosition, long bPosition) {
        super(color, position, wPosition, bPosition);
        this.name = 6;
        this.colorIndex = color == 1 ? -1 : 1;
    }

    @Override
    public String toString() {
        return (color == 1 ? "white" : "black") + " pawn";
    }

    @Override
    public ArrayList<Long> validMovements() {
        ArrayList<Long> movements = new ArrayList<>();
        // for every pawn in the position:
        for (int i = 0; i < BoardParts.NUM_OF_TILES; i++) {
            if (BitOperations.isBitSet(position, i)) {
                long iTile = 0;
                long otherSetTiles = position;
                iTile = BitOperations.setBit(iTile, i);
                otherSetTiles = BitOperations.clearBit(otherSetTiles, i);

                if (isUpMovePossible(iTile, i)) {
                    long upMove = upMove(i);
                    movements.add(otherSetTiles | upMove);
                    if ((iTile & BoardParts.getPawnsStartingPosition(color)) != 0) {
                        long up2move = upMove(i + 8 * colorIndex);
                        if (isUpMovePossible(upMove, i + 8 * colorIndex)) movements.add(otherSetTiles | up2move);
                    }
                }
                if (isLeftCapturePossible(iTile)) {
                    long leftCapture = leftCapture(i);
                    if (isCapturing(leftCapture)) {
                        movements.add(otherSetTiles | leftCapture);
                    }
                }
                if (isRightCapturePossible(iTile)) {
                    long rightCapture = rightCapture(i);
                    if (isCapturing(rightCapture)) {
                        movements.add(otherSetTiles | rightCapture);
                    }
                }
            }
        }
        return movements;
    }

    public long[] getEnPassantMoves(int enPassantIndex) {
        long[] movements = new long[2];
        if (enPassantIndex != -1) {
            long enPassantToTheRight = getRightEnPassantTile(enPassantIndex) & position;
            long enPassantToTheLeft = getLeftEnPassantTile(enPassantIndex) & position;
            if (enPassantToTheLeft != 0) {
                movements[0] = position | enPassantToTheLeft;
                movements[0] = BitOperations.clearBit(movements[0], enPassantIndex);
            }
            if (enPassantToTheRight != 0) {
                movements[1] = position | enPassantToTheRight;
                movements[1] = BitOperations.clearBit(movements[1], enPassantIndex);
            }
        }
        return movements;
    }

    @Override
    public long getAttackedTiles() {
        long attackedTile = 0L;
        for (int i = 0; i < BoardParts.NUM_OF_TILES; i++) {
            if (BitOperations.isBitSet(position, i)) {
                if (isLeftCapturePossible(i)) attackedTile |= leftCapture(i);
                if (isRightCapturePossible(i)) attackedTile |= rightCapture(i);
            }
        }
        return attackedTile;
    }

    @Override
    public boolean isAttackingTheOpponentPiece(BitPiece piece) {
        return false;
    }

    // for diagonal captures:
    @Override
    public boolean isSelfCapturing(long target) {
        return (target & playerPosition) != 0;
    }
    public boolean isCapturing(long target) {
        return (target & opponentPosition) != 0;
    }

    // for up moves:
    private boolean isUpMovePossible(long tile, int i) {
        long target = BitOperations.setBit(0L, i + 8 * colorIndex);
        return (tile & BoardParts.getPromotionRow(color)) == 0 && (target & (playerPosition | opponentPosition)) == 0;
    }
    private long upMove(int i) {
        return BitOperations.setBit(0L, i + 8 * colorIndex);
    }

    private boolean isRightCapturePossible(long tile) {
        return (tile & (BoardParts.getPromotionRow(color) | BoardParts.H_FILE)) == 0;
    }
    private long rightCapture(int i) {
        return BitOperations.setBit(0L, i + 8 * colorIndex + 1);
    }

    private boolean isLeftCapturePossible(long tile) {
        return (tile & (BoardParts.getPromotionRow(color) | BoardParts.A_FILE)) == 0;
    }
    private long leftCapture(int i) {
        return BitOperations.setBit(0L, i + 8 * colorIndex - 1);
    }

    private long getRightEnPassantTile(int enPassantNum) {
        long tile = BitOperations.setBit (0L, enPassantNum + 8 * colorIndex - 1);
        if (isRightCapturePossible(tile)) return tile;
        return - 1;
    }
    private long getLeftEnPassantTile(int enPassantNum) {
        long tile = BitOperations.setBit (0L, enPassantNum + 8 * colorIndex + 1);
        if (isLeftCapturePossible(tile)) return tile;
        return - 1;
    }

    public ArrayList<BitBoard> getPromotions(BitBoard board, long target) {
        int targetInt = BitOperations.getPositionFromBit(target);
        ArrayList<BitBoard> promotions = new ArrayList<>();
        BitBoard queenPromotion = new BitBoard(board);
        BitBoard knightPromotion = new BitBoard(board);
        BitBoard rookPromotion = new BitBoard(board);
        BitBoard bishopPromotion = new BitBoard(board);

        queenPromotion.setQueens(color, BitOperations.setBit(queenPromotion.getQueens(color), targetInt));
        knightPromotion.setKnights(color, BitOperations.setBit(knightPromotion.getKnights(color), targetInt));
        rookPromotion.setRooks(color, BitOperations.setBit(rookPromotion.getRooks(color), targetInt));
        bishopPromotion.setBishops(color, BitOperations.setBit(bishopPromotion.getBishops(color), targetInt));

        queenPromotion.setPawns(color, BitOperations.clearBit(queenPromotion.getPawns(color), targetInt));
        knightPromotion.setPawns(color, BitOperations.clearBit(knightPromotion.getPawns(color), targetInt));
        rookPromotion.setPawns(color, BitOperations.clearBit(rookPromotion.getPawns(color), targetInt));
        bishopPromotion.setPawns(color, BitOperations.clearBit(bishopPromotion.getPawns(color), targetInt));

        queenPromotion.setPromotionChoice('q');
        knightPromotion.setPromotionChoice('n');
        rookPromotion.setPromotionChoice('r');
        bishopPromotion.setPromotionChoice('b');

        promotions.add(queenPromotion);
        promotions.add(knightPromotion);
        promotions.add(rookPromotion);
        promotions.add(bishopPromotion);

        for (BitBoard board1 : promotions) {
            System.out.println(board1.lastMove.promotionChoice);
        }
        System.out.println(promotions.size());

        return promotions;
    }

    public static void main(String[] args) {
        BitPawn pawn = new BitPawn(1, (BoardParts.Tile.E2.position), BoardParts.SECOND_RANK, BoardParts.BLACK_START_POSITION | BoardParts.Tile.F3.position | BoardParts.Tile.D3.position);

        ArrayList<Long> movements = pawn.validMovements();
        System.out.println(movements.size());
        for (Long move : movements) {
            long l = move;
            System.out.println(BitOperations.printBitboard(l));
        }
    }

}
