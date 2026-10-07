package lab;

import ai.eval.ChessEvaluate;
import ai.eval.Evaluators;
import ai.eval.ParamSchema;
import ai.variant.Variant;
import ai.variant.VariantJson;
import ai.variant.Variants;
import ai.eval.ParamVector;
import arena.GameRecord;
import arena.Score;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Every individual worth keeping, from every run: one JSON file each in a folder
 * ({@code runs/hall-of-fame/} by default), with its weights, where it came from, how it did
 * against the yardsticks, and its games as PGN. The arena and the run settings name an entry
 * {@code hof:NAME}; the Lab tab lists them and plays against them.
 *
 * <p>The runner keeps each run's last champion and every champion that beat a yardstick (the
 * whole 95% interval above 0); {@code lab.Cli keep} keeps any member by hand, with a note.
 */
public final class HallOfFame {

    /** The folder next to the runs, where the runner keeps its entries. */
    public static final String FOLDER = "hall-of-fame";

    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9._-]+");
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    /**
     * One kept individual.
     *
     * @param run        the run file it came from (e.g. "try1.db"), or null if added another way
     * @param reason     why it was kept: "last champion", "beat classic", or the note given by hand
     * @param yardsticks how it did, one line each, e.g. "classic, depth 3: +12 =3 -5 (67.5%), Elo +127 [+8, +276]"
     * @param pgn        its games, PGN
     * @param variant    the game its weights are for; chess when null
     */
    public record Entry(String name, String reason, String savedAt, String run, String runName, int generation,
                        int member, ParamVector params, List<String> yardsticks, String pgn, Variant variant) {

        /** A chess entry. */
        public Entry(String name, String reason, String savedAt, String run, String runName, int generation,
                     int member, ParamVector params, List<String> yardsticks, String pgn) {
            this(name, reason, savedAt, run, runName, generation, member, params, yardsticks, pgn, null);
        }

        /** The game its weights are for. */
        public Variant game() {
            return variant == null ? Variants.CHESS : variant;
        }

        public Entry {
            if (!NAME.matcher(name).matches()) {
                throw new IllegalArgumentException("a hall of fame name is letters, digits, '.', '_' and '-': " + name);
            }
            yardsticks = List.copyOf(yardsticks);
        }
    }

    private final Path dir;

    public HallOfFame(Path dir) {
        this.dir = dir;
    }

    /** The hall of fame beside the run file {@code runFile}. */
    public static HallOfFame besides(Path runFile) {
        Path parent = runFile.toAbsolutePath().getParent();
        return new HallOfFame(parent.resolve(FOLDER));
    }

    public Path dir() {
        return dir;
    }

    /** Keeps {@code entry}, replacing an entry of the same name. */
    public void save(Entry entry) {
        JsonObject o = new JsonObject();
        o.addProperty("name", entry.name());
        o.addProperty("reason", entry.reason());
        o.addProperty("savedAt", entry.savedAt());
        o.addProperty("run", entry.run());
        o.addProperty("runName", entry.runName());
        o.addProperty("generation", entry.generation());
        o.addProperty("member", entry.member());
        JsonArray yardsticks = new JsonArray();
        entry.yardsticks().forEach(yardsticks::add);
        o.add("yardsticks", yardsticks);
        o.add("params", JsonParser.parseString(entry.params().toJson()));
        if (entry.variant() != null && !entry.variant().equals(Variants.CHESS)) {
            o.add("variant", VariantJson.toTree(entry.variant()));
        }
        o.addProperty("pgn", entry.pgn());
        try {
            Files.createDirectories(dir);
            Files.writeString(dir.resolve(entry.name() + ".json"), GSON.toJson(o));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Keeps {@code entry}; if an entry of that name is already kept (the same member kept again),
     * the reasons are joined rather than replaced.
     */
    public Entry add(Entry entry) {
        Entry kept = get(entry.name()).filter(old -> !old.reason().contains(entry.reason()))
                .map(old -> new Entry(entry.name(), old.reason() + "; " + entry.reason(), entry.savedAt(), entry.run(),
                        entry.runName(), entry.generation(), entry.member(), entry.params(), entry.yardsticks(),
                        entry.pgn(), entry.variant()))
                .orElse(entry);
        save(kept);
        return kept;
    }

    public Optional<Entry> get(String name) {
        if (!NAME.matcher(name).matches()) {
            return Optional.empty();
        }
        Path file = dir.resolve(name + ".json");
        return Files.isRegularFile(file) ? Optional.of(read(file)) : Optional.empty();
    }

    /** Every entry, newest first. */
    public List<Entry> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(f -> f.getFileName().toString().endsWith(".json")).map(HallOfFame::read)
                    .sorted(Comparator.comparing(Entry::savedAt).reversed()).toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static Entry read(Path file) {
        try {
            JsonObject o = JsonParser.parseString(Files.readString(file)).getAsJsonObject();
            List<String> yardsticks = new ArrayList<>();
            o.getAsJsonArray("yardsticks").forEach(e -> yardsticks.add(e.getAsString()));
            Variant variant = o.has("variant") ? VariantJson.fromTree(o.getAsJsonObject("variant")) : null;
            ParamSchema schema = variant == null ? ChessEvaluate.SCHEMA : Evaluators.schema(variant);
            return new Entry(o.get("name").getAsString(), o.get("reason").getAsString(), o.get("savedAt").getAsString(),
                    string(o, "run"), string(o, "runName"), o.get("generation").getAsInt(), o.get("member").getAsInt(),
                    ParamVector.fromJson(schema, o.get("params").toString()), yardsticks,
                    string(o, "pgn"), variant);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static String string(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    // ---- from a run ---------------------------------------------------------------------------

    /** A name for member {@code member} of {@code generation} in the run named {@code runName}. */
    public static String nameFor(String runName, int generation, int member) {
        String base = runName.replaceAll("[^A-Za-z0-9._-]+", "-");
        return base + "-g" + generation + "-m" + member;
    }

    /**
     * An entry for member {@code member} of {@code generation} in the run in {@code store}: its
     * weights, its yardstick results (if it was that generation's champion) and every game it
     * played that generation.
     */
    public static Entry fromRun(RunStore store, Path runFile, int generation, int member, String name, String reason) {
        RunStore.RunRow run = store.run().orElseThrow(() -> new IllegalArgumentException("no run in " + runFile));
        Variant variant = run.settings().variant();
        List<ParamVector> members = store.members(generation, Evaluators.schema(variant));
        if (member < 0 || member >= members.size()) {
            throw new IllegalArgumentException("generation " + generation + " has no member " + member);
        }
        Optional<RunStore.GenerationRow> row = store.generations().stream().filter(r -> r.number() == generation).findFirst();
        List<String> yardsticks = new ArrayList<>();
        boolean champion = row.isPresent() && row.get().champion() == member;
        if (champion) {
            row.get().yardsticks().forEach(y -> yardsticks.add(y.label() + ", depth " + y.depth() + ": "
                    + y.score().toString().replaceFirst("^champion: ", "")));
        }
        StringBuilder pgn = new StringBuilder();
        String self = String.valueOf(member);
        for (String kind : List.of("population", "yardstick", "stockfish")) {
            if (kind.equals("yardstick") && !champion) {
                continue;
            }
            List<GameRecord> games = store.games(generation, kind);
            List<Integer> depths = store.gameDepths(generation, kind);
            for (int i = 0; i < games.size(); i++) {
                GameRecord g = games.get(i);
                if (!kind.equals("yardstick") && !g.white().equals(self) && !g.black().equals(self)) {
                    continue;
                }
                GameRecord named = kind.equals("yardstick")
                        ? new GameRecord(g.white().equals(self) ? name : g.white(), g.black().equals(self) ? name : g.black(),
                        g.opening(), g.moves(), g.result(), g.reason(), g.seed())
                        : g;
                pgn.append(arena.GamePgn.write(variant, named, run.name(), generation, depths.get(i))).append('\n');
            }
        }
        String fileName = runFile.getFileName().toString();
        return new Entry(name, reason, Instant.now().toString(), fileName, run.name(), generation, member,
                members.get(member), yardsticks, pgn.toString(), variant.equals(Variants.CHESS) ? null : variant);
    }

    /**
     * What the runner keeps after generation {@code row}: the champion if any yardstick's whole
     * interval is above 0 (it beat that yardstick), and the last generation's champion.
     */
    static List<String> reasonsToKeep(RunStore.GenerationRow row, boolean last) {
        List<String> reasons = new ArrayList<>();
        List<String> seen = new ArrayList<>();
        for (RunStore.YardstickResult y : row.yardsticks()) {
            if (seen.contains(y.opponent())) {
                continue;
            }
            seen.add(y.opponent());
            Score s = RunStore.YardstickResult.combined(row.yardsticks(), y.opponent()).orElseThrow();
            if (s.eloLow() > 0) {
                reasons.add("beat " + y.label());
            }
        }
        if (last) {
            reasons.add(0, "last champion");
        }
        return reasons;
    }
}
