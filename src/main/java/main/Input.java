package main;

import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

/**
 * Mouse handling for the {@link Board}: click a piece then a square, or drag a piece. It only
 * tracks the gesture; legality and the move itself go through the board's game session.
 */
public class Input extends MouseAdapter {

    private final Board board;
    private boolean isDragged = false;
    private int pressX, pressY;

    Input(Board board) {
        this.board = board;
    }

    @Override
    public void mousePressed(MouseEvent e) {
        // deleting the hint mark
        board.clearHint();
        int square = board.squareAt(e.getX(), e.getY());
        pressX = e.getX();
        pressY = e.getY();
        isDragged = false;

        if (board.selectedSquare < 0) {
            // בחירת כלי
            if (board.canSelect(square)) {
                board.playSelectSound();
                board.selectedSquare = square;
            }
        } else if (board.isLegalTarget(board.selectedSquare, square)) {
            // הזזת כלי
            if (!board.tryMove(board.selectedSquare, square, false)) {
                board.selectedSquare = -1;
            }
        } else if (board.canSelect(square)) {
            board.playSelectSound();
            board.selectedSquare = square;
        } else {
            board.playInvalidMoveSound();
            board.selectedSquare = -1;
        }
        board.repaint();
    }

    @Override
    public void mouseDragged(MouseEvent e) {
        if (board.selectedSquare >= 0) {
            board.dragSquare = board.selectedSquare;
            board.dragX = e.getX() - Board.tileSize / 2;
            board.dragY = e.getY() - Board.tileSize / 2;
            isDragged = true;
            board.repaint();
        }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
        if (isDragged && board.dragSquare >= 0) {
            int from = board.dragSquare;
            board.dragSquare = -1;
            boolean movedFar = Math.abs(e.getX() - pressX) > Board.tileSize / 2
                    || Math.abs(e.getY() - pressY) > Board.tileSize / 2;
            if (movedFar) {
                int to = board.squareAt(e.getX(), e.getY());
                if (!board.tryMove(from, to, true)) {
                    board.playInvalidMoveSound();
                }
                board.selectedSquare = -1;
            }
            board.repaint();
        }
        isDragged = false;
    }
}
