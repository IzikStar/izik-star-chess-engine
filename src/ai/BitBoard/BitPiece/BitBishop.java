package ai.BitBoard.BitPiece;

import ai.BitBoard.BitOperations;
import ai.BitBoard.BoardParts;
import main.Debug;

import java.util.ArrayList;

public class BitBishop extends BitPiece{

    public BitBishop(int color, long position) {
        super(color, position);
        this.name = 1;
    }

    @Override
    public ArrayList<Long> validMovements() {
        ArrayList<Long> movements = new ArrayList<>();
        // for each bishop in the position:
        for (int i = 0; i < BoardParts.NUM_OF_TILES; i++) {
            if (BitOperations.isBitSet(position, i)) {
                long iTile = BitOperations.setBit(0x0L, i);
                long otherSetTiles = BitOperations.clearBit(position, i);
                int row = BitOperations.getRowIndexFromBit(iTile);
                int col = BitOperations.getColIndexFromBit(iTile);
                Debug.log("iTile:" + BitOperations.printBitboard(iTile) + "otherSetTiles: " + BitOperations.printBitboard(otherSetTiles)/* + "position:" + BitOperations.printBitboard(position)*/);
                Debug.log("i = " + i);
                int counter = 1;

                // checking if up left move is possible:
                for (int r = row, c = col; r > 0 && c > 0; r--, c--) {
                    long upMove = otherSetTiles | upLeftMove(iTile, i, counter);
                    counter++;
                    if (BitOperations.countSetBits(position) == BitOperations.countSetBits(upMove)) {
                        Debug.log("up left move");
                        movements.add(upMove);
                    }
                    else {
                        break;
                    }
                }
                counter = 1;
                // checking if up right move is possible:
                for (int r = row, c = col; r > 0 && c < 7; r--, c++) {
                    long rightMove = otherSetTiles | upRightMove(iTile, i, counter);
                    counter++;
                    if (BitOperations.countSetBits(position) == BitOperations.countSetBits(rightMove)) {
                        Debug.log("up right move");
                        movements.add(rightMove);
                    }
                    else {
                        break;
                    }
                }
                counter = 1;
                // checking if down left move is possible:
                for (int r = row, c = col; r < 7 && c > 0; r++, c--) {
                    long leftMove = otherSetTiles | downLeftMove(iTile, i, counter);
                    counter++;
                    if (BitOperations.countSetBits(position) == BitOperations.countSetBits(leftMove)) {
                        Debug.log("down left move");
                        movements.add(leftMove);
                    }
                    else {
                        break;
                    }
                }
                counter = 1;
                // checking if down right move is possible:
                for (int r = row, c = col; r < 7 && c < 7; r++, c++) {
                    long downMove = otherSetTiles | downRightMove(iTile, i, counter);
                    counter++;
                    if (BitOperations.countSetBits(position) == BitOperations.countSetBits(downMove)) {
                        Debug.log("down right move");
                        movements.add(downMove);
                    }
                    else break;
                }
            }
        }
        return movements;
    }

    @Override
    public boolean isAttackingTheOpponentPiece(BitPiece piece) {

        return false;
    }


    // the 4 options of valid movements:
    // up left
    private long upLeftMove(long tile, int i, int counter) {
        long move = BitOperations.setBit(tile, i - 9 * counter);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // up right
    private long upRightMove(long tile, int i, int counter) {
        long move = BitOperations.setBit(tile, i - 7 * counter);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down left
    private long downLeftMove(long tile, int i, int counter) {
        long move = BitOperations.setBit(tile, i + 7 * counter);
        move = BitOperations.clearBit(move, i);
        return move;
    }
    // down right
    private long downRightMove(long tile, int i, int counter) {
        long move = BitOperations.setBit(tile, i + 9 * counter);
        move = BitOperations.clearBit(move, i);
        return move;
    }


    public static void main(String[] args) {
        BitBishop bishop = new BitBishop(1, (BoardParts.Tile.D4.position | BoardParts.Tile.E5.position));

        ArrayList<Long> movements = bishop.validMovements();
        System.out.println(movements.size());
        for (Long move : movements) {
            long l = move;
            System.out.println(BitOperations.printBitboard(l));
        }
    }

}
