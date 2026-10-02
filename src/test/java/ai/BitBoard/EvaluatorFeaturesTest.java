package ai.BitBoard;

import ai.eval.ParamSpec;
import ai.eval.ParamVector;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Game;
import rules.Rules;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Phase 5 step 1b: the new features measure what they say, and the score is features × weights. */
class EvaluatorFeaturesTest {

    private static final BitBoardEvaluate EVAL = BitBoardEvaluate.DEFAULT;

    private static int feature(String fen, String name) {
        int i = BitBoardEvaluate.SCHEMA.indexOf(name + ".mg") / 2; // named features come first, as mg/eg pairs
        return EVAL.features(BitBoardRules.fromFen(fen)).values()[i];
    }

    /** Positions from seeded random games (not finished ones). */
    static List<String> randomPositions(long seed, int count) {
        Random random = new Random(seed);
        List<String> out = new ArrayList<>();
        Game game = new Game();
        while (out.size() < count) {
            List<ChessMove> legal = Rules.legalMoves(game.fen());
            if (legal.isEmpty() || game.status().isGameOver() || game.history().size() > 160) {
                game = new Game();
                continue;
            }
            game.play(legal.get(random.nextInt(legal.size())).toUci());
            if (!game.status().isGameOver()) {
                out.add(game.fen());
            }
        }
        return out;
    }

    /** The same position with the colours swapped and the board turned round. */
    static String mirror(String fen) {
        String[] parts = fen.split(" ");
        String[] ranks = parts[0].split("/");
        StringBuilder board = new StringBuilder();
        for (int i = ranks.length - 1; i >= 0; i--) {
            board.append(swapCase(ranks[i]));
            if (i > 0) board.append('/');
        }
        String side = parts[1].equals("w") ? "b" : "w";
        String castling = "-";
        if (!parts[2].equals("-")) {
            String swapped = swapCase(parts[2]);
            StringBuilder c = new StringBuilder();
            for (char k : "KQkq".toCharArray()) {
                if (swapped.indexOf(k) >= 0) c.append(k);
            }
            castling = c.toString();
        }
        String ep = parts[3].equals("-") ? "-" : "" + parts[3].charAt(0) + (9 - (parts[3].charAt(1) - '0'));
        return board + " " + side + " " + castling + " " + ep + " " + parts[4] + " " + parts[5];
    }

    private static String swapCase(String s) {
        StringBuilder out = new StringBuilder();
        for (char c : s.toCharArray()) {
            out.append(Character.isUpperCase(c) ? Character.toLowerCase(c) : Character.toUpperCase(c));
        }
        return out.toString();
    }

    @Test
    @DisplayName("Pawn structure: doubled, isolated, defended, passed and blocked pawns")
    void pawnStructure() {
        // White: a2, c2, c3, e5 (all isolated, c doubled, all passed, c2 blocked by c3); Black: h7 (isolated, passed)
        String fen = "4k3/7p/8/4P3/8/2P5/P1P5/4K3 w - - 0 1";
        assertEquals(-1, feature(fen, "pawns.doubled"));
        assertEquals(1 - 4, feature(fen, "pawns.isolated"));
        assertEquals(0, feature(fen, "pawns.defended"));
        assertEquals(1 - 2, feature(fen, "pawns.passed.rank2"));
        assertEquals(-1, feature(fen, "pawns.passed.rank3"));
        assertEquals(-1, feature(fen, "pawns.passed.rank5"));
        assertEquals(-1, feature(fen, "pawns.passed.blocked"));

        // White c3, d4, e3: d4 is defended (by both), none isolated; Black's lone c5 is isolated
        String chain = "4k3/8/8/2p5/3P4/2P1P3/8/4K3 w - - 0 1";
        assertEquals(-1, feature(chain, "pawns.defended"));
        assertEquals(1, feature(chain, "pawns.isolated"));
    }

