package main.setting;

import GUI.CustomButtonPanel;
import ai.StockfishEngine;

import javax.swing.*;
import java.awt.*;

public class SettingPanel extends JPanel {

    private JFrame parentFrame;

    private final CustomButtonPanel customButtonPanel;
    private final CustomButtonPanel chooseLevel;
    public static int skillLevel = 0;

    public SettingPanel() {
        this.setPreferredSize(new Dimension(670 /* כמו הלוח */, 670));
        // יצירת מופע של CustomButtonPanel והוספתו לחלון
        customButtonPanel = new CustomButtonPanel(1, "Two players", (Integer id) -> {
            System.out.println("Button Change is own player clicked!");
            ChoosePlayFormat.isOnePlayer = !ChoosePlayFormat.isOnePlayer;
            changeButtonText();
        });
        this.add(customButtonPanel);

        chooseLevel = new CustomButtonPanel(1, "Next level (" + SettingPanel.skillLevel + ")", (Integer id) -> {
            skillLevel++;
            System.out.println("New level: " + skillLevel);
        });
        this.add(chooseLevel);
    }

    public void changeButtonText() {
        String newText = ChoosePlayFormat.isOnePlayer ?  "Two players": "Play with computer";
        customButtonPanel.changeText(newText);
    }
}
