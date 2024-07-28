package ai.BitBoard.BitPiece;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitOperations;
import ai.BitBoard.BoardParts;

import java.util.ArrayList;


public class BitPawn extends BitPiece{

    public int colorIndex;
    public BitPawn(int color, long position, long wPosition, long bPosition) {
        super(color, position, wPosition, bPosition);
        this.name = 6;
        this.colorIndex = color == 1 ? 1 : -1;
    }

    @Override
    public ArrayList<Long> validMovements() {
        return new ArrayList<>();
    }

    @Override
    public long getAttackedTiles() {
        long attackedTile = 0L;
        for (int i = 0; i < BoardParts.NUM_OF_TILES; i++) {
            if (BitOperations.isBitSet(position, i)) {
                long iTile = BitOperations.setBit(0x0L, i);
                long otherSetTiles = BitOperations.clearBit(position, i);
                int row = BitOperations.getRowIndexFromBit(iTile);
                int col = BitOperations.getColIndexFromBit(iTile);
                int counter = 1;

                // checking if up move is possible:
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

    private boolean isRightEnPassantPossible(int enPassantNum, int i) {
        return enPassantNum == (i + 8 * colorIndex + 1);
    }
    private boolean isLeftEnPassantPossible(int enPassantNum, int i) {
        return enPassantNum == (i + 8 * colorIndex - 1);
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

        promotions.add(queenPromotion);
        promotions.add(knightPromotion);
        promotions.add(rookPromotion);
        promotions.add(bishopPromotion);

        return promotions;
    }

}
