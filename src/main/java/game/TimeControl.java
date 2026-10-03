package game;

/**
 * A game's time control: each side starts with {@code initialMs} and gains {@code incrementMs}
 * after each of its moves (Fischer increment). {@link #NONE} is an untimed game.
 */
public record TimeControl(long initialMs, long incrementMs) {

    public static final TimeControl NONE = new TimeControl(0, 0);

    public TimeControl {
        if (initialMs < 0 || incrementMs < 0) {
            throw new IllegalArgumentException("negative time control: " + initialMs + "+" + incrementMs);
        }
    }

    public static TimeControl ofMinutes(double minutes, int incrementSeconds) {
        return new TimeControl(Math.round(minutes * 60_000), incrementSeconds * 1000L);
    }

    public boolean isTimed() {
        return initialMs > 0;
    }

    /** The PGN {@code TimeControl} tag: {@code "300+2"} (seconds), or {@code "-"} when untimed. */
    public String pgnTag() {
        if (!isTimed()) {
            return "-";
        }
        return seconds(initialMs) + (incrementMs > 0 ? "+" + seconds(incrementMs) : "");
    }

    private static String seconds(long ms) {
        return ms % 1000 == 0 ? Long.toString(ms / 1000) : Double.toString(ms / 1000.0);
    }
}
