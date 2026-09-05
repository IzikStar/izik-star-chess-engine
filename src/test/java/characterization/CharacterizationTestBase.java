package characterization;

import ai.BitBoard.BitBoard;
import ai.BoardState;
import main.Board;
import main.setting.ChoosePlayFormat;
import main.setting.SettingPanel;
import main.savedGames.SavedStatesForDraws;
import org.junit.jupiter.api.BeforeEach;

/**
 * Phase 0 characterization tests — see docs/phase-0-notes.md.
 *
 * <p>These tests pin down what the two rule engines actually do TODAY (bugs included).
 * They do not change production logic. Tests tagged {@code "known-bug"} assert the
 * <em>correct</em> chess answer and are expected to fail until a later phase fixes the
 * engine; run them with {@code mvn test -Pknown-bugs}.
 *
 * <p>Two independent rule paths are exercised:
 * <ul>
 *   <li><b>OO path</b> — {@link BoardState} + {@code main.CheckScanner}
 *       ({@code getAllPossibleMovesForASide}, {@code getAccurateStatus}, {@code getIsCheck}).</li>
 *   <li><b>Bitboard path</b> — {@link BitBoard} ({@code getNextStates}, {@code getStatus}),
 *       the representation the minimax search runs on.</li>
 * </ul>
 */
public abstract class CharacterizationTestBase {

    // ---- Well-known positions -------------------------------------------------

    protected static final String START =
            "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";

    /** 1.f3 e5 2.g4 Qh4# — White is to move and checkmated. */
    protected static final String FOOLS_MATE =
            "rnb1kbnr/pppp1ppp/8/4p3/6Pq/5P2/PPPPP2P/RNBQKBNR w KQkq - 1 3";

    /** Rook on a8 mates the castled Black king; Black is to move and checkmated. */
    protected static final String BACK_RANK_MATE =
            "R5k1/5ppp/8/8/8/8/8/6K1 b - - 0 1";

    /** Kf6 + Qf7 vs Kh8; Black is to move, not in check, and has no legal move. */
    protected static final String STALEMATE =
            "7k/5Q2/5K2/8/8/8/8/8 b - - 0 1";

    /** Both sides still have both rooks and the king on its home square. */
    protected static final String CASTLING_OPEN =
            "r3k2r/8/8/8/8/8/8/R3K2R w KQkq - 0 1";

    /** Black has just played ...f7-f5; White can take e5xf6 e.p. */
    protected static final String EN_PASSANT =
            "rnbqkbnr/ppp1p1pp/8/3pPp2/8/8/PPPP1PPP/RNBQKBNR w KQkq f6 0 3";

    /** White pawn on a7, both kings far away — a7-a8 promotes. */
    protected static final String PROMOTION =
            "8/P6k/8/8/8/8/8/7K w - - 0 1";

    // ---- Fixtures -----------------------------------------------------------

    /**
     * The engines read mutable global UI settings ({@code ChoosePlayFormat},
     * {@code SettingPanel.skillLevel}) and a static repetition history
     * ({@code SavedStatesForDraws}). Reset them before every test so ordering
     * cannot leak state between cases.
     */
    @BeforeEach
    void resetGlobals() {
        // The stock application defaults (see main.setting.ChoosePlayFormat field initializers).
        ChoosePlayFormat.isPlayingWhite = true;
        ChoosePlayFormat.isEnginePlayingBlack = true;
        ChoosePlayFormat.isOnePlayer = true;
        ChoosePlayFormat.isComputersGame = false;
        ChoosePlayFormat.setSkillLevel = 0;
        SettingPanel.skillLevel = 0;
        SavedStatesForDraws.clear();
        Board.selectedPiece = null; // CheckScanner reads/writes this static mid-algorithm
    }

    // ---- Helpers ----------------------------------------------------------

    protected static BoardState oo(String fen) {
        return new BoardState(fen, null);
    }

    protected static BitBoard bit(String fen) {
        return new BitBoard(new BoardState(fen, null));
    }

    /** Legal moves for the side to move, via the object-oriented rule path. */
    protected static int legalMovesOO(String fen) {
        return oo(fen).getAllPossibleMovesForASide().length;
    }

    /** Legal moves for the side to move, via the bitboard rule path. */
    protected static int legalMovesBit(String fen) {
        return bit(fen).getNextStates().size();
    }
}
