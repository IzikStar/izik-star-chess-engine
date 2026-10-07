package arena;

import rules.Game;
import rules.MoveResult;
import rules.Pgn;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * An arena game as PGN, for any chess program. Players are named as the arena names them ("#3"
 * for an evolution run's member 3, "classic", "sf1320"); the opening, the depth and why the game
 * ended are tags.
 */
public final class GamePgn {

    private GamePgn() {}

    /** One game of {@code variant}, which gets its {@code Variant} tag; {@code depth} 0 leaves the depth out. */
    public static String write(ai.variant.Variant variant, GameRecord g, String event, int round, int depth) {
        Game game = new Game(variant);
        List<MoveResult> moves = new ArrayList<>();
        g.moves().forEach(uci -> moves.add(game.play(uci)));
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Event", event);
        tags.put("Site", "IzikStar Chess arena");
        tags.put("Round", String.valueOf(round));
        tags.put("White", player(g.white()));
        tags.put("Black", player(g.black()));
        tags.put("Opening", g.opening());
        if (depth > 0) {
            tags.put("Depth", String.valueOf(depth));
        }
        tags.put("Termination", g.reason().equals("PLY_CAP") ? "adjudication" : "normal");
        tags.put("EndReason", g.reason());
        return Pgn.write(variant, tags, variant.startFen(), moves, switch (g.result()) {
            case WHITE_WINS -> "1-0";
            case BLACK_WINS -> "0-1";
            case DRAW -> "1/2-1/2";
        });
    }

    private static String player(String name) {
        return name.matches("\\d+") ? "#" + name : name;
    }

}
