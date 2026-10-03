package arena;

import ai.BitBoard.BitBoardEvaluate;
import ai.eval.ParamVector;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Players named by text, as the command lines and run settings name them:
 * <ul>
 *   <li>{@code default}: the schema defaults, where tuning and evolution start;</li>
 *   <li>a preset name, e.g. {@code classic}: the hand-written weights the game plays with;</li>
 *   <li>a parameter file (JSON, see {@link ParamVector});</li>
 *   <li>{@code sf:1500}: Stockfish held to UCI_Elo 1500, {@link ExternalEngine#STOCKFISH_NODES}
 *       nodes a move; {@code sf:1500@50000} with 50000 nodes a move.</li>
 * </ul>
 * Built-in players search at the depth and variety given.
 */
public final class Players {

    private Players() {}

    /** True if {@code spec} names Stockfish ({@code sf:...}). */
    public static boolean isStockfish(String spec) {
        return spec.startsWith("sf:");
    }

    /** The player {@code spec} names, called by its spec (a file by its name without ".json"). */
    public static Player parse(String spec, int depth, int variety) {
        return parse(spec, label(spec), depth, variety);
    }

    public static Player parse(String spec, String name, int depth, int variety) {
        if (isStockfish(spec)) {
            String[] parts = spec.substring(3).split("@");
            int elo = Integer.parseInt(parts[0]);
            long nodes = parts.length > 1 ? Long.parseLong(parts[1]) : ExternalEngine.STOCKFISH_NODES;
            return Player.external(name, ExternalEngine.stockfish(elo, nodes));
        }
        return Player.of(name, new BitBoardEvaluate(params(spec)), depth, variety);
    }

    /** The weights {@code spec} names: "default", a preset, or a parameter file. */
    public static ParamVector params(String spec) {
        if (spec.equals("default")) {
            return BitBoardEvaluate.SCHEMA.defaults();
        }
        Path file = Path.of(spec);
        if (Files.isRegularFile(file)) {
            try {
                return ParamVector.fromJson(BitBoardEvaluate.SCHEMA, Files.readString(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return BitBoardEvaluate.preset(spec);
    }

    /** How results name the player {@code spec}: a file by its name without ".json", Stockfish as "sf1500". */
    public static String label(String spec) {
        if (isStockfish(spec)) {
            return "sf" + spec.substring(3);
        }
        Path file = Path.of(spec);
        return Files.isRegularFile(file) ? file.getFileName().toString().replaceFirst("\\.json$", "") : spec;
    }
}
