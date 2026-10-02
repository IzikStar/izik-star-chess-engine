package main;

// מחלקות שלי
import GUI.AudioPlayer;
import GUI.ChessAnimation;
import GUI.PieceSprites;
import game.GameConfig;
import game.GameListener;
import game.GameSession;
import main.savedGames.SavedGamesPanel;
import rules.ChessMove;
import rules.Game;
import rules.GameStatus;
import rules.MoveResult;
import rules.Position;
import rules.Square;
// מחלקות של ג'אווה
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/**
 * The chess board widget. Since Phase 3 it holds no chess rules and no game state of its own: it
 * paints the {@link GameSession}'s current {@link Position}, keeps only view state (selection,
 * drag, hint, last move, animations, orientation), and turns the session's events into
 * animation, sound and the game-over dialog. Moves go in through {@link #tryMove}.
 */
public class Board extends JPanel implements GameListener {

    // משתנים לציור הלוח
    public static final int tileSize = 85;
    public static final int cols = 8;
    public static final int rows = 8;
    private static final int ANIMATION_MS = 500;

    final GameSession session;
    private final SavedGamesPanel savedGamesPanel;
    private final PieceSprites sprites = new PieceSprites(tileSize);
    private final AudioPlayer audioPlayer = new AudioPlayer();
    private final List<ChessAnimation> animations = new ArrayList<>();
    final Input input;

    // מצב התצוגה בלבד
    /** True when White is drawn at the bottom. */
    private boolean whiteAtBottom = true;
    int selectedSquare = -1;
    /** While dragging: the square being dragged from, and the sprite's top-left pixel. */
    int dragSquare = -1, dragX, dragY;
    private int hintFrom = -1, hintTo = -1;
    private int lastFrom = -1, lastTo = -1;
    /** Set while a dragged move is being played, so it is not also animated. */
    private boolean moveWasDragged;
    private Timer flipTimer;

    public Board(GameSession session, SavedGamesPanel savedGamesPanel) {
        this.session = session;
        this.savedGamesPanel = savedGamesPanel;
        this.setPreferredSize(new Dimension(cols * tileSize, rows * tileSize));
        this.input = new Input(this);
        this.addMouseListener(input);
        this.addMouseMotionListener(input);
        session.addListener(this);
        updateOrientation();

        Timer animationTimer = new Timer(15, e -> {
            if (!animations.isEmpty()) {
                repaint();
            }
        });
        animationTimer.start();
    }

    // ---- painting -----------------------------------------------------------

    @Override
    public void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2d = (Graphics2D) g;
        Position position = session.position();

