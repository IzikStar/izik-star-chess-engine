package ai.variant;

import ai.piece.Atom;
import ai.piece.Betza;
import ai.piece.Grid;
import ai.piece.PieceType;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.ArrayList;
import java.util.List;

/**
 * A {@link Variant} as JSON, the form a variant is saved, sent to the browser and edited in. Every
 * field is written out, so a file says the whole game:
 *
 * <pre>{"id": "antichess", "name": "Antichess", "width": 8, "height": 8, "start": "rnbqkbnr/... w - - 0 1",
 *  "goals": [{"kind": "LOSE_EVERYTHING"}], "royalMode": "ALL_SAFE", "stalemate": "WIN", "repetition": true,
 *  "moveLimit": 50, "forcedCapture": true, "castling": false,
 *  "castlingRule": {"steps": 0, "partner": "INSIDE", "sides": "BOTH", "safePassage": true},
 *  "pieces": [{"name": "King", "letter": "K", "value": 0, "royal": false, "promotesTo": "",
 *              "enPassant": false, "castlingRole": "NONE",
 *              "atoms": [{"kind": "LEAP", "forward": 1, "right": 0, "symmetry": "ALL", "mode": "BOTH",
 *                         "range": 0, "firstMoveOnly": false}, ...], "betza": "WF"}, ...]}</pre>
 *
 * <p>A goal carries {@code count} (CHECKS), {@code squares} (REACH_SQUARES) and {@code pieces} (letters;
 * REACH_SQUARES and CAPTURE_ALL_OF) only where it has them. The first variant files had one
 * {@code "goal"} with {@code "checksToWin"} and none of the settings after it; such a file reads as
 * the preset that goal stood for ({@link Variant.Goal}), and so plays as it always did. A newer file
 * missing a setting gets its chess value.
 *
 * <p>Each piece also carries its moves in Betza text ({@link Betza}), written for people to read;
 * the atoms decide. A piece read without atoms is read from its Betza text, so a hand-written file
 * may give only that.
 */
public final class VariantJson {

    private VariantJson() {}

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public static String write(Variant v) {
        return GSON.toJson(toTree(v));
    }

    public static JsonObject toTree(Variant v) {
        JsonObject o = new JsonObject();
        o.addProperty("id", v.id());
        o.addProperty("name", v.name());
        o.addProperty("width", v.grid().width());
        o.addProperty("height", v.grid().height());
        o.addProperty("start", v.startFen());
        o.add("goals", goals(v.goals()));
        o.addProperty("royalMode", v.royalMode().name());
        o.addProperty("stalemate", v.stalemate().name());
        o.addProperty("repetition", v.repetition());
        o.addProperty("moveLimit", v.moveLimit());
        o.addProperty("forcedCapture", v.forcedCapture());
        o.addProperty("castling", v.castling().enabled());
        JsonObject castling = new JsonObject();
        castling.addProperty("steps", v.castling().steps());
        castling.addProperty("partner", v.castling().partner().name());
        castling.addProperty("sides", v.castling().sides().name());
        castling.addProperty("safePassage", v.castling().safePassage());
        o.add("castlingRule", castling);
        JsonArray pieces = new JsonArray();
        for (PieceType t : v.pieces()) {
            JsonObject p = new JsonObject();
            p.addProperty("name", t.name());
            p.addProperty("letter", String.valueOf(t.letter()));
            p.addProperty("value", t.value());
            p.addProperty("royal", t.royal());
            StringBuilder promotes = new StringBuilder();
            t.promotesTo().forEach(promotes::append);
            p.addProperty("promotesTo", promotes.toString());
            p.addProperty("enPassant", t.enPassant());
            p.addProperty("castlingRole", t.castling().name());
            p.add("atoms", atoms(t.atoms()));
            try {
                p.addProperty("betza", Betza.write(t.atoms()));
            } catch (IllegalArgumentException e) {
                // a move Betza has no letter for: the atoms alone say it
            }
            pieces.add(p);
        }
        o.add("pieces", pieces);
        return o;
    }

    /** Reads a variant; a missing or malformed field is an {@link IllegalArgumentException} naming it. */
    public static Variant read(String json) {
        try {
            return fromTree(JsonParser.parseString(json).getAsJsonObject());
        } catch (IllegalStateException | UnsupportedOperationException | NullPointerException
                 | ClassCastException | com.google.gson.JsonParseException e) {
            throw new IllegalArgumentException("not a variant: " + e.getMessage(), e);
        }
    }

