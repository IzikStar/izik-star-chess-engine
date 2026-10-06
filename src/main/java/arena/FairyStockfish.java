package arena;

import ai.variant.Variant;
import ai.variant.Variants;
import engine.FairyStockfishLocator;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Fairy-Stockfish as an arena player: the yardstick for variants, the way Stockfish is for chess.
 * It plays at full strength (variants have no UCI_Elo), so its strength is set by the nodes it may
 * search each move. Our built-in variants are its {@code UCI_Variant}s of the same rules (the move
 * generator is checked against it move for move, {@code VariantOracleTest}); a made variant is
 * handed to it as a config file ({@link FairyConfig}) when it can be written as one.
 */
public final class FairyStockfish {

    /** Nodes a move when a spec names none: about as strong as it gets at the arena's pace. */
    public static final long DEFAULT_NODES = 20_000;

    /** Our variant id -> Fairy-Stockfish's name for the same game (tools/variant_oracle.py). */
    private static final Map<String, String> NAMES = Map.of(
            Variants.CHESS.id(), "chess",
            Variants.ANTICHESS.id(), "antichess",
            Variants.KING_OF_THE_HILL.id(), "kingofthehill",
            Variants.THREE_CHECK.id(), "3check");

    private FairyStockfish() {}

    /**
     * Fairy-Stockfish's name for {@code variant}, if it plays it: its own name for a built-in
     * variant, the variant's id for a made one that {@link FairyConfig} can write.
     */
    public static Optional<String> variantName(Variant variant) {
        String name = NAMES.get(variant.id());
        if (name != null && Variants.ALL.contains(variant)) {
            return Optional.of(name);
        }
        return Variants.byId(variant.id()).isEmpty() && FairyConfig.of(variant).isPresent()
                ? Optional.of(variant.id()) : Optional.empty();
    }

    /** True if Fairy-Stockfish can play {@code variant}, whether or not it is installed. */
    public static boolean plays(Variant variant) {
        return variantName(variant).isPresent();
    }

    /** True if Fairy-Stockfish is installed on this computer. */
    public static boolean installed() {
        return FairyStockfishLocator.find().isPresent();
    }

    /** Fairy-Stockfish playing {@code variant} at full strength, {@code nodes} a move, one thread. */
    public static ExternalEngine engine(Variant variant, long nodes) {
        String name = variantName(variant).orElseThrow(() ->
                new IllegalArgumentException("Fairy-Stockfish does not know " + variant.name()));
        String path = FairyStockfishLocator.find().orElseThrow(() -> new IllegalStateException(
                "Fairy-Stockfish not found: put it in engine/ (" + FairyStockfishLocator.DOWNLOAD + ")")).toString();
        Map<String, String> options = new LinkedHashMap<>();
        options.put("Threads", "1");
        options.put("Hash", "16");
        Optional<String> config = Variants.ALL.contains(variant) ? Optional.empty() : FairyConfig.of(variant);
        if (config.isPresent()) { // before UCI_Variant, which must find the variant it defines
            options.put("VariantPath", configFile(config.get()).toString());
        }
        options.put("UCI_Variant", name);
        return new ExternalEngine(path, options, nodes, 0);
    }

    private static final Map<String, java.nio.file.Path> CONFIG_FILES = new java.util.concurrent.ConcurrentHashMap<>();

    /** A temporary file holding {@code config} (one per config), removed when the program ends. */
    private static java.nio.file.Path configFile(String config) {
        return CONFIG_FILES.computeIfAbsent(config, FairyStockfish::writeConfig);
    }

    private static java.nio.file.Path writeConfig(String config) {
        try {
            java.nio.file.Path file = java.nio.file.Files.createTempFile("fairy-variant-", ".ini");
            file.toFile().deleteOnExit();
            return java.nio.file.Files.writeString(file, config);
        } catch (java.io.IOException e) {
            throw new java.io.UncheckedIOException(e);
        }
    }

    /** True if {@code engine} is set to play {@code variant} ({@code UCI_Variant} is its name). */
    static boolean playsAs(ExternalEngine engine, Variant variant) {
        return variantName(variant).map(n -> n.equals(engine.options().get("UCI_Variant"))).orElse(false);
    }
}
