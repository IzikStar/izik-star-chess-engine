package main.setting;

import GUI.CustomButtonPanel;
import ai.StockfishEngine;
import main.Board;
import main.Main;

import javax.swing.*;
import java.awt.*;

public class SettingPanel extends JPanel {

    private JFrame parentFrame;

    private CustomButtonPanel chooseIsOnePlayer = null;
    private CustomButtonPanel chooseIsPlayingWhite = null;
    private CustomButtonPanel chooseLevel = null;
    private CustomButtonPanel restartGame = null;
    public static int skillLevel = 0;

    public SettingPanel() {
        this.setPreferredSize(new Dimension(670 /* כמו הלוח */, 670));
        // יצירת מופע של CustomButtonPanel והוספתו לחלון
        chooseIsOnePlayer = new CustomButtonPanel(1, "Two players", (Integer id) -> {
            // System.out.println("Button Change is own player clicked!");
            ChoosePlayFormat.isOnePlayer = !ChoosePlayFormat.isOnePlayer;
            String newText = ChoosePlayFormat.isOnePlayer ?  "Two players": "Play with computer";
            changeButtonText(chooseIsOnePlayer, newText);
            Main.board.refresh();
        });
        this.add(chooseIsOnePlayer);

        chooseIsPlayingWhite = new CustomButtonPanel(1, "Play as black", (Integer id) -> {
            // System.out.println("Button Change is own player clicked!");
            ChoosePlayFormat.isPlayingWhite = !ChoosePlayFormat.isPlayingWhite;

            String newText = ChoosePlayFormat.isPlayingWhite ?  "Play as black": "Play as white";
            changeButtonText(chooseIsPlayingWhite, newText);
            Main.board.refresh();
        });
        this.add(chooseIsPlayingWhite);

        chooseLevel = new CustomButtonPanel(1, "Next level (" + (SettingPanel.skillLevel + 1) + ")", (Integer id) -> {
            skillLevel++;
            String newText = "Next level (" + (SettingPanel.skillLevel + 1) + ")";
            changeButtonText(chooseLevel, newText);
        });
        this.add(chooseLevel);

        restartGame = new CustomButtonPanel(1, "New Game", (Integer id) -> {
            Main.restartGame();
        });
        this.add(restartGame);
    }

    public void changeButtonText(CustomButtonPanel button, String newText) {
        button.changeText(newText);
    }

}
