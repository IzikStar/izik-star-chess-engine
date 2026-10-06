package ai.variant;

import ai.board.Board;
import ai.board.Boards;
import ai.board.Outcome;
import ai.piece.Betza;
import ai.piece.Grid;
import ai.piece.PieceType;
import ai.piece.StandardPieces;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.Rules;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** The rule building blocks (docs/variant-rules.md): goals as a list, royal mode, endings, castling. */
class VariantRulesTest {

    /** Chess's pieces and board with other rules. */
    static Variant chessWith(String start, List<WinCondition> goals, Variant.RoyalMode royal, Variant.Stalemate stalemate,
                             boolean repetition, int moveLimit, CastlingRule castling) {
        return new Variant("test", "Test", StandardPieces.ALL, Grid.CHESS, start, goals, royal, stalemate, repetition,
                moveLimit, false, castling);
    }

    static Variant goals(WinCondition... goals) {
        return chessWith(Variants.CHESS.startFen(), List.of(goals), Variant.RoyalMode.ALL_SAFE, Variant.Stalemate.DRAW,
                true, 50, CastlingRule.CHESS);
    }

    static List<String> moves(Variant v, String fen) {
        return Rules.legalMoves(v, fen).stream().map(ChessMove::toUci).toList();
    }

    static String play(Variant v, String fen, String... uci) {
        for (String m : uci) {
            fen = Rules.applyMove(v, fen, ChessMove.fromUci(m));
        }
        return fen;
    }

    // ---- the old files ------------------------------------------------------------------------

    /** A variant written as the first files were: one "goal" and "checksToWin", none of the newer settings. */
    static String oldJson(Variant v, Variant.Goal goal, int checks) {
        JsonObject o = JsonParser.parseString(VariantJson.write(v)).getAsJsonObject();
        for (String key : List.of("goals", "royalMode", "stalemate", "repetition", "moveLimit", "castlingRule")) {
            o.remove(key);
        }
        o.addProperty("goal", goal.name());
        o.addProperty("checksToWin", checks);
        return o.toString();
    }

    @Test
    @DisplayName("An old file's single goal reads as the preset it stood for: each built-in comes back equal")
    void oldFilesReadAsPresets() {
        assertEquals(Variants.CHESS, VariantJson.read(oldJson(Variants.CHESS, Variant.Goal.CHECKMATE, 0)));
        assertEquals(Variants.ANTICHESS, VariantJson.read(oldJson(Variants.ANTICHESS, Variant.Goal.LOSE_EVERYTHING, 0)));
        assertEquals(Variants.KING_OF_THE_HILL, VariantJson.read(oldJson(Variants.KING_OF_THE_HILL, Variant.Goal.KING_OF_THE_HILL, 0)));
        assertEquals(Variants.THREE_CHECK, VariantJson.read(oldJson(Variants.THREE_CHECK, Variant.Goal.CHECKS, 3)));
        // and the made test files, all in the old form
        Variant amazon = TestVariants.made("amazon-chess");
        assertEquals(List.of(WinCondition.checkmate()), amazon.goals());
        assertEquals(CastlingRule.CHESS, amazon.castling());
        assertEquals(List.of(WinCondition.loseEverything()), TestVariants.made("amazon-antichess").goals());
        assertEquals(Variant.Stalemate.WIN, TestVariants.made("amazon-antichess").stalemate());
        for (Variant v : Variants.ALL) {
            assertEquals(v.id(), v.legacyGoal().map(g -> v.id()).orElse("none"), "a built-in is its own preset");
        }
    }

