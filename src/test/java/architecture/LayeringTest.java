package architecture;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Phase 3 exit criterion, enforced: the headless core (rules, engines, the game session and the
 * bitboard search) imports nothing from Swing, AWT or the desktop UI packages, so it can run
 * unchanged behind a server later. Also keeps the layers pointing one way.
 */
class LayeringTest {

    private static final Path SRC = Path.of("src/main/java");
    private static final Pattern IMPORT = Pattern.compile("^import\\s+(static\\s+)?([\\w.]+)", Pattern.MULTILINE);

    /** Package -> import prefixes it must not use. */
    private static final String[][] RULES = {
            {"rules", "javax.swing", "java.awt", "main.", "GUI.", "engine.", "game."},
            {"ai", "javax.swing", "java.awt", "main.", "GUI.", "engine.", "game."},
            {"engine", "javax.swing", "java.awt", "main.", "GUI.", "game."},
            {"game", "javax.swing", "java.awt", "main.", "GUI."},
    };

    @Test
    @DisplayName("The headless core does not import Swing, AWT or the UI")
    void headlessCoreHasNoUiImports() throws IOException {
        List<String> violations = new ArrayList<>();
        for (String[] rule : RULES) {
            try (Stream<Path> files = Files.walk(SRC.resolve(rule[0]))) {
                for (Path file : files.filter(p -> p.toString().endsWith(".java")).toList()) {
                    Matcher m = IMPORT.matcher(Files.readString(file));
                    while (m.find()) {
                        String imported = m.group(2);
                        for (int i = 1; i < rule.length; i++) {
                            if (imported.startsWith(rule[i])) {
                                violations.add(SRC.relativize(file) + " imports " + imported);
                            }
                        }
                    }
                }
            }
        }
        assertEquals(List.of(), violations);
    }

    @Test
    @DisplayName("The legacy global-settings and object-model classes stay deleted")
    void legacyClassesStayDeleted() {
        for (String gone : new String[]{"main/setting/ChoosePlayFormat.java", "ai/BoardState.java",
                "main/Move.java", "pieces", "ai/myEngine.java", "main/savedGames/SavedStatesForDraws.java"}) {
            assertEquals(false, Files.exists(SRC.resolve(gone)), gone + " is back");
        }
    }
}
