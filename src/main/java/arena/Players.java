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
 *       nodes a move; {@code sf:1500@50000} with 50000 nodes a move;</li>
 *   <li>{@code hof:NAME}: an entry of the hall of fame ({@code runs/hall-of-fame/NAME.json}, see
 *       {@code lab.HallOfFame}).</li>
 * </ul>
 * Built-in players search at the depth and variety given.
 */
public final class Players {

    /** Where {@code hof:NAME} is looked up. */
    public static final Path HALL_OF_FAME = Path.of("runs", "hall-of-fame");

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
        return parse(spec, name, depth, variety, HALL_OF_FAME);
    }

    public static Player parse(String spec, String name, int depth, int variety, Path hallOfFame) {
        if (isStockfish(spec)) {
            String[] parts = spec.substring(3).split("@");
            int elo = Integer.parseInt(parts[0]);
            long nodes = parts.length > 1 ? Long.parseLong(parts[1]) : ExternalEngine.STOCKFISH_NODES;
            return Player.external(name, ExternalEngine.stockfish(elo, nodes));
        }
        return Player.of(name, new BitBoardEvaluate(params(spec, hallOfFame)), depth, variety);
    }

    /** The weights {@code spec} names: "default", a preset, a parameter file, or "hof:NAME". */
    public static ParamVector params(String spec) {
        return params(spec, HALL_OF_FAME);
    }

    /** As {@link #params(String)}, looking up "hof:NAME" in {@code hallOfFame}. */
    public static ParamVector params(String spec, Path hallOfFame) {
        if (spec.equals("default")) {
            return BitBoardEvaluate.SCHEMA.defaults();
        }
        if (spec.startsWith("hof:")) {
            Path entry = hallOfFame.resolve(spec.substring(4) + ".json");
            try {
                // an entry is a JSON object whose "params" are the weights
                String json = com.google.gson.JsonParser.parseString(Files.readString(entry)).getAsJsonObject()
                        .get("params").toString();
                return ParamVector.fromJson(BitBoardEvaluate.SCHEMA, json);
            } catch (IOException e) {
                throw new IllegalArgumentException("no hall of fame entry " + spec.substring(4) + " in " + hallOfFame, e);
            }
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
        if (spec.startsWith("hof:")) {
            return spec.substring(4);
        }
        if (isStockfish(spec)) {
            return "sf" + spec.substring(3);
        }
        Path file = Path.of(spec);
        return Files.isRegularFile(file) ? file.getFileName().toString().replaceFirst("\\.json$", "") : spec;
    }
}
