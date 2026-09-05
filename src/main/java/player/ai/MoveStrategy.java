package player.ai;

import main.Board;
import main.Move;

public interface MoveStrategy {
    Move execute(Board board, int depth);
}

