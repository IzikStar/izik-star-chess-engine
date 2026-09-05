package GUI;

import pieces.Piece;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ChessAnimation {
    private Piece piece;
    private int startX, startY, endX, endY, duration;
    private long startTime;
    private boolean finished;

    public ChessAnimation(Piece piece, int startX, int startY, int endX, int endY, int duration) {
        this.piece = piece;
        this.startX = startX;
        this.startY = startY;
        this.endX = endX;
        this.endY = endY;
        this.duration = duration;
        this.startTime = System.currentTimeMillis();
        this.finished = false;
    }

    public void paint(Graphics2D g2d) {
        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= duration) {
            piece.xPos = endX;
            piece.yPos = endY;
            finished = true;
        } else {
            float progress = (float) elapsed / duration;
            int x = startX + Math.round((endX - startX) * progress);
            int y = startY + Math.round((endY - startY) * progress);
            piece.xPos = x;
            piece.yPos = y;
        }
        piece.paint(g2d);
    }

    public boolean isFinished() {
        return finished;
    }


}
