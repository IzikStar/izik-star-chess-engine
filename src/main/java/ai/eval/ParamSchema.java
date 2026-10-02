package ai.eval;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * The ordered list of an evaluator's parameters. A {@link ParamVector} is a value for each, in
 * this order, so an algorithm can treat it as a plain array of numbers and still look any of
 * them up by name.
 */
public final class ParamSchema {

    private final List<ParamSpec> specs;
    private final Map<String, Integer> index = new HashMap<>();

    public ParamSchema(List<ParamSpec> specs) {
        this.specs = List.copyOf(specs);
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

    /** Every parameter at its default. */
    public ParamVector defaults() {
        int[] values = new int[specs.size()];
        for (int i = 0; i < values.length; i++) {
            values[i] = specs.get(i).defaultValue();
        }
        return new ParamVector(this, values);
    }
}
