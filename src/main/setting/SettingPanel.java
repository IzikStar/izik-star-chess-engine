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
    public static int skillLevel = 0;

    public SettingPanel() {
        this.setPreferredSize(new Dimension(670 /* כמו הלוח */, 670));
        // יצירת מופע של CustomButtonPanel והוספתו לחלון
        chooseIsOnePlayer = new CustomButtonPanel(1, "Two players", (Integer id) -> {
            // System.out.println("Button Change is own player clicked!");
            ChoosePlayFormat.isOnePlayer = !ChoosePlayFormat.isOnePlayer;
            String newText = ChoosePlayFormat.isOnePlayer ?  "Two players": "Play with computer";
            changeButtonText(chooseIsOnePlayer, newText);
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

        chooseLevel = new CustomButtonPanel(1, "Next level (" + SettingPanel.skillLevel + ")", (Integer id) -> {
            skillLevel++;
            System.out.println("New level: " + skillLevel);
        });
        this.add(chooseLevel);
    }

    public void changeButtonText(CustomButtonPanel button, String newText) {
        button.changeText(newText);
    }
}
