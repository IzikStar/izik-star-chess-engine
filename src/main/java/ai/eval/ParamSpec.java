package ai.eval;

/**
 * One tunable number of an evaluator: its name, the group it belongs to (for display and for
 * algorithms that evolve one group at a time), its default and the range a tuner may move it in
 * (Phase 5, docs/phase-5-research.md §5.1).
 *
 * @param name         unique within its schema, e.g. {@code "material.knight"}
 * @param group        e.g. {@code "material"}
 * @param defaultValue the value the engine plays with unless told otherwise
 * @param min          lowest value a tuner may set (inclusive)
 * @param max          highest value a tuner may set (inclusive)
 * @param description  what the number does, in a sentence
 */
public record ParamSpec(String name, String group, int defaultValue, int min, int max, String description) {

    public ParamSpec {
        if (min > max || defaultValue < min || defaultValue > max) {
            throw new IllegalArgumentException(name + ": default " + defaultValue + " outside [" + min + ", " + max + "]");
        }
    }

    public int clamp(int value) {
        return Math.max(min, Math.min(max, value));
    }
}
