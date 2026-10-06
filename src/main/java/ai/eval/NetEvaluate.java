package ai.eval;

import ai.board.Board;
import ai.board.Outcome;
import ai.board.PieceBoard;
import ai.variant.Variant;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/**
 * An evaluation by a small network that reads the board (Phase 7, docs/phase-7-research.md): the
 * inputs of {@link NetFeatures}, one hidden layer of {@code H} units with a ReLU (what is negative
 * becomes 0), and one output, the score in centipawns for the player to move. Weights come from a
 * file the trainer writes ({@code tools/net}, docs/net-training-guide.md):
 *
 * <pre>
 * { "format": "net-v1", "variant": "chess", "inputs": 768, "hidden": 32, "activation": "relu",
 *   "w1": [ [768 numbers], ... 32 rows ], "b1": [32 numbers], "w2": [32 numbers], "b2": 0.0 }
 * </pre>
 *
 * {@code w1[h][i]} is what input {@code i} adds to hidden unit {@code h} (PyTorch's
 * {@code Linear(768, 32).weight} has this shape), {@code b1} the units' biases, {@code w2} what each
 * unit adds to the score, {@code b2} the score's bias. The score is read as centipawns: the trainer
 * fits {@code 1 / (1 + 10^(-score / 400))} to the expected result, the curve Texel tuning uses.
 *
 * <p>Scoring sums one column of {@code w1} per piece on the board (about 30) and the output: for
 * 32 units about 1,000 multiplications, the same order as the hand-written evaluation. Holds no
 * mutable state, so one instance serves every search thread.
 */
public final class NetEvaluate implements Evaluator {

    /** The {@code "format"} this class reads. */
    public static final String FORMAT = "net-v1";

    /** The network has no evolvable parameter vector; the weights live in its file. */
    public static final ParamSchema NO_PARAMS = new ParamSchema(List.of());

    private final Variant variant;
    private final int inputs;
    private final int hidden;
    /** {@code w1} transposed: the {@code hidden} weights of input {@code i} at {@code i * hidden}. */
    private final float[] columns;
    private final float[] b1;
    private final float[] w2;
    private final float b2;

    /**
     * @param w1 {@code [hidden][inputs]}, as the file holds it
     */
    public NetEvaluate(Variant variant, float[][] w1, float[] b1, float[] w2, float b2) {
        this.variant = variant;
        inputs = NetFeatures.inputs(variant);
        hidden = w1.length;
        if (hidden == 0 || b1.length != hidden || w2.length != hidden) {
            throw new IllegalArgumentException("b1 and w2 need one value per hidden unit (" + hidden + ")");
        }
        columns = new float[inputs * hidden];
        for (int h = 0; h < hidden; h++) {
            if (w1[h].length != inputs) {
                throw new IllegalArgumentException("w1 row " + h + " has " + w1[h].length + " weights, " + variant.name()
                        + " has " + inputs + " inputs");
            }
            for (int i = 0; i < inputs; i++) {
                columns[i * hidden + h] = w1[h][i];
            }
        }
        this.b1 = b1.clone();
        this.w2 = w2.clone();
        this.b2 = b2;
    }

    /** Reads a {@code net-v1} file for {@code variant}. */
    public static NetEvaluate read(Variant variant, Path file) throws IOException {
        return fromJson(variant, Files.readString(file));
    }

    /** Reads a {@code net-v1} JSON text for {@code variant}; its {@code variant} and {@code inputs} must match. */
    public static NetEvaluate fromJson(Variant variant, String json) {
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        String format = o.has("format") ? o.get("format").getAsString() : "?";
        if (!format.equals(FORMAT)) {
            throw new IllegalArgumentException("a " + FORMAT + " file is needed, this is " + format);
        }
        if (o.has("variant") && !o.get("variant").getAsString().equals(variant.id())) {
            throw new IllegalArgumentException("this network plays " + o.get("variant").getAsString() + ", not " + variant.id());
        }
        if (o.has("activation") && !o.get("activation").getAsString().equals("relu")) {
            throw new IllegalArgumentException("only relu is supported, not " + o.get("activation").getAsString());
        }
        int inputs = NetFeatures.inputs(variant);
        if (o.has("inputs") && o.get("inputs").getAsInt() != inputs) {
            throw new IllegalArgumentException("the file has " + o.get("inputs").getAsInt() + " inputs, " + variant.name()
                    + " has " + inputs);
        }
        JsonArray rows = o.getAsJsonArray("w1");
        float[][] w1 = new float[rows.size()][];
        for (int h = 0; h < w1.length; h++) {
            w1[h] = floats(rows.get(h));
        }
        return new NetEvaluate(variant, w1, floats(o.get("b1")), floats(o.get("w2")), o.get("b2").getAsFloat());
    }

    private static float[] floats(JsonElement e) {
        JsonArray a = e.getAsJsonArray();
        float[] out = new float[a.size()];
        for (int i = 0; i < out.length; i++) {
            out[i] = a.get(i).getAsFloat();
        }
        return out;
    }

    public Variant variant() {
        return variant;
    }

    public int hidden() {
        return hidden;
    }

    @Override
    public ParamVector params() {
        return NO_PARAMS.defaults();
    }

    /** The network's raw output for {@code board}: centipawns for the player to move, before rounding. */
    public float forward(PieceBoard board) {
        float[] acc = b1.clone();
        for (int input : NetFeatures.active(board)) {
            int at = input * hidden;
            for (int h = 0; h < hidden; h++) {
                acc[h] += columns[at + h];
            }
        }
        float out = b2;
        for (int h = 0; h < hidden; h++) {
            if (acc[h] > 0) {
                out += acc[h] * w2[h];
            }
        }
        return out;
    }

    @Override
    public int evaluate(Board position, int rootPlayer) {
        Outcome outcome = position.outcome();
        if (outcome != Outcome.ONGOING) {
            int forMover = outcome == Outcome.WIN ? MATE : outcome == Outcome.LOSS ? -MATE : 0;
            return position.sideToMove() == rootPlayer ? forMover : -forMover;
        }
        int forMover = Math.round(forward((PieceBoard) position));
        return position.sideToMove() == rootPlayer ? forMover : -forMover;
    }
}
