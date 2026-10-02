package ai.BitBoard.BitPiece;

import ai.BitBoard.BitOperations;
import ai.BitBoard.BoardParts;

import java.util.ArrayList;

public class BitKing extends BitPiece{

    public BitKing(int color, long position, long wPosition, long bPosition) {
        super(color, position, wPosition, bPosition);
        this.name = 1;
    }

    @Override
    public String toString() {
        return (color == 1 ? "white" : "black") + " king";
    }

    /** The king's eight steps, in the order the moves are listed: up, right, left, down, then the diagonals. */
    private static final int[] STEPS = {-8, 1, -1, 8, -7, -9, 7, 9};
    /** For each step, the edge squares a king cannot take it from. */
    private static final long[] EDGES = {
            BoardParts.EIGHTH_RANK, BoardParts.H_FILE, BoardParts.A_FILE, BoardParts.FIRST_RANK,
            BoardParts.BACK_RIGHT_CORNER, BoardParts.BACK_LEFT_CORNER,
            BoardParts.FIRST_LEFT_CORNER, BoardParts.FIRST_RIGHT_CORNER};

    @Override
    public ArrayList<Long> validMovements() {
        ArrayList<Long> movements = new ArrayList<>(8);
        for (long kings = position; kings != 0; kings &= kings - 1) {
            int i = Long.numberOfTrailingZeros(kings);
            long from = 1L << i;
            long otherSetTiles = position & ~from;
            for (int k = 0; k < STEPS.length; k++) {
                if ((from & EDGES[k]) == 0) {
                    long to = 1L << (i + STEPS[k]);
                    if (!isSelfCapturing(to)) {
                        movements.add(otherSetTiles | to);
                    }
                }
            }
        }
        return movements;
    }

    @Override
    public boolean isAttackingTheOpponentPiece(BitPiece piece) {
        return false;
    }

    @Override
    public boolean isSelfCapturing(long tile) {
        return  (tile & playerPosition) != 0;
    }

    public static void main(String[] args) {
        BitKing king = new BitKing(1, (BoardParts.Tile.E1.position), BoardParts.SECOND_RANK, BoardParts.BLACK_START_POSITION);

        ArrayList<Long> movements = king.validMovements();
        System.out.println(movements.size());
        for (Long move : movements) {
            long l = move;
            System.out.println(BitOperations.printBitboard(l));
        }
    }

}
