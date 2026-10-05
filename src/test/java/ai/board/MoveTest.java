package ai.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MoveTest {

    @Test
    @DisplayName("A move keeps its squares and promotion, and is never NONE")
    void roundTrip() {
        for (int from = 0; from < 256; from += 17) {
            for (int to = 0; to < 256; to += 13) {
                if (from == to) {
                    continue;
                }
                for (char promotion : new char[]{0, 'q', 'n', 'z', 'a'}) {
                    int move = Move.of(from, to, promotion);
                    assertEquals(from, Move.from(move));
                    assertEquals(to, Move.to(move));
                    assertEquals(promotion, Move.promotion(move));
                    assertNotEquals(Move.NONE, move);
                    assertTrue(move < 1 << 22, "fits the transposition table's 22 bits");
                }
            }
        }
    }

    @Test
    @DisplayName("Upper-case promotions read as lower case; anything but a letter is refused")
    void promotionLetters() {
        assertEquals('q', Move.promotion(Move.of(8, 0, 'Q')));
        assertThrows(IllegalArgumentException.class, () -> Move.of(8, 0, '1'));
    }
}