    public static Variant fromTree(JsonObject o) {
        List<PieceType> pieces = new ArrayList<>();
        for (var element : array(o, "pieces")) {
            JsonObject p = element.getAsJsonObject();
            List<Atom> atoms = new ArrayList<>();
            if (!p.has("atoms") && p.has("betza")) {
                atoms.addAll(Betza.parse(string(p, "betza")));
            }
            if (p.has("atoms") || !p.has("betza")) {
                atoms.addAll(atoms(array(p, "atoms")));
            }
            List<Character> promotes = new ArrayList<>();
            for (char c : string(p, "promotesTo").toCharArray()) {
                promotes.add(c);
            }
            String letter = string(p, "letter");
            if (letter.length() != 1) {
                throw new IllegalArgumentException("a piece's letter is one letter: " + letter);
            }
            pieces.add(new PieceType(string(p, "name"), letter.charAt(0), atoms, bool(p, "royal"), promotes,
                    bool(p, "enPassant"), PieceType.Castling.valueOf(string(p, "castlingRole")), integer(p, "value")));
        }
        Grid grid = new Grid(integer(o, "width"), integer(o, "height"));
        String id = string(o, "id");
        String name = string(o, "name");
        String start = string(o, "start");
        if (!o.has("goals")) { // a file from before the building blocks: its one goal is a preset
            return new Variant(id, name, pieces, grid, start, Variant.Goal.valueOf(string(o, "goal")),
                    integer(o, "checksToWin"), bool(o, "forcedCapture"), bool(o, "castling"));
        }
        CastlingRule castling = CastlingRule.CHESS;
        if (o.has("castlingRule")) {
            JsonObject c = o.getAsJsonObject("castlingRule");
            castling = new CastlingRule(true, c.has("steps") ? integer(c, "steps") : 0,
                    c.has("partner") ? CastlingRule.Partner.valueOf(string(c, "partner")) : CastlingRule.Partner.INSIDE,
                    c.has("sides") ? CastlingRule.Sides.valueOf(string(c, "sides")) : CastlingRule.Sides.BOTH,
                    !c.has("safePassage") || bool(c, "safePassage"));
        }
        castling = new CastlingRule(bool(o, "castling"), castling.steps(), castling.partner(), castling.sides(),
                castling.safePassage());
        return new Variant(id, name, pieces, grid, start, goals(array(o, "goals")),
                o.has("royalMode") ? Variant.RoyalMode.valueOf(string(o, "royalMode")) : Variant.RoyalMode.ALL_SAFE,
                o.has("stalemate") ? Variant.Stalemate.valueOf(string(o, "stalemate")) : Variant.Stalemate.DRAW,
                !o.has("repetition") || bool(o, "repetition"),
                o.has("moveLimit") ? integer(o, "moveLimit") : 50,
                bool(o, "forcedCapture"), castling);
    }

    /** Goals as the JSON array a variant carries. */
    public static JsonArray goals(List<WinCondition> goals) {
        JsonArray out = new JsonArray();
        for (WinCondition g : goals) {
            JsonObject j = new JsonObject();
            j.addProperty("kind", g.kind().name());
            if (g.kind() == WinCondition.Kind.CHECKS) {
                j.addProperty("count", g.count());
            }
            if (g.kind() == WinCondition.Kind.REACH_SQUARES) {
                JsonArray squares = new JsonArray();
                g.squares().forEach(squares::add);
                j.add("squares", squares);
            }
            if (g.kind() == WinCondition.Kind.REACH_SQUARES || g.kind() == WinCondition.Kind.CAPTURE_ALL_OF) {
                j.addProperty("pieces", g.pieces());
            }
            out.add(j);
        }
        return out;
    }

    /** Goals from a variant's JSON array; a field a goal needs and lacks is an {@link IllegalArgumentException}. */
    public static List<WinCondition> goals(JsonArray array) {
        List<WinCondition> out = new ArrayList<>();
        for (var element : array) {
            JsonObject j = element.getAsJsonObject();
            WinCondition.Kind kind = WinCondition.Kind.valueOf(string(j, "kind"));
            List<String> squares = new ArrayList<>();
            if (kind == WinCondition.Kind.REACH_SQUARES) {
                for (var s : array(j, "squares")) {
                    squares.add(s.getAsString());
                }
            }
            boolean typed = kind == WinCondition.Kind.REACH_SQUARES || kind == WinCondition.Kind.CAPTURE_ALL_OF;
            out.add(new WinCondition(kind, kind == WinCondition.Kind.CHECKS ? integer(j, "count") : 0, squares,
                    typed && j.has("pieces") ? j.get("pieces").getAsString() : ""));
        }
        return out;
    }

    /** Atoms as the JSON array a piece carries. */
    public static JsonArray atoms(List<Atom> list) {
        JsonArray atoms = new JsonArray();
        for (Atom a : list) {
            JsonObject j = new JsonObject();
            j.addProperty("kind", a.kind().name());
            j.addProperty("forward", a.forward());
            j.addProperty("right", a.right());
            j.addProperty("symmetry", a.symmetry().name());
            j.addProperty("mode", a.mode().name());
            j.addProperty("range", a.range());
            j.addProperty("firstMoveOnly", a.firstMoveOnly());
            atoms.add(j);
        }
        return atoms;
    }

    /** Atoms from a piece's JSON array; a missing field is an {@link IllegalArgumentException} naming it. */
    public static List<Atom> atoms(JsonArray array) {
        List<Atom> atoms = new ArrayList<>();
        for (var a : array) {
            JsonObject j = a.getAsJsonObject();
            atoms.add(new Atom(Atom.Kind.valueOf(string(j, "kind")), integer(j, "forward"), integer(j, "right"),
                    Atom.Symmetry.valueOf(string(j, "symmetry")), Atom.Mode.valueOf(string(j, "mode")),
                    integer(j, "range"), bool(j, "firstMoveOnly")));
        }
        return atoms;
    }

    private static JsonArray array(JsonObject o, String key) {
        return field(o, key).getAsJsonArray();
    }

    private static String string(JsonObject o, String key) {
        return field(o, key).getAsString();
    }

    private static int integer(JsonObject o, String key) {
        return field(o, key).getAsInt();
    }

    private static boolean bool(JsonObject o, String key) {
        return field(o, key).getAsBoolean();
    }

    private static com.google.gson.JsonElement field(JsonObject o, String key) {
        if (!o.has(key) || o.get(key).isJsonNull()) {
            throw new IllegalArgumentException("missing \"" + key + "\"");
        }
        return o.get(key);
    }
}
