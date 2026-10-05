package ai.piece;

import ai.piece.Atom.Mode;
import ai.piece.Atom.Symmetry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class CompiledPieceTest {

    /** Squares by name on the chess grid, a8 = 0. */
    private static int sq(String name) {
        return (8 - (name.charAt(1) - '0')) * 8 + (name.charAt(0) - 'a');
    }

    private static long set(String... names) {
        long bits = 0;
        for (String name : names) {
            bits |= 1L << sq(name);
        }
        return bits;
    }

    @Test
    @DisplayName("Symmetries give each offset once")
    void symmetries() {
        assertEquals(8, Atom.leap(2, 1, Symmetry.ALL, Mode.BOTH).offsets().size());
        assertEquals(4, Atom.leap(1, 0, Symmetry.ALL, Mode.BOTH).offsets().size());
        assertEquals(4, Atom.leap(1, 1, Symmetry.ALL, Mode.BOTH).offsets().size());
        assertEquals(2, Atom.leap(1, 1, Symmetry.SIDEWAYS, Mode.BOTH).offsets().size());
        assertEquals(1, Atom.leap(1, 0, Symmetry.SIDEWAYS, Mode.BOTH).offsets().size());
        assertEquals(1, Atom.leap(2, 0, Symmetry.ONE, Mode.BOTH).offsets().size());
    }

    @Test
    @DisplayName("An invented piece: slides diagonally up to two squares, captures only by jumping two straight ahead or back")
    void inventedPiece() {
        PieceType piece = PieceType.of("Lancer", 'L', 400,
                Atom.slide(1, 1, Symmetry.ALL, Mode.MOVE).withRange(2),
                Atom.leap(2, 0, Symmetry.ONE, Mode.CAPTURE),
                Atom.leap(-2, 0, Symmetry.ONE, Mode.CAPTURE));
        CompiledPiece white = new CompiledPiece(piece, Grid.CHESS, 0);
        long occupied = set("d4", "c3");
        assertEquals(set("c5", "b6", "e5", "f6", "e3", "f2"), white.quietTargets(sq("d4"), occupied, false));
        assertEquals(set("d6", "d2"), white.captureTargets(sq("d4"), occupied, false));
    }

    @Test
    @DisplayName("Forward is toward the opponent: the same piece points the other way for player 1")
    void forwardDependsOnThePlayer() {
        PieceType spear = PieceType.of("Spear", 'S', 300, Atom.leap(2, 0, Symmetry.ONE, Mode.BOTH));
        assertEquals(set("d6"), new CompiledPiece(spear, Grid.CHESS, 0).captureTargets(sq("d4"), 0, false));
        assertEquals(set("d2"), new CompiledPiece(spear, Grid.CHESS, 1).captureTargets(sq("d4"), 0, false));
    }

    @Test
    @DisplayName("Pieces work on other rectangular grids")
    void smallGrid() {
        Grid grid = new Grid(5, 5);
        CompiledPiece rook = new CompiledPiece(StandardPieces.ROOK, grid, 0);
        int center = grid.square(2, 2);
        assertEquals(8, Long.bitCount(rook.quietTargets(center, 1L << center, false)));
        assertThrows(IllegalArgumentException.class, () -> new Grid(10, 8));
    }

    @Test
    @DisplayName("A piece needs a letter and a way to move")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> PieceType.of("Nothing", 'X', 0));
        assertThrows(IllegalArgumentException.class,
                () -> PieceType.of("Lower", 'x', 0, Atom.leap(1, 0, Symmetry.ALL, Mode.BOTH)));
        assertThrows(IllegalArgumentException.class, () -> Atom.leap(0, 0, Symmetry.ALL, Mode.BOTH));
        assertEquals(List.of('Q', 'R', 'B', 'N'), StandardPieces.PAWN.promotesTo());
    }
}
