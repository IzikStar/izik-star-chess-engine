package main;

import javax.swing.*;
import java.awt.*;

import GUI.CustomButtonPanel;
import ai.*;

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

        JPanel settingsPanel = new JPanel();
        settingsPanel.setBackground(Color.white);
        settingsPanel.add(new JLabel("Settings Panel"));
        tabbedPane.addTab("Settings", settingsPanel);

        JPanel savedGamesPanel = new JPanel();
        savedGamesPanel.setBackground(Color.white);
        savedGamesPanel.add(new JLabel("Saved Games Panel"));
        tabbedPane.addTab("Saved Games", savedGamesPanel);

        frame.add(tabbedPane, tabConstraints);
        frame.setVisible(true);
    }

    public static void showEndGameMessage(JFrame frame, String message) {
        JOptionPane.showMessageDialog(frame, message, "סיום המשחק", JOptionPane.INFORMATION_MESSAGE);
    }
}
