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
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
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
            Files.createDirectories(dir);
            Path tmp = dir.resolve(variant.id() + ".json.tmp");
            Files.writeString(tmp, VariantJson.write(variant), StandardCharsets.UTF_8);
            try {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING);
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
            return Files.deleteIfExists(file(id));
        } catch (IOException e) {
            throw new UncheckedIOException(e);
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
