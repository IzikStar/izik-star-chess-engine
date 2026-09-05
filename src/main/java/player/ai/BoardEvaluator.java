package player.ai;

import main.Board;

public interface BoardEvaluator {
    int evaluate(Board board, int depth);
}
