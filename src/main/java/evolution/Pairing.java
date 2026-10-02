package evolution;

/** Two members of a population, by their index in it, who play each other. */
public record Pairing(int a, int b) {

    public Pairing {
        if (a == b || a < 0 || b < 0) {
            throw new IllegalArgumentException("a pairing needs two different members, got " + a + " and " + b);
        }
    }
}
