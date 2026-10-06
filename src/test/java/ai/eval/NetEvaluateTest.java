package ai.eval;

import ai.board.Boards;
import ai.board.PieceBoard;
import ai.variant.Variants;
import arena.GameRecord;
import arena.Match;
import arena.Opening;
import arena.Player;
import arena.Players;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Phase 7: the network as an evaluation, and its file. */
class NetEvaluateTest {

    static final int INPUTS = 768;
    /** Queen is type 1, d1 is square 59. */
    static final int OWN_QUEEN_D1 = NetFeatures.index(Variants.CHESS, 0, 1, 59);

    /**
     * Two hidden units: unit 0 fires on "my queen stands on d1" (weight 1, bias -0.5, worth 200);
     * unit 1 is always on (bias 1, worth 50); the output bias is 10.
     */
    static NetEvaluate tiny() {
        float[][] w1 = new float[2][INPUTS];
        w1[0][OWN_QUEEN_D1] = 1;
        return new NetEvaluate(Variants.CHESS, w1, new float[]{-0.5f, 1}, new float[]{200, 50}, 10);
    }

    static String tinyJson() {
        StringBuilder row0 = new StringBuilder();
        for (int i = 0; i < INPUTS; i++) {
            row0.append(i == OWN_QUEEN_D1 ? "1" : "0").append(i + 1 < INPUTS ? "," : "");
        }
        return "{\"format\":\"net-v1\",\"variant\":\"chess\",\"inputs\":768,\"hidden\":2,\"activation\":\"relu\","
                + "\"w1\":[[" + row0 + "],[" + "0,".repeat(INPUTS - 1) + "0]],"
                + "\"b1\":[-0.5,1],\"w2\":[200,50],\"b2\":10}";
    }

    static PieceBoard board(String fen) {
        return (PieceBoard) Boards.fromFen(Variants.CHESS, fen);
    }

    @Test
    @DisplayName("a unit adds its weights for the inputs that are on, is cut at 0, and the output sums the units")
    void forwardByHand() {
        NetEvaluate net = tiny();
        // start position, White to move: own queen on d1 -> unit 0 = relu(1 - 0.5) = 0.5 -> 100; unit 1 -> 50; bias 10
        assertEquals(160, net.forward(board(NetFeaturesTest.START)), 1e-4);
        assertEquals(160, net.evaluate(board(NetFeaturesTest.START), 0));
        assertEquals(-160, net.evaluate(board(NetFeaturesTest.START), 1), "the same position for Black's search");
        // Black to move at the start: Black's queen on d8 is "my queen on d1" from Black's view
        assertEquals(160, net.evaluate(board(NetFeaturesTest.START.replace(" w ", " b ")), 1));
        // the queen has left d1: unit 0 = relu(-0.5) = 0, only the always-on unit remains
        assertEquals(60, net.evaluate(board("rnbqkbnr/pppppppp/8/8/3P4/8/PPPQPPPP/RNB1KBNR w KQkq - 0 1"), 0));
    }

    @Test
    @DisplayName("a won or lost position scores MATE, whatever the network says")
    void mates() {
        NetEvaluate net = tiny();
        PieceBoard foolsMate = board("rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3");
        assertEquals(-Evaluator.MATE, net.evaluate(foolsMate, 0));
        assertEquals(Evaluator.MATE, net.evaluate(foolsMate, 1));
    }

    @Test
    @DisplayName("the JSON file gives the same scores as the weights it holds")
    void fromJson() {
        NetEvaluate fromFile = NetEvaluate.fromJson(Variants.CHESS, tinyJson());
        NetEvaluate built = tiny();
        assertEquals(2, fromFile.hidden());
        for (String fen : List.of(NetFeaturesTest.START, "r1bqkb1r/pppp1ppp/2n2n2/4p3/2B1P3/5N2/PPPP1PPP/RNBQK2R w KQkq - 4 4",
                "8/5pk1/6p1/3P4/2p5/2P3P1/5PK1/8 w - - 0 40")) {
            assertEquals(built.forward(board(fen)), fromFile.forward(board(fen)), 1e-5, fen);
        }
        assertEquals(0, fromFile.params().size(), "a network has no evolvable parameters");
    }

    @Test
    @DisplayName("a file of another format, variant or input count is refused")
    void refusesOtherFiles() {
        assertThrows(IllegalArgumentException.class,
                () -> NetEvaluate.fromJson(Variants.CHESS, tinyJson().replace("net-v1", "net-v2")));
        assertThrows(IllegalArgumentException.class,
                () -> NetEvaluate.fromJson(Variants.ANTICHESS, tinyJson()));
        assertThrows(IllegalArgumentException.class,
                () -> NetEvaluate.fromJson(Variants.CHESS, tinyJson().replace("\"inputs\":768", "\"inputs\":770")));
        assertThrows(IllegalArgumentException.class,
                () -> NetEvaluate.fromJson(Variants.CHESS, tinyJson().replace("relu", "sigmoid")));
    }

    @Test
    @DisplayName("net:FILE.json names a player that plays a whole game with the usual search")
    void playsAsAPlayer(@TempDir Path dir) throws IOException {
        Path file = dir.resolve("tiny.json");
        Files.writeString(file, tinyJson());
        String spec = "net:" + file;
        assertEquals("tiny", Players.label(spec));
        assertTrue(!Players.hasWeights(spec), "a network is not a parameter vector");
        Player net = Players.parse(spec, 2, 2);
        assertTrue(net.evaluator() instanceof NetEvaluate);
        Opening opening = Opening.suite().getFirst();
        GameRecord game = Match.play(net, Player.of("default", ChessEvaluate.DEFAULT, 2, 2), opening, 40, 1);
        assertTrue(game.plies() > opening.moves().size() && game.plies() <= 40, "plies: " + game.plies());
        assertEquals(opening.moves(), game.moves().subList(0, opening.moves().size()));
    }
}
