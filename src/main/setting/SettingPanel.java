package main.setting;

import GUI.CustomButtonPanel;

import javax.swing.*;
import java.awt.*;

public class SettingPanel extends JPanel {

    private JFrame parentFrame;

    private final CustomButtonPanel customButtonPanel;

    public SettingPanel() {
        this.setPreferredSize(new Dimension(670 /* כמו הלוח */, 670));
        // יצירת מופע של CustomButtonPanel והוספתו לחלון
        customButtonPanel = new CustomButtonPanel(1, "Two players", (Integer id) -> {
            System.out.println("Button Change is own player clicked!");
            ChoosePlayFormat.isOnePlayer = !ChoosePlayFormat.isOnePlayer;
            changeButtonText();
        });
        this.add(customButtonPanel);
    }

    public void changeButtonText() {
        String newText = ChoosePlayFormat.isOnePlayer ? "Play with computer" : "Two players";
        customButtonPanel.changeText(newText);
    }
}