    @Test
    @DisplayName("Pieces: bishop pair, rooks on open, half-open and seventh-rank squares, knight outposts")
    void pieces() {
        // White: Ra1 (open a-file), Bc1 and Bf1 (the pair), Ne5 defended by d4 with no Black pawn on d/f to chase it
        String fen = "4k3/7p/8/4N3/3P4/8/8/R1B1KB2 w - - 0 1";
        assertEquals(-1, feature(fen, "pieces.bishopPair"));
        assertEquals(-1, feature(fen, "pieces.rookOpenFile"));
        assertEquals(-1, feature(fen, "pieces.knightOutpost"));
        assertEquals(-1, feature(fen, "pieces.tempo"));
        // with a Black pawn on d7 the knight can be chased (d7-d6), so it is no outpost
        assertEquals(0, feature("4k3/3p4/8/4N3/3P4/8/8/R1B1KB2 w - - 0 1", "pieces.knightOutpost"));

        String seventh = "4k3/3R4/8/8/8/8/8/4K3 b - - 0 1";
        assertEquals(-1, feature(seventh, "pieces.rookOnSeventh"));
        assertEquals(-1, feature(seventh, "pieces.rookOpenFile"));
        assertEquals(1, feature(seventh, "pieces.tempo"));
    }

    @Test
    @DisplayName("King safety: pawn shield and attackers near the king")
    void kingSafety() {
        // White king g1 with f2, g2, h3 (two near, one far); Black queen h4 attacks around g1? h4-g3 is next to... h2 is in the zone via the h-file
        String fen = "6k1/8/8/8/7q/7P/5PP1/6K1 w - - 0 1";
        assertEquals(-2, feature(fen, "kingSafety.shieldNear"));
        assertEquals(-1, feature(fen, "kingSafety.shieldFar"));
        assertEquals(1, feature(fen, "kingSafety.queenAttacker")); // Black's queen sees g3/h2... near White's king
    }

    @Test
    @DisplayName("Every feature changes sign when the colours are swapped (no side is favoured by a sign slip)")
    void mirrorSymmetry() {
        for (String fen : randomPositions(11, 400)) {
            int[] a = EVAL.features(BitBoardRules.fromFen(fen)).values();
            int[] b = EVAL.features(BitBoardRules.fromFen(mirror(fen))).values();
            for (int i = 0; i < a.length; i++) {
                assertEquals(-a[i], b[i], fen + " feature " + i);
            }
        }
    }

    @Test
    @DisplayName("The score is the tapered sum of features × weights, for random weights")
    void scoreIsFeaturesTimesWeights() {
        Random random = new Random(3);
        int named = BitBoardEvaluate.NAMED;
        int pst = BitBoardEvaluate.PST_SIZE;
        int[] values = BitBoardEvaluate.SCHEMA.defaults().toArray();
        for (int i = 0; i < values.length; i++) {
            ParamSpec spec = BitBoardEvaluate.SCHEMA.spec(i);
            boolean gate = i >= named * 2 && i < named * 2 + 3;
            if (!gate) {
                values[i] = spec.min() + random.nextInt(spec.max() - spec.min() + 1);
            }
        }
        ParamVector params = new ParamVector(BitBoardEvaluate.SCHEMA, values);
        BitBoardEvaluate eval = new BitBoardEvaluate(params);
        for (String fen : randomPositions(12, 300)) {
            BitBoard board = BitBoardRules.fromFen(fen);
            BitBoardEvaluate.Features f = eval.features(board);
            long mg = 0;
            long eg = 0;
            for (int i = 0; i < named; i++) {
                mg += (long) f.values()[i] * params.get(2 * i);
                eg += (long) f.values()[i] * params.get(2 * i + 1);
            }
            int base = named * 2 + 3;
            for (int j = 0; j < pst; j++) {
                mg += (long) f.values()[named + j] * params.get(base + j);
                eg += (long) f.values()[named + j] * params.get(base + pst + j);
            }
            long expected = (mg * f.phase() + eg * (BitBoardEvaluate.MAX_PHASE - f.phase())) / BitBoardEvaluate.MAX_PHASE;
            assertEquals(expected, eval.evaluate(board, true), fen);
        }
    }
}
