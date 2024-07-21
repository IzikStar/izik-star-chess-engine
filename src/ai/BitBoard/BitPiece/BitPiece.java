package ai.BitBoard.BitPiece;

import java.util.ArrayList;

public abstract class BitPiece {
    public int name; // 1 to 6 for king to pawn.
    public int color; // 1 or -1.
    public long position; // a position of all the pieces of the same kind on the board

    public BitPiece(int color, long position) {
        this.color = color;
        this.position = position;
    }

    public abstract ArrayList<Long> validMovements();

    public abstract boolean isAttackingTheOpponentPiece(BitPiece piece);


}
