package cloud;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.net.InetAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Where the shared database is and who this copy is: a Cloudflare account, a D1 database, an API
 * token that may edit it, and a name for this copy of the game (the server, the home computer).
 *
 * <p>Read from the environment ({@code IZIKSTAR_D1_ACCOUNT}, {@code IZIKSTAR_D1_DATABASE},
 * {@code IZIKSTAR_D1_TOKEN}, {@code IZIKSTAR_COPY}), else from a {@code cloud.properties} file in the
 * working directory ({@code account}, {@code database}, {@code token}, {@code copy}). Without an
 * account, database and token there is no sync and the game keeps everything on this computer only,
 * as before. The copy name defaults to the computer's name.
 */
public record CloudConfig(String account, String database, String token, String copy, String apiBase) {

    public static final String FILE = "cloud.properties";
    public static final String API_BASE = "https://api.cloudflare.com/client/v4";

    public CloudConfig {
        if (!copy.matches("[A-Za-z0-9._-]{1,64}")) {
            throw new IllegalArgumentException("a copy name is letters, digits, '.', '_' and '-': " + copy);
        }
    }

    /** The configuration in the environment or in {@code cloud.properties}, if there is one. */
    public static Optional<CloudConfig> load() {
        return load(System.getenv(), Path.of(FILE));
    }

    static Optional<CloudConfig> load(Map<String, String> env, Path file) {
        Properties p = new Properties();
        if (Files.isRegularFile(file)) {
            try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                p.load(r);
            } catch (IOException e) {
                throw new UncheckedIOException("cannot read " + file, e);
            }
        }
        String account = pick(env.get("IZIKSTAR_D1_ACCOUNT"), p.getProperty("account"));
        String database = pick(env.get("IZIKSTAR_D1_DATABASE"), p.getProperty("database"));
        String token = pick(env.get("IZIKSTAR_D1_TOKEN"), p.getProperty("token"));
        if (account == null || database == null || token == null) {
            return Optional.empty();
        }
        String copy = pick(env.get("IZIKSTAR_COPY"), p.getProperty("copy"));
        String base = pick(env.get("IZIKSTAR_D1_API"), p.getProperty("api"));
        return Optional.of(new CloudConfig(account, database, token, copy == null ? hostName() : copy,
                base == null ? API_BASE : base));
    }

    private static String pick(String first, String second) {
        String v = first != null && !first.isBlank() ? first : second;
        return v == null || v.isBlank() ? null : v.trim();
    }

    static String hostName() {
        try {
            String name = InetAddress.getLocalHost().getHostName().replaceAll("[^A-Za-z0-9._-]", "-");
            return name.isEmpty() ? "computer" : name.length() > 64 ? name.substring(0, 64) : name;
        } catch (IOException e) {
            return "computer";
        }
    }

    @Override
    public String toString() { // never print the token
        return "CloudConfig[account=" + account + ", database=" + database + ", copy=" + copy + "]";
    }
}
