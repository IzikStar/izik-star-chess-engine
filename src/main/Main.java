package main;

import ai.myEngine;
import com.formdev.flatlaf.FlatLightLaf;

import javax.swing.*;
import java.awt.*;
import java.util.concurrent.CountDownLatch;

import GUI.CustomButtonPanel;
import main.savedGames.SavedGamesPanel;
import main.setting.ChoosePlayFormat;
import main.setting.SettingPanel;

public class Main {
    private static JLabel player1ScoreLabel;
    private static JLabel player2ScoreLabel;
    public static Board board;
    private static final CountDownLatch latch = new CountDownLatch(1);
    public static boolean computerGame = false;

    public static void main(String[] args) throws InterruptedException {
        // Apply FlatLaf theme
        FlatLightLaf.install();

        JFrame frame = new JFrame("Chess Game");
        frame.setMinimumSize(new Dimension(900, 900));
        frame.setExtendedState(JFrame.MAXIMIZED_BOTH);
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



        // יצירת עמוד חדש להצגת המהלכים
        SavedGamesPanel savedGamesPanel = new SavedGamesPanel();
        savedGamesPanel.setBackground(Color.white);
        // savedGamesPanel.add(new JLabel("Saved Games Panel"));


        board = new Board(savedGamesPanel);
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
            board.input.takeEngineHint();
        });
        styleButton(takeHintButton);

        // Create custom button panel for "New Game"
        CustomButtonPanel computerGameButton = new CustomButtonPanel(4, "New computer Game", (Integer id) -> {
            restartGame();
            computerGame = !computerGame;
            System.out.println("click");
            play();
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
    }

    private static void play() {
        new SwingWorker<Void, Void>() {
            @Override
            protected Void doInBackground() throws InterruptedException {
                Input temp = board.input;
                board.input = new Input(board, latch);
                while (!board.input.isStatusChanged && computerGame) {
                    if (board.state.getIsWhiteToMove()) {
                        SettingPanel.skillLevel = 12;
                        board.input.makeEngineMove();
                        // Wait for a specific time or until the next move
                        Thread.sleep(2500);
                    } else {
                        SettingPanel.skillLevel = 8;
                        board.input.makeEngineMove();
                        board.input.latch.await(); // Wait for the engine move to complete
                    }
                }
                board.input.myEngine.shutdown(); // Shut down the executor service when done
                board.input = temp;
                computerGame = false;
                return null;
            }

            @Override
            protected void done() {
                // Update the UI once the background task is finished
                SwingUtilities.invokeLater(() -> {
                    // any UI updates or cleanups
                    // e.g., updateScores if needed
                });
            }
        }.execute();
    }


    public static void showEndGameMessage(JFrame frame, String message) {
        JOptionPane.showMessageDialog(frame, message, "End of Game", JOptionPane.INFORMATION_MESSAGE);
    }

    public static void updateScores(int player1Score, int player2Score) {
        if (player1Score >= 0) {
            player1ScoreLabel.setText("    White:    \n\t" + player1Score + "\t    ");
            if (ChoosePlayFormat.isPlayingWhite && player1Score > 0) {
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
            if (player2Score > 0 && ChoosePlayFormat.isPlayingWhite) {
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
        board.restart();
        computerGame = false;
    }

    private static void styleButton(CustomButtonPanel button) {
        button.setBackground(Color.blue);
        button.setForeground(Color.white);
        button.setFont(new Font("Arial", Font.BOLD, 14));
    }

}
