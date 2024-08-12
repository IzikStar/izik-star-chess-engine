package ai.BitBoard.BitPiece;

import java.util.ArrayList;

public abstract class BitPiece {
    public int name; // 1 to 6 for king to pawn.
    public int color; // 1 or -1.
    public long position; // a position of all the pieces of the same kind on the board
    public long playerPosition;
    public long opponentPosition;

    public BitPiece(int color, long position, long wPosition, long bPosition) {
        this.color = color;
        this.position = position;
        if (color == 1) {
            this.playerPosition = wPosition;
            this.opponentPosition = bPosition;
        } else {
            this.playerPosition = bPosition;
            this.opponentPosition = wPosition;
        }
    }

    public abstract ArrayList<Long> validMovements();

    public abstract long getAttackedTiles();

    public abstract boolean isSelfCapturing(long target);

    public abstract boolean isAttackingTheOpponentPiece(BitPiece piece);



}
