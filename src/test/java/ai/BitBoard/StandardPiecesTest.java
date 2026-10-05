package ai.BitBoard;

import ai.piece.CompiledPiece;
import ai.piece.Grid;
import ai.piece.PieceType;
import ai.piece.StandardPieces;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 6 R2: the six chess pieces written as data attack exactly the squares the hand-written
 * bitboard tables and rays ({@link Attacks}) give, on every square, for both players, over random
 * occupancies; and they move to the same empty squares.
 */
class StandardPiecesTest {

    private static final int OCCUPANCIES = 300;

    private static CompiledPiece compile(PieceType type, int player) {
        return new CompiledPiece(type, Grid.CHESS, player);
    }

    /** Engine colour (1 White, 0 Black) of a player (0 White, 1 Black). */
    private static int color(int player) {
        return 1 - player;
    }

    @Test
    @DisplayName("Attacks match the bitboard's tables and rays")
    void attacksMatch() {
        Random random = new Random(62);
        for (int player = 0; player < 2; player++) {
            CompiledPiece king = compile(StandardPieces.KING, player);
            CompiledPiece queen = compile(StandardPieces.QUEEN, player);
            CompiledPiece rook = compile(StandardPieces.ROOK, player);
            CompiledPiece bishop = compile(StandardPieces.BISHOP, player);
            CompiledPiece knight = compile(StandardPieces.KNIGHT, player);
            CompiledPiece pawn = compile(StandardPieces.PAWN, player);
            for (int i = 0; i < OCCUPANCIES; i++) {
                long occupied = random.nextLong() & random.nextLong(); // about a quarter of the squares
                for (int sq = 0; sq < 64; sq++) {
                    String where = "player " + player + " square " + sq + " occupied " + Long.toHexString(occupied);
                    assertEquals(Attacks.KING[sq], king.captureTargets(sq, occupied, false), where);
                    assertEquals(Attacks.KNIGHT[sq], knight.captureTargets(sq, occupied, false), where);
                    assertEquals(Attacks.rook(sq, occupied), rook.captureTargets(sq, occupied, false), where);
                    assertEquals(Attacks.bishop(sq, occupied), bishop.captureTargets(sq, occupied, false), where);
                    assertEquals(Attacks.rook(sq, occupied) | Attacks.bishop(sq, occupied),
                            queen.captureTargets(sq, occupied, false), where);
                    assertEquals(Attacks.PAWN[color(player)][sq], pawn.captureTargets(sq, occupied, false), where);
                    assertEquals(Attacks.PAWN[color(player)][sq], pawn.captureTargets(sq, occupied, true), where);
                }
            }
        }
    }

    @Test
    @DisplayName("Pieces that move as they capture go to every empty attacked square")
    void quietMovesAreEmptyAttackedSquares() {
        Random random = new Random(63);
        for (PieceType type : StandardPieces.ALL) {
            if (type == StandardPieces.PAWN) {
                continue;
            }
            for (int player = 0; player < 2; player++) {
                CompiledPiece piece = compile(type, player);
                for (int i = 0; i < OCCUPANCIES; i++) {
                    long occupied = random.nextLong() & random.nextLong();
                    for (int sq = 0; sq < 64; sq++) {
                        assertEquals(piece.captureTargets(sq, occupied, false) & ~occupied,
                                piece.quietTargets(sq, occupied, false), type.name() + " " + sq);
                    }
                }
            }
        }
    }

    @Test
    @DisplayName("Pawns step one square forward, two on their first move, never through a piece")
    void pawnPushes() {
        Random random = new Random(64);
        for (int player = 0; player < 2; player++) {
            CompiledPiece pawn = compile(StandardPieces.PAWN, player);
            int step = player == 0 ? -8 : 8;
            for (int i = 0; i < OCCUPANCIES; i++) {
                long occupied = random.nextLong() & random.nextLong();
                for (int sq = 8; sq < 56; sq++) {
                    long one = 1L << (sq + step);
                    long expected = (occupied & one) == 0 ? one : 0;
                    assertEquals(expected, pawn.quietTargets(sq, occupied, false), "square " + sq);
                    int twoSq = sq + 2 * step;
                    if (expected != 0 && twoSq >= 0 && twoSq < 64 && (occupied & 1L << twoSq) == 0) {
                        expected |= 1L << twoSq;
                    }
                    assertEquals(expected, pawn.quietTargets(sq, occupied, true), "first move, square " + sq);
                }
            }
        }
    }
}
