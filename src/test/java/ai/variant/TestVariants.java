package ai.variant;

import ai.piece.Betza;
import ai.piece.Grid;
import ai.piece.PieceType;
import ai.piece.StandardPieces;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Made-up variants for tests (Phase 6 R5): what the owner could build in the designer. */
public final class TestVariants {

    private TestVariants() {}

    /** The Amazon: a queen that also jumps like a knight. */
    public static final PieceType AMAZON = PieceType.of("Amazon", 'A', 1200, Betza.parse("QN").toArray(ai.piece.Atom[]::new));

    /** Chess with an Amazon in place of the queen; pawns promote to it. */
    public static final Variant AMAZON_CHESS = new Variant("amazon-chess", "Amazon chess",
            List.of(StandardPieces.KING, AMAZON, StandardPieces.ROOK, StandardPieces.BISHOP, StandardPieces.KNIGHT,
                    pawnPromotingTo('A', 'R', 'B', 'N')),
            Grid.CHESS, "rnbakbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBAKBNR w KQkq - 0 1", Variant.Goal.CHECKMATE, 0, false, true);

    public static PieceType pawnPromotingTo(Character... letters) {
        PieceType p = StandardPieces.PAWN;
        return new PieceType(p.name(), p.letter(), p.atoms(), p.royal(), List.of(letters), p.enPassant(), p.castling(), p.value());
    }

    private static final Map<String, Variant> MADE = new ConcurrentHashMap<>();

    /**
     * A made-up variant from {@code variants/made/<id>.json} (the files {@code tools/variant_oracle.py}
     * gives Fairy-Stockfish), read through its pieces' Betza text alone.
     */
    public static Variant made(String id) {
        return MADE.computeIfAbsent(id, key -> {
            try (InputStream in = TestVariants.class.getResourceAsStream("/variants/made/" + key + ".json")) {
                if (in == null) {
                    throw new IllegalStateException("no variant " + key + " in variants/made");
                }
                JsonObject tree = JsonParser.parseString(new String(in.readAllBytes(), StandardCharsets.UTF_8)).getAsJsonObject();
                tree.getAsJsonArray("pieces").forEach(p -> p.getAsJsonObject().remove("atoms"));
                return VariantJson.fromTree(tree);
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }
}
