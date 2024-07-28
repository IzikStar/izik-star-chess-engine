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
                    long up = (otherSetTiles | upMove(i));
                    if (!isSelfCapturing(up)) {
                        movements.add(up);
                    }
                }
                // checking if right move is possible:
                if (isRightMoveValid(iTile)) {
                    long right = (otherSetTiles | rightMove(i));
                    if (!isSelfCapturing(right)) {
                        movements.add(right);
                    }
                }
                // checking if left move is possible:
                if (isLeftMoveValid(iTile)) {
                    long left = (otherSetTiles | leftMove(i));
                    if (!isSelfCapturing(left)) {
                        movements.add(left);
                    }
                }
                // checking if down move is possible:
                if (isDownMoveValid(iTile)) {
                    long down = (otherSetTiles | downMove(i));
                    if (!isSelfCapturing(down)) {
                        movements.add(down);
                    }
                }
                // checking if up-right move is possible:
                if (isUpRightMoveValid(iTile)) {
                    long upRight = (otherSetTiles | upRightMove(i));
                    if (!isSelfCapturing(upRight)) {
                        movements.add(upRight);
                    }
                }
                // checking if up-left move is possible:
                if (isUpLeftMoveValid(iTile)) {
                    long upLeft = (otherSetTiles | upLeftMove(i));
                    if (!isSelfCapturing(upLeft)) {
                        movements.add(upLeft);
                    }
                }
                // checking if down-left move is possible:
                if (isDownLeftMoveValid(iTile)) {
                    long downLeft = (otherSetTiles | downLeftMove(i));
                    if (!isSelfCapturing(downLeft)) {
                        movements.add(downLeft);
                    }
                }
                // checking if down-right move is possible:
                if (isDownRightMoveValid(iTile)) {
                    long downRight = (otherSetTiles | downRightMMove(i));
                    if (!isSelfCapturing(downRight)) {
                        movements.add(downRight);
                    }
                }

            }
        }
        return movements;
    }

    @Override
    public long getAttackedTiles() {
        long attackedTile = 0L;
        for (int i = 0; i < BoardParts.NUM_OF_TILES; i++) {
            if (BitOperations.isBitSet(position, i)) {
                long iTile = BitOperations.setBit(0x0L, i);

                // checking if up move is possible:
                if (isUpMoveValid(iTile)) {
                    attackedTile |= upMove(i);
                }
                // checking if right move is possible:
                if (isRightMoveValid(iTile)) {
                    attackedTile |= rightMove(i);
                }
                // checking if left move is possible:
                if (isLeftMoveValid(iTile)) {
                    attackedTile |= leftMove(i);
                }
                // checking if down move is possible:
                if (isDownMoveValid(iTile)) {
                    attackedTile |= downMove(i);
                }
                // checking if up-right move is possible:
                if (isUpRightMoveValid(iTile)) {
                    attackedTile |= upRightMove(i);
                }
                // checking if up-left move is possible:
                if (isUpLeftMoveValid(iTile)) {
                    attackedTile |= upLeftMove(i);
                }
                // checking if down-left move is possible:
                if (isDownLeftMoveValid(iTile)) {
                    attackedTile |= downLeftMove(i);
                }
                // checking if down-right move is possible:
                if (isDownRightMoveValid(iTile)) {
                    downRightMMove(i);
                }
            }
        }
        return attackedTile;
    }

    @Override
    public boolean isAttackingTheOpponentPiece(BitPiece piece) {
        return false;
    }

    @Override
    public boolean isSelfCapturing(long tile) {
        return  (tile & playerPosition) != 0;
    }

    // the 8 options of valid movements:
    // up
    private boolean isUpMoveValid(long tile) {
        return (tile & BoardParts.EIGHTH_RANK) == 0;
    }
    private long upMove(int i) {
        long move = BitOperations.setBit(0x0L, i - 8);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // right
    private boolean isRightMoveValid(long tile) {
        return (tile & BoardParts.H_FILE) == 0;
    }
    private long rightMove(int i) {
        long move = BitOperations.setBit(0L, i + 1);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // left
    private boolean isLeftMoveValid(long tile) {
        return (tile & BoardParts.A_FILE) == 0;
    }
    private long leftMove(int i) {
        long move = BitOperations.setBit(0L, i - 1);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down
    private boolean isDownMoveValid(long tile) {
        return (tile & BoardParts.FIRST_RANK) == 0;
    }
    private long downMove(int i) {
        long move = BitOperations.setBit(0L, i + 8);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // up right
    private boolean isUpRightMoveValid(long tile) {
        return (tile & BoardParts.BACK_RIGHT_CORNER) == 0;
    }
    private long upRightMove(int i) {
        long move = BitOperations.setBit(0L, i - 7);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // up left
    private boolean isUpLeftMoveValid(long tile) {
        return (tile & BoardParts.BACK_LEFT_CORNER) == 0;
    }
    private long upLeftMove(int i) {
        long move = BitOperations.setBit(0L, i - 9);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down right
    private boolean isDownRightMoveValid(long tile) {
        return (tile & BoardParts.FIRST_RIGHT_CORNER) == 0;
    }
    private long downRightMMove(int i) {
        long move = BitOperations.setBit(0L, i + 9);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down left
    private boolean isDownLeftMoveValid(long tile) {
        return (tile & BoardParts.FIRST_LEFT_CORNER) == 0;
    }
    private long downLeftMove(int i) {
        long move = BitOperations.setBit(0L, i + 7);
        move = BitOperations.clearBit(move, i);
        return move;
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
