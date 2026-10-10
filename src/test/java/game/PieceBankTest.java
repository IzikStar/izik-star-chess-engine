package game;

import ai.piece.PieceType;
import ai.piece.StandardPieces;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The piece bank: pieces kept apart from any variant, with their pictures. */
class PieceBankTest {

    @TempDir
    Path dir;

    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G'};

    @Test
    @DisplayName("a piece is added under an id from its name, read back the same, replaced and deleted")
    void addReadReplaceDelete() {
        PieceBank bank = new PieceBank(dir.resolve("bank"));
        assertEquals(List.of(), bank.all());
        PieceType knight = StandardPieces.KNIGHT;
        String id = bank.add(knight);
        assertEquals("knight", id);
        assertEquals("knight-2", bank.add(knight));
        assertEquals(knight, bank.byId("knight").orElseThrow().piece());
        assertEquals(2, bank.all().size());

        PieceType renamed = new PieceType("Horse", 'H', knight.atoms(), false, List.of(), false, PieceType.Castling.NONE, 310);
        bank.put("knight", renamed);
        assertEquals(renamed, bank.byId("knight").orElseThrow().piece());
        assertTrue(bank.delete("knight-2"));
        assertFalse(bank.delete("knight-2"));
        assertEquals(List.of("knight"), bank.all().stream().map(PieceBank.Entry::id).toList());
    }

    @Test
    @DisplayName("a name with no latin letters still gets an id")
    void hebrewName() {
        PieceBank bank = new PieceBank(dir);
        PieceType k = StandardPieces.KNIGHT;
        PieceType p = new PieceType("פרש", 'A', k.atoms(), false, List.of(), false, PieceType.Castling.NONE, 300);
        assertEquals("piece", bank.add(p));
        assertEquals("piece-2", bank.add(p));
        assertEquals("פרש", bank.byId("piece").orElseThrow().piece().name());
    }

    @Test
    @DisplayName("pictures are kept per side, listed with the piece, and go when the piece goes")
    void pictures() {
        PieceBank bank = new PieceBank(dir);
        String id = bank.add(StandardPieces.KNIGHT);
        bank.saveArt(id, 'w', "image/png", PNG);
        assertArrayEquals(PNG, bank.art(id, 'w').orElseThrow().bytes());
        assertTrue(bank.art(id, 'b').isEmpty());
        assertEquals(java.util.Set.of('w'), bank.byId(id).orElseThrow().art().keySet());
        assertThrows(IllegalArgumentException.class, () -> bank.saveArt(id, 'w', "text/html", PNG));
        assertThrows(IllegalArgumentException.class, () -> bank.saveArt("nobody", 'w', "image/png", PNG));
        assertThrows(IllegalArgumentException.class, () -> bank.put("../x", StandardPieces.KNIGHT));
        assertTrue(bank.delete(id));
        assertTrue(bank.art(id, 'w').isEmpty());
    }
}
