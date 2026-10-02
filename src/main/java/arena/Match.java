package arena;

import ai.Minimax;
import engine.MinimaxEngine;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.MoveResult;

import java.util.List;
import java.util.Random;

/** Plays one arena game between two players. */
public final class Match {

    /** Plies after which an unfinished game is stopped and scored as a draw. */
    public static final int DEFAULT_MAX_PLIES = 300;

    private Match() {}

    /**
     * Plays {@code opening}, then lets the players move in turn until the game ends by the rules
     * or reaches {@code maxPlies} (a draw, reason {@code PLY_CAP}). The same players, opening and
     * {@code seed} always give the same game.
     */
    public static GameRecord play(Player white, Player black, Opening opening, int maxPlies, long seed) {
        Game game = new Game();
        for (String uci : opening.moves()) {
            game.play(uci);
        }
        Minimax.Options whiteOptions = options(white, new Random(seed));
        Minimax.Options blackOptions = options(black, new Random(~seed));
        while (!game.status().isGameOver() && game.plyCount() < maxPlies) {
            boolean whiteToMove = game.fen().split(" ")[1].equals("w");
            Player side = whiteToMove ? white : black;
            ChessMove move = MinimaxEngine.searchAtDepth(game.fen(), side.depth(), side.evaluator(),
                    whiteToMove ? whiteOptions : blackOptions);
            game.play(move);
        }
        List<String> moves = game.moves().stream().map(MoveResult::move).map(ChessMove::toUci).toList();
        GameStatus status = game.status();
        GameRecord.Result result;
        String reason;
        if (status == GameStatus.CHECKMATE) {
            boolean whiteMated = game.fen().split(" ")[1].equals("w");
            result = whiteMated ? GameRecord.Result.BLACK_WINS : GameRecord.Result.WHITE_WINS;
            reason = status.name();
        } else if (status.isGameOver()) {
            result = GameRecord.Result.DRAW;
            reason = status.name();
        } else {
            result = GameRecord.Result.DRAW;
            reason = "PLY_CAP";
        }
        return new GameRecord(white.name(), black.name(), opening.name(), moves, result, reason, seed);
    }

    private static Minimax.Options options(Player player, Random random) {
        return new Minimax.Options(player.variety(), random, player.quiescence());
    }
}