        // paint board
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g2d.setColor((c + r) % 2 != 0 ? new Color(152, 97, 42) : new Color(208, 182, 164));
                g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
            }
        }

        // paint last move
        fillSquare(g2d, lastFrom, new Color(72, 255, 0, 158));
        fillSquare(g2d, lastTo, new Color(55, 255, 0, 186));

        // paint engine hint
        fillSquare(g2d, hintFrom, new Color(0, 255, 215, 158));
        fillSquare(g2d, hintTo, new Color(47, 206, 255, 237));

        // paint the border of the king red if it's under attack
        GameStatus status = session.status();
        if (status == GameStatus.CHECK || status == GameStatus.CHECKMATE) {
            int king = findKing(position, position.whiteToMove());
            if (king >= 0) {
                drawRedBorder(g2d, king);
            }
        }

        // paint highlights: the selected piece and where it can go
        if (selectedSquare >= 0) {
            fillSquare(g2d, selectedSquare, new Color(0, 0, 255, 128)); // כחול חצי שקוף
            for (ChessMove move : session.legalMoves()) {
                if (move.from() != selectedSquare) {
                    continue;
                }
                if (position.pieceAt(move.to()) == 0) {
                    // ציור עיגול במרכז הריבוע
                    g2d.setColor(new Color(72, 255, 0, 158));
                    int diameter = tileSize / 3;
                    g2d.fillOval(xOf(move.to()) + (tileSize - diameter) / 2,
                            yOf(move.to()) + (tileSize - diameter) / 2, diameter, diameter);
                } else {
                    drawRedBorder(g2d, move.to());
                }
            }
        }

        // paint pieces (except those still sliding in, and the one being dragged)
        for (int sq = 0; sq < 64; sq++) {
            char piece = position.pieceAt(sq);
            if (piece == 0 || sq == dragSquare || isAnimatingTo(sq)) {
                continue;
            }
            g2d.drawImage(sprites.get(piece), xOf(sq), yOf(sq), null);
        }

        Iterator<ChessAnimation> it = animations.iterator();
        while (it.hasNext()) {
            ChessAnimation animation = it.next();
            animation.paint(g2d);
            if (animation.isFinished()) {
                it.remove();
            }
        }

        if (dragSquare >= 0 && position.pieceAt(dragSquare) != 0) {
            g2d.drawImage(sprites.get(position.pieceAt(dragSquare)), dragX, dragY, null);
        }
    }

    private boolean isAnimatingTo(int square) {
        for (ChessAnimation a : animations) {
            if (a.targetSquare() == square && !a.isFinished()) {
                return true;
            }
        }
        return false;
    }

    private void fillSquare(Graphics2D g, int square, Color color) {
        if (square < 0) {
            return;
        }
        g.setColor(color);
        g.fillRect(xOf(square), yOf(square), tileSize, tileSize);
    }

    private void drawRedBorder(Graphics g, int square) {
        int x = xOf(square);
        int y = yOf(square);
        int borderThickness = 5;

        g.setColor(Color.RED);
        g.fillRect(x, y, tileSize, borderThickness); // Top
        g.fillRect(x, y, borderThickness, tileSize); // Left
        g.fillRect(x + tileSize - borderThickness, y, borderThickness, tileSize); // Right
        g.fillRect(x, y + tileSize - borderThickness, tileSize, borderThickness); // Bottom
    }

    private static int findKing(Position position, boolean white) {
        char king = white ? 'K' : 'k';
        for (int sq = 0; sq < 64; sq++) {
            if (position.pieceAt(sq) == king) {
                return sq;
            }
        }
        return -1;
    }

    // ---- coordinates (טיפול בהפיכת הלוח) ------------------------------------------

    public int xOf(int square) {
        int col = Square.file(square);
        return (whiteAtBottom ? col : cols - 1 - col) * tileSize;
    }

    public int yOf(int square) {
        int row = Square.rank8Row(square);
        return (whiteAtBottom ? row : rows - 1 - row) * tileSize;
    }

    /** The square under a pixel, or -1 off the board. */
    public int squareAt(int x, int y) {
        int col = x / tileSize;
        int row = y / tileSize;
        if (x < 0 || y < 0 || col >= cols || row >= rows) {
            return -1;
        }
        if (!whiteAtBottom) {
            col = cols - 1 - col;
            row = rows - 1 - row;
        }
        return Square.of(col, row);
    }

    public boolean isWhiteAtBottom() {
        return whiteAtBottom;
    }

    /** White at the bottom for a White human; in two-player mode, the side to move. */
    private void updateOrientation() {
        GameConfig config = session.config();
        switch (config.mode()) {
            case HUMAN_VS_ENGINE -> whiteAtBottom = config.humanPlaysWhite();
            case HUMAN_VS_HUMAN -> whiteAtBottom = session.whiteToMove();
            case ENGINE_VS_ENGINE -> { /* keep */ }
        }
    }

    // ---- input from the mouse ----------------------------------------------

    /** True if the human may pick up the piece on {@code square} now. */
    boolean canSelect(int square) {
        if (square < 0 || !session.isHumanTurn()) {
            return false;
        }
        char piece = session.position().pieceAt(square);
        return piece != 0 && Character.isUpperCase(piece) == session.whiteToMove();
    }

    boolean isLegalTarget(int from, int to) {
        for (ChessMove m : session.legalMoves()) {
            if (m.from() == from && m.to() == to) {
                return true;
            }
        }
        return false;
    }

    /**
     * Plays the human move {@code from -> to} (asking for the promotion piece if needed).
     * Returns false if it was not played (illegal, or the promotion dialog was cancelled).
     */
    boolean tryMove(int from, int to, boolean dragged) {
        if (!isLegalTarget(from, to)) {
            return false;
        }
        ChessMove move = new ChessMove(from, to);
        if (Game.isPromotionMove(session.position(), move)) {
            PromotionDialog dialog = new PromotionDialog(
                    (JFrame) SwingUtilities.getWindowAncestor(this), session.whiteToMove());
            String choice = dialog.getSelection();
            if (choice == null) {
                return false;
            }
            move = new ChessMove(from, to, choice.charAt(0));
        }
        moveWasDragged = dragged;
        try {
            return session.playHumanMove(move) != null;
        } finally {
            moveWasDragged = false;
        }
    }

    void playSelectSound() {
        audioPlayer.playSelectPieceSound();
    }

    void playInvalidMoveSound() {
        audioPlayer.playInvalidMoveSound();
    }

    void clearHint() {
        hintFrom = hintTo = -1;
    }

    // ---- buttons -------------------------------------------------------------

    // שינוי מצב הלוח שלא באמצעות ביצוע מהלך
    public void goBack() {
        if (session.isHumanTurn() || session.isOver()) {
            if (session.undo()) {
                audioPlayer.playGoBackSound();
            }
        } else {
            System.out.println("doing nothing");
        }
    }

    public void restart() {
        audioPlayer.playHintSound();
        session.newGame();
    }

    // ---- session events --------------------------------------------------

    @Override
    public void moveMade(MoveResult move, boolean byEngine) {
        selectedSquare = -1;
        dragSquare = -1;
        clearHint();
        lastFrom = move.move().from();
        lastTo = move.move().to();

        Position after = Position.fromFen(move.fenAfter());
        if (!moveWasDragged) {
            animate(after.pieceAt(move.move().to()), move.move().from(), move.move().to());
        }
        if (move.castling()) {
            int row = Square.rank8Row(move.move().to());
            boolean kingSide = Square.file(move.move().to()) > Square.file(move.move().from());
            int rookFrom = Square.of(kingSide ? 7 : 0, row);
            int rookTo = Square.of(kingSide ? 5 : 3, row);
            animate(after.pieceAt(rookTo), rookFrom, rookTo);
            audioPlayer.playCastlingSound();
        }

        if (move.isCapture()) {
            audioPlayer.playCaptureSound();
        } else {
            audioPlayer.playMovingPieceSound();
        }
        if (move.isPromotion()) {
            later(ANIMATION_MS, audioPlayer::playCastlingSound);
        }
        playStatusSound(move.status());

        savedGamesPanel.addMove(MoveListText.of(move));
        ShowScore.update(after, session.config());

        if (session.config().mode() == GameConfig.Mode.HUMAN_VS_HUMAN && !move.status().isGameOver()) {
            // two players: after a moment, turn the board to the side to move
            if (flipTimer != null) {
                flipTimer.stop();
            }
            flipTimer = later(ANIMATION_MS, () -> {
                audioPlayer.playSwitchSound();
                flipTimer = later(ANIMATION_MS, () -> {
                    updateOrientation();
                    repaint();
                });
            });
        }
        repaint();
    }

    private void playStatusSound(GameStatus status) {
        switch (status) {
            case CHECKMATE -> {
                boolean matedSideIsHuman = session.config().mode() == GameConfig.Mode.HUMAN_VS_ENGINE
                        && session.config().isHuman(session.whiteToMove());
                if (matedSideIsHuman) {
                    audioPlayer.playLosingSound();
                } else {
                    audioPlayer.playCheckMateSound();
                }
            }
            case STALEMATE, DRAW_FIFTY_MOVE, DRAW_INSUFFICIENT_MATERIAL, DRAW_THREEFOLD -> audioPlayer.playDrawSound();
            case CHECK -> audioPlayer.playCheckSound();
            case IN_PROGRESS -> { }
        }
    }

    @Override
    public void gameOver(MoveResult lastMove) {
        // after the board has painted the final move
        SwingUtilities.invokeLater(() -> Main.showEndGameMessage(
                (JFrame) SwingUtilities.getWindowAncestor(this),
                endMessage(lastMove.status(), Position.fromFen(lastMove.fenAfter()).whiteToMove())));
    }

    static String endMessage(GameStatus status, boolean whiteToMove) {
        return switch (status) {
            case CHECKMATE -> whiteToMove ? "שחמט!!! שחור ניצח" : "שחמט!!! לבן ניצח!";
            case STALEMATE -> "פת. ליריב אין מהלכים חוקיים. המשחק נגמר בתיקו";
            default -> "המשחק נגמר בתיקו.";
        };
    }

    @Override
    public void positionReset() {
        selectedSquare = -1;
        dragSquare = -1;
        clearHint();
        animations.clear();
        List<MoveResult> moves = session.moves();
        if (moves.isEmpty()) {
            lastFrom = lastTo = -1;
        } else {
            MoveResult last = moves.get(moves.size() - 1);
            lastFrom = last.move().from();
            lastTo = last.move().to();
        }
        savedGamesPanel.newGame();
        for (MoveResult move : moves) {
            savedGamesPanel.addMove(MoveListText.of(move));
        }
        ShowScore.update(session.position(), session.config());
        updateOrientation();
        repaint();
    }

    @Override
    public void configChanged(GameConfig config) {
        selectedSquare = -1;
        updateOrientation();
        ShowScore.update(session.position(), config);
        repaint();
    }

    @Override
    public void hint(ChessMove move) {
        hintFrom = move.from();
        hintTo = move.to();
        audioPlayer.playHintSound();
        repaint();
    }

    // ---- helpers ---------------------------------------------------------------

    private void animate(char piece, int from, int to) {
        if (piece == 0) {
            return;
        }
        animations.add(new ChessAnimation(sprites.get(piece), to, xOf(from), yOf(from), xOf(to), yOf(to), ANIMATION_MS));
    }

    private static Timer later(int ms, Runnable action) {
        Timer timer = new Timer(ms, e -> action.run());
        timer.setRepeats(false);
        timer.start();
        return timer;
    }
}
