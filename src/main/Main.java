package main;

import javax.swing.*;
import java.awt.*;

import GUI.CustomButtonPanel;
import ai.*;
import main.setting.SettingPanel;

public class Main {
    public static void main(String[] args) {
        JFrame frame = new JFrame();
        frame.setMinimumSize(new Dimension(1000, 1000));
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLayout(new GridBagLayout());
        frame.getContentPane().setBackground(Color.gray);
        frame.setLocationRelativeTo(null);

        JTabbedPane tabbedPane = new JTabbedPane();

        GridBagConstraints tabConstraints = new GridBagConstraints();
        tabConstraints.gridx = 0;
        tabConstraints.gridy = 3;
        tabConstraints.gridwidth = 2;
        frame.add(tabbedPane, tabConstraints);

        Board board = new Board();
        tabbedPane.addTab("Game", board);

        SettingPanel settingsPanel = new SettingPanel();
        settingsPanel.setBackground(Color.gray);
        settingsPanel.add(new JLabel("Settings Panel"));
        tabbedPane.addTab("Settings", settingsPanel);

        JPanel savedGamesPanel = new JPanel();
        savedGamesPanel.setBackground(Color.white);
        savedGamesPanel.add(new JLabel("Saved Games Panel"));
        tabbedPane.addTab("Saved Games", savedGamesPanel);
        frame.add(tabbedPane, tabConstraints);

        // יצירת מופע של CustomButtonPanel והוספתו לחלון
        CustomButtonPanel customButtonPanel = new CustomButtonPanel(1, "Go back", (Integer id) -> {
            board.goBack();
        });
        frame.add(customButtonPanel);

        // הגדרת GridBagConstraints עבור הכפתור
        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 0;
        buttonConstraints.gridy = 0;
        buttonConstraints.anchor = GridBagConstraints.NORTHWEST;
        buttonConstraints.insets = new Insets(10, 10, 10, 10);

        // יצירת מופע של CustomButtonPanel והוספתו לחלון
        CustomButtonPanel customButtonPanel2 = new CustomButtonPanel(2, "Take a hint", (Integer id) -> {
            board.input.takeEngineHint();
        });
        frame.add(customButtonPanel2);

        // הגדרת GridBagConstraints עבור הכפתור
        GridBagConstraints buttonConstraints2 = new GridBagConstraints();
        buttonConstraints2.gridx = 1;
        buttonConstraints2.gridy = 0;
        buttonConstraints2.anchor = GridBagConstraints.NORTHWEST;
        buttonConstraints2.insets = new Insets(10, 10, 10, 10);

        // הוספת הכפתורים לחלון
        frame.add(customButtonPanel, buttonConstraints);
        frame.add(customButtonPanel2, buttonConstraints2);

        // יצירת פאנל למצב הניקוד והוספתו לחלון
        JPanel scorePanel = new JPanel();
        scorePanel.setBackground(Color.lightGray);
        scorePanel.setLayout(new BoxLayout(scorePanel, BoxLayout.Y_AXIS)); // הגדרת סידור אנכי
        scorePanel.add(new JLabel("Score Panel"));
        scorePanel.add(new JLabel("Player 1: 0")); // טקסט לדוגמה
        scorePanel.add(new JLabel("Player 2: 0")); // טקסט לדוגמה

        GridBagConstraints scorePanelConstraints = new GridBagConstraints();
        scorePanelConstraints.gridx = 2; // עמודה שלישית (ליד הלוח)
        scorePanelConstraints.gridy = 0; // שורה ראשונה (למעלה)
        scorePanelConstraints.gridheight = 4; // יתפוס את כל הגובה של הלוח והטאב פאנל
        scorePanelConstraints.insets = new Insets(10, 10, 10, 10);
        scorePanelConstraints.fill = GridBagConstraints.BOTH;

        frame.add(scorePanel, scorePanelConstraints);

        frame.setVisible(true);
    }

    public static void showEndGameMessage(JFrame frame, String message) {
        JOptionPane.showMessageDialog(frame, message, "סיום המשחק", JOptionPane.INFORMATION_MESSAGE);
    }
}
