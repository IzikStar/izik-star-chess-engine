package ai.eval;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.util.Arrays;
import java.util.Map;

/**
 * A value for every parameter of a {@link ParamSchema}: one individual, in evolution terms.
 * Immutable; values are kept inside each parameter's range. Saved as a JSON object of
 * {@code name: value}, readable and editable by hand, plus {@code "unit": "centipawn"}: scores are
 * in hundredths of a pawn (a pawn is 100). Files written before that marker existed were in tenths
 * of a pawn and are converted when read.
 */
public final class ParamVector {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private final ParamSchema schema;
    private final int[] values;

    /** Values in schema order; each is clamped to its parameter's range. */
    public ParamVector(ParamSchema schema, int[] values) {
        if (values.length != schema.size()) {
            throw new IllegalArgumentException("expected " + schema.size() + " values, got " + values.length);
        }
        this.schema = schema;
        this.values = new int[values.length];
        for (int i = 0; i < values.length; i++) {
            this.values[i] = schema.spec(i).clamp(values[i]);
        }
    }

    public ParamSchema schema() {
        return schema;
    }

    public int size() {
        return values.length;
    }

    public int get(int i) {
        return values[i];
    }

    public int get(String name) {
        int i = schema.indexOf(name);
        if (i < 0) {
            throw new IllegalArgumentException("no parameter " + name);
        }
        return values[i];
    }

    /** A copy of the values, in schema order. */
    public int[] toArray() {
        return values.clone();
    }

    public ParamVector with(int i, int value) {
        int[] copy = values.clone();
        copy[i] = value;
        return new ParamVector(schema, copy);
    }

    public ParamVector with(String name, int value) {
        int i = schema.indexOf(name);
        if (i < 0) {
            throw new IllegalArgumentException("no parameter " + name);
        }
        return with(i, value);
    }

    /** The value of {@code "unit"} in saved files: scores are in hundredths of a pawn. */
    public static final String UNIT = "centipawn";

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("unit", UNIT);
        for (int i = 0; i < values.length; i++) {
            o.addProperty(schema.spec(i).name(), values[i]);
        }
        return GSON.toJson(o);
    }

    /**
     * Reads {@code name: value} pairs; parameters the JSON leaves out keep their defaults (so a
     * file written before a parameter existed still loads). An unknown name is an error, since it
     * is most likely a typo. A file without {@code "unit": "centipawn"} is from before the unit
     * changed and its scores are read as tenths of a pawn ({@link ParamSchema#fromTenths}).
     */
    public static ParamVector fromJson(ParamSchema schema, String json) {
        int[] values = schema.defaults().toArray();
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        boolean tenths = !o.has("unit");
        if (!tenths && !UNIT.equals(o.get("unit").getAsString())) {
            throw new IllegalArgumentException("unknown unit " + o.get("unit").getAsString());
        }
        for (Map.Entry<String, JsonElement> e : o.entrySet()) {
            if (e.getKey().equals("unit")) {
                continue;
            }
            int i = schema.indexOf(e.getKey());
            if (i < 0) {
                throw new IllegalArgumentException("unknown parameter " + e.getKey());
            }
            int value = e.getValue().getAsInt();
            values[i] = tenths ? schema.fromTenths(i, value) : value;
        }
        return new ParamVector(schema, values);
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof ParamVector v && v.schema == schema && Arrays.equals(v.values, values);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(values);
    }

    @Override
    public String toString() {
        return toJson();
    }
}
