package cloud;

import ai.eval.Evaluators;
import ai.eval.ParamVector;
import ai.variant.Variants;
import arena.GameRecord;
import arena.Score;
import lab.RunSettings;
import lab.RunStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CloudSyncTest {

    @TempDir
    Path tmp;
    FakeD1 d1;

    @BeforeEach
    void setUp() throws Exception {
        d1 = new FakeD1();
    }

    /** One copy of the game: its folders, runs and sync state under {@code tmp/name}. */
    private record Copy(Path root, CloudSync sync) {
        Path games() { return root.resolve("games"); }
        Path variants() { return root.resolve("variants"); }
        Path runs() { return root.resolve("runs"); }
    }

    private Copy copy(String name) {
        Path root = tmp.resolve(name);
        Path runs = root.resolve("runs");
        CloudSync sync = new CloudSync(d1, name, Map.of("games", root.resolve("games"),
                "variants", root.resolve("variants"), "hall-of-fame", runs.resolve("hall-of-fame")),
                runs, root.resolve("cloud-sync.db"));
        return new Copy(root, sync);
    }

    private static void write(Path file, String text) throws Exception {
        Files.createDirectories(file.getParent());
        Files.writeString(file, text);
    }

    @Test
    @DisplayName("A saved game, a variant and its piece picture reach the other copy; a deletion follows them")
    void foldersTravel() throws Exception {
        Copy server = copy("server");
        Copy home = copy("home");
        write(server.games().resolve("2026-10-06_12-00-00_ab12.json"), "{\"id\":\"g1\"}");
        write(server.variants().resolve("my-variant.json"), "{\"id\":\"my-variant\"}");
        byte[] picture = {(byte) 0x89, 'P', 'N', 'G', 0, 1, 2, 3};
        Files.createDirectories(server.variants().resolve("my-variant.art"));
        Files.write(server.variants().resolve("my-variant.art/W-w.png"), picture);
        write(server.games().resolve("half-written.json.tmp"), "{");

        server.sync().syncOnce();
        home.sync().syncOnce();

        assertEquals("{\"id\":\"g1\"}", Files.readString(home.games().resolve("2026-10-06_12-00-00_ab12.json")));
        assertEquals("{\"id\":\"my-variant\"}", Files.readString(home.variants().resolve("my-variant.json")));
        assertArrayEquals(picture, Files.readAllBytes(home.variants().resolve("my-variant.art/W-w.png")));
        assertFalse(Files.exists(home.games().resolve("half-written.json.tmp")), "temporary files stay home");

        Files.delete(home.games().resolve("2026-10-06_12-00-00_ab12.json"));
        write(home.variants().resolve("my-variant.json"), "{\"id\":\"my-variant\",\"edited\":true}");
        home.sync().syncOnce();
        server.sync().syncOnce();
        assertFalse(Files.exists(server.games().resolve("2026-10-06_12-00-00_ab12.json")));
        assertEquals("{\"id\":\"my-variant\",\"edited\":true}",
                Files.readString(server.variants().resolve("my-variant.json")));
    }

    @Test
    @DisplayName("A file changed on both copies before they synced keeps each copy's own version, and says so")
    void conflictKeepsLocal() throws Exception {
        Copy a = copy("a");
        Copy b = copy("b");
        write(a.variants().resolve("v.json"), "one");
        a.sync().syncOnce();
        b.sync().syncOnce();
        write(a.variants().resolve("v.json"), "from a");
        write(b.variants().resolve("v.json"), "from b");
        a.sync().syncOnce();
        b.sync().syncOnce(); // b sees a's change but keeps its own, then sends it
        assertEquals("from b", Files.readString(b.variants().resolve("v.json")));
        assertTrue(b.sync().status().get("warnings").toString().contains("v.json"));
        a.sync().syncOnce();
        assertEquals("from b", Files.readString(a.variants().resolve("v.json")), "the last one sent wins");
    }

    @Test
    @DisplayName("A file bigger than one statement is sent in parts and comes back whole")
    void bigFile() throws Exception {
        byte[] noise = new byte[CloudSync.CHUNK * 2];
        new Random(7).nextBytes(noise); // does not compress
        Copy a = copy("a");
        Copy b = copy("b");
        Files.createDirectories(a.variants());
        Files.write(a.variants().resolve("big.png"), noise);
        a.sync().syncOnce();
        assertTrue(d1.select("SELECT chunks FROM files WHERE path = 'big.png'").get(0).get("chunks").getAsInt() >= 3);
        b.sync().syncOnce();
        assertArrayEquals(noise, Files.readAllBytes(b.variants().resolve("big.png")));
    }

    private static final ParamVector WEIGHTS = Evaluators.schema(Variants.CHESS).defaults();

    private static void playGeneration(RunStore run, int n) {
        run.saveGame(n, "population", 0, 1, new GameRecord("0", "1", "e2e4", List.of("e2e4", "e7e5"),
                GameRecord.Result.DRAW, "PLY_CAP", 11L * n), 3, null);
        run.saveGame(n, "population", 1, 0, new GameRecord("1", "0", "d2d4", List.of("d2d4"),
                GameRecord.Result.WHITE_WINS, "CHECKMATE", 12L * n), 3, null);
        run.saveGame(n, "yardstick", 0, -1, new GameRecord("0", "classic", "c2c4", List.of(),
                GameRecord.Result.BLACK_WINS, "RESIGN", 13L * n), 2, "classic");
        run.finishGeneration(new RunStore.GenerationRow(n, n % 2, 1.5 + n, 3,
                Optional.of(new Score("champion", 1, 2, 3)), Instant.parse("2026-10-06T10:00:00Z").plusSeconds(n).toString(),
                List.of(new RunStore.YardstickResult("classic", 0, 2, new Score("champion", 1, 2, 3)))),
                List.of(WEIGHTS.with(0, 100 + n), WEIGHTS.with(0, 200 + n)));
    }

    @Test
    @DisplayName("A run played on the server shows up at home generation by generation, and goes when deleted")
    void runsTravel() throws Exception {
        Copy server = copy("server");
        Copy home = copy("home");
        Path file = server.runs().resolve("antichess-zero-2.db");
        try (RunStore run = RunStore.open(file)) {
            run.createRun("Antichess from zero 2", "evolution.FromZero", RunSettings.defaults());
            run.saveMembers(0, List.of(WEIGHTS, WEIGHTS.with(0, 50)));
            playGeneration(run, 0);
            playGeneration(run, 1);
            run.saveGame(2, "population", 0, 1, new GameRecord("0", "1", "", List.of(),
                    GameRecord.Result.DRAW, "PLY_CAP", 0), 3, null); // generation 2 still playing
        }
        server.sync().syncOnce();
        assertEquals(2, d1.select("SELECT COUNT(*) AS n FROM run_generations").get(0).get("n").getAsInt());
        home.sync().syncOnce();

        Path replica = home.runs().resolve("antichess-zero-2.db");
        assertTrue(home.sync().isReplica("antichess-zero-2.db"));
        assertEquals("server", home.sync().origin("antichess-zero-2.db"));
        assertFalse(server.sync().isReplica("antichess-zero-2.db"));
        try (RunStore original = RunStore.open(file); RunStore copy = RunStore.open(replica)) {
            assertEquals(original.run().get().name(), copy.run().get().name());
            assertEquals(original.generations(), copy.generations());
            for (int n = 0; n < 2; n++) {
                for (String kind : List.of("population", "yardstick")) {
                    assertEquals(original.games(n, kind), copy.games(n, kind));
                    assertEquals(original.gameDepths(n, kind), copy.gameDepths(n, kind));
                }
                assertEquals(original.members(n, WEIGHTS.schema()), copy.members(n, WEIGHTS.schema()));
            }
            assertEquals(original.members(2, WEIGHTS.schema()), copy.members(2, WEIGHTS.schema()),
                    "the next population too, so the run could go on elsewhere");
            assertEquals(0, copy.gameCount(2), "an unfinished generation is not sent");
        }

        try (RunStore run = RunStore.open(file)) {
            playGeneration(run, 2);
        }
        server.sync().syncOnce();
        home.sync().syncOnce();
        try (RunStore copy = RunStore.open(replica)) {
            assertEquals(3, copy.generations().size());
            assertEquals(4, copy.gameCount(2), "all the games of generation 2, the one played before too");
        }

        Files.delete(file);
        Files.deleteIfExists(server.runs().resolve("antichess-zero-2.db-wal"));
        Files.deleteIfExists(server.runs().resolve("antichess-zero-2.db-shm"));
        server.sync().syncOnce();
        home.sync().syncOnce();
        assertFalse(Files.exists(replica));
        assertEquals(0, d1.select("SELECT COUNT(*) AS n FROM blobs WHERE key LIKE 'games:%'").get(0).get("n").getAsInt());
    }

    @Test
    @DisplayName("The same run file on two copies (the runs that ship with the repository) is sent once, not twice")
    void sameRunShippedTwice() throws Exception {
        Copy server = copy("server");
        Copy home = copy("home");
        try (RunStore run = RunStore.open(server.runs().resolve("material-1.db"))) {
            run.createRun("Material 1", "evolution.MaterialExperiment", RunSettings.defaults());
            run.saveMembers(0, List.of(WEIGHTS));
            playGeneration(run, 0);
        }
        Files.createDirectories(home.runs());
        Files.copy(server.runs().resolve("material-1.db"), home.runs().resolve("material-1.db"));
        server.sync().syncOnce();
        home.sync().syncOnce();
        assertTrue(home.sync().isReplica("material-1.db"));
        assertEquals("server", d1.select("SELECT origin FROM runs").get(0).get("origin").getAsString());
        assertEquals(0, ((com.google.gson.JsonArray) home.sync().status().get("warnings")).size());
    }

    @Test
    @DisplayName("While the database is out of reach nothing is lost: the next pass that gets through sends it all")
    void debtIsPaidLater() throws Exception {
        Copy a = copy("a");
        Copy b = copy("b");
        d1.overQuota = true;
        write(a.games().resolve("g1.json"), "1");
        write(a.games().resolve("g2.json"), "2");
        a.sync().tick();
        assertNotNull(a.sync().status().get("error").getAsString());
        assertNotNull(a.sync().status().get("retryAt").getAsString());
        assertTrue(a.sync().status().get("error").getAsString().contains("limit"));

        a.sync().tick(); // still backing off: does not even try
        d1.overQuota = false;
        a.sync().syncOnce();
        assertTrue(a.sync().status().get("waitingFiles").getAsInt() == 0);
        b.sync().syncOnce();
        assertEquals("1", Files.readString(b.games().resolve("g1.json")));
        assertEquals("2", Files.readString(b.games().resolve("g2.json")));
    }

    @Test
    @DisplayName("Names that could escape the folder are never written")
    void unsafeNames() {
        Path dir = tmp.resolve("games");
        assertNull(CloudSync.resolve(dir, "../secret.json"));
        assertNull(CloudSync.resolve(dir, "/etc/passwd"));
        assertNull(CloudSync.resolve(dir, ".hidden"));
        assertNull(CloudSync.resolve(dir, "a/../../b"));
        assertNotNull(CloudSync.resolve(dir, "my-variant.art/W-w.png"));
    }

    @Test
    @DisplayName("Settings come from the environment or cloud.properties; without them there is no sync")
    void config() throws Exception {
        Path props = tmp.resolve("cloud.properties");
        assertTrue(CloudConfig.load(Map.of(), props).isEmpty());
        Files.writeString(props, "account=acc\ndatabase=db\ntoken=secret-token\ncopy=home-pc\n");
        CloudConfig c = CloudConfig.load(Map.of(), props).orElseThrow();
        assertEquals("home-pc", c.copy());
        assertEquals(CloudConfig.API_BASE, c.apiBase());
        assertFalse(c.toString().contains("secret-token"));
        CloudConfig e = CloudConfig.load(Map.of("IZIKSTAR_D1_TOKEN", "t2", "IZIKSTAR_COPY", "server"), props).orElseThrow();
        assertEquals("t2", e.token());
        assertEquals("server", e.copy());
        assertThrows(IllegalArgumentException.class, () -> new CloudConfig("a", "d", "t", "bad name!", "x"));
    }
}
