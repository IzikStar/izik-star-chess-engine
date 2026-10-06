package arena;

import ai.eval.ChessEvaluate;
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
 *       {@code lab.HallOfFame});</li>
 *   <li>{@code fsf}: Fairy-Stockfish at full strength, {@link FairyStockfish#DEFAULT_NODES} nodes a
 *       move, in any built-in variant; {@code fsf:5000} with 5000 nodes a move;</li>
 *   <li>{@code random}: a player that knows only the rules and picks any legal move (it still
 *       takes a win it sees one move ahead), the floor every evaluation should beat.</li>
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

    /** True if {@code spec} names Fairy-Stockfish ({@code fsf} or {@code fsf:NODES}). */
    public static boolean isFairyStockfish(String spec) {
        return spec.equals("fsf") || spec.startsWith("fsf:");
    }

    /** The random mover's name. */
    public static final String RANDOM = "random";

    /**
     * True if {@code spec} names weights for the built-in engine (default, zero, a preset, a file,
     * hof:NAME), false for Stockfish, Fairy-Stockfish and the random mover.
     */
    public static boolean hasWeights(String spec) {
        return !isStockfish(spec) && !isFairyStockfish(spec) && !spec.equals(RANDOM);
    }

    /** Nodes a move of the Fairy-Stockfish {@code spec} names. */
    static long fairyNodes(String spec) {
        if (spec.equals("fsf")) {
            return FairyStockfish.DEFAULT_NODES;
        }
        long nodes = Long.parseLong(spec.substring(4));
        if (nodes < 1) {
            throw new IllegalArgumentException("Fairy-Stockfish needs at least one node a move: " + spec);
        }
        return nodes;
    }

    /** The random mover in a game of {@code variant}: depth 1, no evaluation, any legal move. */
    public static Player random(String name, ai.variant.Variant variant) {
        ai.eval.ParamSchema schema = ai.eval.Evaluators.schema(variant);
        return new Player(name, ai.eval.Evaluators.evaluator(variant, params("zero", HALL_OF_FAME, schema)), 1,
                RANDOM_VARIETY, false);
    }

    /** A margin wider than any score short of a won or lost game: every move is as good as the best. */
    static final int RANDOM_VARIETY = 3 * ai.eval.Evaluator.MATE;

    /** The player {@code spec} names, called by its spec (a file by its name without ".json"). */
    public static Player parse(String spec, int depth, int variety) {
        return parse(spec, label(spec), depth, variety);
    }

    public static Player parse(String spec, String name, int depth, int variety) {
        return parse(spec, name, depth, variety, HALL_OF_FAME);
    }

    /**
     * The player {@code spec} names in a game of {@code variant}: in a variant other than chess,
     * "default" and "zero" are its piece-set evaluation's defaults and all zeros, a file or hall of
     * fame entry holds weights of that evaluation, and Stockfish does not play.
     */
    public static Player parse(String spec, String name, int depth, int variety, Path hallOfFame,
                               ai.variant.Variant variant) {
        if (isFairyStockfish(spec)) {
            return Player.external(name, FairyStockfish.engine(variant, fairyNodes(spec)));
        }
        if (spec.equals(RANDOM)) {
            return random(name, variant);
        }
        if (ai.eval.Evaluators.schema(variant) == ChessEvaluate.SCHEMA) {
            return parse(spec, name, depth, variety, hallOfFame);
        }
        if (isStockfish(spec)) {
            throw new IllegalArgumentException("Stockfish plays chess only, not " + variant.name());
        }
        return Player.of(name, ai.eval.Evaluators.evaluator(variant, params(spec, hallOfFame,
                ai.eval.Evaluators.schema(variant))), depth, variety);
    }

    public static Player parse(String spec, String name, int depth, int variety, Path hallOfFame) {
        if (isFairyStockfish(spec) || spec.equals(RANDOM)) {
            return parse(spec, name, depth, variety, hallOfFame, ai.variant.Variants.CHESS);
        }
        if (isStockfish(spec)) {
            String[] parts = spec.substring(3).split("@");
            int elo = Integer.parseInt(parts[0]);
            long nodes = parts.length > 1 ? Long.parseLong(parts[1]) : ExternalEngine.STOCKFISH_NODES;
            return Player.external(name, ExternalEngine.stockfish(elo, nodes));
        }
        return Player.of(name, new ChessEvaluate(params(spec, hallOfFame)), depth, variety);
    }

    /** The weights {@code spec} names: "default", a preset, a parameter file, or "hof:NAME". */
    public static ParamVector params(String spec) {
        return params(spec, HALL_OF_FAME);
    }

    /** As {@link #params(String)}, looking up "hof:NAME" in {@code hallOfFame}. */
    public static ParamVector params(String spec, Path hallOfFame) {
        return params(spec, hallOfFame, ChessEvaluate.SCHEMA);
    }

    /**
     * The weights of {@code schema} that {@code spec} names: "default", "zero" (every weight 0), a
     * parameter file, "hof:NAME", or (the chess schema only) a preset.
     */
    public static ParamVector params(String spec, Path hallOfFame, ai.eval.ParamSchema schema) {
        if (spec.equals("default")) {
            return schema.defaults();
        }
        if (spec.equals("zero")) {
            int[] zero = new int[schema.size()];
            for (int i = 0; i < zero.length; i++) {
                zero[i] = schema.spec(i).clamp(0);
            }
            return new ParamVector(schema, zero);
        }
        if (spec.startsWith("hof:")) {
            Path entry = hallOfFame.resolve(spec.substring(4) + ".json");
            try {
                // an entry is a JSON object whose "params" are the weights
                String json = com.google.gson.JsonParser.parseString(Files.readString(entry)).getAsJsonObject()
                        .get("params").toString();
                return ParamVector.fromJson(schema, json);
            } catch (IOException e) {
                throw new IllegalArgumentException("no hall of fame entry " + spec.substring(4) + " in " + hallOfFame, e);
            }
        }
        Path file = Path.of(spec);
        if (Files.isRegularFile(file)) {
            try {
                return ParamVector.fromJson(schema, Files.readString(file));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        if (schema != ChessEvaluate.SCHEMA) {
            throw new IllegalArgumentException("no player " + spec + " for this game (use default, zero, a file or hof:NAME)");
        }
        return ChessEvaluate.preset(spec);
    }

    /** How results name the player {@code spec}: a file by its name without ".json", Stockfish as "sf1500". */
    public static String label(String spec) {
        if (spec.startsWith("hof:")) {
            return spec.substring(4);
        }
        if (isStockfish(spec)) {
            return "sf" + spec.substring(3);
        }
        if (isFairyStockfish(spec)) {
            return "fsf" + fairyNodes(spec);
        }
        Path file = Path.of(spec);
        return Files.isRegularFile(file) ? file.getFileName().toString().replaceFirst("\\.json$", "") : spec;
    }
}
