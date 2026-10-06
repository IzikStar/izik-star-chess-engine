package lab;

import ai.board.Boards;
import ai.board.PieceBoard;
import ai.eval.NetFeatures;
import ai.variant.Variant;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The network's training file: {@link TrainingExport}'s {@code fen,result,score} lines encoded as
 * {@link NetFeatures} inputs, ready for a trainer to read as one array ({@code tools/net/data.py}).
 * Everything is seen from the player to move, as the network scores:
 *
 * <pre>
 * header   "NETDATA1" (8 bytes), int32 inputs, int32 slots, int32 rows, int32 0 (24 bytes)
 * row      int16[slots] inputs that are on, ascending, then -1 until the slot count
 *          float32 result for the player to move: 1, 0.5 or 0
 *          float32 score for the player to move in centipawns, NaN when the file had none
 *          uint8 side to move (0 or 1), then 3 bytes of 0
 * </pre>
 *
 * All numbers little-endian. {@code slots} is the most pieces a position of the variant can have
 * (those of its start position), so every row has the same length and Python can map the file as
 * one array; a row's padding is -1.
 */
public final class NetData {

    public static final String MAGIC = "NETDATA1";
    public static final int HEADER_BYTES = 24;

    private NetData() {}

    /** Bytes in one row of a file with {@code slots} slots. */
    public static int rowBytes(int slots) {
        return 2 * slots + 4 + 4 + 4;
    }

    /** The most pieces a position of {@code variant} can hold: the pieces of its start position. */
    public static int slots(Variant variant) {
        int count = 0;
        for (char c : variant.startFen().split("\\s+")[0].toCharArray()) {
            if (Character.isLetter(c)) {
                count++;
            }
        }
        return count;
    }

    /**
     * Encodes the {@code fen,result,score} lines of {@code in} (header line first) for
     * {@code variant} into {@code out}; returns the rows written. Reads the whole input twice: once to
     * count rows for the header, once to write.
     */
    public static int write(Variant variant, Path in, Path out) throws IOException {
        int rows = 0;
        try (BufferedReader r = Files.newBufferedReader(in)) {
            for (String line; (line = r.readLine()) != null; ) {
                if (!line.isBlank() && !line.startsWith("fen,")) {
                    rows++;
                }
            }
        }
        try (BufferedReader r = Files.newBufferedReader(in); OutputStream o = Files.newOutputStream(out)) {
            return write(variant, r, rows, o);
        }
    }

    /** Encodes {@code rows} lines of {@code in} into {@code out}. */
    public static int write(Variant variant, BufferedReader in, int rows, OutputStream out) throws IOException {
        int slots = slots(variant);
        ByteBuffer header = ByteBuffer.allocate(HEADER_BYTES).order(ByteOrder.LITTLE_ENDIAN);
        header.put(MAGIC.getBytes(StandardCharsets.US_ASCII)).putInt(NetFeatures.inputs(variant)).putInt(slots)
                .putInt(rows).putInt(0);
        out.write(header.array());
        ByteBuffer row = ByteBuffer.allocate(rowBytes(slots)).order(ByteOrder.LITTLE_ENDIAN);
        int written = 0;
        for (String line; (line = in.readLine()) != null; ) {
            if (line.isBlank() || line.startsWith("fen,")) {
                continue;
            }
            String[] parts = line.split(",", -1);
            PieceBoard board = (PieceBoard) Boards.fromFen(variant, parts[0]);
            int side = board.sideToMove();
            double result = Double.parseDouble(parts[1]);
            float score = parts.length > 2 && !parts[2].isEmpty() ? Float.parseFloat(parts[2]) : Float.NaN;
            int[] active = NetFeatures.active(board);
            if (active.length > slots) {
                throw new IllegalArgumentException(parts[0] + " has " + active.length + " pieces, more than the "
                        + slots + " of the start position");
            }
            row.clear();
            for (int i = 0; i < slots; i++) {
                row.putShort(i < active.length ? (short) active[i] : (short) -1);
            }
            row.putFloat((float) (side == 0 ? result : 1 - result));
            row.putFloat(side == 0 ? score : -score);
            row.put((byte) side).put((byte) 0).put((byte) 0).put((byte) 0);
            out.write(row.array());
            written++;
        }
        if (written != rows) {
            throw new IOException("the header says " + rows + " rows, " + written + " were written");
        }
        return written;
    }

    /** One decoded row, for tests and checks. */
    public record Row(int[] active, float result, float score, int sideToMove) {}

    /** Reads a whole file back. */
    public static List<Row> read(Path file) throws IOException {
        try (InputStream in = Files.newInputStream(file)) {
            ByteBuffer header = ByteBuffer.wrap(in.readNBytes(HEADER_BYTES)).order(ByteOrder.LITTLE_ENDIAN);
            byte[] magic = new byte[8];
            header.get(magic);
            if (!new String(magic, StandardCharsets.US_ASCII).equals(MAGIC)) {
                throw new IOException("not a " + MAGIC + " file");
            }
            header.getInt(); // inputs
            int slots = header.getInt();
            int rows = header.getInt();
            List<Row> out = new ArrayList<>(rows);
            for (int r = 0; r < rows; r++) {
                ByteBuffer row = ByteBuffer.wrap(in.readNBytes(rowBytes(slots))).order(ByteOrder.LITTLE_ENDIAN);
                int[] active = new int[slots];
                int n = 0;
                for (int i = 0; i < slots; i++) {
                    short v = row.getShort();
                    if (v >= 0) {
                        active[n++] = v;
                    }
                }
                float result = row.getFloat();
                float score = row.getFloat();
                int side = row.get();
                out.add(new Row(java.util.Arrays.copyOf(active, n), result, score, side));
            }
            return out;
        }
    }
}