    @Test
    @DisplayName("Every setting survives the JSON round trip")
    void roundTrip() {
        Variant v = new Variant("mix", "Mix", StandardPieces.ALL, Grid.CHESS, Variants.CHESS.startFen(),
                List.of(WinCondition.reach(List.of("a8", "h8"), "N"), WinCondition.captureAllOf("Q"), WinCondition.bareRoyal(),
                        WinCondition.checks(5), WinCondition.checkmate()),
                Variant.RoyalMode.LAST_STANDING, Variant.Stalemate.LOSS, false, 0, true,
                new CastlingRule(true, 1, CastlingRule.Partner.OUTSIDE, CastlingRule.Sides.QUEEN_SIDE, false));
        assertEquals(v, VariantJson.read(VariantJson.write(v)));
        assertTrue(v.legacyGoal().isEmpty());
    }

    @Test
    @DisplayName("Goals that cannot work are refused: none at all, a goal twice, a royal goal without royal pieces")
    void validation() {
        assertThrows(IllegalArgumentException.class, () -> goals());
        assertThrows(IllegalArgumentException.class, () -> goals(WinCondition.checkmate(), WinCondition.checkmate()));
        assertThrows(IllegalArgumentException.class, () -> WinCondition.checks(0));
        assertThrows(IllegalArgumentException.class, () -> WinCondition.reach(List.of(), ""));
        assertThrows(IllegalArgumentException.class, () -> goals(WinCondition.captureAllOf("Z")));
        assertThrows(IllegalArgumentException.class, () -> new Variant("x", "X", List.of(StandardPieces.QUEEN), Grid.CHESS,
                "q7/8/8/8/8/8/8/Q7 w - - 0 1", List.of(WinCondition.bareRoyal()), Variant.RoyalMode.ALL_SAFE,
                Variant.Stalemate.DRAW, true, 50, false, CastlingRule.NONE));
        // two squares goals are fine
        goals(WinCondition.reach(List.of("a8"), ""), WinCondition.reach(List.of("h8"), "N"));
    }

    // ---- R1: royal mode ----------------------------------------------------------------------

    static final String TWO_KINGS = "4k3/8/8/8/8/8/r7/K3K3 w - - 0 1";

    @Test
    @DisplayName("All safe: a check on either of two kings must be answered")
    void allSafeTwoKings() {
        Variant v = chessWith(TWO_KINGS, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE,
                Variant.Stalemate.DRAW, true, 50, CastlingRule.NONE);
        assertEquals(GameStatus.CHECK, Rules.status(v, TWO_KINGS));
        assertFalse(moves(v, TWO_KINGS).contains("e1e2"));
        assertEquals(List.of("a1a2", "a1b1"), moves(v, TWO_KINGS).stream().filter(m -> m.startsWith("a1")).sorted().toList());
    }

    @Test
    @DisplayName("Last standing: with two kings neither is in check and either may be captured; the last one is held to check")
    void lastStanding() {
        Variant v = chessWith(TWO_KINGS, List.of(WinCondition.checkmate()), Variant.RoyalMode.LAST_STANDING,
                Variant.Stalemate.DRAW, true, 50, CastlingRule.NONE);
        assertEquals(GameStatus.IN_PROGRESS, Rules.status(v, TWO_KINGS));
        assertTrue(Rules.checkedSquares(v, TWO_KINGS).isEmpty());
        assertTrue(moves(v, TWO_KINGS).contains("e1e2"), "the king on a1 may be left attacked");
        String taken = play(v, TWO_KINGS, "e1e2", "a2a1");
        assertEquals("4k3/8/8/8/8/8/4K3/r7 w - - 0 2", taken);
        assertEquals(GameStatus.IN_PROGRESS, Rules.status(v, taken));
        // now one king is left: check applies to it
        String check = "4k3/8/8/8/8/8/r7/K7 w - - 0 1";
        assertEquals(GameStatus.CHECK, Rules.status(v, check));
        assertEquals(GameStatus.CHECKMATE, Rules.status(v, "k7/8/8/8/8/8/5PPP/3r2K1 w - - 0 1"));
        // a side with none left has lost
        Variant none = chessWith("4k3/8/8/8/8/8/8/Q7 w - - 0 1", List.of(WinCondition.checkmate()),
                Variant.RoyalMode.LAST_STANDING, Variant.Stalemate.DRAW, true, 50, CastlingRule.NONE);
        assertEquals(GameStatus.ROYALS_LOST, Rules.status(none, none.startFen()));
        assertEquals(Outcome.LOSS, Boards.fromFen(none, none.startFen()).outcome());
    }

