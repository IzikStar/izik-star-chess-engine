package player.ai;

import main.Board;
import main.Move;

public class MiniMax implements MoveStrategy{
    private final BoardEvaluator boardEvaluator;

    public MiniMax() {
        this.boardEvaluator = null;
    }

    @Override
    public String toString() {
        return "MiniMax";
    }

    @Override
    public Move execute(Board board, int depth) {
        return null;
    }

    public int min(final Board board, final int depth) {
        if (depth == 0 /* || gameOver */) {
            return this.boardEvaluator.evaluate(board, depth);
        }
        return 0;
    }



}
