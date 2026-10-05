package lab;

import ai.variant.TestVariants;
import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VariantHealthTest {

    @Test
    @DisplayName("Self-play counts every game once, reports progress, and the same settings give the same report")
    void selfPlay() {
        VariantHealth.Settings settings = new VariantHealth.Settings(6, 1, 4, 120, 7);
        AtomicInteger last = new AtomicInteger();
        VariantHealth.Report report = VariantHealth.run(Variants.CHESS, settings, n -> last.accumulateAndGet(n, Math::max), () -> false);
        assertEquals(6, report.games());
        assertEquals(6, report.whiteWins() + report.blackWins() + report.draws());
        assertEquals(6, last.get());
        assertEquals(6, report.endings().values().stream().mapToInt(Integer::intValue).sum());
        assertTrue(report.averagePlies() >= 4 && report.longest() <= 120, report.toString());
        assertTrue(report.movesPerTurn() > 15 && report.movesPerTurn() < 60, report.toString());
        assertEquals(report, VariantHealth.run(Variants.CHESS, settings, n -> {}, () -> false));
    }

    @Test
    @DisplayName("Made variants and antichess play to an end the rules know")
    void otherVariants() {
        VariantHealth.Settings settings = new VariantHealth.Settings(4, 1, 2, 200, 3);
        VariantHealth.Report anti = VariantHealth.run(Variants.ANTICHESS, settings, n -> {}, () -> false);
        assertEquals(4, anti.games());
        assertTrue(anti.endings().keySet().stream().allMatch(e -> List.of("NO_PIECES_LEFT", "NO_MOVES_LEFT", "STALEMATE",
                "DRAW_FIFTY_MOVE", "DRAW_THREEFOLD", "DRAW_INSUFFICIENT_MATERIAL", "PLY_CAP").contains(e)), anti.toString());
        VariantHealth.Report amazon = VariantHealth.run(TestVariants.made("amazon-chess"), settings, n -> {}, () -> false);
        assertEquals(4, amazon.games());
    }

    @Test
    @DisplayName("A cancelled game is left out")
    void cancel() {
        assertNull(VariantHealth.play(Variants.CHESS, VariantHealth.Settings.of(1, 2), 1, () -> true));
    }

    @Test
    @DisplayName("The notes name what a player would notice")
    void notes() {
        assertEquals(List.of(), VariantHealth.notes(20, 8, 7, 5, 80, 30, 0));
        assertTrue(VariantHealth.notes(20, 15, 3, 2, 80, 30, 0).get(0).startsWith("White scores 80%"));
        assertTrue(VariantHealth.notes(20, 2, 14, 4, 80, 30, 0).get(0).startsWith("Black scores 80%"));
        List<String> dull = VariantHealth.notes(20, 1, 1, 18, 400, 5, 10);
        assertEquals(List.of("90% draws: games rarely get decided.", "50% of the games were still going after the move limit.",
                "Only 5.0 moves to choose from per turn on average."), dull);
        assertTrue(VariantHealth.notes(20, 10, 10, 0, 12, 30, 0).get(0).contains("end too soon"));
    }
}
