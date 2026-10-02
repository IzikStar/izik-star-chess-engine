package engine;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import rules.ChessMove;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The built-in engine still plays the recorded moves at depths 1-4 (same-moves.txt). Phase 4b's
 * speed work may only make the search faster, never change what it plays
 * (docs/phase-4b-research.md Fork B1).
 */
class SameMoveTest {

    record Recorded(String fen, List<String> moves) {
        @Override
        public String toString() {
            return fen;
        }
    }

    static List<String> lines() {
        try (InputStream in = SameMoveTest.class.getResourceAsStream("same-moves.txt")) {
            if (in == null) {
                throw new IllegalStateException("same-moves.txt is missing");
            }
            return new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8)).lines().toList();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    static List<Recorded> recorded() {
        List<Recorded> out = new ArrayList<>();
        for (String line : lines()) {
            if (line.isBlank() || line.startsWith("#")) {
                continue;
            }
            String[] parts = line.split(" \\| ");
            out.add(new Recorded(parts[0], Arrays.asList(parts).subList(1, parts.length)));
        }
        return out;
    }

    static List<String> play(String fen, int maxDepth) {
        List<String> moves = new ArrayList<>();
        for (int depth = 1; depth <= maxDepth; depth++) {
            ChessMove move = MinimaxEngine.searchAtDepth(fen, depth);
            moves.add(move == null ? "-" : move.toUci());
        }
        return moves;
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("recorded")
    @DisplayName("The engine plays the recorded move at each depth")
    void playsTheRecordedMoves(Recorded position) {
        assertEquals(position.moves(), play(position.fen(), position.moves().size()));
    }

    /** Prints same-moves.txt again with the moves the engine plays now; see the file's header. */
    public static void main(String[] args) {
        for (String line : lines()) {
            if (line.isBlank() || line.startsWith("#")) {
                System.out.println(line);
            }
        }
        for (Recorded position : recorded()) {
            System.out.println(position.fen() + " | " + String.join(" | ", play(position.fen(), position.moves().size())));
        }
    }
}
