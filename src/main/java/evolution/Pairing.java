package evolution;

/**
 * Two members of a population, by their index in it, who play each other.
 *
 * @param depth the search depth of their games, or 0 to let the run's depth schedule decide
 *              (see {@code lab.RunSettings}); both sides always search to the same depth
 */
public record Pairing(int a, int b, int depth) {

    public Pairing {
        if (a == b || a < 0 || b < 0) {
            throw new IllegalArgumentException("a pairing needs two different members, got " + a + " and " + b);
        }
        if (depth < 0) {
            throw new IllegalArgumentException("depth must not be negative");
        }
    }

    /** A pairing whose depth the run's schedule decides. */
    public Pairing(int a, int b) {
        this(a, b, 0);
    }
}
