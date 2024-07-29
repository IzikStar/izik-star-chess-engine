package ai.BitBoard.BitPiece;

import ai.BitBoard.BitOperations;
import ai.BitBoard.BoardParts;
import main.Debug;

import java.util.ArrayList;

public class BitQueen extends BitPiece{

    public BitQueen(int color, long position, long wPosition, long bPosition) {
        super(color, position, wPosition, bPosition);
        this.name = 1;
    }

    @Override
    public String toString() {
        return (color == 1 ? "white" : "black") + " queen";
    }

    @Override
    public ArrayList<Long> validMovements() {
        ArrayList<Long> movements = new ArrayList<>();
        // for each queen in the position:
        for (int i = 0; i < BoardParts.NUM_OF_TILES; i++) {
            if (BitOperations.isBitSet(position, i)) {
                long iTile = BitOperations.setBit(0x0L, i);
                long otherSetTiles = BitOperations.clearBit(position, i);
                int row = BitOperations.getRowIndexFromBit(iTile);
                int col = BitOperations.getColIndexFromBit(iTile);
                Debug.log("iTile:" + BitOperations.printBitboard(iTile) + "otherSetTiles: " + BitOperations.printBitboard(otherSetTiles)/* + "position:" + BitOperations.printBitboard(position)*/);
                Debug.log("i = " + i);

                int counter = 1;
                // checking if up move is possible:
                for (int j = row; j > 0; j--) {
                    long upMove = upMove(i, counter);
                    counter++;
                    Debug.log("self capture = " + isSelfCapturing(upMove) + " capture = " + isCapturing(upMove));
                    if ((!isSelfCapturing(upMove)) && (!isCapturing(upMove))) {
                        Debug.log("up move");
                        movements.add(upMove | otherSetTiles);
                    } else {
                        if (isCapturing(upMove)) movements.add(upMove | otherSetTiles);
                        break;
                    }
                }
                counter = 1;
                // checking if left move is possible:
                for (int k = col; k > 0; k--) {
                    long leftMove = leftMove(i, counter);
                    counter++;
                    if ((!isSelfCapturing(leftMove)) && (!isCapturing(leftMove | otherSetTiles))) {
                        Debug.log("left move");
                        movements.add(leftMove);
                    } else {
                        if (isCapturing(leftMove)) movements.add(leftMove | otherSetTiles);
                        break;
                    }
                }
                counter = 1;
                // checking if right move is possible:
                for (int x = col; x < 7; x++) {
                    long rightMove = rightMove(i, counter);
                    counter++;
                    if ((!isSelfCapturing(rightMove)) && (!isCapturing(rightMove | otherSetTiles))) {
                        Debug.log("right move");
                        movements.add(rightMove);
                    } else {
                        if (isCapturing(rightMove)) movements.add(rightMove | otherSetTiles);
                        break;
                    }
                }
                counter = 1;
                // checking if down move is possible:
                for (int y = row; y < 7; y++) {
                    long downMove = downMove(i, counter);
                    counter++;
                    if ((!isSelfCapturing(downMove)) && (!isCapturing(downMove))) {
                        Debug.log("down move");
                        movements.add(downMove | otherSetTiles);
                    } else {
                        if (isCapturing(downMove)) movements.add(downMove | otherSetTiles);
                        break;
                    }
                }
                counter = 1;
                // checking if up left move is possible:
                for (int r = row, c = col; r > 0 && c > 0; r--, c--) {
                    long upMove = upLeftMove(i, counter);
                    counter++;
                    if ((!isSelfCapturing(upMove)) && (!isCapturing(upMove))) {
                        Debug.log("up left move");
                        movements.add(upMove | otherSetTiles);
                    }
                    else {
                        if (isCapturing(upMove)) movements.add(upMove | otherSetTiles);
                        break;
                    }
                }
                counter = 1;
                // checking if up right move is possible:
                for (int r = row, c = col; r > 0 && c < 7; r--, c++) {
                    long rightMove = upRightMove(i, counter);
                    counter++;
                    if ((!isSelfCapturing(rightMove)) && (!isCapturing(rightMove))) {
                        Debug.log("up right move");
                        movements.add(rightMove | otherSetTiles);
                    }
                    else {
                        if (isCapturing(rightMove)) movements.add(rightMove | otherSetTiles);
                        break;
                    }
                }
                counter = 1;
                // checking if down left move is possible:
                for (int r = row, c = col; r < 7 && c > 0; r++, c--) {
                    long leftMove = downLeftMove(i, counter);
                    counter++;
                    if ((!isSelfCapturing(leftMove)) && (!isCapturing(leftMove))) {
                        Debug.log("down left move");
                        movements.add(leftMove | otherSetTiles);
                    }
                    else {
                        if (isCapturing(leftMove)) movements.add(leftMove | otherSetTiles);
                        break;
                    }
                }
                counter = 1;
                // checking if down right move is possible:
                for (int r = row, c = col; r < 7 && c < 7; r++, c++) {
                    long downMove = downRightMove(i, counter);
                    counter++;
                    if ((!isSelfCapturing(downMove)) && (!isCapturing(downMove))) {
                        Debug.log("down right move");
                        movements.add(downMove | otherSetTiles);
                    }
                    else {
                        if (isCapturing(downMove)) movements.add(downMove | otherSetTiles);
                        break;
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

                // checking if up move is possible:
                for (int j = row; j > 0; j--) {
                    long upMove = upMove(i, counter);
                    counter++;
                    attackedTile |= upMove;
                    if (isSelfCapturing(upMove) || isCapturing(upMove)) break;
                }
                counter = 1;
                // checking if left move is possible:
                for (int k = col; k > 0; k--) {
                    long leftMove = leftMove(i, counter);
                    counter++;
                    attackedTile |= leftMove;
                    if (isSelfCapturing(leftMove) || isCapturing(leftMove)) break;
                }
                counter = 1;
                // checking if right move is possible:
                for (int x = col; x < 7; x++) {
                    long rightMove = rightMove(i, counter);
                    counter++;
                    attackedTile |= rightMove;
                    if (isSelfCapturing(rightMove) || isCapturing(rightMove)) break;
                }
                counter = 1;
                // checking if down move is possible:
                for (int y = row; y < 7; y++) {
                    long downMove = downMove(i, counter);
                    counter++;
                    attackedTile |= downMove;
                    if (isSelfCapturing(downMove) || isCapturing(downMove)) break;
                }

                // checking if up left move is possible:
                for (int r = row, c = col; r > 0 && c > 0; r--, c--) {
                    long upMove = upLeftMove(i, counter);
                    counter++;
                    attackedTile |= upMove;
                    if (isSelfCapturing(upMove) || isCapturing(upMove)) break;
                }
                counter = 1;
                // checking if up right move is possible:
                for (int r = row, c = col; r > 0 && c < 7; r--, c++) {
                    long rightMove = upRightMove(i, counter);
                    counter++;
                    attackedTile |= rightMove;
                    if (isSelfCapturing(rightMove) || isCapturing(rightMove)) break;
                }
                counter = 1;
                // checking if down left move is possible:
                for (int r = row, c = col; r < 7 && c > 0; r++, c--) {
                    long leftMove = downLeftMove(i, counter);
                    counter++;
                    attackedTile |= leftMove;
                    if (isSelfCapturing(leftMove) || isCapturing(leftMove)) break;
                }
                counter = 1;
                // checking if down right move is possible:
                for (int r = row, c = col; r < 7 && c < 7; r++, c++) {
                    long downMove = downRightMove(i, counter);
                    counter++;
                    attackedTile |= downMove;
                    if (isSelfCapturing(downMove) || isCapturing(downMove)) break;
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
    public boolean isSelfCapturing(long target) {
        return (target & playerPosition) != 0;
    }
    public boolean isCapturing(long target) {
        return (target & opponentPosition) != 0;
    }


    // the 8 options of valid movements:
    // rook moves:
    // up
    private long upMove(int i, int counter) {
        return BitOperations.setBit(0L, i - 8 * counter);
    }
    // left
    private long leftMove(int i, int counter) {
        return BitOperations.setBit(0L, i - counter);
    }
    // right
    private long rightMove(int i, int counter) {
        return BitOperations.setBit(0L, i + counter);
    }
    // down
    private long downMove(int i, int counter) {
        return BitOperations.setBit(0L, i + 8 * counter);
    }
    // bishop moves:
    // up left
    private long upLeftMove(int i, int counter) {
        return BitOperations.setBit(0L, i - 9 * counter);
    }
    // up right
    private long upRightMove(int i, int counter) {
        return BitOperations.setBit(0L, i - 7 * counter);
    }
    // down left
    private long downLeftMove(int i, int counter) {
        return BitOperations.setBit(0L, i + 7 * counter);
    }
    // down right
    private long downRightMove(int i, int counter) {
        return BitOperations.setBit(0L, i + 9 * counter);
    }



    public static void main(String[] args) {
        BitQueen queen = new BitQueen(1, (BoardParts.Tile.D1.position), BoardParts.WHITE_START_POSITION, BoardParts.BLACK_START_POSITION);

        ArrayList<Long> movements = queen.validMovements();
        System.out.println(movements.size());
        for (Long move : movements) {
            long l = move;
            System.out.println(BitOperations.printBitboard(l));
        }
    }

}
