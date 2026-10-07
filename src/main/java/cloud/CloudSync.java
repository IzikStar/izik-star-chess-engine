package cloud;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lab.RunStore;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Base64;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Keeps this copy's runs, saved games, variants and hall of fame in the shared database, and brings
 * in what the other copies saved there.
 *
 * <p>Every copy keeps writing its own files exactly as before; nothing waits on the network. A
 * background pass (every {@link #PERIOD}) compares the files with what was last synced and sends
 * what changed. If the database cannot be reached, or a daily quota runs out, the pass stops and
 * tries again later, and the next pass that gets through sends everything still waiting: the files
 * themselves are the queue.
 *
 * <ul>
 *   <li><b>Folders</b> (saved games, variants, hall of fame): each file is one row, its content
 *       gzipped in {@code blobs}. Last write wins; a file changed here and elsewhere since the last
 *       sync keeps this copy's version.</li>
 *   <li><b>Runs</b>: a run belongs to the copy that plays it. Each finished generation is one row in
 *       {@code run_generations} (summary, yardsticks, members) plus its games packed and gzipped in
 *       {@code blobs}, so a generation of hundreds of games costs a few rows, not hundreds. Other
 *       copies rebuild the run file from those rows as a read-only replica, which the Lab lists like
 *       any run.</li>
 * </ul>
 *
 * <p>The database is SQLite (Cloudflare D1), so it can be queried directly: {@code runs} and
 * {@code run_generations} hold everything but the games and file contents.
 */
public final class CloudSync implements AutoCloseable {

    public static final Duration PERIOD = Duration.ofSeconds(30);
    /** Longest text sent in one statement; D1 caps a statement at 100 KB. */
    static final int CHUNK = 90_000;
    /** At most this many generations of a replica are brought in per pass, so a pass stays short. */
    static final int PULL_GENERATIONS_PER_PASS = 20;
    static final Duration LIMIT_BACKOFF = Duration.ofMinutes(30);
    static final Duration MAX_BACKOFF = Duration.ofMinutes(15);
    /** How often a copy notes in {@code copies} that it is alive. */
    static final Duration HEARTBEAT = Duration.ofMinutes(10);

    private static final Pattern RUN_FILE = Pattern.compile("[A-Za-z0-9._-]+\\.db");
    private static final String NOW_MS = "CAST((julianday('now') - 2440587.5) * 86400000 AS INTEGER)";

    private final Sql db;
    private final String copy;
    private final Map<String, Path> folders;
    private final Path runs;
    private final Path stateFile;
    private ScheduledExecutorService timer;
    private boolean schemaReady;
    private Instant lastHeartbeat;

    // what the status line shows
    private volatile Instant lastSync;
    private volatile String lastError;
    private volatile Instant retryAt;
    private volatile int waitingFiles;
    private volatile int waitingGenerations;
    private volatile int failures;
    private final Set<String> warnings = new LinkedHashSet<>();

    /**
     * @param folders   the folders to share, by the name they have in the database
     *                  ({@code games}, {@code variants}, {@code hall-of-fame})
     * @param runs      the folder of run files
     * @param stateFile where this copy remembers what it last synced
     */
    public CloudSync(Sql db, String copy, Map<String, Path> folders, Path runs, Path stateFile) {
        this.db = db;
        this.copy = copy;
        this.folders = new LinkedHashMap<>(folders);
        this.runs = runs;
        this.stateFile = stateFile;
        try (Connection c = state()) {
            try (Statement s = c.createStatement()) {
                s.execute("CREATE TABLE IF NOT EXISTS file_state (folder TEXT, path TEXT, sha TEXT,"
                        + " PRIMARY KEY (folder, path))");
                s.execute("CREATE TABLE IF NOT EXISTS run_state (file TEXT PRIMARY KEY, role TEXT, origin TEXT,"
                        + " last_generation INTEGER)");
                s.execute("CREATE TABLE IF NOT EXISTS meta (key TEXT PRIMARY KEY, value TEXT)");
            }
        } catch (SQLException e) {
            throw new IllegalStateException("cannot open " + stateFile, e);
        }
        // What was synced from another folder says nothing about this one: started with another
        // --runs or --games, a copy would otherwise take every file it remembers for deleted.
        for (Map.Entry<String, Path> f : folders.entrySet()) {
            if (folderMoved("folder:" + f.getKey(), f.getValue())) {
                stateUpdate("DELETE FROM file_state WHERE folder = ?", f.getKey());
                stateUpdate("DELETE FROM meta WHERE key = 'files_cursor'");
            }
        }
        if (folderMoved("runs", runs)) {
            stateUpdate("DELETE FROM run_state");
        }
    }

    private boolean folderMoved(String key, Path dir) {
        String now = dir.toAbsolutePath().normalize().toString();
        String before = meta(key, null);
        setMeta(key, now);
        return before != null && !before.equals(now);
    }

    /** Syncs now and then every {@link #PERIOD} on a background thread. */
    public synchronized void start() {
        if (timer == null) {
            timer = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "cloud-sync");
                t.setDaemon(true);
                return t;
            });
            timer.scheduleWithFixedDelay(this::tick, 0, PERIOD.toSeconds(), TimeUnit.SECONDS);
        }
    }

    @Override
    public synchronized void close() {
        if (timer != null) {
            timer.shutdownNow();
            timer = null;
        }
    }

    /** One timed pass: skipped while backing off after a failure. */
    void tick() {
        Instant retry = retryAt;
        if (retry != null && Instant.now().isBefore(retry)) {
            return;
        }
        try {
            syncOnce();
            failures = 0;
            retryAt = null;
            lastError = null;
        } catch (RuntimeException e) {
            failures++;
            boolean limit = e instanceof Sql.SqlException se && se.limit();
            Duration wait = limit ? LIMIT_BACKOFF
                    : PERIOD.multipliedBy(1L << Math.min(failures, 6)).compareTo(MAX_BACKOFF) > 0 ? MAX_BACKOFF
                    : PERIOD.multipliedBy(1L << Math.min(failures, 6));
            retryAt = Instant.now().plus(wait);
            lastError = e.getMessage();
            System.err.println("Cloud sync: " + e.getMessage() + " (trying again in " + wait.toMinutes() + " min)");
        }
    }

    /** A whole pass: bring in what other copies saved, then send what this copy changed. */
    public synchronized void syncOnce() {
        ensureSchema();
        pullFiles();
        pushFiles();
        Map<String, JsonObject> remote = remoteRuns();
        pullRuns(remote);
        pushRuns(remote);
        lastSync = Instant.now();
        if (lastHeartbeat == null || lastSync.isAfter(lastHeartbeat.plus(HEARTBEAT))) { // a row write costs quota
            db.query("INSERT OR REPLACE INTO copies (name, last_seen_ms) VALUES (?, " + NOW_MS + ")", copy);
            lastHeartbeat = lastSync;
        }
    }

    /** Whether the run file is a copy of another copy's run (so it must not be resumed here). */
    public synchronized boolean isReplica(String file) {
        return runState(file).map(s -> s.role.equals("replica")).orElse(false);
    }

    /** The copy that plays a replicated run, if {@code file} is one. */
    public synchronized String origin(String file) {
        return runState(file).map(s -> s.origin).orElse(null);
    }

    /** What the settings dialog shows. */
    public JsonObject status() {
        JsonObject o = new JsonObject();
        o.addProperty("enabled", true);
        o.addProperty("copy", copy);
        o.addProperty("lastSync", lastSync == null ? null : lastSync.toString());
        o.addProperty("error", lastError);
        o.addProperty("retryAt", retryAt == null ? null : retryAt.toString());
        o.addProperty("waitingFiles", waitingFiles);
        o.addProperty("waitingGenerations", waitingGenerations);
        JsonArray w = new JsonArray();
        synchronized (warnings) {
            warnings.forEach(w::add);
        }
        o.add("warnings", w);
        return o;
    }

    // ---- schema ------------------------------------------------------------

    private void ensureSchema() {
        if (schemaReady) {
            return;
        }
        db.query("CREATE TABLE IF NOT EXISTS files (folder TEXT NOT NULL, path TEXT NOT NULL, sha TEXT, size INTEGER,"
                + " chunks INTEGER, deleted INTEGER NOT NULL DEFAULT 0, origin TEXT, updated_ms INTEGER,"
                + " PRIMARY KEY (folder, path))");
        db.query("CREATE INDEX IF NOT EXISTS files_updated ON files (updated_ms)");
        db.query("CREATE TABLE IF NOT EXISTS runs (file TEXT PRIMARY KEY, name TEXT, algorithm TEXT, settings TEXT,"
                + " started_at TEXT, origin TEXT, last_generation INTEGER, deleted INTEGER NOT NULL DEFAULT 0,"
                + " updated_ms INTEGER)");
        db.query("CREATE TABLE IF NOT EXISTS run_generations (file TEXT NOT NULL, number INTEGER NOT NULL,"
                + " champion INTEGER, champion_score REAL, games INTEGER, yardstick_wins INTEGER,"
                + " yardstick_draws INTEGER, yardstick_losses INTEGER, yardstick_elo REAL, yardstick_elo_low REAL,"
                + " yardstick_elo_high REAL, finished_at TEXT, yardsticks TEXT, members TEXT, next_members TEXT,"
                + " game_chunks INTEGER, PRIMARY KEY (file, number))");
        db.query("CREATE TABLE IF NOT EXISTS blobs (key TEXT NOT NULL, chunk INTEGER NOT NULL, data TEXT,"
                + " PRIMARY KEY (key, chunk))");
        db.query("CREATE TABLE IF NOT EXISTS copies (name TEXT PRIMARY KEY, last_seen_ms INTEGER)");
        schemaReady = true;
    }

    // ---- blobs -------------------------------------------------------------

    /** Stores {@code bytes} gzipped under {@code key}; the number of chunks. */
    private int putBlob(String key, byte[] bytes) {
        String text = Base64.getEncoder().encodeToString(gzip(bytes));
        int chunks = Math.max(1, (text.length() + CHUNK - 1) / CHUNK);
        for (int i = 0; i < chunks; i++) {
            db.query("INSERT OR REPLACE INTO blobs (key, chunk, data) VALUES (?, ?, ?)", key, i,
                    text.substring(i * CHUNK, Math.min(text.length(), (i + 1) * CHUNK)));
        }
        db.query("DELETE FROM blobs WHERE key = ? AND chunk >= ?", key, chunks);
        return chunks;
    }

    private byte[] getBlob(String key, int chunks) {
        List<JsonObject> rows = db.query("SELECT chunk, data FROM blobs WHERE key = ? ORDER BY chunk", key);
        if (rows.size() != chunks) {
            throw new Sql.SqlException(key + " has " + rows.size() + " of its " + chunks + " parts", false);
        }
        StringBuilder text = new StringBuilder();
        rows.forEach(r -> text.append(r.get("data").getAsString()));
        return gunzip(Base64.getDecoder().decode(text.toString()));
    }

    // ---- folders -----------------------------------------------------------

    private void pullFiles() {
        long cursor = Long.parseLong(meta("files_cursor", "0"));
        // a little overlap, in case two copies' writes landed in the same moment
        List<JsonObject> rows = db.query("SELECT folder, path, sha, chunks, deleted, updated_ms FROM files"
                + " WHERE updated_ms > ? ORDER BY updated_ms", Math.max(0, cursor - 120_000));
        long seen = cursor;
        for (JsonObject row : rows) {
            seen = Math.max(seen, row.get("updated_ms").getAsLong());
            String folder = row.get("folder").getAsString();
            String rel = row.get("path").getAsString();
            Path dir = folders.get(folder);
            Path file = dir == null ? null : resolve(dir, rel);
            if (file == null) {
                continue;
            }
            String remoteSha = row.get("deleted").getAsInt() != 0 ? null : text(row, "sha");
            String base = fileState(folder, rel);
            if (Objects.equals(remoteSha, base)) {
                continue;
            }
            String localSha = sha(file);
            if (Objects.equals(localSha, remoteSha)) {
                setFileState(folder, rel, remoteSha);
            } else if (Objects.equals(localSha, base)) {
                if (remoteSha == null) {
                    deleteQuietly(file);
                } else {
                    write(file, getBlob(fileKey(folder, rel), row.get("chunks").getAsInt()));
                }
                setFileState(folder, rel, remoteSha);
            } else {
                warn("Kept this copy's " + folder + "/" + rel + ": it was changed here and on another copy");
            }
        }
        setMeta("files_cursor", Long.toString(seen));
    }

    private void pushFiles() {
        List<String[]> changed = new ArrayList<>(); // folder, rel, sha
        List<String[]> gone = new ArrayList<>();
        for (Map.Entry<String, Path> f : folders.entrySet()) {
            if (!Files.isDirectory(f.getValue())) {
                continue; // not there (yet): nothing to send, and nothing deleted
            }
            Map<String, String> known = fileStates(f.getKey());
            Set<String> present = new LinkedHashSet<>();
            for (String rel : listFiles(f.getValue())) {
                present.add(rel);
                String sha = sha(f.getValue().resolve(rel));
                if (sha != null && !sha.equals(known.get(rel))) {
                    changed.add(new String[] {f.getKey(), rel, sha});
                }
            }
            known.forEach((rel, sha) -> {
                if (sha != null && !present.contains(rel)) {
                    gone.add(new String[] {f.getKey(), rel});
                }
            });
        }
        waitingFiles = changed.size() + gone.size();
        for (String[] c : changed) {
            byte[] bytes;
            try {
                bytes = Files.readAllBytes(folders.get(c[0]).resolve(c[1]));
            } catch (IOException e) {
                continue; // removed or being written; next pass
            }
            String sha = sha(bytes);
            int chunks = putBlob(fileKey(c[0], c[1]), bytes);
            db.query("INSERT OR REPLACE INTO files (folder, path, sha, size, chunks, deleted, origin, updated_ms)"
                    + " VALUES (?, ?, ?, ?, ?, 0, ?, " + NOW_MS + ")", c[0], c[1], sha, bytes.length, chunks, copy);
            setFileState(c[0], c[1], sha);
            waitingFiles--;
        }
        for (String[] g : gone) {
            db.query("INSERT OR REPLACE INTO files (folder, path, sha, size, chunks, deleted, origin, updated_ms)"
                    + " VALUES (?, ?, NULL, 0, 0, 1, ?, " + NOW_MS + ")", g[0], g[1], copy);
            db.query("DELETE FROM blobs WHERE key = ?", fileKey(g[0], g[1]));
            setFileState(g[0], g[1], null);
            waitingFiles--;
        }
    }

    private static String fileKey(String folder, String rel) {
        return "file:" + folder + "/" + rel;
    }

    /** The shareable files under {@code dir}, as '/'-separated paths: no temporary or hidden files. */
    static List<String> listFiles(Path dir) {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> s = Files.walk(dir)) {
            return s.filter(Files::isRegularFile)
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .filter(rel -> resolve(dir, rel) != null)
                    .sorted()
                    .toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** {@code rel} under {@code dir}, or null if it is not a name this sync may write. */
    static Path resolve(Path dir, String rel) {
        for (String part : rel.split("/", -1)) {
            if (part.isEmpty() || part.startsWith(".") || !part.matches("[A-Za-z0-9._ -]{1,128}")) {
                return null;
            }
        }
        if (rel.endsWith(".tmp")) {
            return null;
        }
        Path p = dir.resolve(rel).normalize();
        return p.startsWith(dir.normalize()) ? p : null;
    }

    // ---- runs --------------------------------------------------------------

    private Map<String, JsonObject> remoteRuns() {
        Map<String, JsonObject> runsByFile = new LinkedHashMap<>();
        for (JsonObject r : db.query("SELECT file, name, algorithm, settings, started_at, origin, last_generation,"
                + " deleted FROM runs")) {
            runsByFile.put(r.get("file").getAsString(), r);
        }
        return runsByFile;
    }

    private void pullRuns(Map<String, JsonObject> remote) {
        for (JsonObject r : remote.values()) {
            String file = r.get("file").getAsString();
            String origin = text(r, "origin");
            if (copy.equals(origin) || !RUN_FILE.matcher(file).matches()) {
                continue;
            }
            Path path = runs.resolve(file);
            RunState st = runState(file).orElse(null);
            if (st != null && st.role.equals("replica") && !Files.exists(path)) {
                deleteRunState(file); // the replica file went missing here: build it again
                st = null;
            }
            if (r.get("deleted").getAsInt() != 0) {
                if (st != null && st.role.equals("replica")) {
                    for (String suffix : List.of("", "-wal", "-shm")) {
                        deleteQuietly(runs.resolve(file + suffix));
                    }
                    deleteRunState(file);
                }
                continue;
            }
            if (st == null) {
                if (!Files.exists(path)) {
                    createReplica(path, r);
                    st = new RunState("replica", origin, -1);
                } else if (Objects.equals(localStartedAt(path), text(r, "started_at"))
                        && localLastGeneration(path) <= number(r, "last_generation", -1)) {
                    // the same run, copied here as a file (the runs that ship with the repository)
                    st = new RunState("replica", origin, localLastGeneration(path));
                } else {
                    warn("Run " + file + " exists here and on " + origin + " as different runs; not synced");
                    continue;
                }
                setRunState(file, st);
            }
            if (!st.role.equals("replica")) {
                continue;
            }
            List<JsonObject> gens = db.query("SELECT * FROM run_generations WHERE file = ? AND number > ?"
                    + " ORDER BY number LIMIT " + PULL_GENERATIONS_PER_PASS, file, st.lastGeneration);
            for (JsonObject g : gens) {
                byte[] games = getBlob(gamesKey(file, g.get("number").getAsInt()), g.get("game_chunks").getAsInt());
                applyGeneration(path, g, games);
                st = new RunState("replica", origin, g.get("number").getAsInt());
                setRunState(file, st);
            }
        }
    }

    private void pushRuns(Map<String, JsonObject> remote) {
        List<Path> local = new ArrayList<>();
        if (Files.isDirectory(runs)) {
            try (Stream<Path> s = Files.list(runs)) {
                s.filter(p -> RUN_FILE.matcher(p.getFileName().toString()).matches() && Files.isRegularFile(p))
                        .sorted().forEach(local::add);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        // work out what waits first, so the status line counts it even if the pass stops half way
        Map<Path, List<Integer>> todo = new LinkedHashMap<>();
        Map<Path, RunState> states = new LinkedHashMap<>();
        for (Path path : local) {
            String file = path.getFileName().toString();
            RunState st = runState(file).orElse(null);
            JsonObject r = remote.get(file);
            if (st == null) {
                if (r != null && !copy.equals(text(r, "origin")) && number(r, "deleted", 0) == 0) {
                    continue; // another copy's run; pullRuns decided what to do with it
                }
                st = new RunState("own", copy, r != null && copy.equals(text(r, "origin"))
                        ? number(r, "last_generation", -1) : -1);
            }
            if (!st.role.equals("own")) {
                continue;
            }
            states.put(path, st);
            List<Integer> numbers = new ArrayList<>();
            for (int n : finishedGenerations(path)) {
                if (n > st.lastGeneration) {
                    numbers.add(n);
                }
            }
            todo.put(path, numbers);
        }
        waitingGenerations = todo.values().stream().mapToInt(List::size).sum();
        for (Map.Entry<Path, List<Integer>> e : todo.entrySet()) {
            Path path = e.getKey();
            String file = path.getFileName().toString();
            RunState st = states.get(path);
            JsonObject run = localRun(path);
            if (run == null) {
                continue; // a run file that holds no run yet
            }
            boolean known = runState(file).isPresent();
            if (!known) {
                pushRunRow(file, run, st.lastGeneration);
                setRunState(file, st);
            }
            for (int n : e.getValue()) {
                pushGeneration(path, file, n);
                st = new RunState("own", copy, n);
                pushRunRow(file, run, n);
                setRunState(file, st);
                waitingGenerations--;
            }
        }
        // runs deleted here (a missing folder is not a deletion)
        for (Map.Entry<String, RunState> e : Files.isDirectory(runs) ? runStates().entrySet()
                : Map.<String, RunState>of().entrySet()) {
            if (e.getValue().role.equals("own") && !Files.exists(runs.resolve(e.getKey()))) {
                String file = e.getKey();
                for (JsonObject g : db.query("SELECT number FROM run_generations WHERE file = ?", file)) {
                    db.query("DELETE FROM blobs WHERE key = ?", gamesKey(file, g.get("number").getAsInt()));
                }
                db.query("DELETE FROM run_generations WHERE file = ?", file);
                db.query("UPDATE runs SET deleted = 1, last_generation = -1, updated_ms = " + NOW_MS
                        + " WHERE file = ?", file);
                deleteRunState(file);
            }
        }
    }

    private void pushRunRow(String file, JsonObject run, int lastGeneration) {
        db.query("INSERT OR REPLACE INTO runs (file, name, algorithm, settings, started_at, origin, last_generation,"
                        + " deleted, updated_ms) VALUES (?, ?, ?, ?, ?, ?, ?, 0, " + NOW_MS + ")",
                file, text(run, "name"), text(run, "algorithm"), text(run, "settings"), text(run, "started_at"),
                copy, lastGeneration);
    }

    private void pushGeneration(Path path, String file, int n) {
        try (Connection c = runFile(path)) {
            JsonObject g = rows(c, "SELECT * FROM generation WHERE number = ?", n).get(0);
            JsonArray games = new JsonArray();
            for (JsonObject game : rows(c, "SELECT generation, kind, white, black, opening, moves, result, reason,"
                    + " plies, seed, depth, opponent FROM game WHERE generation = ? ORDER BY id", n)) {
                games.add(game);
            }
            int chunks = putBlob(gamesKey(file, n), games.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));
            JsonArray yardsticks = new JsonArray();
            rows(c, "SELECT opponent, level, depth, wins, draws, losses FROM yardstick WHERE generation = ?"
                    + " ORDER BY rowid", n).forEach(yardsticks::add);
            db.query("INSERT OR REPLACE INTO run_generations (file, number, champion, champion_score, games,"
                            + " yardstick_wins, yardstick_draws, yardstick_losses, yardstick_elo, yardstick_elo_low,"
                            + " yardstick_elo_high, finished_at, yardsticks, members, next_members, game_chunks)"
                            + " VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    file, n, value(g, "champion"), value(g, "champion_score"), value(g, "games"),
                    value(g, "yardstick_wins"), value(g, "yardstick_draws"), value(g, "yardstick_losses"),
                    value(g, "yardstick_elo"), value(g, "yardstick_elo_low"), value(g, "yardstick_elo_high"),
                    value(g, "finished_at"), yardsticks.toString(), members(c, n), members(c, n + 1), chunks);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private static String members(Connection c, int generation) throws SQLException {
        List<JsonObject> rows = rows(c, "SELECT params FROM member WHERE generation = ? ORDER BY idx", generation);
        if (rows.isEmpty()) {
            return null;
        }
        JsonArray a = new JsonArray();
        rows.forEach(r -> a.add(r.get("params")));
        return a.toString();
    }

    /** Builds an empty replica of a remote run: the schema and its run row. */
    private void createReplica(Path path, JsonObject r) {
        RunStore.open(path).close();
        try (Connection c = runFile(path); PreparedStatement p = c.prepareStatement("INSERT INTO run VALUES (?, ?, ?, ?)")) {
            p.setString(1, text(r, "name"));
            p.setString(2, text(r, "algorithm"));
            p.setString(3, text(r, "settings"));
            p.setString(4, text(r, "started_at"));
            p.executeUpdate();
        } catch (SQLException e) {
            throw new IllegalStateException("cannot create " + path, e);
        }
    }

    /** Writes one generation of a replica, in one transaction, the way RunStore writes it. */
    private static void applyGeneration(Path path, JsonObject g, byte[] gamesJson) {
        int n = g.get("number").getAsInt();
        JsonArray games = JsonParser.parseString(new String(gamesJson, java.nio.charset.StandardCharsets.UTF_8))
                .getAsJsonArray();
        try (Connection c = runFile(path)) {
            c.setAutoCommit(false);
            try {
                update(c, "DELETE FROM game WHERE generation = ?", n);
                for (JsonElement e : games) {
                    JsonObject x = e.getAsJsonObject();
                    update(c, "INSERT INTO game (generation, kind, white, black, opening, moves, result, reason, plies,"
                                    + " seed, depth, opponent) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                            value(x, "generation"), value(x, "kind"), value(x, "white"), value(x, "black"),
                            value(x, "opening"), value(x, "moves"), value(x, "result"), value(x, "reason"),
                            value(x, "plies"), value(x, "seed"), value(x, "depth"), value(x, "opponent"));
                }
                setMembers(c, n, text(g, "members"));
                setMembers(c, n + 1, text(g, "next_members"));
                update(c, "DELETE FROM yardstick WHERE generation = ?", n);
                String ys = text(g, "yardsticks");
                if (ys != null) {
                    for (JsonElement e : JsonParser.parseString(ys).getAsJsonArray()) {
                        JsonObject y = e.getAsJsonObject();
                        update(c, "INSERT INTO yardstick VALUES (?, ?, ?, ?, ?, ?, ?)", n, value(y, "opponent"),
                                value(y, "level"), value(y, "depth"), value(y, "wins"), value(y, "draws"),
                                value(y, "losses"));
                    }
                }
                update(c, "INSERT OR REPLACE INTO generation VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)", n,
                        value(g, "champion"), value(g, "champion_score"), value(g, "games"), value(g, "yardstick_wins"),
                        value(g, "yardstick_draws"), value(g, "yardstick_losses"), value(g, "yardstick_elo"),
                        value(g, "yardstick_elo_low"), value(g, "yardstick_elo_high"), value(g, "finished_at"));
                c.commit();
            } catch (SQLException | RuntimeException e) {
                c.rollback();
                throw e;
            }
        } catch (SQLException e) {
            throw new IllegalStateException("cannot write " + path, e);
        }
    }

    private static void setMembers(Connection c, int generation, String json) throws SQLException {
        if (json == null) {
            return;
        }
        update(c, "DELETE FROM member WHERE generation = ?", generation);
        JsonArray a = JsonParser.parseString(json).getAsJsonArray();
        for (int i = 0; i < a.size(); i++) {
            update(c, "INSERT INTO member VALUES (?, ?, ?)", generation, i, a.get(i).getAsString());
        }
    }

    private static String gamesKey(String file, int generation) {
        return "games:" + file + ":" + generation;
    }

    private static JsonObject localRun(Path path) {
        try (Connection c = runFile(path)) {
            List<JsonObject> r = rows(c, "SELECT name, algorithm, settings, started_at FROM run");
            return r.isEmpty() ? null : r.get(0);
        } catch (SQLException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private static String localStartedAt(Path path) {
        JsonObject r = localRun(path);
        return r == null ? null : text(r, "started_at");
    }

    private static int localLastGeneration(Path path) {
        List<Integer> n = finishedGenerations(path);
        return n.isEmpty() ? -1 : n.get(n.size() - 1);
    }

    private static List<Integer> finishedGenerations(Path path) {
        RunStore.open(path).close(); // brings an older run file's schema up to date
        try (Connection c = runFile(path)) {
            List<Integer> numbers = new ArrayList<>();
            rows(c, "SELECT number FROM generation ORDER BY number").forEach(r -> numbers.add(r.get("number").getAsInt()));
            return numbers;
        } catch (SQLException e) {
            throw new IllegalStateException("cannot read " + path, e);
        }
    }

    private static Connection runFile(Path path) throws SQLException {
        Connection c = DriverManager.getConnection("jdbc:sqlite:" + path);
        try (Statement s = c.createStatement()) {
            s.execute("PRAGMA busy_timeout = 10000");
        }
        return c;
    }

    // ---- this copy's memory ------------------------------------------------

    private record RunState(String role, String origin, int lastGeneration) {}

    private Connection state() throws SQLException {
        Path parent = stateFile.toAbsolutePath().getParent();
        if (parent != null) {
            try {
                Files.createDirectories(parent);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
        return DriverManager.getConnection("jdbc:sqlite:" + stateFile);
    }

    private java.util.Optional<RunState> runState(String file) {
        try (Connection c = state()) {
            List<JsonObject> r = rows(c, "SELECT role, origin, last_generation FROM run_state WHERE file = ?", file);
            return r.isEmpty() ? java.util.Optional.empty()
                    : java.util.Optional.of(new RunState(text(r.get(0), "role"), text(r.get(0), "origin"),
                            r.get(0).get("last_generation").getAsInt()));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, RunState> runStates() {
        Map<String, RunState> all = new LinkedHashMap<>();
        try (Connection c = state()) {
            for (JsonObject r : rows(c, "SELECT file, role, origin, last_generation FROM run_state")) {
                all.put(text(r, "file"), new RunState(text(r, "role"), text(r, "origin"),
                        r.get("last_generation").getAsInt()));
            }
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return all;
    }

    private void setRunState(String file, RunState s) {
        stateUpdate("INSERT OR REPLACE INTO run_state VALUES (?, ?, ?, ?)", file, s.role, s.origin, s.lastGeneration);
    }

    private void deleteRunState(String file) {
        stateUpdate("DELETE FROM run_state WHERE file = ?", file);
    }

    private String fileState(String folder, String rel) {
        try (Connection c = state()) {
            List<JsonObject> r = rows(c, "SELECT sha FROM file_state WHERE folder = ? AND path = ?", folder, rel);
            return r.isEmpty() ? null : text(r.get(0), "sha");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private Map<String, String> fileStates(String folder) {
        Map<String, String> all = new LinkedHashMap<>();
        try (Connection c = state()) {
            rows(c, "SELECT path, sha FROM file_state WHERE folder = ?", folder)
                    .forEach(r -> all.put(text(r, "path"), text(r, "sha")));
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
        return all;
    }

    private void setFileState(String folder, String rel, String sha) {
        if (sha == null) {
            stateUpdate("DELETE FROM file_state WHERE folder = ? AND path = ?", folder, rel);
        } else {
            stateUpdate("INSERT OR REPLACE INTO file_state VALUES (?, ?, ?)", folder, rel, sha);
        }
    }

    private String meta(String key, String fallback) {
        try (Connection c = state()) {
            List<JsonObject> r = rows(c, "SELECT value FROM meta WHERE key = ?", key);
            return r.isEmpty() ? fallback : text(r.get(0), "value");
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void setMeta(String key, String value) {
        stateUpdate("INSERT OR REPLACE INTO meta VALUES (?, ?)", key, value);
    }

    private void stateUpdate(String sql, Object... args) {
        try (Connection c = state()) {
            update(c, sql, args);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void warn(String message) {
        synchronized (warnings) {
            if (warnings.add(message)) {
                System.err.println("Cloud sync: " + message);
            }
        }
    }

    // ---- small helpers -----------------------------------------------------

    /** Rows as JSON objects, columns by name: the same shape D1 answers in. */
    static List<JsonObject> rows(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                p.setObject(i + 1, args[i]);
            }
            List<JsonObject> rows = new ArrayList<>();
            if (!p.execute()) {
                return rows;
            }
            try (ResultSet r = p.getResultSet()) {
                int columns = r.getMetaData().getColumnCount();
                while (r.next()) {
                    JsonObject o = new JsonObject();
                    for (int i = 1; i <= columns; i++) {
                        Object v = r.getObject(i);
                        String name = r.getMetaData().getColumnLabel(i);
                        if (v == null) {
                            o.add(name, null);
                        } else if (v instanceof Number num) {
                            o.addProperty(name, num);
                        } else if (v instanceof byte[] bytes) {
                            o.addProperty(name, Base64.getEncoder().encodeToString(bytes));
                        } else {
                            o.addProperty(name, v.toString());
                        }
                    }
                    rows.add(o);
                }
            }
            return rows;
        }
    }

    private static void update(Connection c, String sql, Object... args) throws SQLException {
        try (PreparedStatement p = c.prepareStatement(sql)) {
            for (int i = 0; i < args.length; i++) {
                p.setObject(i + 1, args[i]);
            }
            p.executeUpdate();
        }
    }

    private static String text(JsonObject o, String key) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? null : e.getAsString();
    }

    private static int number(JsonObject o, String key, int fallback) {
        JsonElement e = o.get(key);
        return e == null || e.isJsonNull() ? fallback : e.getAsInt();
    }

    /** A JSON value as the Java value to bind: a number stays a number (integer when it is one). */
    private static Object value(JsonObject o, String key) {
        JsonElement e = o.get(key);
        if (e == null || e.isJsonNull()) {
            return null;
        }
        if (e.getAsJsonPrimitive().isNumber()) {
            double d = e.getAsDouble();
            return d == Math.rint(d) && Math.abs(d) < 1e15 ? (Object) e.getAsLong() : (Object) d;
        }
        return e.getAsString();
    }

    private static String sha(Path file) {
        try {
            return Files.isRegularFile(file) ? sha(Files.readAllBytes(file)) : null;
        } catch (IOException e) {
            return null;
        }
    }

    static String sha(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static void write(Path file, byte[] bytes) {
        try {
            game.AtomicWrite.write(file, bytes);
        } catch (IOException e) {
            throw new UncheckedIOException("cannot write " + file, e);
        }
    }

    private static void deleteQuietly(Path file) {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("Cloud sync: cannot delete " + file + ": " + e);
        }
    }

    static byte[] gzip(byte[] bytes) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (GZIPOutputStream z = new GZIPOutputStream(out)) {
            z.write(bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out.toByteArray();
    }

    static byte[] gunzip(byte[] bytes) {
        try (GZIPInputStream z = new GZIPInputStream(new ByteArrayInputStream(bytes))) {
            return z.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
