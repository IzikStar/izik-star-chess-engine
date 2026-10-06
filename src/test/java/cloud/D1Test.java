package cloud;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.javalin.Javalin;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The HTTP side of D1, against a stand-in for Cloudflare's API. */
class D1Test {

    Javalin cloudflare;

    @AfterEach
    void stop() {
        if (cloudflare != null) {
            cloudflare.stop();
        }
    }

    private D1 d1(int status, String answer, AtomicReference<String> seen) {
        cloudflare = Javalin.create(c -> c.showJavalinBanner = false)
                .post("/client/v4/accounts/{account}/d1/database/{database}/query", ctx -> {
                    seen.set(ctx.pathParam("account") + " " + ctx.pathParam("database") + " "
                            + ctx.header("Authorization") + " " + ctx.body());
                    ctx.status(status).contentType("application/json").result(answer);
                })
                .start("127.0.0.1", 0);
        return new D1(new CloudConfig("acc", "db1", "tok", "home",
                "http://127.0.0.1:" + cloudflare.port() + "/client/v4"));
    }

    @Test
    @DisplayName("A statement goes out with its parameters and the token; the rows come back")
    void query() {
        AtomicReference<String> seen = new AtomicReference<>();
        D1 d1 = d1(200, "{\"success\":true,\"errors\":[],\"result\":[{\"success\":true,"
                + "\"results\":[{\"file\":\"a.db\",\"n\":3,\"x\":null}],\"meta\":{}}]}", seen);
        List<JsonObject> rows = d1.query("SELECT * FROM runs WHERE file = ? AND n > ?", "a.db", 2, null);
        assertEquals(1, rows.size());
        assertEquals(3, rows.get(0).get("n").getAsInt());
        assertTrue(rows.get(0).get("x").isJsonNull());
        String[] parts = seen.get().split(" ", 5);
        assertEquals("acc", parts[0]);
        assertEquals("db1", parts[1]);
        assertEquals("Bearer tok", parts[2] + " " + parts[3]);
        JsonObject body = JsonParser.parseString(parts[4]).getAsJsonObject();
        assertEquals("SELECT * FROM runs WHERE file = ? AND n > ?", body.get("sql").getAsString());
        assertEquals("[\"a.db\",2,null]", body.get("params").toString());
    }

    @Test
    @DisplayName("A refusal is an error; a quota that ran out is marked as one, so the sync waits longer")
    void errors() {
        D1 limited = d1(429, "{\"success\":false,\"errors\":[{\"code\":7429,\"message\":\"Rate limit exceeded\"}]}",
                new AtomicReference<>());
        Sql.SqlException e = assertThrows(Sql.SqlException.class, () -> limited.query("SELECT 1"));
        assertTrue(e.limit());
        stop();
        D1 broken = d1(400, "{\"success\":false,\"errors\":[{\"code\":7500,\"message\":\"no such table: runz\"}]}",
                new AtomicReference<>());
        Sql.SqlException e2 = assertThrows(Sql.SqlException.class, () -> broken.query("SELECT * FROM runz"));
        assertFalse(e2.limit());
        assertTrue(e2.getMessage().contains("no such table"));
    }
}
