package ai.BitBoard.BitPiece;

import ai.BitBoard.BitOperations;
import ai.BitBoard.BoardParts;
import main.Debug;

import java.util.ArrayList;

public class BitKnight extends BitPiece{

    public BitKnight(int color, long position, long wPosition, long bPosition) {
        super(color, position, wPosition, bPosition);
        this.name = 5;
    }

    @Override
    public String toString() {
        return (color == 1 ? "white" : "black") + " knight";
    }

    @Override
    public ArrayList<Long> validMovements() {
        ArrayList<Long> movements = new ArrayList<>();
        // for every knight in the position:
        for (int i = 0; i < BoardParts.NUM_OF_TILES; i++) {
            if (BitOperations.isBitSet(position, i)) {
                long iTile = BitOperations.setBit(0x0L, i);
                long otherSetTiles = BitOperations.clearBit(position, i);

                // Debug.log("iTile: " + BitOperations.printBitboard(iTile) + ", otherSetTiles: " + BitOperations.printBitboard(otherSetTiles)/* + ", position: " + BitOperations.printBitboard(position)*/);

                // checking if up-left move is possible:
                if (isUpLeftMoveValid(iTile)) {
                    long upLeft = (upLeftMove(i));
                    if (!isSelfCapturing(upLeft)) {
                        movements.add(otherSetTiles | upLeft);
                    }
                }
                // checking if up-right move is possible:
                if (isUpRightMoveValid(iTile)) {
                    long upRight = (upRightMove(i));
                    if (!isSelfCapturing(upRight)) {
                        movements.add(otherSetTiles | upRight);
                    }
                }
                // checking if left-up move is possible:
                if (isLeftUpMoveValid(iTile)) {
                    long leftUp = (leftUpMove(i));
                    if (!isSelfCapturing(leftUp)) {
                        movements.add(otherSetTiles | leftUp);
                    }
                }
                // checking if left-down move is possible:
                if (isLeftDownMoveValid(iTile)) {
                    long leftDown = (leftDownMove(i));
                    if (!isSelfCapturing(leftDown)) {
                        movements.add(otherSetTiles | leftDown);
                    }
                }
                // checking if right-up move is possible:
                if (isRightUpMoveValid(iTile)) {
                    long rightUp = (rightUpMove(i));
                    if (!isSelfCapturing(rightUp)) {
                        movements.add(otherSetTiles | rightUp);
                    }
                }
                // checking if right-down move is possible:
                if (isRightDownMoveValid(iTile)) {
                    long rightDown = (rightDownMove(i));
                    if (!isSelfCapturing(rightDown)) {
                        movements.add(otherSetTiles | rightDown);
                    }
                }
                // checking if down-left move is possible:
                if (isDownLeftMoveValid(iTile)) {
                    long downLeft = (downLeftMove(i));
                    if (!isSelfCapturing(downLeft)) {
                        movements.add(otherSetTiles | downLeft);
                    }
                }
                // checking if down-right move is possible:
                if (isDownRightMoveValid(iTile)) {
                    long downRight = (downRightMove(i));
                    if (!isSelfCapturing(downRight)) {
                        movements.add(otherSetTiles | downRight);
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
                long otherSetTiles = BitOperations.clearBit(position, i);
                int row = BitOperations.getRowIndexFromBit(iTile);
                int col = BitOperations.getColIndexFromBit(iTile);
                int counter = 1;

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
                    downRightMove(i);
                }
                // checking if right-up move is possible:
                if (isRightUpMoveValid(iTile)) {
                    attackedTile |= rightUpMove(i);
                }
                // checking if left-up move is possible:
                if (isLeftUpMoveValid(iTile)) {
                    attackedTile |= leftUpMove(i);
                }
                // checking if left-down move is possible:
                if (isLeftDownMoveValid(iTile)) {
                    attackedTile |= leftDownMove(i);
                }
                // checking if down-right move is possible:
                if (isRightDownMoveValid(iTile)) {
                    rightDownMove(i);
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
    // up left
    private boolean isUpLeftMoveValid(long tile) {
        //Debug.log("pos: " + BitOperations.printBitboard(tile) + " comparing to: " + BitOperations.printBitboard(BoardParts.BACK_LEFT_CORNER) + " and to: " + BitOperations.printBitboard(BoardParts.SEVENTH_RANK));
        if ((tile & BoardParts.BACK_LEFT_CORNER) == 0) {
            if ((tile & BoardParts.SEVENTH_RANK) == 0) {
                Debug.log("UpLeftMove!");
                return true;
            }
        }
        return false;
    }
    private long upLeftMove(int i) {
        long move = BitOperations.setBit(0L, i - 17);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // up right
    private boolean isUpRightMoveValid(long tile) {
        if ((tile & BoardParts.BACK_RIGHT_CORNER) == 0) {
            if ((tile & BoardParts.SEVENTH_RANK) == 0) {
                Debug.log("UpRightMove!");
                return true;
            }
        }
        return false;
    }
    private long upRightMove(int i) {
        long move = BitOperations.setBit(0L, i - 15);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // left up
    private boolean isLeftUpMoveValid(long tile) {
        if ((tile & BoardParts.BACK_LEFT_CORNER) == 0) {
            if ((tile & BoardParts.B_FILE) == 0) {
                Debug.log("LeftUpMove!");
                return true;
            }
        }
        return false;
    }
    private long leftUpMove(int i) {
        long move = BitOperations.setBit(0L, i - 10);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // left down
    private boolean isLeftDownMoveValid(long tile) {
        if ((tile & BoardParts.FIRST_LEFT_CORNER) == 0) {
            if ((tile & BoardParts.SECOND_RANK) == 0) {
                Debug.log("LeftDownMove!");
                return true;
            }
        }
        return false;
    }
    private long leftDownMove(int i) {
        long move = BitOperations.setBit(0L, i + 6);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // right up
    private boolean isRightUpMoveValid(long tile) {
        if ((tile & BoardParts.BACK_RIGHT_CORNER) == 0) {
            if ((tile & BoardParts.G_FILE) == 0) {
                Debug.log("RightUpMove!");
                return true;
            }
        }
        return false;
    }
    private long rightUpMove(int i) {
        long move = BitOperations.setBit(0L, i - 6);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // right down
    private boolean isRightDownMoveValid(long tile) {
        if ((tile & BoardParts.FIRST_RIGHT_CORNER) == 0) {
            if ((tile & BoardParts.G_FILE) == 0) {
                Debug.log("RightDownMove!");
                return true;
            }
        }
        return false;
    }
    private long rightDownMove(int i) {
        long move = BitOperations.setBit(0L, i + 10);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down left
    private boolean isDownLeftMoveValid(long tile) {
        if ((tile & BoardParts.FIRST_LEFT_CORNER) == 0) {
            if ((tile & BoardParts.SECOND_RANK) == 0) {
                Debug.log("DownLeftMove!");
                return true;
            }
        }
        return false;
    }
    private long downLeftMove(int i) {
        long move = BitOperations.setBit(0L, i + 15);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down right
    private boolean isDownRightMoveValid(long tile) {
        if ((tile & BoardParts.FIRST_RIGHT_CORNER) == 0) {
            if ((tile & BoardParts.SECOND_RANK) == 0) {
                Debug.log("DownRightMove!");
                return true;
            }
        }
        return false;
    }
    private long downRightMove(int i) {
        long move = BitOperations.setBit(0L, i + 17);
        move = BitOperations.clearBit(move, i);
        return move;
    }


    public static void main(String[] args) {
        BitKnight knight = new BitKnight(1, (BoardParts.Tile.B1.position | BoardParts.Tile.G1.position | BoardParts.Tile.H3.position), BoardParts.WHITE_START_POSITION, BoardParts.BLACK_START_POSITION);

        ArrayList<Long> movements = knight.validMovements();
        System.out.println(movements.size());
        for (Long move : movements) {
            long l = move;
            System.out.println(BitOperations.printBitboard(l));
        }
    }

}
