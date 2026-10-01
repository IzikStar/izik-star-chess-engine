package GUI;

import java.awt.*;

/** A piece image sliding from one pixel position to another over {@code duration} ms. */
public class ChessAnimation {
    private final Image sprite;
    /** Board square (0..63) the piece is travelling to; the board skips drawing it meanwhile. */
    private final int targetSquare;
    private final int startX, startY, endX, endY, duration;
    private final long startTime;
    private boolean finished;

    public ChessAnimation(Image sprite, int targetSquare, int startX, int startY, int endX, int endY, int duration) {
        this.sprite = sprite;
        this.targetSquare = targetSquare;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
    }

    public void paint(Graphics2D g2d) {
        long elapsed = System.currentTimeMillis() - startTime;
        int x = endX;
        int y = endY;
        if (elapsed >= duration) {
            finished = true;
        } else {
            float progress = (float) elapsed / duration;
            x = startX + Math.round((endX - startX) * progress);
            y = startY + Math.round((endY - startY) * progress);
        }
        g2d.drawImage(sprite, x, y, null);
    }

    public int targetSquare() {
        return targetSquare;
    }

    public boolean isFinished() {
        return finished || System.currentTimeMillis() - startTime >= duration;
    }
}
