package lab;

import arena.Players;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * How an evolution run plays. Stored with the run, so a resumed run plays the same way.
 *
 * @param generations          how many generations to play (a resumed run continues up to it)
 * @param depth                search depth of the games (the deep games aside)
 * @param openingsPerPairing   each pairing plays this many openings, each once with each colour
 * @param variety              how far below the best move (pawn = 100) a move may score and still be played
 * @param maxPlies             a game still going after this many plies is a draw
 * @param threads              games played at once
 * @param seed                 the same seed, algorithm and settings give the same run
 * @param yardstickEvery       every this many generations (and after the last) the champion
 *                             plays the yardsticks; 0 never
 * @param yardstickOpenings    openings of each yardstick match, each with both colours
 * @param yardsticks           who the champion is measured against, as {@link Players} names them
 *                             ("default", "classic", a file, "sf:1500"), plus "sf:auto": Stockfish
 *                             at the strength where the champion scores between 30% and 70%. The
 *                             first is the one the Lab chart follows.
 * @param stockfishFrom        Stockfish yardsticks play from this generation on
 * @param deepDepth            depth of the deep games; 0 = no deep games
 * @param deepShareFirst       percent of the games played at {@code deepDepth} in generation 0
 * @param deepShareLast        ... and in the last generation; the share in between grows evenly.
 *                             Yardstick matches use the same share.
 * @param memberStockfishOpenings every member of every generation plays Stockfish over this many
 *                             openings, each with both colours, at {@code depth}; 0 = never. The
 *                             level moves like "sf:auto", following the population's average score.
 *                             The algorithm sees the results ({@code Generation.stockfishScore}).
 */
public record RunSettings(int generations, int depth, int openingsPerPairing, int variety, int maxPlies,
                          int threads, long seed, int yardstickEvery, int yardstickOpenings,
                          List<String> yardsticks, int stockfishFrom, int deepDepth, int deepShareFirst,
                          int deepShareLast, int memberStockfishOpenings) {

    /** Stockfish at the strength that suits the champion; see {@link #yardsticks}. */
    public static final String STOCKFISH_AUTO = "sf:auto";

    public RunSettings {
        yardsticks = List.copyOf(yardsticks);
        if (generations < 1 || depth < 1 || openingsPerPairing < 1 || maxPlies < 1 || threads < 1
                || variety < 0 || yardstickEvery < 0 || yardstickOpenings < 0 || stockfishFrom < 0
                || deepDepth < 0 || deepShareFirst < 0 || deepShareFirst > 100 || deepShareLast < 0
                || deepShareLast > 100 || memberStockfishOpenings < 0) {
            throw new IllegalArgumentException("invalid run settings");
        }
        if (yardsticks.stream().distinct().count() != yardsticks.size()) {
            throw new IllegalArgumentException("a yardstick is listed twice");
        }
    }

    /** Settings without Stockfish games for every member. */
    public RunSettings(int generations, int depth, int openingsPerPairing, int variety, int maxPlies,
                       int threads, long seed, int yardstickEvery, int yardstickOpenings,
                       List<String> yardsticks, int stockfishFrom, int deepDepth, int deepShareFirst,
                       int deepShareLast) {
        this(generations, depth, openingsPerPairing, variety, maxPlies, threads, seed, yardstickEvery,
                yardstickOpenings, yardsticks, stockfishFrom, deepDepth, deepShareFirst, deepShareLast, 0);
    }

    /** The settings of a run from before yardstick lists and deep games: one yardstick, one depth. */
    public RunSettings(int generations, int depth, int openingsPerPairing, int variety, int maxPlies,
                       int threads, long seed, int yardstickEvery, int yardstickOpenings) {
        this(generations, depth, openingsPerPairing, variety, maxPlies, threads, seed, yardstickEvery,
                yardstickOpenings, List.of("default"), 0, 0, 0, 0, 0);
    }

    public static RunSettings defaults() {
        return new RunSettings(20, 3, 2, 20, 300, Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                1, 5, 20, List.of("default", "classic", STOCKFISH_AUTO), 10, 4, 10, 40, 0);
    }

    /** Percent of generation {@code number}'s games played at {@link #deepDepth}. */
    public int deepShare(int number) {
        if (deepDepth == 0) {
            return 0;
        }
        if (generations == 1) {
            return deepShareLast;
        }
        return Math.round(deepShareFirst + (deepShareLast - deepShareFirst) * (float) number / (generations - 1));
    }

    /** The yardsticks generation {@code number} plays (Stockfish only from {@link #stockfishFrom}). */
    public List<String> yardsticksAt(int number) {
        return yardsticks.stream().filter(y -> !Players.isStockfish(y) || number >= stockfishFrom).toList();
    }

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("generations", generations);
        o.addProperty("depth", depth);
        o.addProperty("openingsPerPairing", openingsPerPairing);
        o.addProperty("variety", variety);
        o.addProperty("maxPlies", maxPlies);
        o.addProperty("threads", threads);
        o.addProperty("seed", seed);
        o.addProperty("yardstickEvery", yardstickEvery);
        o.addProperty("yardstickOpenings", yardstickOpenings);
        JsonArray list = new JsonArray();
        yardsticks.forEach(list::add);
        o.add("yardsticks", list);
        o.addProperty("stockfishFrom", stockfishFrom);
        o.addProperty("deepDepth", deepDepth);
        o.addProperty("deepShareFirst", deepShareFirst);
        o.addProperty("deepShareLast", deepShareLast);
        o.addProperty("memberStockfishOpenings", memberStockfishOpenings);
        return o.toString();
    }

    /** Reads {@link #toJson}; a run saved before a setting existed plays as it did then. */
    public static RunSettings fromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        List<String> yardsticks = new ArrayList<>();
        if (o.has("yardsticks")) {
            o.getAsJsonArray("yardsticks").forEach(e -> yardsticks.add(e.getAsString()));
        } else {
            yardsticks.add("default");
        }
        return new RunSettings(o.get("generations").getAsInt(), o.get("depth").getAsInt(),
                o.get("openingsPerPairing").getAsInt(), o.get("variety").getAsInt(), o.get("maxPlies").getAsInt(),
                o.get("threads").getAsInt(), o.get("seed").getAsLong(), o.get("yardstickEvery").getAsInt(),
                o.get("yardstickOpenings").getAsInt(), yardsticks, intOr(o, "stockfishFrom", 0),
                intOr(o, "deepDepth", 0), intOr(o, "deepShareFirst", 0), intOr(o, "deepShareLast", 0),
                intOr(o, "memberStockfishOpenings", 0));
    }

    private static int intOr(JsonObject o, String key, int fallback) {
        return o.has(key) ? o.get(key).getAsInt() : fallback;
    }
}
