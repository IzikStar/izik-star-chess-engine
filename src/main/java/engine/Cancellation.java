package engine;

/**
 * A one-way "stop searching" flag shared between whoever started a search and the engine running
 * it. Engines look at it while they think and give up soon after it is set (Phase 4).
 */
public final class Cancellation {

    /** Never cancelled; for callers that will always wait for the answer. */
    public static final Cancellation NONE = new Cancellation();

    private volatile boolean cancelled;

    public void cancel() {
        if (this != NONE) {
            cancelled = true;
        }
    }

    public boolean isCancelled() {
        return cancelled;
    }
}