    @Test
    @DisplayName("Last standing: the engine captures a king it can take, and plays on")
    void lastStandingSearch() {
        Variant v = chessWith("4k3/8/8/8/8/8/8/K3K2r b - - 0 1", List.of(WinCondition.checkmate()),
                Variant.RoyalMode.LAST_STANDING, Variant.Stalemate.DRAW, true, 50, CastlingRule.NONE);
        String fen = v.startFen();
        assertTrue(moves(v, fen).contains("h1e1"));
        ChessMove best = engine.MinimaxEngine.searchAtDepth(v, List.of(fen), 2, ai.Minimax.Options.DEFAULT, () -> false);
        assertEquals("h1e1", best.toUci());
    }

    // ---- R2: goals -----------------------------------------------------------------------------

    @Test
    @DisplayName("Reach squares: a knight on a chosen square wins; the king elsewhere does not")
    void reachSquares() {
        Variant v = goals(WinCondition.checkmate(), WinCondition.reach(List.of("a8", "h8"), "N"));
        String fen = "4k3/8/1N6/8/8/8/8/4K3 w - - 0 1";
        String after = play(v, fen, "b6a8");
        assertEquals(GameStatus.HILL_REACHED, Rules.status(v, after));
        assertEquals("1-0", GameStatus.HILL_REACHED.result(false));
        assertEquals(Rules.goalMet(v, after).orElseThrow(), v.goals().get(1));
        assertEquals(GameStatus.IN_PROGRESS, Rules.status(v, play(v, fen, "b6c8")));
        // the default pieces are the royal ones
        Variant kings = goals(WinCondition.checkmate(), WinCondition.reach(List.of("e2"), ""));
        assertEquals(GameStatus.HILL_REACHED, Rules.status(kings, play(kings, fen, "e1e2")));
        assertEquals(GameStatus.IN_PROGRESS, Rules.status(kings, play(kings, fen, "e1d2")));
    }

    @Test
    @DisplayName("Capture all of a type, and bare royal: whichever is listed first names the ending when both are met")
    void captureAllAndBareRoyal() {
        String fen = "3qk3/8/8/8/8/8/8/3QK3 w - - 0 1";
        Variant capture = goals(WinCondition.checkmate(), WinCondition.captureAllOf("Q"), WinCondition.bareRoyal());
        String after = play(capture, fen, "d1d8");
        assertEquals(GameStatus.ALL_CAPTURED, Rules.status(capture, after));
        Variant bare = goals(WinCondition.checkmate(), WinCondition.bareRoyal(), WinCondition.captureAllOf("Q"));
        assertEquals(GameStatus.BARE_ROYAL, Rules.status(bare, play(bare, fen, "d1d8")));
        assertTrue(Rules.legalMoves(bare, after).isEmpty(), "the game is over: no moves");
        Variant chess = goals(WinCondition.checkmate());
        assertEquals(GameStatus.CHECK, Rules.status(chess, play(chess, fen, "d1d8")), "in chess the game goes on");
    }

    @Test
    @DisplayName("Checks: any count wins, and the FEN carries the checks still to give")
    void nChecks() {
        Variant v = goals(WinCondition.checkmate(), WinCondition.checks(2));
        String start = Boards.fromFen(v, v.startFen()).toFen();
        assertEquals("rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 2+2 0 1", start);
        String oneLeft = "4k3/8/8/8/8/8/3Q4/4K3 w - - 1+2 0 1";
        String after = play(v, oneLeft, "d2e2");
        assertEquals("4k3/8/8/8/8/8/4Q3/4K3 b - - 0+2 1 1", after);
        assertEquals(GameStatus.CHECKS_GIVEN, Rules.status(v, after));
        assertEquals(GameStatus.CHECK, Rules.status(v, play(v, oneLeft.replace("1+2", "2+2"), "d2e2")));
    }

