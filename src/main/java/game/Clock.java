package game;

import java.util.function.LongSupplier;

/**
 * A chess clock for one game. The server's clock is the only one that counts: the browser just
 * shows the times it is sent. Not thread-safe; {@link GameSession} only touches it on its
 * dispatcher thread.
 *
 * <p>Neither clock runs before the first move. From then on the clock of the side to move runs,
 * and a move adds the increment to the mover's time (not for a move made before the clock started).
 */
public final class Clock {

    private final TimeControl control;
    private final LongSupplier nanoTime;
    private long whiteMs;
    private long blackMs;
    /** The side whose time is running, or {@code null} while the clock is stopped. */
    private Boolean running;
    /** When the running side's time was last brought up to date ({@link #nanoTime} units). */
    private long since;

    public Clock(TimeControl control, LongSupplier nanoTime) {
        if (!control.isTimed()) {
            throw new IllegalArgumentException("an untimed game has no clock");
        }
        this.control = control;
        this.nanoTime = nanoTime;
        this.whiteMs = control.initialMs();
        this.blackMs = control.initialMs();
    }

    public TimeControl control() {
        return control;
    }

    /** Time left for this side right now, never below zero. */
    public long remainingMs(boolean white) {
        long stored = white ? whiteMs : blackMs;
        if (running != null && running == white) {
            stored -= elapsedMs();
        }
        return Math.max(0, stored);
    }

    /** The side whose time is running: {@code TRUE} White, {@code FALSE} Black, {@code null} stopped. */
    public Boolean running() {
        return running;
    }

    /** True when the running side has no time left. */
    public boolean flagged() {
        return running != null && remainingMs(running) == 0;
    }

    /** How long until the running side's flag falls; {@code Long.MAX_VALUE} while stopped. */
    public long msUntilFlag() {
        return running == null ? Long.MAX_VALUE : remainingMs(running);
    }

    /** A move was made: the mover gets the increment and the other side's time starts running. */
    public void moveMade(boolean whiteMoved) {
        boolean started = running != null;
        settle();
        if (started) {
            add(whiteMoved, control.incrementMs());
        }
        running = !whiteMoved;
        since = nanoTime.getAsLong();
    }

    /** Runs this side's time (after a take-back, say), stopping the other's. */
    public void startFor(boolean white) {
        settle();
        running = white;
        since = nanoTime.getAsLong();
    }

    /** Stops both clocks (game over, or taken back to before the first move). */
    public void stop() {
        settle();
        running = null;
    }

    /** Charges the running side for the time since {@link #since}. */
    private void settle() {
        if (running != null) {
            long left = remainingMs(running);
            if (running) {
                whiteMs = left;
            } else {
                blackMs = left;
            }
            since = nanoTime.getAsLong();
        }
    }

    private long elapsedMs() {
        return (nanoTime.getAsLong() - since) / 1_000_000;
    }

    private void add(boolean white, long ms) {
        if (white) {
            whiteMs += ms;
        } else {
            blackMs += ms;
        }
    }
}
