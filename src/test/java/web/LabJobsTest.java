package web;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import game.VariantStore;
import io.javalin.http.BadRequestResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Runs started, stopped, resumed and deleted from the Lab page. */
class LabJobsTest {

    @Test
    @DisplayName("The algorithms come with their settings")
    void algorithms() {
        JsonObject out = LabJobs.algorithms();
        assertEquals(3, out.getAsJsonArray("algorithms").size());
        JsonObject fromZero = out.getAsJsonArray("algorithms").get(0).getAsJsonObject();
        assertEquals("FromZero", fromZero.get("name").getAsString());
        assertFalse(fromZero.get("chessOnly").getAsBoolean());
        JsonObject population = fromZero.getAsJsonArray("options").get(0).getAsJsonObject();
        assertEquals("population", population.get("key").getAsString());
        assertEquals("12", population.get("default").getAsString());
        assertEquals(4, population.get("min").getAsInt());
    }

    @Test
    @DisplayName("Another copy's run, synced here, is not resumed or deleted here: the message says where it plays")
    void replicas(@TempDir Path dir) {
        LabJobs jobs = new LabJobs(new LabApi(dir.resolve("runs")), new VariantStore(dir.resolve("variants")));
        jobs.useReplicas(file -> file.equals("from-server.db") ? "server" : null);
        BadRequestResponse resume = assertThrows(BadRequestResponse.class, () -> jobs.resume("from-server.db"));
        assertEquals("this run is played on server; resume it there", resume.getMessage());
        BadRequestResponse delete = assertThrows(BadRequestResponse.class, () -> jobs.delete("from-server.db"));
        assertEquals("this run is played on server; delete it there", delete.getMessage());
    }

    @Test
    @DisplayName("An antichess run from the page: it plays, stops at once, resumes, finishes, and can then be deleted")
    void startStopResumeDelete(@TempDir Path dir) throws Exception {
        LabApi lab = new LabApi(dir.resolve("runs"));
        LabJobs jobs = new LabJobs(lab, new VariantStore(dir.resolve("variants")));
        JsonObject body = JsonParser.parseString("{\"name\": \"Anti test\", \"algorithm\": \"evolution.FromZero\","
                + " \"variant\": \"antichess\", \"settings\": {\"generations\": 3, \"depth\": 1, \"openingsPerPairing\": 1,"
                + " \"threads\": 2, \"yardstickEvery\": 1, \"yardstickOpenings\": 1, \"yardsticks\": [\"zero\"],"
                + " \"deepDepth\": 0, \"maxPlies\": 60}, \"options\": {\"population\": \"4\"}}").getAsJsonObject();
        String file = jobs.start(body).get("file").getAsString();
        assertEquals("anti-test.db", file);
        assertTrue(jobs.running());
        assertThrows(BadRequestResponse.class, () -> jobs.start(body), "one run at a time");
        assertThrows(BadRequestResponse.class, () -> jobs.delete(file), "not while it plays");

        jobs.stop(file, true);
        waitUntil(() -> !jobs.running());
        JsonObject status = jobs.status();
        assertTrue(status.get("finished").getAsBoolean());
        assertFalse(status.has("error"), status.toString());
        try (lab.RunStore store = lab.open(file)) {
            assertTrue(store.generations().size() < 3);
            assertEquals("antichess", store.run().orElseThrow().settings().variantId());
            assertEquals("4", store.run().orElseThrow().settings().algorithmOptions().get("population"));
            assertEquals(4, store.run().orElseThrow().settings().openingPlies());
        }

        jobs.resume(file);
        waitUntil(() -> !jobs.running());
        assertFalse(jobs.status().has("error"), jobs.status().toString());
        try (lab.RunStore store = lab.open(file)) {
            assertEquals(3, store.generations().size());
        }
        assertThrows(BadRequestResponse.class, () -> jobs.resume(file), "finished");
        JsonObject run = lab.run(file);
        assertEquals("Antichess", run.get("variantName").getAsString());
        assertEquals(3, run.getAsJsonArray("generations").size());
        JsonObject gen0 = run.getAsJsonArray("generations").get(0).getAsJsonObject();
        assertEquals(12, gen0.getAsJsonObject("stats").get("games").getAsInt());
        JsonObject generation = lab.generation(file, 0);
        assertEquals(4, generation.getAsJsonArray("members").size());
        assertEquals(4, generation.getAsJsonArray("standings").size());
        assertTrue(lab.memberJson(file, 0, 1).contains("material.queen"));
        assertEquals(ai.eval.Evaluators.schema(ai.variant.Variants.ANTICHESS).size(),
                lab.schemaOf(file).getAsJsonArray("parameters").size());

        jobs.delete(file);
        assertFalse(Files.exists(dir.resolve("runs").resolve(file)));
    }

    @Test
    @DisplayName("Settings that cannot be played are refused before anything starts")
    void refused(@TempDir Path dir) {
        LabJobs jobs = new LabJobs(new LabApi(dir), new VariantStore(dir.resolve("variants")));
        JsonObject bad = JsonParser.parseString("{\"algorithm\": \"evolution.FromZero\", \"variant\": \"antichess\","
                + " \"settings\": {\"yardsticks\": [\"sf:auto\"]}}").getAsJsonObject();
        assertThrows(BadRequestResponse.class, () -> jobs.start(bad));
        JsonObject unknown = JsonParser.parseString("{\"algorithm\": \"evolution.FromZero\", \"variant\": \"nope\"}").getAsJsonObject();
        assertThrows(BadRequestResponse.class, () -> jobs.start(unknown));
        JsonObject option = JsonParser.parseString("{\"algorithm\": \"evolution.FromZero\", \"variant\": \"antichess\","
                + " \"options\": {\"population\": \"1\"}}").getAsJsonObject();
        assertThrows(BadRequestResponse.class, () -> jobs.start(option));
        assertFalse(jobs.running());
    }

    private static void waitUntil(java.util.function.BooleanSupplier done) throws InterruptedException {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(60);
        while (!done.getAsBoolean() && System.nanoTime() < end) {
            Thread.sleep(50);
        }
        assertTrue(done.getAsBoolean(), "timed out");
    }
}