    @Test
    @DisplayName("Checkmate off: a king with no way out of check is a stalemate, judged by the stalemate rule")
    void checkmateOff() {
        String mated = "R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1";
        Variant on = goals(WinCondition.checkmate());
        assertEquals(GameStatus.CHECKMATE, Rules.status(on, mated));
        Variant off = goals(WinCondition.reach(List.of("e4"), ""));
        assertEquals(GameStatus.STALEMATE, Rules.status(off, mated));
        assertEquals(Outcome.DRAW, Boards.fromFen(off, mated).outcome());
    }

    @Test
    @DisplayName("Stalemate is a draw, a win or a loss for the side with no move, as the variant says")
    void stalemate() {
        String fen = "7k/5Q2/6K1/8/8/8/8/8 b - - 0 1";
        for (Variant.Stalemate s : Variant.Stalemate.values()) {
            Variant v = chessWith(fen, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE, s, true, 50, CastlingRule.NONE);
            GameStatus status = Rules.status(v, fen);
            switch (s) {
                case DRAW -> assertEquals(GameStatus.STALEMATE, status);
                case WIN -> assertEquals(GameStatus.NO_MOVES_LEFT, status);
                case LOSS -> assertEquals(GameStatus.STALEMATE_LOSS, status);
            }
            assertEquals(s == Variant.Stalemate.DRAW ? "1/2-1/2" : s == Variant.Stalemate.WIN ? "0-1" : "1-0", status.result(false));
        }
    }

    @Test
    @DisplayName("Repetition can be turned off, and the move limit set or turned off")
    void drawRules() {
        String start = Variants.CHESS.startFen();
        Variant off = chessWith(start, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE, Variant.Stalemate.DRAW,
                false, 50, CastlingRule.CHESS);
        Game repeated = new Game(off);
        Game chess = new Game(Variants.CHESS);
        for (int i = 0; i < 2; i++) {
            for (String m : List.of("g1f3", "g8f6", "f3g1", "f6g8")) {
                repeated.play(m);
                chess.play(m);
            }
        }
        assertEquals(GameStatus.DRAW_THREEFOLD, chess.status());
        assertEquals(GameStatus.IN_PROGRESS, repeated.status());

        String quiet = "4k3/8/8/8/8/8/8/R3K3 w - - 20 40";
        Variant ten = chessWith(quiet, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE, Variant.Stalemate.DRAW,
                true, 10, CastlingRule.NONE);
        assertEquals(GameStatus.DRAW_FIFTY_MOVE, Rules.status(ten, quiet));
        Variant none = chessWith(quiet, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE, Variant.Stalemate.DRAW,
                true, 0, CastlingRule.NONE);
        assertEquals(GameStatus.IN_PROGRESS, Rules.status(none, quiet.replace(" 20 ", " 300 ")));
        assertEquals(GameStatus.DRAW_FIFTY_MOVE, Rules.status(Variants.CHESS, quiet.replace(" 20 ", " 100 ")));
    }

    // ---- R3: castling ----------------------------------------------------------------------------

    /** A chancellor (rook + knight) that castles like a rook. */
    static final PieceType CHANCELLOR = new PieceType("Chancellor", 'C', Betza.parse("RN"), false, List.of(), false,
            PieceType.Castling.ROOK, 900);

