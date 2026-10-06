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
    @DisplayName("a made variant's family and notes are kept beside it, not in it, and go with it")
    void about() throws Exception {
        VariantStore store = new VariantStore(dir);
        assertThrows(IllegalArgumentException.class, () -> store.saveAbout("amazon-chess", new VariantStore.About("Amazons", "")));
        store.save(TestVariants.AMAZON_CHESS);
        assertEquals(VariantStore.About.NONE, store.about("amazon-chess"));
        store.saveAbout("amazon-chess", new VariantStore.About("  Amazons ", "Queen plus knight.\nTry it."));
        assertEquals(new VariantStore.About("Amazons", "Queen plus knight.\nTry it."), store.about("amazon-chess"));
        assertTrue(Files.isRegularFile(dir.resolve("amazon-chess.about")));
        // the sidecar is not a variant, and the variant file has no trace of it
        assertEquals(1, store.custom().size());
        assertFalse(Files.readString(dir.resolve("amazon-chess.json")).contains("Amazons"));
        assertTrue(store.modified("amazon-chess") > 0);
        assertEquals(0, store.modified("chess"));
        assertEquals(VariantStore.About.NONE, store.about("chess"));
        assertThrows(IllegalArgumentException.class, () -> store.saveAbout("chess", new VariantStore.About("x", "")));
        // saving the variant again keeps them; empty ones remove the file
        store.save(TestVariants.AMAZON_CHESS);
        assertEquals("Amazons", store.about("amazon-chess").family());
        store.saveAbout("amazon-chess", VariantStore.About.NONE);
        assertFalse(Files.exists(dir.resolve("amazon-chess.about")));
        // a broken sidecar reads as none
        Files.writeString(dir.resolve("amazon-chess.about"), "{not json");
        assertEquals(VariantStore.About.NONE, store.about("amazon-chess"));
        store.saveAbout("amazon-chess", new VariantStore.About("Amazons", ""));
        assertTrue(store.delete("amazon-chess"));
        assertFalse(Files.exists(dir.resolve("amazon-chess.about")));
    }

    @Test
    @DisplayName("a built-in's id, or a variant that cannot be played, is refused with the reason")
    void refused() {
        VariantStore store = new VariantStore(dir);
        Variant v = TestVariants.AMAZON_CHESS;
        assertThrows(IllegalArgumentException.class, () -> store.save(new Variant("chess", "Mine", v.pieces(), v.grid(),
                v.startFen(), Variant.Goal.CHECKMATE, 0, false, true)));
        IllegalArgumentException promotes = assertThrows(IllegalArgumentException.class, () -> store.save(new Variant("bad",
                "Bad", List.of(StandardPieces.KING, StandardPieces.ROOK, TestVariants.pawnPromotingTo('Q')), v.grid(),
                "4k3/pppppppp/8/8/8/8/PPPPPPPP/R3K3 w - - 0 1", Variant.Goal.CHECKMATE, 0, false, false)));
        assertTrue(promotes.getMessage().contains("promotes to Q"), promotes.getMessage());
        IllegalArgumentException fen = assertThrows(IllegalArgumentException.class, () -> store.save(new Variant("bad",
                "Bad", v.pieces(), v.grid(), "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w - - 0 1", Variant.Goal.CHECKMATE, 0, false, false)));
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

    @Test
    @DisplayName("a made piece's pictures are saved per side, listed, checked, and go with the piece or the variant")
    void pictures() {
        VariantStore store = new VariantStore(dir.resolve("variants"));
        byte[] png = {(byte) 0x89, 'P', 'N', 'G'};
        assertThrows(IllegalArgumentException.class, () -> store.saveArt("amazon-chess", 'A', 'w', "image/png", png));
        store.save(TestVariants.AMAZON_CHESS);
        store.saveArt("amazon-chess", 'A', 'w', "image/png", png);
        store.saveArt("amazon-chess", 'A', 'b', "image/svg+xml", "<svg/>".getBytes());
        VariantStore.Art art = store.art("amazon-chess", 'A', 'w').orElseThrow();
        assertEquals("image/png", art.type());
        assertEquals(4, art.bytes().length);
        assertEquals(java.util.Set.of('w', 'b'), store.artIndex("amazon-chess").get('A').keySet());
        // a new picture replaces the old, whatever its type
        store.saveArt("amazon-chess", 'A', 'b', "image/png", png);
        assertEquals("image/png", store.art("amazon-chess", 'A', 'b').orElseThrow().type());
        assertThrows(IllegalArgumentException.class, () -> store.saveArt("amazon-chess", 'A', 'w', "text/html", png));
        assertThrows(IllegalArgumentException.class, () -> store.saveArt("amazon-chess", 'Z', 'w', "image/png", png));
        assertThrows(IllegalArgumentException.class, () -> store.saveArt("amazon-chess", 'A', 'x', "image/png", png));
        assertThrows(IllegalArgumentException.class,
                () -> store.saveArt("amazon-chess", 'A', 'w', "image/png", new byte[VariantStore.MAX_ART_BYTES + 1]));
        assertThrows(IllegalArgumentException.class, () -> store.saveArt("chess", 'K', 'w', "image/png", png));
        assertTrue(store.deleteArt("amazon-chess", 'A', 'b'));
        assertTrue(store.art("amazon-chess", 'A', 'b').isEmpty());

        // saved without the Amazon: its picture goes; deleting the variant takes the rest
        store.saveArt("amazon-chess", 'N', 'w', "image/png", png);
        Variant noAmazon = VariantJson.read(VariantJson.write(Variants.CHESS).replace("\"chess\"", "\"amazon-chess\""));
        store.save(noAmazon);
        assertEquals(java.util.Set.of('N'), store.artIndex("amazon-chess").keySet());
        assertTrue(store.delete("amazon-chess"));
        assertFalse(Files.exists(dir.resolve("variants").resolve("amazon-chess.art")));
    }
}
