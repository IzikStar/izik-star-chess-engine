package lab;

import ai.variant.TestVariants;
import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import rules.Game;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    @Test
    @DisplayName("The details add up: results, endings, lengths, choices, material, pieces, checks and sample games")
    void details() {
        VariantHealth.Settings settings = new VariantHealth.Settings(8, 1, 2, 4, 100, 11, 2, 30,
                VariantHealth.Engine.BUILT_IN, 20_000);
        VariantHealth.Report report = VariantHealth.run(Variants.CHESS, settings, n -> {}, () -> false);
        VariantHealth.Details d = report.details();
        assertEquals(report, VariantHealth.run(Variants.CHESS, settings, n -> {}, () -> false));

        VariantHealth.Results results = d.results();
        assertEquals(report.whiteWins() / 8.0, results.whiteWins().value(), 1e-9);
        assertEquals(report.whiteScore(), results.whiteScore().value(), 1e-9);
        for (VariantHealth.Interval i : List.of(results.whiteWins(), results.blackWins(), results.draws(), results.whiteScore())) {
            assertTrue(i.low() <= i.value() && i.value() <= i.high() && i.low() >= 0 && i.high() <= 1, i.toString());
        }
        assertEquals("white", results.firstMover());
        assertEquals(report.whiteWins(), results.firstMoverWins());

        assertEquals(8, d.endings().stream().mapToInt(VariantHealth.Ending::count).sum());
        assertEquals(1, d.endings().stream().mapToDouble(VariantHealth.Ending::share).sum(), 1e-9);
        assertEquals(report.endings().size(), d.endings().size());
        for (VariantHealth.Ending e : d.endings()) {
            assertEquals(e.count(), e.whiteWins() + e.blackWins() + e.draws());
            assertEquals(report.endings().get(e.reason()), e.count());
        }

        VariantHealth.Lengths lengths = d.lengths();
        assertEquals(8, lengths.all().count());
        assertEquals(8, lengths.decisive().count() + lengths.drawn().count());
        assertEquals(report.shortest(), lengths.all().min());
        assertEquals(report.longest(), lengths.all().max());
        assertEquals(report.averagePlies(), lengths.all().mean(), 1e-9);
        assertTrue(lengths.all().p10() <= lengths.all().median() && lengths.all().median() <= lengths.all().p90());
        assertEquals(8, lengths.histogram().stream().mapToInt(VariantHealth.LengthBucket::all).sum());
        lengths.histogram().forEach(b -> assertEquals(b.all(), b.decisive() + b.drawn()));

        VariantHealth.Branching branching = d.branching();
        assertEquals(report.movesPerTurn(), branching.mean(), 1e-9);
        VariantHealth.PlyBucket first = branching.byPly().get(0);
        assertEquals(8, first.games());
        assertTrue(first.legalMoves() >= 20 && first.legalMoves() < 40, first.toString());
        assertTrue(branching.positions().min() >= 1 && branching.positions().max() >= 20, branching.positions().toString());

        VariantHealth.Material material = d.material();
        int army = 0; // White's start: eight pawns, two each of knights, bishops and rooks, a queen
        for (ai.piece.PieceType type : Variants.CHESS.pieces()) {
            int count = switch (type.letter()) {
                case 'P' -> 8;
                case 'Q' -> 1;
                default -> 2;
            };
            army += type.royal() ? 0 : type.value() * count;
        }
        assertEquals(army, material.whiteStart(), 1e-9);
        assertEquals(army, material.blackStart(), 1e-9);
        assertEquals(32, material.piecesStart(), 1e-9);
        assertTrue(material.piecesEnd() <= 32 && material.pieces() <= 32);

        VariantHealth.PieceStats pawn = d.pieces().stream().filter(p -> p.letter().equals("P")).findFirst().orElseThrow();
        VariantHealth.PieceStats king = d.pieces().stream().filter(p -> p.letter().equals("K")).findFirst().orElseThrow();
        assertEquals(16, pawn.start(), 1e-9);
        assertEquals(2, king.end(), 1e-9);
        assertEquals(1, king.survival(), 1e-9);
        assertEquals(0, king.captured(), 1e-9);
        assertEquals(1, d.pieces().stream().mapToDouble(VariantHealth.PieceStats::moveShare).sum(), 1e-9);
        double took = d.pieces().stream().mapToDouble(VariantHealth.PieceStats::captures).sum();
        assertEquals(took, d.pieces().stream().mapToDouble(VariantHealth.PieceStats::captured).sum(), 1e-9);
        assertEquals(d.captures().perGame().mean(), took, 1e-9);
        assertTrue(d.captures().gamesWithoutCapture() + d.captures().firstCapturePly().count() == 8);

        VariantHealth.Checks checks = d.checks();
        assertTrue(checks.whiteGames() <= 8 && checks.white() >= checks.whiteGames());
        assertEquals(checks.white() / 8.0, checks.whitePerGame(), 1e-9);

        assertTrue(!d.samples().isEmpty() && d.samples().size() <= 5);
        for (VariantHealth.SampleGame sample : d.samples()) {
            assertEquals(sample.plies(), sample.uci().size());
            assertEquals(sample.plies(), sample.san().size());
            Game replay = new Game(Variants.CHESS);
            sample.uci().forEach(replay::play);
            assertEquals(sample.ending(), replay.status().isGameOver() ? replay.status().name() : "PLY_CAP");
        }
    }

    @Test
    @DisplayName("Each side searches at its own depth; variety and the seed change the games")
    void settings() {
        VariantHealth.Settings base = new VariantHealth.Settings(4, 1, 1, 4, 60, 3, 1, 0, VariantHealth.Engine.BUILT_IN, 20_000);
        VariantHealth.Report a = VariantHealth.run(Variants.CHESS, base, n -> {}, () -> false);
        VariantHealth.Report b = VariantHealth.run(Variants.CHESS, new VariantHealth.Settings(4, 1, 1, 4, 60, 4, 1, 0,
                VariantHealth.Engine.BUILT_IN, 20_000), n -> {}, () -> false);
        assertNotEquals(a.details().samples(), b.details().samples());
        assertEquals(1, base.depth());
        assertEquals(1, base.threadCount());
        assertThrows(IllegalArgumentException.class, () -> new VariantHealth.Settings(1001, 1, 4, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new VariantHealth.Settings(10, 6, 4, 100, 1));
        assertThrows(IllegalArgumentException.class, () -> new VariantHealth.Settings(10, 1, 6, 4, 100, 1, 0, 20,
                VariantHealth.Engine.BUILT_IN, 20_000));
        assertThrows(IllegalArgumentException.class, () -> new VariantHealth.Settings(10, 1, 1, 4, 100, 1, 0, -1,
                VariantHealth.Engine.BUILT_IN, 20_000));
        if (!arena.FairyStockfish.installed()) {
            assertThrows(IllegalArgumentException.class, () -> VariantHealth.check(Variants.CHESS, new VariantHealth.Settings(
                    1, 1, 1, 4, 100, 1, 0, 0, VariantHealth.Engine.FAIRY_STOCKFISH, 20_000)));
        }
    }

    @Test
    @DisplayName("Wilson intervals and nearest-rank percentiles")
    void statistics() {
        VariantHealth.Interval half = VariantHealth.wilson(5, 10);
        assertEquals(0.5, half.value(), 1e-9);
        assertEquals(0.2366, half.low(), 1e-4);
        assertEquals(0.7634, half.high(), 1e-4);
        assertEquals(0, VariantHealth.wilson(0, 10).low(), 1e-9);
        assertEquals(0.2775, VariantHealth.wilson(0, 10).high(), 1e-4);
        assertEquals(new VariantHealth.Spread(10, 5.5, 1, 1, 5, 9, 10), VariantHealth.spread(new int[]{10, 9, 8, 7, 6, 5, 4, 3, 2, 1}));
        assertEquals(new VariantHealth.Spread(0, 0, 0, 0, 0, 0, 0), VariantHealth.spread(new int[0]));
        assertEquals(20, VariantHealth.width(300, 16));
        assertEquals(1, VariantHealth.width(10, 16));
    }
}
