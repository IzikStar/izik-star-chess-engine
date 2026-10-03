package ai.eval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * The ordered list of an evaluator's parameters. A {@link ParamVector} is a value for each, in
 * this order, so an algorithm can treat it as a plain array of numbers and still look any of
 * them up by name.
 */
public final class ParamSchema {

    private final List<ParamSpec> specs;
    private final Map<String, Integer> index = new HashMap<>();
    private final Set<String> unscaled;

    public ParamSchema(List<ParamSpec> specs) {
        this(specs, Set.of());
    }

    /**
     * @param unscaled parameters that are not scores (e.g. "until turn N" settings), so they were
     *                 not multiplied by 10 when the unit changed from a tenth of a pawn to a
     *                 centipawn; see {@link #fromTenths}
     */
    public ParamSchema(List<ParamSpec> specs, Set<String> unscaled) {
        this.specs = List.copyOf(specs);
        this.unscaled = Set.copyOf(unscaled);
        for (int i = 0; i < this.specs.size(); i++) {
            if (index.put(this.specs.get(i).name(), i) != null) {
                throw new IllegalArgumentException("duplicate parameter " + this.specs.get(i).name());
            }
        }
    }

    public List<ParamSpec> specs() {
        return specs;
    }

    public int size() {
        return specs.size();
    }

    public ParamSpec spec(int i) {
        return specs.get(i);
    }

    /** The position of {@code name}, or -1 if this schema has no such parameter. */
    public int indexOf(String name) {
        return index.getOrDefault(name, -1);
    }

    /**
     * A value saved before the unit changed from a tenth of a pawn to a centipawn (files without
     * {@code "unit": "centipawn"}), in today's unit: scores times 10, other settings as they were.
     */
    public int fromTenths(int i, int value) {
        return unscaled.contains(specs.get(i).name()) ? value : value * 10;
    }

    /** Every parameter at its default. */
    public ParamVector defaults() {
        int[] values = new int[specs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = specs.get(i).defaultValue();
        }
        return new ParamVector(this, values);
    }
}
