package game;

import java.time.Instant;
import java.util.List;

/**
 * One game the player played in the app, as {@link GameArchive} keeps it: saved after every move,
 * so a game left unfinished (a new game started, the app closed) is kept too and can be continued.
 *
 * @param id                 the archive's name for it (also its file name)
 * @param started            when the game began
 * @param updated            when it was last saved
 * @param config             who played each side, and the engine's level
 * @param championRun        the evolution run whose champion the engine played as, or {@code null}
 * @param championGeneration that champion's generation, or {@code null}
 * @param opponentLabel      the champion's name as the game showed it, or {@code null}
 * @param timeControl        the time control ({@link TimeControl#NONE} when untimed)
 * @param whiteMs            White's time left when last saved, or {@code null} in an untimed game
 * @param blackMs            Black's time left when last saved, or {@code null} in an untimed game
 * @param startFen           the position the game started from
 * @param moves              the moves, in UCI
 * @param result             {@code "1-0"}, {@code "0-1"}, {@code "1/2-1/2"}, or {@code null} if unfinished
 * @param termination        how it ended, in words ("White won by checkmate"), or {@code null}
 * @param pgn                the game in PGN
 * @param weights            the built-in engine's weights ({@code engine.Weights}: "tuned" or
 *                           "classic"); games saved before the choice existed were "classic"
 * @param variant            the variant's id ({@code ai.variant.Variants}); games saved before
 *                           variants existed were chess
 * @param variantDef         a made variant's whole definition ({@code ai.variant.VariantJson}) as it
 *                           was when the game was played, or {@code null} for a built-in one
 */
public record SavedGame(String id, Instant started, Instant updated, GameConfig config,
                        String championRun, Integer championGeneration, String opponentLabel,
                        TimeControl timeControl, Long whiteMs, Long blackMs,
                        String startFen, List<String> moves, String result, String termination, String pgn, String weights,
                        String variant, String variantDef) {

    public SavedGame {
        moves = List.copyOf(moves);
        weights = weights == null ? "classic" : weights;
        variant = variant == null ? "chess" : variant;
    }

    public boolean finished() {
        return result != null;
    }
}
