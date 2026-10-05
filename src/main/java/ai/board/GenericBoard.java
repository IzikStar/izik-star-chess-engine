package ai.board;

import ai.piece.CompiledPiece;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A position on a board whose pieces are data (Phase 6 R3, docs/phase-6-research.md): one bitboard
 * per player and piece type, moves generated from the compiled {@link ai.piece.PieceType}s, castling,
 * en passant and promotion read from the {@link BoardRules}. With {@link BoardRules#CHESS} it plays
 * standard chess and offers the {@link ChessPosition} view the chess evaluation reads.
 *
 * <p>Copy-make: a move makes a new position; a position keeps its children until released.
 */
public final class GenericBoard implements ChessPosition {

    private final BoardRules rules;
    /** [player * types + type]. */
    private final long[] pieces;
    private final long[] occupied;
    private final int side;
    /** Bit {@code i}: castling {@code rules.castlings().get(i)} is still allowed. */
    private final int rights;
    /** Bit {@code player}: that player has castled. */
    private final int castled;
    private final int ep;
    /** Pieces with first-move atoms that have not moved yet. */
    private final long unmoved;
    private final int halfmove;
    private final int fullmove;

    /** The move that made this position, and how it ranks before searching it. */
    private int lastMove = Move.NONE;
    private int moveValue;
    /** For {@link #captureScore}: the moved type, the captured type (-1), whether it promoted to the first choice. */
    private int movedType = -1;
    private int capturedType = -1;
    private boolean promotedToFirst;
    /** Castling, en passant or a promotion: the quick check tests in {@link #addIfLegal} do not cover it. */
    private boolean special;
    private List<GenericBoard> children;

    private GenericBoard(BoardRules rules, long[] pieces, int side, int rights, int castled, int ep, long unmoved,
                         int halfmove, int fullmove) {
        this(rules, pieces, occupiedOf(rules, pieces), side, rights, castled, ep, unmoved, halfmove, fullmove);
    }

    private GenericBoard(BoardRules rules, long[] pieces, long[] occupied, int side, int rights, int castled, int ep,
                         long unmoved, int halfmove, int fullmove) {
        this.rules = rules;
        this.pieces = pieces;
        this.occupied = occupied;
        this.side = side;
        this.rights = rights;
        this.castled = castled;
        this.ep = ep;
        this.unmoved = unmoved;
        this.halfmove = halfmove;
        this.fullmove = fullmove;
    }

    private static long[] occupiedOf(BoardRules rules, long[] pieces) {
        int n = rules.types.size();
        long[] occupied = new long[2];
        for (int player = 0; player < 2; player++) {
            for (int t = 0; t < n; t++) {
                occupied[player] |= pieces[player * n + t];
            }
        }
        return occupied;
    }

    /** The position {@code fen} played by {@code rules}. */
    public static GenericBoard fromFen(BoardRules rules, String fen) {
        String[] f = fen.trim().split("\\s+");
        int n = rules.types.size();
        int[] placement = rules.parsePlacement(f[0]);
        long[] pieces = new long[2 * n];
        long unmoved = 0;
        for (int sq = 0; sq < placement.length; sq++) {
            int p = placement[sq];
            if (p >= 0) {
                pieces[p] |= 1L << sq;
                int player = p / n;
                int type = p % n;
                if (rules.firstMoveAtoms[type] && (rules.startSquares[player][type] & 1L << sq) != 0) {
                    unmoved |= 1L << sq;
                }
            }
        }
        int side = f.length > 1 && f[1].equals("b") ? 1 : 0;
        int rights = 0;
        String castling = f.length > 2 ? f[2] : "-";
        for (BoardRules.Castling c : rules.castlings) {
            if (castling.indexOf(c.letter()) >= 0) {
                rights |= 1 << c.right();
            }
        }
        int ep = -1;
        if (f.length > 3 && !f[3].equals("-")) {
            int col = f[3].charAt(0) - 'a';
            int rank = Integer.parseInt(f[3].substring(1));
            ep = rules.grid.square(rules.grid.height() - rank, col);
        }
        int half = f.length > 4 ? Integer.parseInt(f[4]) : 0;
        int full = f.length > 5 ? Integer.parseInt(f[5]) : 1;
        return new GenericBoard(rules, pieces, side, rights, 0, ep, unmoved, half, full);
    }

    public BoardRules rules() {
        return rules;
    }

    // ---- Board ------------------------------------------------------------------------------

    @Override
    public int sideToMove() {
        return side;
    }

    @Override
    public List<GenericBoard> children() {
        if (children == null) {
            children = generate(false);
        }
        return Collections.unmodifiableList(children);
    }

    @Override
    public List<Board> orderedChildren() {
        ArrayList<Board> sorted = new ArrayList<>(children());
        sorted.sort((a, b) -> Integer.compare(((GenericBoard) b).moveValue, ((GenericBoard) a).moveValue));
        return sorted;
    }

    @Override
    public void releaseChildren() {
        children = null;
    }

    @Override
    public int lastMove() {
        return lastMove;
    }

    @Override
    public int captureScore(Board parent) { // the child knows what it captured; the parent is not needed
        int attacker = rules.roughValue[movedType];
        int victim = capturedType >= 0 ? rules.roughValue[capturedType] : 0;
        if (promotedToFirst) {
            victim += rules.roughValue[rules.promotesTo[movedType][0]];
        }
        if (victim == 0) {
            return -1;
        }
        return victim * 16 + 15 - Math.min(attacker, 10); // a royal piece (1000) attacks last
    }

    @Override
    public boolean inCheck() {
        return inCheck(side);
    }

    @Override
    public boolean isOver() {
        return !hasLegalMove() || halfmove >= 100;
    }

    @Override
    public long repetitionKey() {
        long key = side == 1 ? rules.blackToMove : 0;
        int n = rules.types.size();
        for (int player = 0; player < 2; player++) {
            for (int t = 0; t < n; t++) {
                for (long b = pieces[player * n + t]; b != 0; b &= b - 1) {
                    key ^= rules.zobrist[player][t][Long.numberOfTrailingZeros(b)];
                }
            }
        }
        return key;
    }

    @Override
    public long searchKey() {
        long key = repetitionKey();
        for (int i = 0; i < rules.rightKeys.length; i++) {
            if ((rights & 1 << i) != 0) {
                key ^= rules.rightKeys[i];
            }
        }
        for (int player = 0; player < 2; player++) {
            if ((castled & 1 << player) != 0) {
                key ^= rules.castledKeys[player];
            }
        }
        if (ep >= 0) {
            key ^= rules.epKeys[ep];
        }
        return key ^ fullmove * 0x9E3779B97F4A7C15L; // the evaluation has opening-only terms
    }

    @Override
    public String toFen() {
        int n = rules.types.size();
        StringBuilder sb = new StringBuilder();
        for (int row = 0; row < rules.grid.height(); row++) {
            int empty = 0;
            for (int col = 0; col < rules.grid.width(); col++) {
                long bit = 1L << rules.grid.square(row, col);
                char ch = 0;
                for (int i = 0; i < 2 * n && ch == 0; i++) {
                    if ((pieces[i] & bit) != 0) {
                        char letter = rules.types.get(i % n).letter();
                        ch = i < n ? letter : Character.toLowerCase(letter);
                    }
                }
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
            if (row < rules.grid.height() - 1) {
                sb.append('/');
            }
        }
        sb.append(side == 0 ? " w " : " b ");
        StringBuilder castling = new StringBuilder();
        for (BoardRules.Castling c : rules.castlings) {
            if ((rights & 1 << c.right()) != 0) {
                castling.append(c.letter());
            }
        }
        sb.append(castling.isEmpty() ? "-" : castling);
        sb.append(' ').append(ep < 0 ? "-" : squareName(ep));
        sb.append(' ').append(halfmove).append(' ').append(fullmove);
        return sb.toString();
    }

    private String squareName(int sq) {
        return "" + (char) ('a' + rules.grid.col(sq)) + (rules.grid.height() - rules.grid.row(sq));
    }

    // ---- ChessPosition ----------------------------------------------------------------------

    @Override
    public long pieces(int player, int type) {
        return pieces[player * rules.types.size() + type];
    }

    @Override
    public long occupied(int player) {
        return occupied[player];
    }

    @Override
    public long attackedBy(int player) {
        int n = rules.types.size();
        long all = occupied[0] | occupied[1];
        long squares = 0;
        for (int t = 0; t < n; t++) {
            CompiledPiece piece = rules.compiled[player][t];
            for (long b = pieces[player * n + t]; b != 0; b &= b - 1) {
                int sq = Long.numberOfTrailingZeros(b);
                squares |= piece.captureTargets(sq, all, (unmoved & 1L << sq) != 0);
            }
        }
        return squares;
    }

    @Override
    public boolean inCheck(int player) {
        int n = rules.types.size();
        for (int t = 0; t < n; t++) {
            if (rules.royal[t]) {
                for (long b = pieces[player * n + t]; b != 0; b &= b - 1) {
                    if (attacked(Long.numberOfTrailingZeros(b), 1 - player)) {
                        return true;
                    }
                }
            }
        }
        return false;
    }

    @Override
    public boolean hasLegalMove() {
        if (children != null) {
            return !children.isEmpty();
        }
        return !generate(true).isEmpty();
    }

    @Override
    public int halfmoveClock() {
        return halfmove;
    }

    @Override
    public int fullmoveNumber() {
        return fullmove;
    }

    @Override
    public boolean canCastle(int player, boolean kingSide) {
        for (BoardRules.Castling c : rules.castlings) {
            if (c.player() == player && c.kingSide() == kingSide) {
                return (rights & 1 << c.right()) != 0;
            }
        }
        return false;
    }

    @Override
    public boolean hasCastled(int player) {
        return (castled & 1 << player) != 0;
    }

    // ---- attacks ----------------------------------------------------------------------------

    /**
     * Is {@code sq} attacked by {@code by}? A piece of {@code by} attacks {@code sq} exactly when the
     * same piece of the other player, standing on {@code sq}, would attack it: the other player's
     * offsets are the same turned half a circle, and a slide is blocked the same way both ways.
     */
    private boolean attacked(int sq, int by) {
        int n = rules.types.size();
        long all = occupied[0] | occupied[1];
        for (int t = 0; t < n; t++) {
            long own = pieces[by * n + t];
            if (own == 0) {
                continue;
            }
            CompiledPiece mirror = rules.compiled[1 - by][t];
            if ((mirror.reach(sq) & own) == 0) {
                continue; // none of them could reach sq even on an empty board
            }
            if ((mirror.captureTargets(sq, all, false) & own) != 0) {
                return true;
            }
            if (rules.firstMoveAtoms[t] && (own & unmoved) != 0
                    && (mirror.captureTargets(sq, all, true) & own & unmoved) != 0) {
                return true;
            }
        }
        return false;
    }

    // ---- move generation --------------------------------------------------------------------

    /**
     * The legal children, piece type by type, squares in order; promotions in the order the piece
     * lists them; castling after the king's other moves. With {@code firstOnly}, stops at the first.
     */
    private List<GenericBoard> generate(boolean firstOnly) {
        List<GenericBoard> out = new ArrayList<>();
        int n = rules.types.size();
        long own = occupied[side];
        long enemy = occupied[1 - side];
        long all = own | enemy;
        long ownExposure = inCheck(side) ? -1L : exposure(side);
        long enemyExposure = exposure(1 - side);
        for (int t = 0; t < n; t++) {
            CompiledPiece piece = rules.compiled[side][t];
            for (long b = pieces[side * n + t]; b != 0; b &= b - 1) {
                int from = Long.numberOfTrailingZeros(b);
                boolean first = (unmoved & 1L << from) != 0;
                long targets = piece.targets(from, all, enemy, first);
                if (rules.enPassant[t] && ep >= 0 && (piece.captureTargets(from, all, first) & 1L << ep) != 0
                        && (all & 1L << ep) == 0) {
                    targets |= 1L << ep;
                }
                for (; targets != 0; targets &= targets - 1) {
                    int to = Long.numberOfTrailingZeros(targets);
                    if (rules.promotesTo[t].length > 0 && (rules.promotionRow[side] & 1L << to) != 0) {
                        for (int i = 0; i < rules.promotesTo[t].length; i++) {
                            if (addIfLegal(out, ownExposure, enemyExposure, play(t, from, to, rules.promotesTo[t][i], i == 0, null))
                                    && firstOnly) {
                                return out;
                            }
                        }
                    } else if (addIfLegal(out, ownExposure, enemyExposure, play(t, from, to, -1, false, null)) && firstOnly) {
                        return out;
                    }
                }
            }
            if (rules.types.get(t).castling() == ai.piece.PieceType.Castling.KING) {
                for (BoardRules.Castling c : rules.castlings) {
                    if (c.player() == side && (rights & 1 << c.right()) != 0
                            && (pieces[side * n + t] & 1L << c.kingFrom()) != 0
                            && (pieces[side * n + rules.rookType] & 1L << c.rookFrom()) != 0
                            && (all & c.empty()) == 0 && !anyAttacked(c.safe(), 1 - side)) {
                        if (addIfLegal(out, ownExposure, enemyExposure, play(t, c.kingFrom(), c.kingTo(), -1, false, c)) && firstOnly) {
                            return out;
                        }
                    }
                }
            }
        }
        return out;
    }

    /**
     * The squares between {@code player}'s royal pieces and the sliders of the other player that
     * could reach them on an empty board: only a piece leaving one of these can uncover an attack on
     * them. A slide is blocked the same both ways, so the other player's slides seen from the royal
     * square are the lines along which it can be attacked.
     */
    private long exposure(int player) {
        int n = rules.types.size();
        int by = 1 - player;
        long lines = 0;
        for (int r = 0; r < n; r++) {
            if (!rules.royal[r]) {
                continue;
            }
            for (long b = pieces[player * n + r]; b != 0; b &= b - 1) {
                int sq = Long.numberOfTrailingZeros(b);
                for (int t = 0; t < n; t++) {
                    long reach = rules.compiled[player][t].slideReach(sq);
                    if ((reach & pieces[by * n + t]) != 0) {
                        lines |= reach;
                    }
                }
            }
        }
        return lines;
    }

    private boolean anyAttacked(long squares, int by) {
        for (long b = squares; b != 0; b &= b - 1) {
            if (attacked(Long.numberOfTrailingZeros(b), by)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Keeps {@code child} when the mover's royal pieces are safe; rates a check first. The full
     * attack test runs only when the move could matter: a royal piece moved, the mover was in check
     * ({@code ownExposure} all ones), the piece left a line a slider could attack along, or the move
     * is special; a check is either the moved piece's own attack or uncovered the same way.
     */
    private boolean addIfLegal(List<GenericBoard> out, long ownExposure, long enemyExposure, GenericBoard child) {
        long from = 1L << Move.from(child.lastMove);
        if ((child.special || rules.royal[child.movedType] || (ownExposure & from) != 0) && child.inCheck(side)) {
            return false;
        }
        if (child.special || (enemyExposure & from) != 0 || givesDirectCheck(child)) {
            if (child.inCheck(child.side)) {
                child.moveValue = Integer.MAX_VALUE;
            }
        }
        out.add(child);
        return true;
    }

    /** Whether the piece {@code child}'s move put down attacks one of the opponent's royal pieces. */
    private boolean givesDirectCheck(GenericBoard child) {
        int n = rules.types.size();
        int other = 1 - side;
        long royals = 0;
        for (int t = 0; t < n; t++) {
            if (rules.royal[t]) {
                royals |= child.pieces[other * n + t];
            }
        }
        int to = Move.to(child.lastMove);
        CompiledPiece piece = rules.compiled[side][child.movedType];
        if ((piece.reach(to) & royals) == 0) {
            return false;
        }
        long all = child.occupied[0] | child.occupied[1];
        return (piece.captureTargets(to, all, false) & royals) != 0;
    }

    /**
     * The position after the piece of type {@code type} goes from {@code from} to {@code to}, turning
     * into {@code promotion} (-1: stays), or castling {@code castle}.
     */
    private GenericBoard play(int type, int from, int to, int promotion, boolean firstPromotion,
                              BoardRules.Castling castle) {
        int n = rules.types.size();
        long[] next = pieces.clone();
        long fromBit = 1L << from;
        long toBit = 1L << to;
        int value = 0;
        int captured = -1;
        int other = 1 - side;
        long victimBit = toBit;
        if ((occupied[other] & toBit) == 0 && to == ep && rules.enPassant[type]) {
            // en passant: the piece that just double-stepped stands one row behind the square
            victimBit = side == 0 ? toBit << rules.grid.width() : toBit >>> rules.grid.width();
        }
        if ((occupied[other] & victimBit) != 0) {
            for (int t = 0; t < n; t++) {
                if ((next[other * n + t] & victimBit) != 0) {
                    next[other * n + t] &= ~victimBit;
                    captured = t;
                    value += rules.royal[t] ? Integer.MAX_VALUE / 2 : rules.orderValue[t];
                }
            }
        }
        next[side * n + type] &= ~fromBit;
        next[side * n + (promotion >= 0 ? promotion : type)] |= toBit;
        if (promotion >= 0) {
            value += firstPromotion ? 70 : rules.types.get(promotion).value() < 400 ? 30 : 0;
        }
        long[] nextOccupied = new long[2];
        nextOccupied[side] = occupied[side] & ~fromBit | toBit;
        nextOccupied[other] = occupied[other] & ~victimBit;
        int newCastled = castled;
        if (castle != null) {
            long rookFrom = 1L << castle.rookFrom();
            long rookTo = 1L << castle.rookTo();
            next[side * n + rules.rookType] = next[side * n + rules.rookType] & ~rookFrom | rookTo;
            nextOccupied[side] = nextOccupied[side] & ~rookFrom | rookTo;
            newCastled |= 1 << side;
        }
        int newRights = rights;
        for (BoardRules.Castling c : rules.castlings) {
            if ((newRights & 1 << c.right()) == 0) {
                continue;
            }
            boolean kingMoved = c.player() == side && from == c.kingFrom();
            boolean rookGone = (from == c.rookFrom() && c.player() == side) || to == c.rookFrom();
            if (kingMoved || rookGone) {
                newRights &= ~(1 << c.right());
                if (c.player() == side && !kingMoved && from == c.rookFrom()) {
                    value -= c.kingSide() ? 7 : 5;
                }
            }
        }
        if (rules.types.get(type).castling() == ai.piece.PieceType.Castling.KING) {
            value -= 12;
        }
        int newEp = -1;
        if (rules.enPassant[type] && rules.grid.col(from) == rules.grid.col(to)
                && Math.abs(rules.grid.row(from) - rules.grid.row(to)) == 2) {
            newEp = (from + to) / 2;
        }
        // like a pawn move, a move of a piece that promotes cannot be undone
        boolean resets = captured >= 0 || rules.promotesTo[type].length > 0;
        GenericBoard child = new GenericBoard(rules, next, nextOccupied, other, newRights, newCastled, newEp,
                unmoved & ~fromBit & ~toBit & ~victimBit, resets ? 0 : halfmove + 1,
                side == 1 ? fullmove + 1 : fullmove);
        child.lastMove = Move.of(from, to, promotion >= 0 ? Character.toLowerCase(rules.types.get(promotion).letter()) : 0);
        child.moveValue = value;
        child.movedType = type;
        child.capturedType = captured;
        child.promotedToFirst = firstPromotion;
        child.special = castle != null || victimBit != toBit || promotion >= 0;
        return child;
    }

    // ---- quiescence -------------------------------------------------------------------------

    /**
     * Captures (not a more valuable piece taking a defended, less valuable one) and promotions to
     * the first choice: the moves the quiescence search plays, biggest victim first, then cheapest
     * attacker. Ranked as in {@code BitBoard}: move value times 8 plus the type's number.
     */
    @Override
    public List<Board> noisyChildren() {
        List<GenericBoard> out = new ArrayList<>();
        int n = rules.types.size();
        long own = occupied[side];
        long enemy = occupied[1 - side];
        long all = own | enemy;
        long ownExposure = inCheck(side) ? -1L : exposure(side);
        for (int t = 0; t < n; t++) {
            CompiledPiece piece = rules.compiled[side][t];
            boolean promotes = rules.promotesTo[t].length > 0;
            for (long b = pieces[side * n + t]; b != 0; b &= b - 1) {
                int from = Long.numberOfTrailingZeros(b);
                boolean first = (unmoved & 1L << from) != 0;
                long captures = piece.captureTargets(from, all, first) & enemy;
                long targets = captures;
                if (promotes) {
                    targets |= piece.quietTargets(from, all, first) & rules.promotionRow[side];
                }
                for (; targets != 0; targets &= targets - 1) {
                    int to = Long.numberOfTrailingZeros(targets);
                    if ((captures & 1L << to) != 0 && rules.roughValue[t] > roughValueOn(to) && attacked(to, 1 - side)) {
                        continue; // a bigger piece takes a smaller, defended one: it loses material
                    }
                    boolean promotion = promotes && (rules.promotionRow[side] & 1L << to) != 0;
                    addNoisy(out, ownExposure, play(t, from, to, promotion ? rules.promotesTo[t][0] : -1, promotion, null), t);
                }
                if (rules.enPassant[t] && ep >= 0 && (all & 1L << ep) == 0
                        && (piece.captureTargets(from, all, first) & 1L << ep) != 0) {
                    GenericBoard child = play(t, from, ep, -1, false, null);
                    addNoisy(out, ownExposure, child, t);
                }
            }
        }
        out.sort((a, b) -> Integer.compare(b.moveValue, a.moveValue));
        return new ArrayList<>(out);
    }

    private void addNoisy(List<GenericBoard> out, long ownExposure, GenericBoard child, int type) {
        long from = 1L << Move.from(child.lastMove);
        if (!((child.special || rules.royal[type] || (ownExposure & from) != 0) && child.inCheck(side))) {
            child.moveValue = child.moveValue * 8 + type + 1;
            out.add(child);
        }
    }

    private int roughValueOn(int sq) {
        int n = rules.types.size();
        long bit = 1L << sq;
        for (int i = 0; i < 2 * n; i++) {
            if ((pieces[i] & bit) != 0) {
                return rules.roughValue[i % n];
            }
        }
        return 0;
    }
}
