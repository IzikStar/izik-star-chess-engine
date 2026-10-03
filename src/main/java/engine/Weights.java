package engine;

import ai.BitBoard.BitBoardEvaluate;

/**
 * The evaluation weights the app's built-in engine plays with; the player picks them in the New
 * game dialog.
 */
public enum Weights {

    /** Fitted by Texel tuning to Stockfish self-play ({@code presets/tuned-v1.json}); the app's default. */
    TUNED("tuned", "Tuned", BitBoardEvaluate.preset("tuned-v1")),
    /** The hand-written weights the engine always played with ({@code presets/classic.json}). */
    CLASSIC("classic", "Classic", BitBoardEvaluate.CLASSIC.params());

    public static final Weights DEFAULT = TUNED;

    private final String id;
    private final String label;
    private final BitBoardEvaluate evaluator;

    Weights(String id, String label, ai.eval.ParamVector params) {
        this.id = id;
        this.label = label;
        this.evaluator = new BitBoardEvaluate(params);
    }

    /** The name the protocol uses: "tuned" or "classic". */
    public String id() {
        return id;
    }

    public String label() {
        return label;
    }

    public BitBoardEvaluate evaluator() {
        return evaluator;
    }

    /** The weights named {@code id}; the default for null or an unknown name. */
    public static Weights of(String id) {
        for (Weights w : values()) {
            if (w.id.equals(id)) {
                return w;
            }
        }
        return DEFAULT;
    }
}
