package main.setting;

import GUI.CustomButtonPanel;
import main.Main;

import javax.swing.*;
import java.awt.*;

public class SettingPanel extends JPanel {

    private CustomButtonPanel chooseIsOnePlayer = null;
    private CustomButtonPanel chooseIsPlayingWhite = null;
    private CustomButtonPanel[] levelButtons = new CustomButtonPanel[10];
    public static int skillLevel = 0;

    public SettingPanel() {
        this.setPreferredSize(new Dimension(670, 670));
        this.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // כפתור לשינוי פורמט המשחק (שחקן יחיד/שני שחקנים)
        chooseIsOnePlayer = new CustomButtonPanel(1, "Two players", (Integer id) -> {
            ChoosePlayFormat.isOnePlayer = !ChoosePlayFormat.isOnePlayer;
            String newText = ChoosePlayFormat.isOnePlayer ? "Two players" : "Play with computer";
            changeButtonText(chooseIsOnePlayer, newText);
            Main.board.refresh();
        });
        styleButton(chooseIsOnePlayer);
        gbc.gridx = 0;
        gbc.gridy = 0;
        this.add(chooseIsOnePlayer, gbc);

        // כפתור לשינוי צבע השחקן
        chooseIsPlayingWhite = new CustomButtonPanel(2, "Play as black", (Integer id) -> {
            ChoosePlayFormat.isPlayingWhite = !ChoosePlayFormat.isPlayingWhite;
            String newText = ChoosePlayFormat.isPlayingWhite ? "Play as black" : "Play as white";
            changeButtonText(chooseIsPlayingWhite, newText);
            Main.board.refresh();
        });
        styleButton(chooseIsPlayingWhite);
        gbc.gridx = 1;
        this.add(chooseIsPlayingWhite, gbc);

        // כפתורים לבחירת רמת המשחק (0-18 עם שמות 1-10) בשתי שורות
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        JPanel levelPanel = new JPanel(new GridLayout(2, 5, 10, 10));
        for (int i = 0; i < 10; i++) {
            int level = i * 2;
            levelButtons[i] = new CustomButtonPanel(level, "Level " + (i + 1), (Integer id) -> {
                skillLevel = level;
                Main.board.refresh();
            });
            styleLevelButton(levelButtons[i]);
            levelPanel.add(levelButtons[i]);
        }
        this.add(levelPanel, gbc);
    }

    public void changeButtonText(CustomButtonPanel button, String newText) {
        button.changeText(newText);
    }

    private void styleButton(CustomButtonPanel button) {
        button.setBackground(Color.green);
        button.setForeground(Color.black);
        button.setFont(new Font("Arial", Font.BOLD, 14));
    }

    private void styleLevelButton(CustomButtonPanel button) {
        button.setBackground(Color.orange);
        button.setForeground(Color.black);
        button.setFont(new Font("Arial", Font.PLAIN, 12));
    }
}
