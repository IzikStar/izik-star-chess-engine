package web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import game.Clock;
import game.GameConfig;
import game.GameEnd;
import game.GameSession;
import rules.ChessMove;
import rules.GameStatus;
import rules.MoveResult;
import rules.Pgn;
import rules.Position;

import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The JSON a browser receives: a full snapshot of the session after every change, so the client
 * never has to reconstruct state from a stream of events (docs/ui-research.md §4, Option B). The
 * server stays the only rules authority: the client is told the legal moves, it never computes
 * them.
 */
final class GameStateJson {

    private GameStateJson() {}

    /** The UI's levels 1-10 are the session's skill levels 0, 2, ..., 18 (as in the Swing settings). */
    static int uiLevel(int skillLevel) {
        return skillLevel / 2 + 1;
    }

    static int skillLevel(int uiLevel) {
        return (Math.max(1, Math.min(10, uiLevel)) - 1) * 2;
    }

    /** @param opponentLabel the evolved champion the engine plays as, or null */
    static JsonObject snapshot(GameSession session, ChessMove hint, String opponentLabel) {
        JsonObject state = new JsonObject();
        List<MoveResult> moves = session.moves();
        state.addProperty("startFen", moves.isEmpty() ? session.fen() : moves.get(0).fenBefore());
        state.addProperty("fen", session.fen());
        state.addProperty("turn", session.whiteToMove() ? "white" : "black");
        GameStatus status = session.status();
        state.addProperty("status", status.name());
        state.addProperty("result", session.result());
        state.add("end", end(session.end()));
        state.add("clock", clock(session.clock()));
        Boolean offer = session.drawOfferBy();
        state.addProperty("drawOffer", offer == null ? null : offer ? "white" : "black");
        state.addProperty("canOfferDraw", session.canOfferDraw());
        state.addProperty("canResign", session.canResign());
        state.addProperty("humanTurn", session.isHumanTurn());
        state.addProperty("engineThinking", session.isEngineThinking());
        state.addProperty("hintPending", session.isHintPending());
        state.addProperty("hint", hint == null ? null : hint.toUci());
        state.addProperty("material", material(session.position()));

        JsonArray legal = new JsonArray();
        if (session.isHumanTurn()) {
            for (ChessMove m : session.legalMoves()) {
                legal.add(m.toUci());
            }
        }
        state.add("legalMoves", legal);

        JsonArray list = new JsonArray();
        for (MoveResult m : moves) {
            list.add(move(m));
        }
        state.add("moves", list);
        state.add("config", config(session.config()));
        state.addProperty("pgn", pgn(session, opponentLabel));
        return state;
    }

    static JsonObject end(GameEnd end) {
        if (end == null) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("reason", end.reason().name());
        o.addProperty("side", end.white() ? "white" : "black");
        return o;
    }

    /** The clock as it reads now; the browser counts the running side down from here. */
    static JsonObject clock(Clock clock) {
        if (clock == null) {
            return null;
        }
        JsonObject o = new JsonObject();
        o.addProperty("initialMs", clock.control().initialMs());
        o.addProperty("incrementMs", clock.control().incrementMs());
        o.addProperty("white", clock.remainingMs(true));
        o.addProperty("black", clock.remainingMs(false));
        Boolean running = clock.running();
        o.addProperty("running", running == null ? null : running ? "white" : "black");
        return o;
    }

    /** The game so far as PGN, for the browser's Copy and Download buttons. */
    static String pgn(GameSession session, String opponentLabel) {
        GameConfig config = session.config();
        Map<String, String> tags = new LinkedHashMap<>();
        tags.put("Event", switch (config.mode()) {
            case HUMAN_VS_ENGINE -> "Game against the engine";
            case HUMAN_VS_HUMAN -> "Game between two players";
            case ENGINE_VS_ENGINE -> "Engine against engine";
        });
        tags.put("Site", "IzikStar Chess");
        tags.put("Date", session.date().format(DateTimeFormatter.ofPattern("yyyy.MM.dd")));
        tags.put("Round", "-");
        tags.put("White", playerName(config, true, opponentLabel));
        tags.put("Black", playerName(config, false, opponentLabel));
        tags.put("TimeControl", session.timeControl().pgnTag());
        String result = session.result();
        if (result != null) {
            tags.put("Termination", termination(session));
        }
        List<MoveResult> moves = session.moves();
        return Pgn.write(tags, session.startFen(), moves, result);
    }