    @Test
    @DisplayName("With two partner types each castles with its own: the chancellor on a1 lands on d1, the rook on h1 on f1")
    void twoPartnerTypes() {
        List<PieceType> pieces = new ArrayList<>(StandardPieces.ALL);
        pieces.add(CHANCELLOR);
        Variant v = new Variant("partners", "Partners", pieces, Grid.CHESS, "c3k2r/8/8/8/8/8/8/C3K2R w KQkq - 0 1",
                List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE, Variant.Stalemate.DRAW, true, 50, false,
                CastlingRule.CHESS);
        String fen = v.startFen();
        assertTrue(moves(v, fen).containsAll(List.of("e1g1", "e1c1")));
        assertEquals("c3k2r/8/8/8/8/8/8/2KC3R b kq - 1 1", play(v, fen, "e1c1"));
        assertEquals("c3k2r/8/8/8/8/8/8/C4RK1 b kq - 1 1", play(v, fen, "e1g1"));
        assertEquals("2kc3r/8/8/8/8/8/8/C4RK1 w - - 2 2", play(v, fen, "e1g1", "e8c8"));
        Game game = new Game(v);
        assertEquals("O-O-O", game.play("e1c1").san());
        assertTrue(game.moves().get(0).castling());
    }

    @Test
    @DisplayName("Castling settings: how far the king goes, where the partner lands, which sides, through check or not")
    void castlingSettings() {
        String fen = "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";
        Variant threeSteps = chessWith(fen, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE, Variant.Stalemate.DRAW,
                true, 50, new CastlingRule(true, 3, CastlingRule.Partner.INSIDE, CastlingRule.Sides.KING_SIDE, true));
        assertEquals(1, ai.board.BoardRules.of(threeSteps).castlings().stream().filter(c -> c.player() == 0).count());
        // e1 -> h1, where the rook stood; the rook to the square inside, g1
        assertFalse(moves(threeSteps, fen).contains("e1h1"), "h1 is attacked by the rook on h8");
        String open = "r3k3/8/8/8/8/8/8/R3K2R w KQq - 0 1";
        assertEquals("r3k3/8/8/8/8/8/8/R5RK b - - 1 1", play(threeSteps, open, "e1h1"));
        assertFalse(moves(threeSteps, open).contains("e1c1"));
        assertTrue(new Game(threeSteps, open).play("e1h1").castling());
        // one step lands where the king steps anyway: the ordinary move is the one played, no castling
        Variant oneStep = chessWith(fen, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE, Variant.Stalemate.DRAW,
                true, 50, new CastlingRule(true, 1, CastlingRule.Partner.INSIDE, CastlingRule.Sides.BOTH, true));
        assertEquals(1, moves(oneStep, fen).stream().filter("e1f1"::equals).count());
        assertEquals("r3k2r/8/8/8/8/8/8/R4K1R b kq - 1 1", play(oneStep, fen, "e1f1"));

        Variant outside = chessWith(fen, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE, Variant.Stalemate.DRAW,
                true, 50, new CastlingRule(true, 0, CastlingRule.Partner.OUTSIDE, CastlingRule.Sides.QUEEN_SIDE, true));
        // e1 -> c1, the rook from a1 to b1, beyond the king
        assertEquals("r3k2r/8/8/8/8/8/8/1RK4R b q - 1 1", play(outside, fen, "e1c1"));
        assertFalse(moves(outside, fen).contains("e1g1"));

        // a rook on f8 attacks f1, which the king crosses on the way to g1
        String attacked = "4kr2/8/8/8/8/8/8/4K2R w K - 0 1";
        Variant safe = chessWith(attacked, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE,
                Variant.Stalemate.DRAW, true, 50, CastlingRule.CHESS);
        assertFalse(moves(safe, attacked).contains("e1g1"));
        Variant through = chessWith(attacked, List.of(WinCondition.checkmate()), Variant.RoyalMode.ALL_SAFE,
                Variant.Stalemate.DRAW, true, 50, new CastlingRule(true, 0, CastlingRule.Partner.INSIDE, CastlingRule.Sides.BOTH, false));
        assertTrue(moves(through, attacked).contains("e1g1"));
        // the landing square still may not be attacked
        String landing = "4k1r1/8/8/8/8/8/8/4K2R w K - 0 1";
        assertFalse(moves(through, landing).contains("e1g1"));
        assertNotEquals(ai.board.BoardRules.of(safe).castlings(), ai.board.BoardRules.of(through).castlings());
    }
}
