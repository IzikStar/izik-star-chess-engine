package arena;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** A short opening line in UCI that a game of the arena starts from. */
public record Opening(String name, List<String> moves) {

    public Opening {
        moves = List.copyOf(moves);
    }

    /** The arena's suite of about fifty balanced openings ({@code arena/openings.txt}). */
    public static List<Opening> suite() {
        try (InputStream in = Opening.class.getResourceAsStream("/arena/openings.txt")) {
            if (in == null) {
                throw new IllegalStateException("arena/openings.txt is missing");
            }
            List<Opening> openings = new ArrayList<>();
            for (String line : new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList()) {
                if (line.isBlank() || line.startsWith("#")) {
                    continue;
                }
                String[] parts = line.split("\\|");
                openings.add(new Opening(parts[0].trim(), Arrays.asList(parts[1].trim().split("\\s+"))));
            }
            return List.copyOf(openings);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
