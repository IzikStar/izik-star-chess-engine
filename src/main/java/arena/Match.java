package arena;

import ai.Minimax;
import engine.MinimaxEngine;
import ai.variant.Variant;
import ai.variant.Variants;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.MoveResult;

import java.util.ArrayList;
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
        return play(Variants.CHESS, white, black, opening, maxPlies, seed);
    }

    /**
     * A game of {@code variant}, as {@link #play(Player, Player, Opening, int, long)}. Outside chess
     * the only engine from outside this program is Fairy-Stockfish set to that variant.
     */
    public static GameRecord play(Variant variant, Player white, Player black, Opening opening, int maxPlies, long seed) {
        boolean chess = variant.equals(Variants.CHESS);
        for (Player p : List.of(white, black)) {
            if (!chess && p.isExternal() && !FairyStockfish.playsAs(p.external(), variant)) {
                throw new IllegalArgumentException(p.name() + " does not play " + variant.name()
                        + ": outside chess only Fairy-Stockfish set to it does");
            }
        }
        Game game = new Game(variant);
        for (String uci : opening.moves()) {
            game.play(uci);
        }
        Minimax.Options whiteOptions = options(white, new Random(seed));
        Minimax.Options blackOptions = options(black, new Random(~seed));
        List<Integer> scores = new ArrayList<>(); // before each move after the opening, from White's side
        boolean anyScore = false;
        try (UciSession whiteUci = white.isExternal() ? new UciSession(white.external()) : null;
             UciSession blackUci = black.isExternal() ? new UciSession(black.external()) : null) {
            for (int i = 0; i < opening.moves().size(); i++) {
                scores.add(GameRecord.NO_SCORE);
            }
            while (!game.status().isGameOver() && game.plyCount() < maxPlies) {
                boolean whiteToMove = game.fen().split(" ")[1].equals("w");
                Player side = whiteToMove ? white : black;
                UciSession uci = whiteToMove ? whiteUci : blackUci;
                Minimax.Options options = whiteToMove ? whiteOptions : blackOptions;
                ChessMove move;
                int score = GameRecord.NO_SCORE;
                if (uci != null) {
                    move = uci.move(game.moves().stream().map(MoveResult::move).map(ChessMove::toUci).toList());
                    if (uci.lastScore() != UciSession.NO_SCORE) {
                        score = whiteToMove ? uci.lastScore() : -uci.lastScore();
                        anyScore = true;
                    }
                } else if (!chess) {
                    move = MinimaxEngine.searchAtDepth(variant, game.history(), side.depth(), side.evaluator(), options);
                } else if (side.moveMillis() > 0) {
                    move = MinimaxEngine.searchWithin(game.fen(), game.history(), side.depth(), side.evaluator(), options,
                            side.moveMillis());
                } else {
                    move = MinimaxEngine.searchAtDepth(game.fen(), game.history(), side.depth(), side.evaluator(), options);
                }
                game.play(move);
                scores.add(score);
            }
        }
        List<String> moves = game.moves().stream().map(MoveResult::move).map(ChessMove::toUci).toList();
        GameStatus status = game.status();
        GameRecord.Result result;
        String reason;
        String score = status.result(game.fen().split(" ")[1].equals("w"));
        if (score != null && !status.isDraw()) {
            result = score.equals("1-0") ? GameRecord.Result.WHITE_WINS : GameRecord.Result.BLACK_WINS;
            reason = status.name();
        } else if (status.isGameOver()) {
            result = GameRecord.Result.DRAW;
            reason = status.name();
        } else {
            result = GameRecord.Result.DRAW;
            reason = "PLY_CAP";
        }
        return new GameRecord(white.name(), black.name(), opening.name(), moves, result, reason, seed,
                anyScore ? scores : List.of());
    }

    private static Minimax.Options options(Player player, Random random) {
        return new Minimax.Options(player.variety(), random, player.quiescence(), player.speedups(), player.speedups());
    }
}
