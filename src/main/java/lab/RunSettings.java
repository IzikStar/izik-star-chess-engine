package lab;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * How an evolution run plays. Stored with the run, so a resumed run plays the same way.
 *
 * @param generations          how many generations to play (a resumed run continues up to it)
 * @param depth                fixed search depth of every game
 * @param openingsPerPairing   each pairing plays this many openings, each once with each colour
 * @param variety              how far below the best move (pawn = 100) a move may score and still be played
 * @param maxPlies             a game still going after this many plies is a draw
 * @param threads              games played at once
 * @param seed                 the same seed, algorithm and settings give the same run
 * @param yardstickEvery       every this many generations (and after the last) the champion
 *                             plays the default weights; 0 never
 * @param yardstickOpenings    openings of that match, each with both colours
 */
public record RunSettings(int generations, int depth, int openingsPerPairing, int variety, int maxPlies,
                          int threads, long seed, int yardstickEvery, int yardstickOpenings) {

    public RunSettings {
        if (generations < 1 || depth < 1 || openingsPerPairing < 1 || maxPlies < 1 || threads < 1
                || variety < 0 || yardstickEvery < 0 || yardstickOpenings < 0) {
            throw new IllegalArgumentException("invalid run settings");
        }
    }

    public static RunSettings defaults() {
        return new RunSettings(20, 3, 2, 20, 300, Math.max(1, Runtime.getRuntime().availableProcessors() - 1),
                1, 5, 20);
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
        return o.toString();
    }

    public static RunSettings fromJson(String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        return new RunSettings(o.get("generations").getAsInt(), o.get("depth").getAsInt(),
                o.get("openingsPerPairing").getAsInt(), o.get("variety").getAsInt(), o.get("maxPlies").getAsInt(),
                o.get("threads").getAsInt(), o.get("seed").getAsLong(), o.get("yardstickEvery").getAsInt(),
                o.get("yardstickOpenings").getAsInt());
    }
}
