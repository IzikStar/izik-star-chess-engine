package ai.board;

import ai.piece.CompiledPiece;
import ai.variant.Variant;

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
public final class GenericBoard implements ChessPosition, PieceBoard {

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
    /** With the {@code CHECKS} goal: checks given, player 0 in bits 0-7, player 1 in bits 8-15. */
    private int checks;

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
                         int halfmove, int fullmove, int checks) {
        this(rules, pieces, occupiedOf(rules, pieces), side, rights, castled, ep, unmoved, halfmove, fullmove, checks);
    }

    private GenericBoard(BoardRules rules, long[] pieces, long[] occupied, int side, int rights, int castled, int ep,
                         long unmoved, int halfmove, int fullmove, int checks) {
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
        this.checks = checks;
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
        int next = 4;
        int checks = 0;
        if (f.length > next && f[next].contains("+")) {
            // checks still to give, as Fairy-Stockfish writes them: "3+3" for White's and Black's
            String[] left = f[next++].split("\\+");
            for (int player = 0; player < 2; player++) {
                int given = Math.max(0, rules.checksToWin - Integer.parseInt(left[player]));
                checks |= given << 8 * player;
            }
        }
        int half = f.length > next ? Integer.parseInt(f[next]) : 0;
        int full = f.length > next + 1 ? Integer.parseInt(f[next + 1]) : 1;
        return new GenericBoard(rules, pieces, side, rights, 0, ep, unmoved, half, full, checks);
    }

    public BoardRules rules() {
        return rules;
    }

    @Override
    public Variant variant() {
        return rules.variant;
    }

    @Override
    public int mobility(int player, int type) {
        int n = rules.types.size();
        long all = occupied[0] | occupied[1];
        long enemy = occupied[1 - player];
        CompiledPiece piece = rules.compiled[player][type];
        int count = 0;
        for (long b = pieces[player * n + type]; b != 0; b &= b - 1) {
            int sq = Long.numberOfTrailingZeros(b);
            count += Long.bitCount(piece.targets(sq, all, enemy, (unmoved & 1L << sq) != 0));
        }
        return count;
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

    /** Whether this position still holds its children (for the memory test). */
    boolean holdsChildren() {
        return children != null;
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

    /**
     * The variant's goals first, in their order; then a position without moves: a win with
     * {@code LOSE_EVERYTHING}, checkmate when in check and checkmate wins, else the stalemate rule;
     * then the move limit.
     */
    @Override
    public Outcome outcome() {
        Outcome goal = goalOutcome();
        if (goal != Outcome.ONGOING) {
            return goal;
        }
        if (!hasLegalMove()) {
            if (rules.loseEverything) {
                return Outcome.WIN;
            }
            if (rules.checkmateWins && inCheck(side)) {
                return Outcome.LOSS;
            }
            return switch (rules.stalemate) {
                case DRAW -> Outcome.DRAW;
                case WIN -> Outcome.WIN;
                case LOSS -> Outcome.LOSS;
            };
        }
        return halfmove >= rules.moveLimitPlies ? Outcome.DRAW : Outcome.ONGOING;
    }

    @Override
    public boolean goalReached() {
        return goalMet() != NO_GOAL;
    }

    @Override
    public int goalMet() {
        if (!rules.positionalGoals) {
            return NO_GOAL;
        }
        if (rules.lastStanding && rules.anyRoyal && royals(side) == 0) {
            return ROYALS_GONE;
        }
        for (int i = 0; i < rules.goalKinds.length; i++) {
            boolean met = switch (rules.goalKinds[i]) {
                case CHECKMATE -> false; // needs the moves: outcome() judges it
                case LOSE_EVERYTHING -> occupied[side] == 0;
                case REACH_SQUARES -> (ofTypes(1 - side, rules.goalTypes[i]) & rules.goalSquares[i]) != 0;
                case CHECKS -> checksGiven(1 - side) >= rules.checksToWin;
                case CAPTURE_ALL_OF -> ofTypes(side, rules.goalTypes[i]) == 0;
                case BARE_ROYAL -> (occupied[side] & ~royals(side)) == 0;
            };
            if (met) {
                return i;
            }
        }
        return NO_GOAL;
    }

    /** The game is over by the variant's goal, before anyone looks at the moves; else ONGOING. */
    private Outcome goalOutcome() {
        int goal = goalMet();
        if (goal == NO_GOAL) {
            return Outcome.ONGOING;
        }
        return goal != ROYALS_GONE && rules.goalKinds[goal] == ai.variant.WinCondition.Kind.LOSE_EVERYTHING
                ? Outcome.WIN : Outcome.LOSS;
    }

    /** {@code player}'s pieces of the types in {@code types} (bit per type). */
    private long ofTypes(int player, int types) {
        int n = rules.types.size();
        long squares = 0;
        for (int t = 0; t < n; t++) {
            if ((types & 1 << t) != 0) {
                squares |= pieces[player * n + t];
            }
        }
        return squares;
    }

    @Override
    public boolean repetitionDraws() {
        return rules.repetition;
    }

    /** How many checks {@code player} has given (the {@code CHECKS} goal). */
    public int checksGiven(int player) {
        return checks >>> 8 * player & 0xFF;
    }

    private long royals(int player) {
        int n = rules.types.size();
        long squares = 0;
        for (int t = 0; t < n; t++) {
            if (rules.royal[t]) {
                squares |= pieces[player * n + t];
            }
        }
        return squares;
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
        return key ^ checks * rules.checksKey;
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
        if (rules.checksToWin > 0) {
            sb.append(' ').append(rules.checksToWin - checksGiven(0)).append('+').append(rules.checksToWin - checksGiven(1));
        }
        sb.append(' ').append(halfmove).append(' ').append(fullmove);
        return sb.toString();
    }

    private String squareName(int sq) {
        return rules.grid.name(sq);
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
    public long checkedRoyals() {
        int n = rules.types.size();
        long checked = 0;
        if (!checkApplies(side)) {
            return 0;
        }
        for (int t = 0; t < n; t++) {
            if (rules.royal[t]) {
                for (long b = pieces[side * n + t]; b != 0; b &= b - 1) {
                    int sq = Long.numberOfTrailingZeros(b);
                    if (attacked(sq, 1 - side)) {
                        checked |= 1L << sq;
                    }
                }
            }
        }
        return checked;
    }

    @Override
    public boolean inCheck(int player) {
        if (rules.lastStanding && !checkApplies(player)) {
            return false; // two or more royal pieces: they may be left attacked
        }
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

    /**
     * Whether {@code player}'s royal pieces are held to check: always, unless the royal mode is
     * {@code LAST_STANDING} and the player has two or more of them.
     */
    private boolean checkApplies(int player) {
        return !rules.lastStanding || Long.bitCount(royals(player)) < 2;
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
        if (goalOutcome() != Outcome.ONGOING) {
            return out; // the game is already over
        }
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
            if (rules.castles[t]) {
                for (BoardRules.Castling c : rules.castlings) {
                    if (c.player() == side && c.kingType() == t && (rights & 1 << c.right()) != 0
                            && (pieces[side * n + t] & 1L << c.kingFrom()) != 0
                            && (pieces[side * n + c.rookType()] & 1L << c.rookFrom()) != 0
                            && (all & c.empty()) == 0 && !(c.safe() != 0 && checkApplies(side) && anyAttacked(c.safe(), 1 - side))
                            && (piece.targets(c.kingFrom(), all, enemy, (unmoved & 1L << c.kingFrom()) != 0) & 1L << c.kingTo()) == 0) {
                        // (a castling landing where the piece goes anyway would be the same move: the ordinary one is played)
                        if (addIfLegal(out, ownExposure, enemyExposure, play(t, c.kingFrom(), c.kingTo(), -1, false, c)) && firstOnly) {
                            return out;
                        }
                    }
                }
            }
        }
        if (rules.forcedCapture && out.stream().anyMatch(GenericBoard::isCapture)) {
            out.removeIf(child -> !child.isCapture());
        }
        return out;
    }

    private boolean isCapture() {
        return capturedType >= 0;
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
            next[side * n + castle.rookType()] = next[side * n + castle.rookType()] & ~rookFrom | rookTo;
            // the king may land where the rook stood, so both leave before both land
            nextOccupied[side] = occupied[side] & ~fromBit & ~rookFrom | toBit | rookTo;
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
        if (rules.castles[type]) {
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
                side == 1 ? fullmove + 1 : fullmove, checks);
        if (rules.checksToWin > 0 && child.inCheck(other)) {
            child.checks += 1 << 8 * side;
        }
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
     * attacker. Ranked as the old bitboard did: move value times 8 plus the type's number.
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
