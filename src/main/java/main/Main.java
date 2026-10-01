package main;

import com.formdev.flatlaf.FlatLightLaf;
import engine.EngineSelector;
import engine.MinimaxEngine;
import engine.StockfishEngine;
import game.GameConfig;
import game.GameSession;

import javax.swing.*;
import java.awt.*;

import GUI.CustomButtonPanel;
import main.savedGames.SavedGamesPanel;
import main.setting.SettingPanel;

public class Main {
    private static JLabel player1ScoreLabel;
    private static JLabel player2ScoreLabel;
    public static Board board;
    static GameSession session;
    /** The settings to return to when a "computer game" (engine vs engine) ends. */
    private static GameConfig configBeforeComputerGame;
    /** Engine level both sides use in a computer game. */
    private static final int COMPUTER_GAME_LEVEL = 6;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(Main::createAndShow);
    }

    private static void createAndShow() {
        // Apply FlatLaf theme
        FlatLightLaf.install();

        session = new GameSession(GameConfig.defaults(),
                new EngineSelector(new MinimaxEngine(), new StockfishEngine()),
                SwingUtilities::invokeLater);

        JFrame frame = new JFrame("Chess Game");
        frame.setMinimumSize(new Dimension(900, 900));
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(Color.gray);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        SettingPanel settingsPanel = new SettingPanel(session);
        settingsPanel.setBackground(Color.gray);
        settingsPanel.add(new JLabel("Settings Panel"));
        tabbedPane.addTab("Settings", settingsPanel);

        GridBagConstraints tabConstraints = new GridBagConstraints();
        tabConstraints.gridx = 0;
        tabConstraints.gridy = 3;
        tabConstraints.gridwidth = 2;
        frame.add(tabbedPane, tabConstraints);



        // יצירת עמוד חדש להצגת המהלכים
        SavedGamesPanel savedGamesPanel = new SavedGamesPanel();
        savedGamesPanel.setBackground(Color.white);
        // savedGamesPanel.add(new JLabel("Saved Games Panel"));


        board = new Board(session, savedGamesPanel);
        tabbedPane.addTab("Game", board);
        tabbedPane.addTab("Saved Games", savedGamesPanel);
        frame.add(tabbedPane, tabConstraints);

        // Create custom button panel for "Go back"
        CustomButtonPanel goBackButton = new CustomButtonPanel(1, "Go back", (Integer id) -> {
            board.goBack();
        });
        styleButton(goBackButton);

        // Create custom button panel for "New Game"
        CustomButtonPanel newGameButton = new CustomButtonPanel(2, "New Game", (Integer id) -> {
            restartGame();
        });
        styleButton(newGameButton);

        // Create custom button panel for "Take a hint"
        CustomButtonPanel takeHintButton = new CustomButtonPanel(3, "Take a hint", (Integer id) -> {
            session.requestHint();
        });
        styleButton(takeHintButton);

        // Create custom button panel for "New Game"
        CustomButtonPanel computerGameButton = new CustomButtonPanel(4, "New computer Game", (Integer id) -> {
            toggleComputerGame();
        });
        styleButton(newGameButton);

        // Add buttons to a single row
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 10));
        buttonPanel.setBackground(Color.gray);
        buttonPanel.add(goBackButton);
        buttonPanel.add(newGameButton);
        buttonPanel.add(takeHintButton);
        buttonPanel.add(computerGameButton);

        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 0;
        buttonConstraints.gridwidth = 3;
        buttonConstraints.anchor = GridBagConstraints.NORTHWEST;
        buttonConstraints.insets = new Insets(10, 10, 10, 10);
        frame.add(buttonPanel, buttonConstraints);

        // Create score panel
        JPanel scorePanel = new JPanel();
        scorePanel.setBackground(Color.lightGray);
        scorePanel.setLayout(new BoxLayout(scorePanel, BoxLayout.Y_AXIS));
        scorePanel.setBorder(BorderFactory.createTitledBorder("Score"));

        player1ScoreLabel = new JLabel("    White:    \n\t0\t    ");
        player1ScoreLabel.setFont(new Font("Arial", Font.BOLD, 16));
        player2ScoreLabel = new JLabel("    Black:    \n\t0\t    ");
        player2ScoreLabel.setFont(new Font("Arial", Font.BOLD, 16));

        scorePanel.add(player1ScoreLabel);
        scorePanel.add(player2ScoreLabel);

        // Add score panel to frame
        GridBagConstraints scoreConstraints = new GridBagConstraints();
        scoreConstraints.gridx = 3;
        scoreConstraints.gridy = 0;
        scoreConstraints.gridheight = 4;
        scoreConstraints.fill = GridBagConstraints.BOTH;
        scoreConstraints.insets = new Insets(10, 10, 10, 10);
        frame.add(scorePanel, scoreConstraints);

        // Pack and display the frame
        //frame.pack();
        frame.setVisible(true);
        session.start();
    }

    /** Starts an engine-vs-engine game, or, if one is running, returns to the previous settings. */
    private static void toggleComputerGame() {
        if (configBeforeComputerGame == null) {
            configBeforeComputerGame = session.config();
            board.restart();
            session.updateConfig(new GameConfig(GameConfig.Mode.ENGINE_VS_ENGINE,
                    configBeforeComputerGame.humanPlaysWhite(), COMPUTER_GAME_LEVEL));
        } else {
            restartGame();
        }
    }


    public static void showEndGameMessage(JFrame frame, String message) {
        JOptionPane.showMessageDialog(frame, message, "End of Game", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void updateScores(int player1Score, int player2Score, boolean humanPlaysWhite) {
        if (player1Score >= 0) {
            player1ScoreLabel.setText("    White:    \n\t" + player1Score + "\t    ");
            if (humanPlaysWhite && player1Score > 0) {
                player1ScoreLabel.setForeground(new Color(0, 72, 255));
            } else if (player1Score > 0){
                player1ScoreLabel.setForeground(new Color(255, 0, 0));
            }
            else {
                player1ScoreLabel.setForeground(Color.BLACK);
            }
        } else {
            player1ScoreLabel.setText("                ");
        }
        if (player2Score >= 0) {
            player2ScoreLabel.setText("    Black:    \n\t" + player2Score + "\t    ");
            if (player2Score > 0 && humanPlaysWhite) {
                player2ScoreLabel.setForeground(new Color(255, 0, 0));
            } else if (player2Score > 0){
                player2ScoreLabel.setForeground(new Color(0, 72, 255));
            } else {
                player2ScoreLabel.setForeground(Color.BLACK);
            }
        } else {
            player2ScoreLabel.setText("                ");
        }
    }

    public static void restartGame() {
        if (configBeforeComputerGame != null) {
            session.updateConfig(configBeforeComputerGame);
            configBeforeComputerGame = null;
        }
        board.restart();
    }

    private static void styleButton(CustomButtonPanel button) {
        button.setBackground(Color.blue);
        button.setForeground(Color.white);
        button.setFont(new Font("Arial", Font.BOLD, 14));
    }

}
