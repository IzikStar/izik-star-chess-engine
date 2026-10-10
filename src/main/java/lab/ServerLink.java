package lab;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.time.Duration;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;

/**
 * The game server that holds every run, as this computer reaches it: its address, and the user
 * and password its web server asks for (the cloud server's Caddy). It comes from the environment:
 *
 * <pre>
 * IZIKSTAR_SERVER           https://82-70-208-131.sslip.io/  (the server's address)
 * IZIKSTAR_SERVER_USER      the user name, if the server asks for one
 * IZIKSTAR_SERVER_PASSWORD  the password, if the server asks for one
 * IZIKSTAR_WORKER_NAME      what the Lab calls this computer (default: its host name)
 * </pre>
 *
 * The password is only ever sent to the server, in the Authorization header; {@link #toString()}
 * leaves it out.
 */
public final class ServerLink {

    public static final String SERVER = "IZIKSTAR_SERVER";
    public static final String USER = "IZIKSTAR_SERVER_USER";
    public static final String PASSWORD = "IZIKSTAR_SERVER_PASSWORD";
    public static final String WORKER_NAME = "IZIKSTAR_WORKER_NAME";

    /** An HTTP answer other than 2xx. */
    public static final class Refused extends IOException {
        private final int status;

        Refused(int status, String message) {
            super(status + ": " + message);
            this.status = status;
        }

        public int status() {
            return status;
        }
    }

    private final URI base;
    private final String authorization;
    private final String worker;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL).build();

    public ServerLink(URI base, String user, String password, String worker) {
        String b = base.toString();
        this.base = URI.create(b.endsWith("/") ? b : b + "/");
        this.authorization = user == null || user.isBlank() ? null : "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + (password == null ? "" : password)).getBytes(StandardCharsets.UTF_8));
        this.worker = worker;
    }

    /** The server the environment names ({@value #SERVER}), or empty when it names none. */
    public static Optional<ServerLink> fromEnv(Map<String, String> env) {
        String server = env.get(SERVER);
        if (server == null || server.isBlank()) {
            return Optional.empty();
        }
        String worker = env.get(WORKER_NAME);
        if (worker == null || worker.isBlank()) {
            worker = hostName();
        }
        return Optional.of(new ServerLink(URI.create(server.trim()), env.get(USER), env.get(PASSWORD), worker.trim()));
    }

    private static String hostName() {
        try {
            return java.net.InetAddress.getLocalHost().getHostName();
        } catch (IOException e) {
            return "my-pc";
        }
    }

    public URI base() {
        return base;
    }

    /** What the Lab calls this computer. */
    public String worker() {
        return worker;
    }

    /** {@code path} on the server (no leading slash), with {@code ?worker=} added when {@code asWorker}. */
    URI uri(String path, boolean asWorker) {
        String q = asWorker ? (path.contains("?") ? "&" : "?") + "worker="
                + URLEncoder.encode(worker, StandardCharsets.UTF_8) : "";
        return base.resolve(path + q);
    }

    private HttpRequest.Builder request(URI uri) {
        HttpRequest.Builder b = HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(10));
        if (authorization != null) {
            b.header("Authorization", authorization);
        }
        return b;
    }

    public JsonObject getJson(String path, boolean asWorker) throws IOException {
        return json(send(request(uri(path, asWorker)).GET().build()));
    }

    public JsonObject postJson(String path, JsonObject body) throws IOException {
        return json(send(request(uri(path, true)).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body.toString())).build()));
    }

    public JsonObject put(String path, Path file, String contentType) throws IOException {
        return json(send(request(uri(path, true)).header("Content-Type", contentType)
                .PUT(HttpRequest.BodyPublishers.ofFile(file)).build()));
    }

    /** Saves what the server answers at {@code path} into {@code target}. */
    public void download(String path, Path target) throws IOException {
        HttpResponse<Path> r = exchange(request(uri(path, false)).GET().build(), HttpResponse.BodyHandlers.ofFile(target));
        if (r.statusCode() / 100 != 2) {
            throw new Refused(r.statusCode(), "cannot download " + path);
        }
    }

    private String send(HttpRequest request) throws IOException {
        HttpResponse<String> r = exchange(request, HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() == 401) {
            throw new Refused(401, "the server wants a user and password: set " + USER + " and " + PASSWORD);
        }
        if (r.statusCode() / 100 != 2) {
            throw new Refused(r.statusCode(), r.body().length() > 300 ? r.body().substring(0, 300) : r.body());
        }
        return r.body();
    }

    private <T> HttpResponse<T> exchange(HttpRequest request, HttpResponse.BodyHandler<T> handler) throws IOException {
        try {
            return http.send(request, handler);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IOException("interrupted", e);
        }
    }

    private static JsonObject json(String body) {
        return body.isBlank() ? new JsonObject() : JsonParser.parseString(body).getAsJsonObject();
    }

    @Override
    public String toString() {
        return base + " as " + worker + (authorization == null ? "" : " (with a password)");
    }
}
