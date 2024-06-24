package GUI;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class ChessAnimation {
    private JFrame frame;
    private JPanel boardPanel;
    private JLabel piece;

    public ChessAnimation() {
        frame = new JFrame("Chess Animation");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(800, 800);
        frame.setLayout(null);

        boardPanel = new JPanel();
        boardPanel.setBounds(0, 0, 800, 800);
        frame.add(boardPanel);

        piece = new JLabel(new ImageIcon("path/to/piece/image"));
        piece.setBounds(0, 0, 100, 100);
        frame.add(piece);

        frame.setVisible(true);

        animatePieceMove(piece, 0, 0, 400, 400, 2000);
    }

    private void animatePieceMove(JLabel piece, int startX, int startY, int endX, int endY, int duration) {
        Timer timer = new Timer(10, new ActionListener() {
            long startTime = -1;

            @Override
            public void actionPerformed(ActionEvent e) {
                if (startTime < 0) {
                    startTime = System.currentTimeMillis();
                }
                long currentTime = System.currentTimeMillis();
                long elapsed = currentTime - startTime;
                if (elapsed > duration) {
                    elapsed = duration;
                    ((Timer) e.getSource()).stop();
                }
                float progress = (float) elapsed / duration;
                int x = startX + (int) ((endX - startX) * progress);
                int y = startY + (int) ((endY - startY) * progress);
                piece.setLocation(x, y);
            }
        });
        timer.start();
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ChessAnimation::new);
    }
}

