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
//        tabConstraints.gridheight = GridBagConstraints.REMAINDER;
//        tabConstraints.fill = GridBagConstraints.BOTH;
//        tabConstraints.weightx = 1.0;
//        tabConstraints.weighty = 1.0;
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

        //        // יצירת מופע של CustomButtonPanel והוספתו לחלון
        CustomButtonPanel customButtonPanel = new CustomButtonPanel(1, "Go back", (Integer id) -> {
            System.out.println("Button with id " + id + " clicked!");
            board.goBack();
        });
        frame.add(customButtonPanel);

        // הגדרת GridBagConstraints עבור הכפתור
        GridBagConstraints buttonConstraints = new GridBagConstraints();
        buttonConstraints.gridx = 1;  // עמודה שנייה (צד ימין)
        buttonConstraints.gridy = 0;  // שורה ראשונה (למעלה)
        buttonConstraints.anchor = GridBagConstraints.NORTHEAST;  // עיגון בצד ימין למעלה
        buttonConstraints.insets = new Insets(10, 10, 10, 10);  // רווחים מסביב לכפתור

        // יצירת מופע של CustomButtonPanel והוספתו לחלון
        CustomButtonPanel customButtonPanel2 = new CustomButtonPanel(2,"Take a hint", (Integer id) -> {
            System.out.println("Button with id " + id + " clicked!");
            board.input.takeEngineHint();
        });
        frame.add(customButtonPanel2);

        // הגדרת GridBagConstraints עבור הכפתור
        GridBagConstraints buttonConstraints2 = new GridBagConstraints();
        buttonConstraints2.gridx = 3;  // עמודה שנייה (צד ימין)
        buttonConstraints2.gridy = 0;  // שורה ראשונה (למעלה)
        buttonConstraints2.anchor = GridBagConstraints.NORTHEAST;  // עיגון בצד ימין למעלה
        buttonConstraints2.insets = new Insets(10, 10, 10, 10);  // רווחים מסביב לכפתור

        // הוספת הכפתורים לחלון
        frame.add(customButtonPanel, buttonConstraints);
        frame.add(customButtonPanel2, buttonConstraints2);


        frame.setVisible(true);
    }

    public static void showEndGameMessage(JFrame frame, String message) {
        JOptionPane.showMessageDialog(frame, message, "סיום המשחק", JOptionPane.INFORMATION_MESSAGE);
    }
}
