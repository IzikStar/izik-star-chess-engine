package ai.eval;

import ai.Minimax;
import ai.board.Board;
import ai.board.Boards;
import ai.board.Move;
import ai.variant.Variant;
import ai.variant.Variants;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 6 R4c: an evaluation built from a variant's piece set, and which variant gets which evaluation. */
class PieceSetEvaluateTest {

    @Test
    @DisplayName("chess-piece variants keep the chess evaluation; antichess gets one from its pieces")
    void evaluatorPerVariant() {
        assertSame(ChessEvaluate.DEFAULT, Evaluators.forVariant(Variants.CHESS));
        assertSame(ChessEvaluate.DEFAULT, Evaluators.forVariant(Variants.KING_OF_THE_HILL));
        assertSame(ChessEvaluate.DEFAULT, Evaluators.forVariant(Variants.THREE_CHECK));
        assertTrue(Evaluators.forVariant(Variants.ANTICHESS) instanceof PieceSetEvaluate);
    }

    @Test
    @DisplayName("the schema has material, mobility and 64 squares per piece; antichess starts from zero")
    void schema() {
        ParamSchema schema = PieceSetEvaluate.schema(Variants.ANTICHESS);
        assertEquals(Variants.ANTICHESS.pieces().size() * (2 + 64), schema.size());
        assertTrue(java.util.Arrays.stream(schema.defaults().toArray()).allMatch(v -> v == 0),
                "every antichess weight starts at 0");
        ParamSchema chess = PieceSetEvaluate.schema(Variants.CHESS);
        assertEquals(900, chess.defaults().get("material.queen"));
        assertEquals(0, chess.defaults().get("material.king"));
        assertEquals(0, chess.defaults().get("square.knight.e4"));
    }

    @Test
    @DisplayName("the same position with the colours swapped scores the same for the side to move")
    void colourSymmetry() {
        for (Variant variant : List.of(Variants.ANTICHESS, Variants.CHESS)) {
            Evaluator eval = new PieceSetEvaluate(variant, randomParams(variant, new Random(7)));
            for (Board b : positions(variant, 11, 300)) {
                if (b.isOver()) {
                    continue;
                }
                Board flipped = Boards.fromFen(variant, flip(b.toFen()));
                int mover = b.sideToMove();
                assertEquals(eval.evaluate(b, mover), eval.evaluate(flipped, flipped.sideToMove()), b.toFen());
                assertEquals(-eval.evaluate(b, mover), eval.evaluate(b, 1 - mover), b.toFen());
            }
        }
    }

    @Test
    @DisplayName("with all weights at zero, an antichess search still finds a forced win")
    void antichessForcedWin() {
        Board board = Boards.fromFen(Variants.ANTICHESS, "1k6/8/8/8/8/8/8/R7 w - - 0 1");
        int best = Minimax.getBestMove(board, 2, Evaluators.forVariant(Variants.ANTICHESS), () -> false);
        assertEquals(56, Move.from(best)); // a1, a8 being 0
        assertTrue(Set.of(0, 8).contains(Move.to(best)), "Ra8 or Ra7, either is taken next move");
    }

    @Test
    @DisplayName("the chess evaluation scores a reached hill and a third check as mate")
    void chessEvaluationSeesVariantGoals() {
        Board hill = Boards.fromFen(Variants.KING_OF_THE_HILL, "4k3/8/8/8/4K3/8/8/8 b - - 0 1");
        assertEquals(-Evaluator.MATE, ChessEvaluate.DEFAULT.evaluate(hill, 1));
        assertEquals(Evaluator.MATE, ChessEvaluate.DEFAULT.evaluate(hill, 0));
        Board checked = Boards.fromFen(Variants.THREE_CHECK, "4k3/8/8/8/8/8/8/4K2R b K - 0+3 1 20");
        assertEquals(-Evaluator.MATE, ChessEvaluate.DEFAULT.evaluate(checked, 1));
    }

    private static ParamVector randomParams(Variant variant, Random random) {
        ParamSchema schema = PieceSetEvaluate.schema(variant);
        int[] values = new int[schema.size()];
        for (int i = 0; i < values.length; i++) {
            ParamSpec spec = schema.spec(i);
            values[i] = spec.min() + random.nextInt(spec.max() - spec.min() + 1);
        }
        return new ParamVector(schema, values);
    }

    private static List<Board> positions(Variant variant, long seed, int count) {
        Random random = new Random(seed);
        List<Board> out = new ArrayList<>();
        Board start = Boards.fromFen(variant, variant.startFen());
        Board b = start;
        while (out.size() < count) {
            List<? extends Board> next = b.children();
            if (next.isEmpty() || b.isOver()) {
                b = start;
                continue;
            }
            b = next.get(random.nextInt(next.size()));
            out.add(b);
        }
        return out;
    }

    /** The FEN with the board turned around and the colours swapped. */
    private static String flip(String fen) {
        String[] f = fen.split(" ");
        String[] ranks = f[0].split("/");
        StringBuilder board = new StringBuilder();
        for (int r = ranks.length - 1; r >= 0; r--) {
            board.append(swapCase(new StringBuilder(ranks[r]).reverse().toString()));
            if (r > 0) {
                board.append('/');
            }
        }
        f[0] = board.toString();
        f[1] = f[1].equals("w") ? "b" : "w";
        f[2] = "-"; // castling rights do not survive a mirror; the evaluation ignores them
        if (!f[3].equals("-")) {
            f[3] = "" + (char) ('h' - (f[3].charAt(0) - 'a')) + (char) ('9' - (f[3].charAt(1) - '0'));
        }
        return String.join(" ", f);
    }

    private static String swapCase(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            out.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return out.toString();
    }
}
