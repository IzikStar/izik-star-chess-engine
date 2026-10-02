package main;

import rules.GameStatus;
import rules.MoveResult;

/** How a move appears in the "Saved Games" move list: {@code "\n12. Nf3"} / {@code " e5"}. */
final class MoveListText {

    private MoveListText() {}

    static String of(MoveResult move) {
        StringBuilder text = new StringBuilder();
        if (move.whiteMoved()) {
            text.append('\n').append(move.moveNumber()).append(". ");
        } else {
            text.append(' ');
        }
        text.append(move.san());
        if (move.enPassant()) {
            text.append(" .e.p");
        }
        GameStatus status = move.status();
        if (status == GameStatus.CHECKMATE) {
            text.append(move.whiteMoved() ? "\n1-0" : "\n0-1");
        } else if (status.isDraw()) {
            text.append("\n1/2-1/2");
        }
        return text.toString();
    }
}
