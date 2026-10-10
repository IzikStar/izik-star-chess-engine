package game;

import ai.board.Board;
import ai.board.Boards;
import ai.piece.PieceType;
import ai.variant.Variant;
import ai.variant.VariantJson;
import ai.variant.Variants;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * The variants the player made (Phase 6 R5a), one JSON file each ({@link VariantJson}) in a folder
 * ({@code variants/} by default), next to the built-in ones ({@link Variants}). Thread-safe.
 *
 * <p>A game keeps its own copy of a made variant ({@link SavedGame#variantDef()}), so changing or
 * deleting a variant here never breaks a game played with it.
 */
public final class VariantStore {

    private final Path dir;

    public VariantStore(Path dir) {
        this.dir = dir;
    }

    public Path dir() {
        return dir;
    }

    /** The built-in variants, then the player's, by name. A file that cannot be read is skipped (and reported). */
    public synchronized List<Variant> all() {
        List<Variant> out = new ArrayList<>(Variants.ALL);
        out.addAll(custom());
        return out;
    }

    /** The player's variants, by name. */
    public synchronized List<Variant> custom() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        List<Variant> out = new ArrayList<>();
        try (Stream<Path> files = Files.list(dir)) {
            for (Path f : files.filter(p -> p.getFileName().toString().endsWith(".json")).sorted().toList()) {
                try {
                    out.add(VariantJson.read(Files.readString(f, StandardCharsets.UTF_8)));
                } catch (IOException | RuntimeException e) {
                    System.err.println("Skipping unreadable variant " + f + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        out.sort(Comparator.comparing(v -> v.name().toLowerCase()));
        return out;
    }

    public static boolean isBuiltIn(String id) {
        return Variants.byId(id).isPresent();
    }

    /** A built-in or made variant by id. */
    public synchronized Optional<Variant> byId(String id) {
        Optional<Variant> builtIn = Variants.byId(id);
        if (builtIn.isPresent() || !id.matches("[a-z0-9-]{1,64}") || !Files.isRegularFile(file(id))) {
            return builtIn;
        }
        try {
            return Optional.of(VariantJson.read(Files.readString(file(id), StandardCharsets.UTF_8)));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** A variant by its name or id, any case, as a PGN {@code Variant} tag names it; "Standard" is chess. */
    public synchronized Optional<Variant> byName(String name) {
        String want = name.trim();
        if (want.equalsIgnoreCase("standard")) {
            return Optional.of(Variants.CHESS);
        }
        return all().stream().filter(v -> v.name().equalsIgnoreCase(want) || v.id().equalsIgnoreCase(want)).findFirst();
    }

    /**
     * Saves a made variant, replacing one with the same id.
     *
     * @throws IllegalArgumentException if its id is a built-in's, or it cannot be played ({@link #check})
     */
    public synchronized void save(Variant variant) {
        if (isBuiltIn(variant.id())) {
            throw new IllegalArgumentException("\"" + variant.id() + "\" is a built-in variant; pick another id");
        }
        check(variant);
        Path file = file(variant.id());
        try {
            AtomicWrite.write(file, VariantJson.write(variant));
            // pictures of pieces the variant no longer has go with them
            for (var e : artIndex(variant.id()).entrySet()) {
                if (variant.pieces().stream().noneMatch(t -> t.letter() == e.getKey())) {
                    for (char side : e.getValue().keySet()) {
                        deleteArt(variant.id(), e.getKey(), side);
                    }
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("could not save the variant to " + file, e);
        }
    }

    /** Removes a made variant; false if there was none (built-ins cannot be removed). */
    public synchronized boolean delete(String id) {
        if (isBuiltIn(id) || !id.matches("[a-z0-9-]{1,64}")) {
            return false;
        }
        try {
            deleteAllArt(id);
            Files.deleteIfExists(aboutFile(id));
            return Files.deleteIfExists(file(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * What the player wrote about a made variant, kept beside it in {@code <id>.about} (JSON) so the
     * variant's own file stays the engine's: the family it belongs to (variants that are small
     * changes of one idea share one) and free notes. Empty strings when there are none.
     */
    public record About(String family, String notes) {
        public static final About NONE = new About("", "");

        public About {
            family = family == null ? "" : family.strip();
            notes = notes == null ? "" : notes;
            if (family.length() > 100 || notes.length() > 10_000) {
                throw new IllegalArgumentException("a family name is at most 100 characters and notes at most 10000");
            }
        }
    }

    /** The family and notes of a made variant ({@link About#NONE} for a built-in, or when none were saved). */
    public synchronized About about(String id) {
        if (!id.matches("[a-z0-9-]{1,64}") || isBuiltIn(id) || !Files.isRegularFile(aboutFile(id))) {
            return About.NONE;
        }
        try {
            com.google.gson.JsonObject o = com.google.gson.JsonParser
                    .parseString(Files.readString(aboutFile(id), StandardCharsets.UTF_8)).getAsJsonObject();
            return new About(o.has("family") ? o.get("family").getAsString() : "",
                    o.has("notes") ? o.get("notes").getAsString() : "");
        } catch (IOException | RuntimeException e) {
            System.err.println("Skipping unreadable notes " + aboutFile(id) + ": " + e.getMessage());
            return About.NONE;
        }
    }

    /**
     * Saves the family and notes of a made variant that is saved already; empty ones remove the file.
     *
     * @throws IllegalArgumentException if there is no such made variant
     */
    public synchronized void saveAbout(String id, About about) {
        if (isBuiltIn(id) || !id.matches("[a-z0-9-]{1,64}") || !Files.isRegularFile(file(id))) {
            throw new IllegalArgumentException("save the variant first: no made variant " + id);
        }
        try {
            if (about.family().isEmpty() && about.notes().isEmpty()) {
                Files.deleteIfExists(aboutFile(id));
                return;
            }
            com.google.gson.JsonObject o = new com.google.gson.JsonObject();
            o.addProperty("family", about.family());
            o.addProperty("notes", about.notes());
            AtomicWrite.write(aboutFile(id), o.toString());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /** When a made variant, or what was written about it, last changed (epoch millis); 0 for a built-in. */
    public synchronized long modified(String id) {
        if (isBuiltIn(id) || !id.matches("[a-z0-9-]{1,64}")) {
            return 0;
        }
        long at = 0;
        for (Path f : List.of(file(id), aboutFile(id))) {
            try {
                if (Files.isRegularFile(f)) {
                    at = Math.max(at, Files.getLastModifiedTime(f).toMillis());
                }
            } catch (IOException e) {
                // unknown: leave it out
            }
        }
        return at;
    }

    private Path aboutFile(String id) {
        return dir.resolve(id + ".about");
    }

    /** The picture types a piece's art may have, by file extension. */
    public static final java.util.Map<String, String> ART_TYPES = java.util.Map.of(
            "png", "image/png", "jpg", "image/jpeg", "webp", "image/webp", "gif", "image/gif", "svg", "image/svg+xml");

    /** The largest picture a piece may have. */
    public static final int MAX_ART_BYTES = 1 << 20;

    /** A piece's picture: its bytes, media type and when it was saved (for the browser's cache). */
    public record Art(byte[] bytes, String type, long modified) {}

    private Path artDir(String id) {
        return dir.resolve(id + ".art");
    }

    private static void checkArtKey(char letter, char side) {
        if (letter < 'A' || letter > 'Z' || (side != 'w' && side != 'b')) {
            throw new IllegalArgumentException("a picture is for a letter A-Z and side w or b");
        }
    }

    /**
     * Saves the picture of a made variant's piece for one side ({@code w} or {@code b}), replacing
     * the one there was.
     *
     * @throws IllegalArgumentException if there is no such made variant or piece, or the picture is
     *         not a PNG, JPEG, WebP, GIF or SVG of at most {@link #MAX_ART_BYTES}
     */
    public synchronized void saveArt(String id, char letter, char side, String type, byte[] bytes) {
        checkArtKey(letter, side);
        Variant variant = byId(id).filter(v -> !isBuiltIn(v.id()))
                .orElseThrow(() -> new IllegalArgumentException("save the variant first: no made variant " + id));
        if (variant.pieces().stream().noneMatch(t -> t.letter() == letter)) {
            throw new IllegalArgumentException(id + " has no piece " + letter);
        }
        String ext = artExtension(type, bytes);
        try {
            Files.createDirectories(artDir(id));
            deleteArt(id, letter, side);
            Files.write(artDir(id).resolve(letter + "-" + side + "." + ext), bytes);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    /**
     * The file extension for a picture of this Content-Type.
     *
     * @throws IllegalArgumentException if it is not a PNG, JPEG, WebP, GIF or SVG of at most {@link #MAX_ART_BYTES}
     */
    static String artExtension(String type, byte[] bytes) {
        String ext = ART_TYPES.entrySet().stream().filter(e -> e.getValue().equals(type)).map(java.util.Map.Entry::getKey)
                .findFirst().orElseThrow(() -> new IllegalArgumentException("a picture is PNG, JPEG, WebP, GIF or SVG, not " + type));
        if (bytes.length == 0 || bytes.length > MAX_ART_BYTES) {
            throw new IllegalArgumentException("a picture is at most " + MAX_ART_BYTES / 1024 + " KB");
        }
        return ext;
    }

    /** The picture of a piece for one side, if there is one. */
    public synchronized Optional<Art> art(String id, char letter, char side) {
        if (!id.matches("[a-z0-9-]{1,64}")) {
            return Optional.empty();
        }
        checkArtKey(letter, side);
        for (var e : ART_TYPES.entrySet()) {
            Path f = artDir(id).resolve(letter + "-" + side + "." + e.getKey());
            if (Files.isRegularFile(f)) {
                try {
                    return Optional.of(new Art(Files.readAllBytes(f), e.getValue(), Files.getLastModifiedTime(f).toMillis()));
                } catch (IOException ex) {
                    throw new UncheckedIOException(ex);
                }
            }
        }
        return Optional.empty();
    }

    /** Which pieces of a variant have pictures: letter -> side ({@code w}, {@code b}) -> when saved. */
    public synchronized java.util.Map<Character, java.util.Map<Character, Long>> artIndex(String id) {
        java.util.Map<Character, java.util.Map<Character, Long>> out = new java.util.TreeMap<>();
        if (!id.matches("[a-z0-9-]{1,64}") || !Files.isDirectory(artDir(id))) {
            return out;
        }
        try (Stream<Path> files = Files.list(artDir(id))) {
            for (Path f : files.toList()) {
                String n = f.getFileName().toString();
                if (n.matches("[A-Z]-[wb]\\.[a-z]+") && ART_TYPES.containsKey(n.substring(4))) {
                    out.computeIfAbsent(n.charAt(0), k -> new java.util.TreeMap<>())
                            .put(n.charAt(2), Files.getLastModifiedTime(f).toMillis());
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return out;
    }

    /** Removes a piece's picture for one side; false if there was none. */
    public synchronized boolean deleteArt(String id, char letter, char side) {
        checkArtKey(letter, side);
        boolean any = false;
        try {
            for (String ext : ART_TYPES.keySet()) {
                any |= Files.deleteIfExists(artDir(id).resolve(letter + "-" + side + "." + ext));
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return any;
    }

    private void deleteAllArt(String id) throws IOException {
        Path art = artDir(id);
        if (Files.isDirectory(art)) {
            try (Stream<Path> files = Files.list(art)) {
                for (Path f : files.toList()) {
                    Files.deleteIfExists(f);
                }
            }
            Files.deleteIfExists(art);
        }
    }

    /**
     * Throws, saying why, unless the variant can be played: its pieces promote only to pieces it
     * has, its start position reads, and the first two moves of every line can be generated.
     *
     * @throws IllegalArgumentException with the reason
     */
    public static void check(Variant variant) {
        for (PieceType t : variant.pieces()) {
            for (char p : t.promotesTo()) {
                if (variant.pieces().stream().noneMatch(o -> o.letter() == p)) {
                    throw new IllegalArgumentException(t.name() + " promotes to " + p + ", which is not a piece of this variant");
                }
            }
        }
        Board start;
        try {
            start = Boards.fromFen(variant, variant.startFen());
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("the start position does not read: " + e.getMessage(), e);
        }
        if (start.isOver()) {
            throw new IllegalArgumentException("the game is over in its start position");
        }
        for (Board child : start.children()) {
            child.children();
        }
    }

    private Path file(String id) {
        return dir.resolve(id + ".json");
    }
}
