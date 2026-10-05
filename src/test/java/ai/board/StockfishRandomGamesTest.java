package ai.board;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import rules.ChessMove;
import rules.Position;
import rules.Rules;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Seeded random games checked against Stockfish at every ply (docs/phase-4b-research.md §2.1).
 * The search's moves, read off its own boards and never re-read from FEN, and the game's moves,
 * from the rules facade, must both be exactly Stockfish's legal moves. Runs under {@code -Pstress}
 * when Stockfish is installed ({@code -Dstockfish.path}, default {@code /usr/games/stockfish}).
 */
@Tag("stress")
class StockfishRandomGamesTest {

    private static final int GAMES = 300;
    private static final int MAX_PLIES = 300;

    @Test
    @DisplayName("In random games, the search and the game offer exactly Stockfish's legal moves")
    void legalMovesMatchStockfish() throws IOException {
        String path = System.getProperty("stockfish.path", "/usr/games/stockfish");
        assumeTrue(Files.isExecutable(Path.of(path)), "Stockfish not installed");
        Process stockfish = new ProcessBuilder(path).redirectErrorStream(true).start();
        try (BufferedWriter toStockfish = new BufferedWriter(
                     new OutputStreamWriter(stockfish.getOutputStream(), StandardCharsets.UTF_8));
             BufferedReader fromStockfish = new BufferedReader(
                     new InputStreamReader(stockfish.getInputStream(), StandardCharsets.UTF_8))) {
            Random random = new Random(2026);
            for (int game = 0; game < GAMES; game++) {
                String fen = Position.START_FEN;
                Board board = Boards.fromFen(fen);
                List<String> played = new ArrayList<>();
                for (int ply = 0; ply < MAX_PLIES; ply++) {
                    toStockfish.write("position fen " + Position.START_FEN
                            + (played.isEmpty() ? "" : " moves " + String.join(" ", played)) + "\ngo perft 1\n");
                    toStockfish.flush();
                    List<String> expected = stockfishMoves(fromStockfish);

                    Map<String, Board> children = new HashMap<>();
                    List<String> search = new ArrayList<>();
                    for (Board child : board.children()) {
                        String uci = SearchBoards.uci(child);
                        search.add(uci);
                        children.put(uci, child);
                    }
                    List<String> offered = new ArrayList<>();
                    for (ChessMove m : Rules.legalMoves(fen)) {
                        offered.add(m.toUci());
                    }
                    String where = "game " + game + ", after: " + String.join(" ", played);
                    assertEquals(expected, sorted(search), "the search's moves, " + where);
                    assertEquals(expected, sorted(offered), "the game's moves, " + where);
                    if (expected.isEmpty()) {
                        break;
                    }

                    String move = expected.get(random.nextInt(expected.size()));
                    fen = Rules.applyMove(fen, ChessMove.fromUci(move));
                    board.releaseChildren();
                    board = children.get(move);
                    played.add(move);
                }
            }
        } finally {
            stockfish.destroy();
        }
    }

    /** Reads one {@code go perft 1} answer: a line "e2e4: 1" per legal move, then "Nodes searched". */
    private static List<String> stockfishMoves(BufferedReader fromStockfish) throws IOException {
        List<String> moves = new ArrayList<>();
        String line;
        while ((line = fromStockfish.readLine()) != null && !line.startsWith("Nodes searched")) {
            int colon = line.indexOf(": ");
            if (colon > 0 && line.substring(0, colon).matches("[a-h][1-8][a-h][1-8][qrbn]?")) {
                moves.add(line.substring(0, colon));
            }
        }
        return sorted(moves);
    }

    private static List<String> sorted(List<String> moves) {
        List<String> copy = new ArrayList<>(moves);
        copy.sort(null);
        return copy;
    }
}
