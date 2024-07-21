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
                BitOperations.setBit(iTile, i);
                BitOperations.clearBit(otherSetTiles, i);

                // checking if up-left move is possible:
                if (isUpLeftMoveValid(iTile)) {
                    movements.add(otherSetTiles | upLeftMove(iTile, i));
                }
                // checking if up-right move is possible:
                if (isUpRightMoveValid(iTile)) {
                    movements.add(otherSetTiles | upRightMove(iTile, i));
                }
                // checking if left-up move is possible:
                if (isLeftUpMoveValid(iTile)) {
                    movements.add(otherSetTiles | leftUpMove(iTile, i));
                }
                // checking if left-down move is possible:
                if (isLeftDownMoveValid(iTile)) {
                    movements.add(otherSetTiles | leftDownMove(iTile, i));
                }
                // checking if right-up move is possible:
                if (isRightUpMoveValid(iTile)) {
                    movements.add(otherSetTiles | rightUpMove(iTile, i));
                }
                // checking if right-down move is possible:
                if (isRightDownMoveValid(iTile)) {
                    movements.add(otherSetTiles | rightDownMove(iTile, i));
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
    // up left
    private boolean isUpLeftMoveValid(long tile) {
        if ((tile & BoardParts.BACK_LEFT_CORNER) != 0) {
            return (tile & BoardParts.SEVENTH_RANK) != 0;
        }
        return false;
    }
    private long upLeftMove(long tile, int i) {
        BitOperations.setBit(tile, i - 17);
        BitOperations.clearBit(tile, i);
        return tile;
    }
    // up right
    private boolean isUpRightMoveValid(long tile) {
        if ((tile & BoardParts.BACK_RIGHT_CORNER) != 0) {
            return (tile & BoardParts.SEVENTH_RANK) != 0;
        }
        return false;
    }
    private long upRightMove(long tile, int i) {
        BitOperations.setBit(tile, i - 15);
        BitOperations.clearBit(tile, i);
        return tile;
    }
    // left up
    private boolean isLeftUpMoveValid(long tile) {
        if ((tile & BoardParts.BACK_LEFT_CORNER) != 0) {
            return (tile & BoardParts.B_FILE) != 0;
        }
        return false;
    }
    private long leftUpMove(long tile, int i) {
        BitOperations.setBit(tile, i - 10);
        BitOperations.clearBit(tile, i);
        return tile;
    }
    // left down
    private boolean isLeftDownMoveValid(long tile) {
        if ((tile & BoardParts.FIRST_LEFT_CORNER) != 0) {
            return (tile & BoardParts.SECOND_RANK) != 0;
        }
        return false;
    }
    private long leftDownMove(long tile, int i) {
        BitOperations.setBit(tile, i + 6);
        BitOperations.clearBit(tile, i);
        return tile;
    }
    // right up
    private boolean isRightUpMoveValid(long tile) {
        if ((tile & BoardParts.BACK_RIGHT_CORNER) != 0) {
            return (tile & BoardParts.SEVENTH_RANK) != 0;
        }
        return false;
    }
    private long rightUpMove(long tile, int i) {
        BitOperations.setBit(tile, i - 6);
        BitOperations.clearBit(tile, i);
        return tile;
    }
    // right down
    private boolean isRightDownMoveValid(long tile) {
        if ((tile & BoardParts.FIRST_RIGHT_CORNER) != 0) {
            return (tile & BoardParts.G_FILE) != 0;
        }
        return false;
    }
    private long rightDownMove(long tile, int i) {
        BitOperations.setBit(tile, i + 10);
        BitOperations.clearBit(tile, i);
        return tile;
    }
    // down left
    private boolean isDownLeftMoveValid(long tile) {
        if ((tile & BoardParts.FIRST_LEFT_CORNER) != 0) {
            return (tile & BoardParts.SECOND_RANK) != 0;
        }
        return false;
    }
    private long downLeftMove(long tile, int i) {
        BitOperations.setBit(tile, i + 15);
        BitOperations.clearBit(tile, i);
        return tile;
    }
    // down right
    private boolean isDownRightMoveValid(long tile) {
        if ((tile & BoardParts.FIRST_RIGHT_CORNER) != 0) {
            return (tile & BoardParts.SECOND_RANK) != 0;
        }
        return false;
    }
    private long downRightMMove(long tile, int i) {
        BitOperations.setBit(tile, i + 17);
        BitOperations.clearBit(tile, i);
        return tile;
    }

}
