package main;

import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;

import GUI.CustomButtonPanel;
import main.setting.SettingPanel;

public class Main {
    private static JLabel player1ScoreLabel;
    private static JLabel player2ScoreLabel;
    public static Board board;

    public static void main(String[] args) {
        // Apply FlatLaf theme
        FlatLightLaf.install();

        JFrame frame = new JFrame("Chess Game");
        frame.setMinimumSize(new Dimension(900, 900));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(Color.gray);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        SettingPanel settingsPanel = new SettingPanel();
        settingsPanel.setBackground(Color.gray);
        settingsPanel.add(new JLabel("Settings Panel"));
        tabbedPane.addTab("Settings", settingsPanel);

        GridBagConstraints tabConstraints = new GridBagConstraints();
        tabConstraints.gridx = 0;
        tabConstraints.gridy = 3;
        tabConstraints.gridwidth = 2;
        frame.add(tabbedPane, tabConstraints);

        board = new Board();
        tabbedPane.addTab("Game", board);

        JPanel savedGamesPanel = new JPanel();
        savedGamesPanel.setBackground(Color.white);
        savedGamesPanel.add(new JLabel("Saved Games Panel"));
        tabbedPane.addTab("Saved Games", savedGamesPanel);
        frame.add(tabbedPane, tabConstraints);

        // Create custom button panel for "Go back"
        CustomButtonPanel customButtonPanel = new CustomButtonPanel(1, "Go back", (Integer id) -> {
            board.goBack();
        });

        // Add "Go back" button to frame
        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 0;
        buttonConstraints.anchor = GridBagConstraints.NORTHWEST;
        buttonConstraints.insets = new Insets(10, 10, 10, 10);
        frame.add(customButtonPanel, buttonConstraints);

        // Create custom button panel for "Take a hint"
        CustomButtonPanel customButtonPanel2 = new CustomButtonPanel(2, "Take a hint", (Integer id) -> {
            board.input.takeEngineHint();
        });

        // Add "Take a hint" button to frame
        GridBagConstraints buttonConstraints2 = new GridBagConstraints();
        buttonConstraints2.gridx = 1;
        buttonConstraints2.gridy = 0;
        buttonConstraints2.anchor = GridBagConstraints.NORTHWEST;
        buttonConstraints2.insets = new Insets(10, 10, 10, 10);
        frame.add(customButtonPanel2, buttonConstraints2);

        // Create score panel
        JPanel scorePanel = new JPanel();
        scorePanel.setBackground(Color.lightGray);
        scorePanel.setLayout(new BoxLayout(scorePanel, BoxLayout.Y_AXIS));

        player1ScoreLabel = new JLabel("    White:    \n\t0\t    ");
        player2ScoreLabel = new JLabel("    Black:    \n\t0\t    ");

        scorePanel.add(player1ScoreLabel);
        scorePanel.add(player2ScoreLabel);

        // Add score panel to frame
        GridBagConstraints scoreConstraints = new GridBagConstraints();
        scoreConstraints.gridx = 2;
        scoreConstraints.gridy = 0;
        scoreConstraints.gridheight = 4;
        scoreConstraints.fill = GridBagConstraints.BOTH;
        scoreConstraints.insets = new Insets(10, 10, 10, 10);
        frame.add(scorePanel, scoreConstraints);

        // Pack and display the frame
        frame.pack();
        frame.setVisible(true);
    }

    public static void showEndGameMessage(JFrame frame, String message) {
        JOptionPane.showMessageDialog(frame, message, "End of Game", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void updateScores(int player1Score, int player2Score) {
        if (player1Score >= 0) {
            player1ScoreLabel.setText("    White:    \n\t" + player1Score + "\t    ");
        } else {
            player1ScoreLabel.setText("                ");
        }
        if (player2Score >= 0) {
            player2ScoreLabel.setText("    Black:    \n\t" + player2Score + "\t    ");
        } else {
            player2ScoreLabel.setText("                ");
        }
    }

    public static void restartGame() {
        board.restart();
    }
}
