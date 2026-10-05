package game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * The player's games, one JSON file each in a folder ({@code games/} by default). Files are
 * written whole to a temporary file and then moved into place, so a crash mid-write never leaves
 * half a game behind. Thread-safe: the game thread writes while the web server's threads read.
 */
public final class GameArchive {

    private static final Pattern ID = Pattern.compile("[0-9A-Za-z_-]{1,64}");
    private static final DateTimeFormatter ID_TIME = DateTimeFormatter.ofPattern("yyyy-MM-dd_HH-mm-ss").withZone(ZoneId.systemDefault());

    private final Path dir;

    public GameArchive(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    /** A fresh id: when the game started, readable in a file browser, plus a few random letters. */
    public String newId(Instant started) {
        return ID_TIME.format(started) + "_" + Integer.toString(ThreadLocalRandom.current().nextInt(36 * 36 * 36 * 36), 36);
    }

    public synchronized void save(SavedGame game) {
        Path file = file(game.id());
        try {
            Files.createDirectories(dir);
            Path tmp = dir.resolve(game.id() + ".json.tmp");
            Files.writeString(tmp, toJson(game).toString(), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not save the game to " + file, e);
        }
    }

    /** Every saved game, newest first. A file that cannot be read is skipped (and reported). */
    public synchronized List<SavedGame> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<SavedGame> games = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).toList()) {
                try {
                    games.add(read(f));
                } catch (IOException | RuntimeException e) {
                    System.err.println("Skipping unreadable saved game " + f + ": " + e);
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        games.sort(Comparator.comparing(SavedGame::started).reversed());
        return games;
    }

    public synchronized Optional<SavedGame> get(String id) {
        if (!ID.matcher(id).matches() || !Files.isRegularFile(file(id))) {
            return Optional.empty();
        }
        try {
            return Optional.of(read(file(id)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Removes a game; false if there was none by that id. */
    public synchronized boolean delete(String id) {
        if (!ID.matcher(id).matches()) {
            return false;
        }
        try {
            return Files.deleteIfExists(file(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(String id) {
        if (!ID.matcher(id).matches()) {
            throw new IllegalArgumentException("not a game id: " + id);
        }
        return dir.resolve(id + ".json");
    }

    private static SavedGame read(Path file) throws IOException {
        return fromJson(JsonParser.parseString(Files.readString(file, StandardCharsets.UTF_8)).getAsJsonObject());
    }

    // ---- JSON --------------------------------------------------------------

    static JsonObject toJson(SavedGame g) {
        JsonObject o = new JsonObject();
        o.addProperty("version", 1);
        o.addProperty("id", g.id());
        o.addProperty("started", g.started().toString());
        o.addProperty("updated", g.updated().toString());
        GameConfig c = g.config();
        o.addProperty("mode", c.mode().name());
        o.addProperty("humanPlaysWhite", c.humanPlaysWhite());
        o.addProperty("level", c.skillLevel());
        o.addProperty("blackLevel", c.blackSkillLevel());
        o.addProperty("championRun", g.championRun());
        o.addProperty("championGeneration", g.championGeneration());
        o.addProperty("opponentLabel", g.opponentLabel());
        o.addProperty("initialMs", g.timeControl().initialMs());
        o.addProperty("incrementMs", g.timeControl().incrementMs());
        o.addProperty("whiteMs", g.whiteMs());
        o.addProperty("blackMs", g.blackMs());
        o.addProperty("startFen", g.startFen());
        JsonArray moves = new JsonArray();
        g.moves().forEach(moves::add);
        o.add("moves", moves);
        o.addProperty("result", g.result());
        o.addProperty("termination", g.termination());
        o.addProperty("pgn", g.pgn());
        o.addProperty("weights", g.weights());
        o.addProperty("variant", g.variant());
        if (g.variantDef() != null) {
            o.add("variantDef", JsonParser.parseString(g.variantDef()));
        }
        return o;
    }

    static SavedGame fromJson(JsonObject o) {
        GameConfig config = new GameConfig(GameConfig.Mode.valueOf(o.get("mode").getAsString()),
                o.get("humanPlaysWhite").getAsBoolean(), o.get("level").getAsInt(), o.get("blackLevel").getAsInt());
        List<String> moves = new ArrayList<>();
        o.getAsJsonArray("moves").forEach(m -> moves.add(m.getAsString()));
        return new SavedGame(o.get("id").getAsString(), Instant.parse(o.get("started").getAsString()),
                Instant.parse(o.get("updated").getAsString()), config,
                string(o, "championRun"), o.has("championGeneration") && !o.get("championGeneration").isJsonNull()
                        ? o.get("championGeneration").getAsInt() : null,
                string(o, "opponentLabel"),
                new TimeControl(o.get("initialMs").getAsLong(), o.get("incrementMs").getAsLong()),
                number(o, "whiteMs"), number(o, "blackMs"), o.get("startFen").getAsString(), moves,
                string(o, "result"), string(o, "termination"), string(o, "pgn"), string(o, "weights"),
                string(o, "variant"), o.has("variantDef") && !o.get("variantDef").isJsonNull()
                        ? o.get("variantDef").toString() : null);
    }

    private static String string(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static Long number(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsLong();
    }
}
