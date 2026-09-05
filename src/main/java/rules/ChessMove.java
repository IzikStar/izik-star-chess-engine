package rules;

import java.util.Objects;

/**
 * An immutable move: from / to square indices (see {@link Square}) plus an optional promotion
 * piece. The wire form is UCI ({@code "e2e4"}, {@code "e7e8q"}), the interchange format the
 * Phase 2 rules API commits to.
 */
public final class ChessMove {

    private final int from;
    private final int to;
    /** {@code 'q'}, {@code 'r'}, {@code 'b'}, {@code 'n'}, or {@code 0} for no promotion. */
    private final char promotion;

    public ChessMove(int from, int to, char promotion) {
        this.from = from;
        this.to = to;
        this.promotion = promotion == 0 ? 0 : Character.toLowerCase(promotion);
    }

    public ChessMove(int from, int to) {
        this(from, to, (char) 0);
    }

    public static ChessMove fromUci(String uci) {
        if (uci == null || (uci.length() != 4 && uci.length() != 5)) {
            throw new IllegalArgumentException("bad UCI move: " + uci);
        }
        int from = Square.fromName(uci.substring(0, 2));
        int to = Square.fromName(uci.substring(2, 4));
        char promo = uci.length() == 5 ? Character.toLowerCase(uci.charAt(4)) : 0;
        return new ChessMove(from, to, promo);
    }

    public int from() {
        return from;
    }

    public int to() {
        return to;
    }

    public char promotion() {
        return promotion;
    }

    public boolean isPromotion() {
        return promotion != 0;
    }

    public String toUci() {
        String s = Square.name(from) + Square.name(to);
        return promotion == 0 ? s : s + promotion;
    }

    @Override
    public String toString() {
        return toUci();
    }

    @Override
    public boolean equals(Object o) {
        if (!(o instanceof ChessMove m)) {
            return false;
        }
        return from == m.from && to == m.to && promotion == m.promotion;
    }

    @Override
    public int hashCode() {
        return Objects.hash(from, to, promotion);
    }
}
