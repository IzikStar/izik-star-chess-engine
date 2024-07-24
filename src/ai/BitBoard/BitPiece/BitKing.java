package ai.BitBoard.BitPiece;

import ai.BitBoard.BitOperations;
import ai.BitBoard.BoardParts;

import java.util.ArrayList;

public class BitKing extends BitPiece{

    public BitKing(int color, long position) {
        super(color, position);
        this.name = 1;
    }

    @Override
    public ArrayList<Long> validMovements() {
        ArrayList<Long> movements = new ArrayList<>();
        // for every knight in the position:
        for (int i = 0; i < BoardParts.NUM_OF_TILES; i++) {
            if (BitOperations.isBitSet(position, i)) {
                long iTile = 0;
                long otherSetTiles = position;
                iTile = BitOperations.setBit(iTile, i);
                otherSetTiles = BitOperations.clearBit(otherSetTiles, i);

                // checking if up move is possible:
                if (isUpMoveValid(iTile)) {
                    movements.add(otherSetTiles | upMove(iTile, i));
                }
                // checking if right move is possible:
                if (isRightMoveValid(iTile)) {
                    movements.add(otherSetTiles | rightMove(iTile, i));
                }
                // checking if left move is possible:
                if (isLeftMoveValid(iTile)) {
                    movements.add(otherSetTiles | leftMove(iTile, i));
                }
                // checking if down move is possible:
                if (isDownMoveValid(iTile)) {
                    movements.add(otherSetTiles | downMove(iTile, i));
                }
                // checking if up-right move is possible:
                if (isUpRightMoveValid(iTile)) {
                    movements.add(otherSetTiles | upRightMove(iTile, i));
                }
                // checking if up-left move is possible:
                if (isUpLeftMoveValid(iTile)) {
                    movements.add(otherSetTiles | upLeftMove(iTile, i));
                }
                // checking if down-left move is possible:
                if (isDownLeftMoveValid(iTile)) {
                    movements.add(otherSetTiles | downLeftMove(iTile, i));
                }
                // checking if down-right move is possible:
                if (isDownRightMoveValid(iTile)) {
                    movements.add(otherSetTiles | downRightMMove(iTile, i));
                }

            }
        }
        return movements;
    }

    @Override
    public boolean isAttackingTheOpponentPiece(BitPiece piece) {
        return false;
    }

    // the 8 options of valid movements:
    // up
    private boolean isUpMoveValid(long tile) {
        return (tile & BoardParts.EIGHTH_RANK) == 0;
    }
    private long upMove(long tile, int i) {
        long move = BitOperations.setBit(0x0L, i - 8);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // right
    private boolean isRightMoveValid(long tile) {
        return (tile & BoardParts.H_FILE) == 0;
    }
    private long rightMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i + 1);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // left
    private boolean isLeftMoveValid(long tile) {
        return (tile & BoardParts.A_FILE) == 0;
    }
    private long leftMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i - 1);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down
    private boolean isDownMoveValid(long tile) {
        return (tile & BoardParts.FIRST_RANK) == 0;
    }
    private long downMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i + 8);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // up right
    private boolean isUpRightMoveValid(long tile) {
        return (tile & BoardParts.BACK_RIGHT_CORNER) == 0;
    }
    private long upRightMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i - 7);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // up left
    private boolean isUpLeftMoveValid(long tile) {
        return (tile & BoardParts.BACK_LEFT_CORNER) == 0;
    }
    private long upLeftMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i - 9);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down right
    private boolean isDownRightMoveValid(long tile) {
        return (tile & BoardParts.FIRST_RIGHT_CORNER) == 0;
    }
    private long downRightMMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i + 9);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down left
    private boolean isDownLeftMoveValid(long tile) {
        return (tile & BoardParts.FIRST_LEFT_CORNER) == 0;
    }
    private long downLeftMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i + 7);
        move = BitOperations.clearBit(move, i);
        return move;
    }

    public static void main(String[] args) {
        BitKing king = new BitKing(1, (BoardParts.Tile.E1.position));

        ArrayList<Long> movements = king.validMovements();
        System.out.println(movements.size());
        for (Long move : movements) {
            long l = move;
            System.out.println(BitOperations.printBitboard(l));
        }
    }

}
