package ai.piece;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Phase 6 R5a: pieces as Betza text, and back. */
class BetzaTest {

    /** Same quiet and capture squares from every square, on random boards, for both players and both first-move states. */
    private static void assertSameMoves(List<Atom> expected, List<Atom> actual, String label) {
        Random random = new Random(label.hashCode());
        for (int player = 0; player < 2; player++) {
            CompiledPiece a = new CompiledPiece(PieceType.of("A", 'A', 0, expected.toArray(Atom[]::new)), Grid.CHESS, player);
            CompiledPiece b = new CompiledPiece(PieceType.of("B", 'B', 0, actual.toArray(Atom[]::new)), Grid.CHESS, player);
            for (int trial = 0; trial < 40; trial++) {
                long occupied = random.nextLong() & random.nextLong();
                for (int sq = 0; sq < 64; sq++) {
                    for (boolean first : new boolean[]{false, true}) {
                        assertEquals(a.quietTargets(sq, occupied, first), b.quietTargets(sq, occupied, first), label + " quiet " + sq);
                        assertEquals(a.captureTargets(sq, occupied, first), b.captureTargets(sq, occupied, first), label + " capture " + sq);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("the chess pieces in Betza, as Fairy-Stockfish writes them, and back to the same moves")
    void standardPieces() {
        assertEquals("WF", Betza.write(StandardPieces.KING.atoms()));
        assertEquals("RB", Betza.write(StandardPieces.QUEEN.atoms()));
        assertEquals("R", Betza.write(StandardPieces.ROOK.atoms()));
        assertEquals("B", Betza.write(StandardPieces.BISHOP.atoms()));
        assertEquals("N", Betza.write(StandardPieces.KNIGHT.atoms()));
        assertEquals("mfWimfW2cfF", Betza.write(StandardPieces.PAWN.atoms()));
        for (PieceType t : StandardPieces.ALL) {
            assertSameMoves(t.atoms(), Betza.parse(Betza.write(t.atoms())), t.name());
        }
        assertSameMoves(StandardPieces.KING.atoms(), Betza.parse("K"), "K");
        assertSameMoves(StandardPieces.QUEEN.atoms(), Betza.parse("Q"), "Q");
        assertSameMoves(StandardPieces.ROOK.atoms(), Betza.parse("WW"), "WW");
        assertSameMoves(StandardPieces.PAWN.atoms(), Betza.parse("fmWifmW2fcF"), "pawn, modifiers in another order");
    }

    @Test
    @DisplayName("directions: Fairy-Stockfish's meanings for the knight's pairs, sides and halves")
    void directions() {
        assertSameMoves(List.of(Atom.leap(2, 1, Atom.Symmetry.ONE, Atom.Mode.BOTH)), Betza.parse("rfN"), "rfN");
        assertSameMoves(List.of(Atom.leap(1, 2, Atom.Symmetry.ONE, Atom.Mode.BOTH)), Betza.parse("frN"), "frN");
        assertSameMoves(List.of(Atom.leap(2, 1, Atom.Symmetry.SIDEWAYS, Atom.Mode.BOTH)), Betza.parse("ffN"), "ffN");
        assertSameMoves(List.of(Atom.leap(1, 2, Atom.Symmetry.SIDEWAYS, Atom.Mode.BOTH),
                Atom.leap(-1, 2, Atom.Symmetry.SIDEWAYS, Atom.Mode.BOTH)), Betza.parse("sN"), "sN");
        assertSameMoves(List.of(Atom.leap(1, 2, Atom.Symmetry.ONE, Atom.Mode.BOTH),
                Atom.leap(-1, 2, Atom.Symmetry.ONE, Atom.Mode.BOTH)), Betza.parse("rN"), "rN");
        assertSameMoves(List.of(Atom.slide(1, 0, Atom.Symmetry.ONE, Atom.Mode.BOTH),
                Atom.slide(-1, 0, Atom.Symmetry.ONE, Atom.Mode.BOTH)), Betza.parse("vR"), "vR");
        assertSameMoves(List.of(Atom.slide(1, 1, Atom.Symmetry.SIDEWAYS, Atom.Mode.MOVE)), Betza.parse("mfB"), "mfB");
        assertSameMoves(List.of(Atom.slide(1, 0, Atom.Symmetry.ALL, Atom.Mode.BOTH).withRange(3)), Betza.parse("R3"), "R3");
        assertSameMoves(List.of(Atom.slide(2, 1, Atom.Symmetry.ALL, Atom.Mode.BOTH)), Betza.parse("NN"), "nightrider");
    }

    @Test
    @DisplayName("any atom the designer can make writes to text that reads back to the same moves")
    void roundTrip() {
        Random random = new Random(5);
        int[][] offsets = {{1, 0}, {1, 1}, {2, 0}, {2, 1}, {1, 2}, {2, 2}, {3, 0}, {3, 1}, {3, 2}, {3, 3}};
        for (int n = 0; n < 400; n++) {
            int[] o = offsets[random.nextInt(offsets.length)];
            int f = random.nextBoolean() ? o[0] : -o[0];
            int r = random.nextBoolean() ? o[1] : -o[1];
            if (random.nextBoolean()) {
                int t = f;
                f = r;
                r = t;
            }
            Atom.Symmetry symmetry = Atom.Symmetry.values()[random.nextInt(3)];
            Atom.Mode mode = Atom.Mode.values()[random.nextInt(3)];
            Atom atom = random.nextBoolean()
                    ? Atom.leap(f, r, symmetry, mode)
                    : Atom.slide(f, r, symmetry, mode).withRange(random.nextInt(4));
            if (random.nextInt(4) == 0) {
                atom = atom.firstMove();
            }
            String text = Betza.write(atom);
            assertSameMoves(List.of(atom), Betza.parse(text), atom + " as " + text);
        }
    }

    @Test
    @DisplayName("what is not Betza is refused with a reason")
    void errors() {
        assertThrows(IllegalArgumentException.class, () -> Betza.parse(""));
        assertThrows(IllegalArgumentException.class, () -> Betza.parse("X"));
        assertThrows(IllegalArgumentException.class, () -> Betza.parse("fm"));
        assertThrows(IllegalArgumentException.class, () -> Betza.parse("xN"));
        assertThrows(IllegalArgumentException.class, () -> Betza.parse("R0"));
        assertThrows(IllegalArgumentException.class, () -> Betza.write(Atom.leap(4, 1, Atom.Symmetry.ALL, Atom.Mode.BOTH)));
    }
}
