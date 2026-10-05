package ai.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.PerftPositions;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 6 R3: the generic board keeps its state move by move (occupied squares, unmoved pieces,
 * castling rights, en passant) and skips attack tests it can prove unneeded. Along random games,
 * a position reached by moves must read exactly as the same position parsed from its FEN, and
 * the shortcuts must agree with the full tests. Perft against the published counts is
 * {@link SearchPerftTest}; equality with the old bitboard was proven before it was deleted
 * (docs/phase-6-research.md §4c).
 */
class GenericBoardTest {

    static Stream<PerftPositions.Position> positions() {
        return Stream.concat(PerftPositions.STANDARD.stream(), PerftPositions.EDGE.stream());
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("positions")
    @DisplayName("Along random games a played position reads as the same position parsed from its FEN")
    void playedEqualsParsed(PerftPositions.Position start) {
        Random random = new Random(63);
        for (int game = 0; game < 4; game++) {
            ChessPosition board = (ChessPosition) Boards.fromFen(start.fen());
            for (int ply = 0; ply < 150; ply++) {
                String fen = board.toFen();
                ChessPosition parsed = (ChessPosition) Boards.fromFen(fen);
                assertEquals(fen, parsed.toFen(), "FEN round trip");
                assertEquals(byMove(parsed.children()), byMove(board.children()), fen);
                assertEquals(parsed.repetitionKey(), board.repetitionKey(), fen);
                // (the search key also knows who has castled, which a FEN does not say)
                for (int player = 0; player < 2; player++) {
                    assertEquals(parsed.occupied(player), board.occupied(player), fen);
                    assertEquals(parsed.attackedBy(player), board.attackedBy(player), fen);
                }
                checkShortcuts(board, fen);
                List<? extends Board> children = board.children();
                if (children.isEmpty()) {
                    break;
                }
                ChessPosition next = (ChessPosition) children.get(random.nextInt(children.size()));
                board.releaseChildren();
                board = next;
            }
        }
    }

    /** What the search reads equals what the full tests say. */
    private static void checkShortcuts(ChessPosition board, String fen) {
        int side = board.sideToMove();
        long king = board.pieces(side, ChessPosition.KING);
        assertEquals((board.attackedBy(1 - side) & king) != 0, board.inCheck(), fen);
        assertEquals(!board.children().isEmpty(), board.hasLegalMove(), fen);
        // the moves that give check come first, so every check is found by the full test
        boolean checksDone = false;
        for (Board child : board.orderedChildren()) {
            ChessPosition c = (ChessPosition) child;
            long enemyKing = c.pieces(1 - side, ChessPosition.KING);
            assertEquals((c.attackedBy(side) & enemyKing) != 0, c.inCheck(), fen);
            assertTrue(!(checksDone && c.inCheck()), "a check after a quiet move in " + fen);
            checksDone |= !c.inCheck();
            assertTrue((c.attackedBy(1 - side) & c.pieces(side, ChessPosition.KING)) == 0,
                    "the mover's king is left attacked in " + fen);
        }
        board.releaseChildren();
    }

    private static Map<Integer, String> byMove(List<? extends Board> children) {
        Map<Integer, String> out = new TreeMap<>();
        for (Board child : children) {
            out.put(child.lastMove(), child.toFen());
        }
        return out;
    }
}
