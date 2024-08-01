package main.savedGames;

import javax.swing.*;
import java.awt.*;
import java.util.Stack;

public class SavedGamesPanel extends JPanel {
    private Stack<String> moves;
    private ShowCurrentGame currentGamePanel;

    public SavedGamesPanel() {
        this.moves = new Stack<>();
        initializeUI();
    }

    private void initializeUI() {
        // הגדרת מאפייני הפאנל עצמו
        this.setPreferredSize(new Dimension(470, 670));
        this.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 0;

        // דוגמא להוספת מהלכים לסטאק

        currentGamePanel = new ShowCurrentGame(moves);
        this.add(currentGamePanel, gbc);
    }

    public void addMove(String move) {
        moves.push(move);
        currentGamePanel.updateMoves(moves);
    }

    public void newGame() {
        moves.clear();
        currentGamePanel.updateMoves(moves);
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            JFrame frame = new JFrame("Saved Games");
            frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            frame.setSize(670, 670);

            SavedGamesPanel savedGamesPanel = new SavedGamesPanel();
            frame.add(savedGamesPanel);

            frame.setVisible(true);
        });
    }
}
