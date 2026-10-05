package rules;

import ai.variant.Variant;
import ai.variant.Variants;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Portable Game Notation: writes a game for other chess programs and reads one back. Reading
 * checks every move against {@link Rules}, so a game that loads is a legal game.
 *
 * <p>Variants (Phase 6 R4d) go in a {@code Variant} tag with the names Lichess uses ("Antichess",
 * "King of the Hill", "Three-check"); no tag, or "Standard", is chess.
 */
public final class Pgn {

    private Pgn() {}

    /** A game read from PGN: its tags, its variant, the position it starts from, and its moves. */
    public record Parsed(Map<String, String> tags, Variant variant, String startFen, List<ChessMove> moves) {}

    /**
     * The game as PGN: the tags in the order given (the {@code Result} tag is set from
     * {@code result}; a non-standard start adds {@code SetUp} and {@code FEN}), then the moves,
     * wrapped at 80 columns, ending with the result ({@code "*"} while the game is on).
     */
    public static String write(Map<String, String> tags, String startFen, List<MoveResult> moves, String result) {
        return write(Variants.CHESS, tags, startFen, moves, result);
    }

    /**
     * Like {@link #write(Map, String, List, String)} for a game of {@code variant}: a variant other
     * than chess adds its {@code Variant} tag, and only a start other than the variant's own adds
     * {@code FEN}.
     */
    public static String write(Variant variant, Map<String, String> tags, String startFen, List<MoveResult> moves,
                               String result) {
        String res = result == null ? "*" : result;
        Map<String, String> all = new LinkedHashMap<>(tags);
        all.put("Result", res);
        if (!variant.equals(Variants.CHESS)) {
            all.put("Variant", variant.name());
        }
        if (!startFen.equals(variant.startFen())) {
            all.put("SetUp", "1");
            all.put("FEN", startFen);
        }
        StringBuilder out = new StringBuilder();
        all.forEach((k, v) -> out.append('[').append(k).append(" \"")
                .append(v.replace("\\", "\\\\").replace("\"", "\\\"")).append("\"]\n"));
        out.append('\n');

        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < moves.size(); i++) {
            MoveResult m = moves.get(i);
            if (m.whiteMoved()) {
                tokens.add(m.moveNumber() + ".");
            } else if (i == 0) {
                tokens.add(m.moveNumber() + "...");
            }
            tokens.add(m.san());
        }
        tokens.add(res);
        int column = 0;
        for (String t : tokens) {
            if (column > 0 && column + 1 + t.length() > 80) {
                out.append('\n');
                column = 0;
            } else if (column > 0) {
                out.append(' ');
                column++;
            }
            out.append(t);
            column += t.length();
        }
        return out.append('\n').toString();
    }

    private static final Pattern TAG = Pattern.compile("\\[\\s*(\\w+)\\s+\"((?:[^\"\\\\]|\\\\.)*)\"\\s*]");
    private static final Pattern RESULT = Pattern.compile("1-0|0-1|1/2-1/2|\\*");
    private static final Pattern MOVE_NUMBER = Pattern.compile("\\d+\\.+");

    /**
     * Reads the first game of {@code text}: tags, then the main line (comments, variations, move
     * numbers and annotation glyphs are skipped). Moves may be in SAN, also written without
     * {@code =} before a promotion piece or with zeros for castling, or in UCI.
     *
     * @throws IllegalArgumentException naming the first move that is not legal, or for an
     *                                  unreadable start position
     */
    public static Parsed read(String text) {
        return read(text, Pgn::builtIn);
    }

    /**
     * Like {@link #read(String)}, finding the {@code Variant} tag's variant with {@code variants}
     * (a name or id; empty when unknown), so a game of a variant the player made reads too.
     */
    public static Parsed read(String text, java.util.function.Function<String, java.util.Optional<Variant>> variants) {
        Map<String, String> tags = new LinkedHashMap<>();
        Matcher tag = TAG.matcher(text);
        int bodyStart = 0;
        // tags come first; stop at the first one that isn't at the head of the remaining text
        while (tag.find() && text.substring(bodyStart, tag.start()).isBlank()) {
            tags.put(tag.group(1), tag.group(2).replace("\\\"", "\"").replace("\\\\", "\\"));
            bodyStart = tag.end();
        }
        String tagged = tags.get("Variant");
        Variant variant = tagged == null || tagged.isBlank() ? Variants.CHESS : variants.apply(tagged.trim())
                .orElseThrow(() -> new IllegalArgumentException("unknown variant: " + tagged.trim()));
        String startFen = tags.getOrDefault("FEN", variant.startFen()).trim();
        try {
            Position.fromFen(startFen);
        } catch (RuntimeException e) {
            throw new IllegalArgumentException("the FEN tag is not a position: " + startFen);
        }

        String body = stripCommentsAndVariations(text.substring(bodyStart));
        List<ChessMove> moves = new ArrayList<>();
        String fen = startFen;
        for (String raw : body.trim().split("\\s+")) {
            String token = MOVE_NUMBER.matcher(raw).replaceFirst("");
            if (token.isEmpty() || token.startsWith("$")) {
                continue;
            }
            if (RESULT.matcher(token).matches()) {
                break; // the end of the first game
            }
            ChessMove move = find(variant, fen, token);
            if (move == null) {
                Position pos = Position.fromFen(fen);
                throw new IllegalArgumentException("move " + pos.fullmoveNumber() + (pos.whiteToMove() ? ". " : "... ")
                        + raw + " is not a legal move");
            }
            moves.add(move);
            fen = Rules.applyMove(variant, fen, move);
        }
        return new Parsed(tags, variant, startFen, moves);
    }

    /** A built-in variant a {@code Variant} tag names (its name or id, any case); "Standard" is chess. */
    public static java.util.Optional<Variant> builtIn(String tag) {
        if (tag.equalsIgnoreCase("standard")) {
            return java.util.Optional.of(Variants.CHESS);
        }
        return Variants.ALL.stream().filter(v -> v.name().equalsIgnoreCase(tag) || v.id().equalsIgnoreCase(tag)).findFirst();
    }

    private static String stripCommentsAndVariations(String body) {
        StringBuilder out = new StringBuilder();
        int depth = 0;
        for (int i = 0; i < body.length(); i++) {
            char c = body.charAt(i);
            if (c == '{') {
                int close = body.indexOf('}', i);
                i = close < 0 ? body.length() : close;
                out.append(' ');
            } else if (c == ';') {
                int eol = body.indexOf('\n', i);
                i = eol < 0 ? body.length() : eol;
                out.append(' ');
            } else if (c == '(') {
                depth++;
            } else if (c == ')') {
                depth = Math.max(0, depth - 1);
                out.append(' ');
            } else if (depth == 0) {
                out.append(c);
            }
        }
        return out.toString();
    }

    /** The legal move {@code token} names in {@code fen}, or null. */
    private static ChessMove find(Variant variant, String fen, String token) {
        String want = normalise(token);
        List<ChessMove> legal = Rules.legalMoves(variant, fen);
        for (ChessMove m : legal) {
            if (normalise(San.of(variant, fen, m)).equals(want)) {
                return m;
            }
        }
        if (token.matches("[a-h][1-8][a-h][1-8][qrbnk]?")) {
            ChessMove uci = ChessMove.fromUci(token);
            for (ChessMove m : legal) {
                if (m.equals(uci)) {
                    return m;
                }
            }
        }
        return null;
    }

    /** SAN without check marks or annotations, castling with letters, promotions with {@code =}. */
    private static String normalise(String san) {
        String s = san.replaceAll("[+#!?]+$", "").replace('0', 'O');
        return s.replaceFirst("^([a-h](?:x[a-h])?[18])([QRBNK])$", "$1=$2");
    }
}
