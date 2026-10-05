package game;

import ai.piece.StandardPieces;
import ai.variant.TestVariants;
import ai.variant.Variant;
import ai.variant.VariantJson;
import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 R5a: the player's variants, kept as files. */
class VariantStoreTest {

    @TempDir
    Path dir;

    @Test
    @DisplayName("a made variant is saved, listed after the built-ins, found by id and by name, and deleted")
    void saveListFindDelete() {
        VariantStore store = new VariantStore(dir.resolve("variants"));
        assertEquals(Variants.ALL, store.all());
        store.save(TestVariants.AMAZON_CHESS);
        assertEquals(Variants.ALL.size() + 1, store.all().size());
        assertEquals(TestVariants.AMAZON_CHESS, store.byId("amazon-chess").orElseThrow());
        assertEquals(TestVariants.AMAZON_CHESS, store.byName("Amazon Chess").orElseThrow());
        assertEquals(Variants.CHESS, store.byName("Standard").orElseThrow());
        assertEquals(Variants.ANTICHESS, store.byId("antichess").orElseThrow());
        assertTrue(store.delete("amazon-chess"));
        assertFalse(store.delete("amazon-chess"));
        assertFalse(store.delete("chess"));
        assertTrue(store.byId("amazon-chess").isEmpty());
        assertTrue(store.byId("../x").isEmpty());
    }

    @Test
    @DisplayName("a built-in's id, or a variant that cannot be played, is refused with the reason")
    void refused() {
        VariantStore store = new VariantStore(dir);
        Variant v = TestVariants.AMAZON_CHESS;
        assertThrows(IllegalArgumentException.class, () -> store.save(new Variant("chess", "Mine", v.pieces(), v.grid(),
                v.startFen(), v.goal(), 0, false, true)));
        IllegalArgumentException promotes = assertThrows(IllegalArgumentException.class, () -> store.save(new Variant("bad",
                "Bad", List.of(StandardPieces.KING, StandardPieces.ROOK, TestVariants.pawnPromotingTo('Q')), v.grid(),
                "4k3/pppppppp/8/8/8/8/PPPPPPPP/R3K3 w - - 0 1", Variant.Goal.CHECKMATE, 0, false, false)));
        assertTrue(promotes.getMessage().contains("promotes to Q"), promotes.getMessage());
        IllegalArgumentException fen = assertThrows(IllegalArgumentException.class, () -> store.save(new Variant("bad",
                "Bad", v.pieces(), v.grid(), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", v.goal(), 0, false, false)));
        assertTrue(fen.getMessage().contains("start position"), fen.getMessage());
        assertEquals(List.of(), store.custom());
    }

    @Test
    @DisplayName("a hand-written file may give a piece's moves as Betza only; an unreadable file is skipped")
    void handWritten() throws Exception {
        Files.createDirectories(dir);
        String json = VariantJson.write(TestVariants.AMAZON_CHESS)
                .replaceAll("(?s)\"atoms\": \\[.*?\\],\\s*", "");
        assertFalse(json.contains("atoms"), json);
        Files.writeString(dir.resolve("amazon-chess.json"), json);
        Files.writeString(dir.resolve("broken.json"), "{");
        VariantStore store = new VariantStore(dir);
        Variant read = store.byId("amazon-chess").orElseThrow();
        assertEquals(TestVariants.AMAZON, read.pieces().get(1)); // "RBN" read back
        assertEquals(1, store.custom().size());
        VariantStore.check(read);
    }
}
