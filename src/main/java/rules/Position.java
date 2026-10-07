package rules;

import java.util.ArrayList;
import java.util.List;

/**
 * An immutable chess position — the headless value type behind the Phase 2 rules API.
 * Parsed from / rendered to FEN, with no Swing/AWT and no dependency on {@code main.*}
 * (unlike {@code ai.BoardState}, which decodes sprite sheets and reads UI globals just to exist).
 *
 * <p>Squares use {@link Square}'s index order (a8 == 0, h1 == 63). The FEN half-move clock is
 * read <em>in full</em> here — {@code ai.BoardState.loadPiecesFromFen} truncates it to its first
 * digit, one of the {@code -Pknown-bugs} reds Phase 2 fixes.
 */
public final class Position {

    public static final String START_FEN =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** {@code board[sq]} = FEN piece letter (upper = white), or {@code 0} for an empty square. */
    private final char[] board;
    private final boolean whiteToMove;
    private final boolean castleWK;
    private final boolean castleWQ;
    private final boolean castleBK;
    private final boolean castleBQ;
    private final int epSquare;
    private final int halfmoveClock;
    private final int fullmoveNumber;
    /** Three-check's "checks still to give" field ("3+3"), or null. */
    private String checks;

    public Position(char[] board, boolean whiteToMove,
                    boolean castleWK, boolean castleWQ, boolean castleBK, boolean castleBQ,
                    int epSquare, int halfmoveClock, int fullmoveNumber) {
        this.board = board;
        this.whiteToMove = whiteToMove;
        this.castleWK = castleWK;
        this.castleWQ = castleWQ;
        this.castleBK = castleBK;
        this.castleBQ = castleBQ;
        this.epSquare = epSquare;
        this.halfmoveClock = halfmoveClock;
        this.fullmoveNumber = fullmoveNumber;
    }

    public static Position fromFen(String fen) {
        String[] p = fen.trim().split("\\s+");
        if (p.length < 4) {
            throw new IllegalArgumentException("bad FEN: " + fen);
        }
        char[] b = new char[64];
        int r = 0;
        int c = 0;
        for (int i = 0; i < p[0].length(); i++) {
            char ch = p[0].charAt(i);
            if (ch == '/') {
                r++;
                c = 0;
            } else if (Character.isDigit(ch)) {
                c += ch - '0';
            } else {
                b[r * 8 + c] = ch;
                c++;
            }
        }
        boolean wtm = p[1].equals("w");
        String cr = p[2];
        int ep = p[3].equals("-") ? Square.NONE : Square.fromName(p[3]);
        int next = 4;
        String checks = null;
        if (p.length > next && p[next].contains("+")) {
            checks = p[next++];
        }
        int half = p.length > next ? Integer.parseInt(p[next]) : 0;
        int full = p.length > next + 1 ? Integer.parseInt(p[next + 1]) : 1;
        Position position = new Position(b, wtm,
                cr.contains("K"), cr.contains("Q"), cr.contains("k"), cr.contains("q"),
                ep, half, full);
        position.checks = checks;
        return position;
    }

    public String toFen() {
        StringBuilder sb = new StringBuilder();
        for (int r = 0; r < 8; r++) {
            int empty = 0;
            for (int c = 0; c < 8; c++) {
                char ch = board[r * 8 + c];
                if (ch == 0) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    sb.append(empty);
                    empty = 0;
                }
                sb.append(ch);
            }
            if (empty > 0) {
                sb.append(empty);
            }
            if (r < 7) {
                sb.append('/');
            }
        }
        sb.append(whiteToMove ? " w " : " b ");
        String cr = "" + (castleWK ? "K" : "") + (castleWQ ? "Q" : "")
                + (castleBK ? "k" : "") + (castleBQ ? "q" : "");
        sb.append(cr.isEmpty() ? "-" : cr);
        sb.append(' ').append(epSquare == Square.NONE ? "-" : Square.name(epSquare));
        if (checks != null) {
            sb.append(' ').append(checks);
        }
        sb.append(' ').append(halfmoveClock);
        sb.append(' ').append(fullmoveNumber);
        return sb.toString();
    }

    public char pieceAt(int sq) {
        return board[sq];
    }

    public boolean whiteToMove() {
        return whiteToMove;
    }

    public int epSquare() {
        return epSquare;
    }

    public int halfmoveClock() {
        return halfmoveClock;
    }

    public int fullmoveNumber() {
        return fullmoveNumber;
    }

    /** Every piece letter currently on the board, in no particular order. */
    public List<Character> pieces() {
        List<Character> out = new ArrayList<>();
        for (char ch : board) {
            if (ch != 0) {
                out.add(ch);
            }
        }
        return out;
    }

    /** The key for threefold repetition: placement + side to move + castling + en-passant (+ checks). */
    public String repetitionKey() {
        String[] f = toFen().split("\\s+");
        return f[0] + " " + f[1] + " " + f[2] + " " + f[3] + (checks == null ? "" : " " + checks);
    }
}
