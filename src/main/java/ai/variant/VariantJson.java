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
 *  "goal": "LOSE_EVERYTHING", "checksToWin": 0, "forcedCapture": true, "castling": false,
 *  "pieces": [{"name": "King", "letter": "K", "value": 0, "royal": false, "promotesTo": "",
 *              "enPassant": false, "castlingRole": "NONE",
 *              "atoms": [{"kind": "LEAP", "forward": 1, "right": 0, "symmetry": "ALL", "mode": "BOTH",
 *                         "range": 0, "firstMoveOnly": false}, ...], "betza": "WF"}, ...]}</pre>
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
        o.addProperty("goal", v.goal().name());
        o.addProperty("checksToWin", v.checksToWin());
        o.addProperty("forcedCapture", v.forcedCapture());
        o.addProperty("castling", v.castling());
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
        return new Variant(string(o, "id"), string(o, "name"), pieces, new Grid(integer(o, "width"), integer(o, "height")),
                string(o, "start"), Variant.Goal.valueOf(string(o, "goal")), integer(o, "checksToWin"),
                bool(o, "forcedCapture"), bool(o, "castling"));
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
