package game;

import ai.piece.PieceType;
import ai.variant.VariantJson;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import java.util.stream.Stream;

/**
 * The player's piece bank: pieces kept apart from any variant, to put into any variant. One JSON
 * file each ({@link VariantJson#piece(PieceType)}) in a folder, with the piece's pictures beside it
 * as {@code <id>-w.<ext>} and {@code <id>-b.<ext>}.
 */
public final class PieceBank {

    /** A piece in the bank and when its pictures were saved (side {@code w}/{@code b} -> epoch millis). */
    public record Entry(String id, PieceType piece, Map<Character, Long> art) {}

    private final Path dir;

    public PieceBank(Path dir) {
        this.dir = dir;
    }

    /** All the pieces, by name. */
    public synchronized List<Entry> all() {
        List<Entry> out = new ArrayList<>();
        if (!Files.isDirectory(dir)) {
            return out;
        }
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                String id = f.getFileName().toString().replaceFirst("\\.json$", "");
                try {
                    out.add(read(id, f));
                } catch (RuntimeException e) {
                    // a file that does not read is left out, not fatal
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort((a, b) -> a.piece().name().compareToIgnoreCase(b.piece().name()));
        return out;
    }

    public synchronized Optional<Entry> byId(String id) {
        Path f = file(id);
        return validId(id) && Files.isRegularFile(f) ? Optional.of(read(id, f)) : Optional.empty();
    }

    /** Adds a piece under a new id made from its name; returns the id. */
    public synchronized String add(PieceType piece) {
        String base = slug(piece.name());
        String id = base;
        for (int n = 2; Files.exists(file(id)); n++) {
            id = base + "-" + n;
        }
        put(id, piece);
        return id;
    }

    /**
     * Saves a piece under {@code id}, replacing the one there was (its pictures stay).
     *
     * @throws IllegalArgumentException if the id is not lower-case letters, digits and dashes
     */
    public synchronized void put(String id, PieceType piece) {
        check(id);
        try {
            Files.createDirectories(dir);
            AtomicWrite.write(file(id), VariantJson.piece(piece).toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** Removes a piece and its pictures; false if there was none. */
    public synchronized boolean delete(String id) {
        if (!validId(id)) {
            return false;
        }
        for (char side : new char[] {'w', 'b'}) {
            deleteArt(id, side);
        }
        try {
            return Files.deleteIfExists(file(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * Saves a piece's picture for one side, replacing the one there was.
     *
     * @throws IllegalArgumentException if there is no such piece, or the picture is not one
     *         {@link VariantStore#artExtension} takes
     */
    public synchronized void saveArt(String id, char side, String type, byte[] bytes) {
        checkSide(side);
        if (byId(id).isEmpty()) {
            throw new IllegalArgumentException("the bank has no piece " + id);
        }
        String ext = VariantStore.artExtension(type, bytes);
        deleteArt(id, side);
        try {
            Files.write(dir.resolve(id + "-" + side + "." + ext), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    public synchronized Optional<VariantStore.Art> art(String id, char side) {
        if (!validId(id) || (side != 'w' && side != 'b')) {
            return Optional.empty();
        }
        for (var e : VariantStore.ART_TYPES.entrySet()) {
            Path f = dir.resolve(id + "-" + side + "." + e.getKey());
            if (Files.isRegularFile(f)) {
                try {
                    return Optional.of(new VariantStore.Art(Files.readAllBytes(f), e.getValue(), Files.getLastModifiedTime(f).toMillis()));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        }
        return Optional.empty();
    }

    /** Removes a piece's picture for one side; false if there was none. */
    public synchronized boolean deleteArt(String id, char side) {
        checkSide(side);
        check(id);
        boolean any = false;
        try {
            for (String ext : VariantStore.ART_TYPES.keySet()) {
                any |= Files.deleteIfExists(dir.resolve(id + "-" + side + "." + ext));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return any;
    }

    private Entry read(String id, Path f) {
        try {
            JsonObject o = JsonParser.parseString(Files.readString(f, StandardCharsets.UTF_8)).getAsJsonObject();
            Map<Character, Long> art = new TreeMap<>();
            for (char side : new char[] {'w', 'b'}) {
                art(id, side).ifPresent(a -> art.put(side, a.modified()));
            }
            return new Entry(id, VariantJson.piece(o), art);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private Path file(String id) {
        return dir.resolve(id + ".json");
    }

    private static boolean validId(String id) {
        return id.matches("[a-z0-9][a-z0-9-]{0,63}");
    }

    private static void check(String id) {
        if (!validId(id)) {
            throw new IllegalArgumentException("a bank id is lower-case letters, digits and dashes: " + id);
        }
    }

    private static void checkSide(char side) {
        if (side != 'w' && side != 'b') {
            throw new IllegalArgumentException("a picture is for side w or b");
        }
    }

    /** An id from a name: lower-case latin letters and digits joined by dashes; "piece" when none are left. */
    static String slug(String name) {
        String s = Normalizer.normalize(name, Normalizer.Form.NFKD).toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        if (s.length() > 48) {
            s = s.substring(0, 48).replaceAll("-+$", "");
        }
        return s.isEmpty() ? "piece" : s;
    }
}
