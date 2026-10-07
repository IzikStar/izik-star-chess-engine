package web;

import ai.variant.Variant;
import ai.variant.WinCondition;
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

    /** @param opponentLabel the evolved champion the engine plays as, or null */
    static JsonObject snapshot(GameSession session, ChessMove hint, String opponentLabel) {
        JsonObject state = new JsonObject();
        List<MoveResult> moves = session.moves();
        state.addProperty("startFen", moves.isEmpty() ? session.fen() : moves.get(0).fenBefore());
        state.addProperty("fen", session.fen());
        state.add("variant", variant(session.variant()));
        state.addProperty("turn", session.whiteToMove() ? "white" : "black");
        GameStatus status = session.status();
        state.addProperty("status", status.name());
        state.add("checked", squares(rules.Rules.checkedSquares(session.variant(), session.fen())));
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
        return Pgn.write(session.variant(), tags, session.startFen(), moves, result);
    }

    private static String playerName(GameConfig config, boolean white, String opponentLabel) {
        if (config.isHuman(white)) {
            return "Player";
        }
        if (opponentLabel != null && config.mode() == GameConfig.Mode.HUMAN_VS_ENGINE) {
            return opponentLabel;
        }
        return "Engine, level " + config.skillLevelFor(white);
    }

    /** The ending in words, as chess sites write it in the Termination tag. */
    static String termination(GameSession session) {
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
        String mover = session.whiteToMove() ? "White" : "Black";
        String other = session.whiteToMove() ? "Black" : "White";
        Variant variant = session.variant();
        WinCondition goal = rules.Rules.goalMet(variant, session.fen()).orElse(null);
        return switch (session.status()) {
            case CHECKMATE -> other + " won by checkmate";
            case HILL_REACHED -> other + " won by reaching " + (goal != null && goal.squares().equals(
                    WinCondition.centre(variant.grid().width(), variant.grid().height())) ? "the centre"
                    : goal != null ? String.join(", ", goal.squares()) : "a goal square");
            case CHECKS_GIVEN -> other + " won by giving " + variant.checksToWin() + " checks";
            case ALL_CAPTURED -> other + " won by capturing every " + (goal != null ? names(variant, goal.pieces()) : "piece of a kind");
            case BARE_ROYAL -> other + " won by leaving " + mover + " only royal pieces";
            case ROYALS_LOST -> other + " won by capturing every royal piece";
            case STALEMATE_LOSS -> other + " won: " + mover + " has no legal move";
            case NO_PIECES_LEFT -> mover + " won by losing every piece";
            case NO_MOVES_LEFT -> mover + " won with no move left";
            case STALEMATE -> "Game drawn by stalemate";
            case DRAW_FIFTY_MOVE -> "Game drawn by the " + variant.moveLimit() + "-move rule";
            case DRAW_THREEFOLD -> "Game drawn by repetition";
            case DRAW_INSUFFICIENT_MATERIAL -> "Game drawn by insufficient material";
            default -> "";
        };
    }

    /** The names of the piece types with these letters: "Queen", "Queen or Rook". */
    static String names(Variant variant, String letters) {
        List<String> names = new java.util.ArrayList<>();
        for (char c : letters.toCharArray()) {
            variant.pieces().stream().filter(p -> p.letter() == c).findFirst().ifPresent(p -> names.add(p.name()));
        }
        return String.join(" or ", names);
    }

    /** The variant the game is played by: its id, its name, its goals and the settings the page shows. */
    static JsonObject variant(Variant variant) {
        JsonObject o = new JsonObject();
        o.addProperty("id", variant.id());
        o.addProperty("name", variant.name());
        o.add("goals", ai.variant.VariantJson.goals(variant.goals()));
        if (variant.checksToWin() > 0) {
            o.addProperty("checksToWin", variant.checksToWin());
        }
        o.addProperty("royalMode", variant.royalMode().name());
        o.addProperty("stalemate", variant.stalemate().name());
        o.addProperty("repetition", variant.repetition());
        o.addProperty("moveLimit", variant.moveLimit());
        o.addProperty("custom", !game.VariantStore.isBuiltIn(variant.id()));
        JsonArray pieces = new JsonArray();
        for (ai.piece.PieceType t : variant.pieces()) {
            JsonObject p = new JsonObject();
            p.addProperty("letter", String.valueOf(t.letter()));
            p.addProperty("name", t.name());
            p.addProperty("value", t.value());
            if (!game.VariantStore.isBuiltIn(variant.id())) {
                // a made variant: how each piece moves, so the board can show it under the mouse
                p.add("atoms", ai.variant.VariantJson.atoms(t.atoms()));
                p.addProperty("invented", invented(t));
            }
            pieces.add(p);
        }
        o.add("pieces", pieces);
        return o;
    }

    /** A piece that is not one of chess's six, or moves differently from the chess piece with its letter. */
    static boolean invented(ai.piece.PieceType t) {
        return ai.piece.StandardPieces.ALL.stream()
                .noneMatch(c -> c.letter() == t.letter() && new java.util.HashSet<>(c.atoms()).equals(new java.util.HashSet<>(t.atoms())));
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
        o.add("checked", squares(m.checked()));
        return o;
    }

    private static JsonArray squares(List<String> squares) {
        JsonArray a = new JsonArray();
        squares.forEach(a::add);
        return a;
    }

    static JsonObject config(GameConfig config) {
        JsonObject o = new JsonObject();
        o.addProperty("mode", switch (config.mode()) {
            case HUMAN_VS_ENGINE -> "engine";
            case HUMAN_VS_HUMAN -> "friend";
            case ENGINE_VS_ENGINE -> "computer";
        });
        o.addProperty("humanColor", config.humanPlaysWhite() ? "white" : "black");
        o.addProperty("level", config.skillLevel());
        o.addProperty("blackLevel", config.skillLevelFor(false));
        return o;
    }

    /** "1-0", "0-1", "1/2-1/2", or null while the game is on. */
    static String result(GameStatus status, boolean whiteToMove) {
        return status.result(whiteToMove);
    }
}