    private static String playerName(GameConfig config, boolean white, String opponentLabel) {
        if (config.isHuman(white)) {
            return "Player";
        }
        if (opponentLabel != null && config.mode() == GameConfig.Mode.HUMAN_VS_ENGINE) {
            return opponentLabel;
        }
        return "Engine, level " + uiLevel(config.skillLevelFor(white));
    }

    /** The ending in words, as chess sites write it in the Termination tag. */
    private static String termination(GameSession session) {
        GameEnd end = session.end();
        if (end != null) {
            String side = end.white() ? "White" : "Black";
            String winner = end.white() ? "Black" : "White";
            return switch (end.reason()) {
                case RESIGNATION -> winner + " won by resignation";
                case TIMEOUT -> winner + " won on time";
                case TIMEOUT_VS_INSUFFICIENT_MATERIAL -> "Game drawn: " + side + " ran out of time, " + winner + " cannot mate";
                case AGREEMENT -> "Game drawn by agreement";
            };
        }
        return switch (session.status()) {
            case CHECKMATE -> (session.whiteToMove() ? "Black" : "White") + " won by checkmate";
            case STALEMATE -> "Game drawn by stalemate";
            case DRAW_FIFTY_MOVE -> "Game drawn by the 50-move rule";
            case DRAW_THREEFOLD -> "Game drawn by repetition";
            case DRAW_INSUFFICIENT_MATERIAL -> "Game drawn by insufficient material";
            default -> "";
        };
    }

    static JsonObject move(MoveResult m) {
        JsonObject o = new JsonObject();
        o.addProperty("uci", m.move().toUci());
        o.addProperty("san", m.san());
        o.addProperty("color", m.whiteMoved() ? "white" : "black");
        o.addProperty("number", m.moveNumber());
        o.addProperty("fenAfter", m.fenAfter());
        o.addProperty("capture", m.isCapture());
        o.addProperty("castling", m.castling());
        o.addProperty("enPassant", m.enPassant());
        o.addProperty("promotion", m.isPromotion());
        o.addProperty("status", m.status().name());
        return o;
    }

    static JsonObject config(GameConfig config) {
        JsonObject o = new JsonObject();
        o.addProperty("mode", switch (config.mode()) {
            case HUMAN_VS_ENGINE -> "engine";
            case HUMAN_VS_HUMAN -> "friend";
            case ENGINE_VS_ENGINE -> "computer";
        });
        o.addProperty("humanColor", config.humanPlaysWhite() ? "white" : "black");
        o.addProperty("level", uiLevel(config.skillLevel()));
        o.addProperty("blackLevel", uiLevel(config.skillLevelFor(false)));
        return o;
    }

    /** "1-0", "0-1", "1/2-1/2", or null while the game is on. */
    static String result(GameStatus status, boolean whiteToMove) {
        if (status == GameStatus.CHECKMATE) {
            return whiteToMove ? "0-1" : "1-0";
        }
        return status.isDraw() ? "1/2-1/2" : null;
    }

    /** White's material minus Black's (P 1, N/B 3, R 5, Q 9), as the Swing score panel showed it. */
    static int material(Position position) {
        int score = 0;
        for (char piece : position.pieces()) {
            int value = switch (Character.toLowerCase(piece)) {
                case 'p' -> 1;
                case 'n', 'b' -> 3;
                case 'r' -> 5;
                case 'q' -> 9;
                default -> 0;
            };
            score += Character.isUpperCase(piece) ? value : -value;
        }
        return score;
    }
}
