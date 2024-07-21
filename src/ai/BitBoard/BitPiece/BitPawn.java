package ai.BitBoard.BitPiece;

import ai.BitBoard.BitMove;

import java.util.ArrayList;

public class BitPawn extends BitPiece{

    public BitPawn(int color, long position) {
        super(color, position);
        this.name = 1;
    }

    @Override
    public ArrayList<Long> validMovements() {
        return new ArrayList<>();
    }

    @Override
    public boolean isAttackingTheOpponentPiece(BitPiece piece) {
        return false;
    }

}
