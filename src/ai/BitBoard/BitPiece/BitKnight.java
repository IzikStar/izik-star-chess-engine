package ai.BitBoard.BitPiece;

import ai.BitBoard.BitOperations;
import ai.BitBoard.BoardParts;
import main.Debug;

import java.util.ArrayList;

public class BitKnight extends BitPiece{

    public BitKnight(int color, long position) {
        super(color, position);
        this.name = 5;
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
        //Debug.log("pos: " + BitOperations.printBitboard(tile) + " comparing to: " + BitOperations.printBitboard(BoardParts.BACK_LEFT_CORNER) + " and to: " + BitOperations.printBitboard(BoardParts.SEVENTH_RANK));
        if ((tile & BoardParts.BACK_LEFT_CORNER) == 0) {
            if ((tile & BoardParts.SEVENTH_RANK) == 0) {
                Debug.log("UpLeftMove!");
                return true;
            }
        }
        return false;
    }
    private long upLeftMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i - 17);
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
    private long upRightMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i - 15);
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
    private long leftUpMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i - 10);
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
    private long leftDownMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i + 6);
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
    private long rightUpMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i - 6);
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
    private long rightDownMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i + 10);
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
    private long downLeftMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i + 15);
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
    private long downRightMMove(long tile, int i) {
        long move = BitOperations.setBit(tile, i + 17);
        move = BitOperations.clearBit(move, i);
        return move;
    }


    public static void main(String[] args) {
        BitKnight knight = new BitKnight(1, (BoardParts.Tile.B1.position | BoardParts.Tile.G1.position | BoardParts.Tile.H3.position));

        ArrayList<Long> movements = knight.validMovements();
        System.out.println(movements.size());
        for (Long move : movements) {
            long l = move;
            System.out.println(BitOperations.printBitboard(l));
        }
    }

}
