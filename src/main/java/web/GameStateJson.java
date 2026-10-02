package web;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import game.GameConfig;
import game.GameSession;
import rules.ChessMove;
import rules.GameStatus;
import rules.MoveResult;
import rules.Position;

import java.util.List;

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

    static JsonObject snapshot(GameSession session, ChessMove hint) {
        JsonObject state = new JsonObject();
        List<MoveResult> moves = session.moves();
        state.addProperty("startFen", moves.isEmpty() ? session.fen() : moves.get(0).fenBefore());
        state.addProperty("fen", session.fen());
        state.addProperty("turn", session.whiteToMove() ? "white" : "black");
        GameStatus status = session.status();
        state.addProperty("status", status.name());
        state.addProperty("result", result(status, session.whiteToMove()));
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
        return state;
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
