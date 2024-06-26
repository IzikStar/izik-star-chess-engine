package main;

import javax.imageio.ImageIO;
import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.image.BufferedImage;
import java.io.IOException;

public class PromotionDialog extends JDialog {
    private String selection;

    BufferedImage sheet;
    {
        try {
            sheet = ImageIO.read(ClassLoader.getSystemResourceAsStream("pieces.png"));
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
    protected int sheetScale = sheet.getWidth()/6;

    public PromotionDialog(JFrame parent, boolean isWhite) {
        super(parent, "הכתרת רגלי", true);
        setLayout(new BorderLayout());

        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new GridLayout(1, 4));

        String[] pieces = {"q", "r", "b", "n"};
        Icon[] icons;


        icons = new Icon[]{
                new ImageIcon(sheet.getSubimage(1 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(Board.tileSize, Board.tileSize, BufferedImage.SCALE_SMOOTH)),
                new ImageIcon(sheet.getSubimage(4 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(Board.tileSize, Board.tileSize, BufferedImage.SCALE_SMOOTH)),
                new ImageIcon(sheet.getSubimage(2 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(Board.tileSize, Board.tileSize, BufferedImage.SCALE_SMOOTH)),
                new ImageIcon(sheet.getSubimage(3 * sheetScale, isWhite ? 0 : sheetScale, sheetScale, sheetScale).getScaledInstance(Board.tileSize, Board.tileSize, BufferedImage.SCALE_SMOOTH))
        };

        for (int i = 0; i < pieces.length; i++) {
            JButton button = new JButton(pieces[i], icons[i]);
            final String piece = pieces[i];
            button.addActionListener(new ActionListener() {
                @Override
                public void actionPerformed(ActionEvent e) {
                    selection = piece;
                    setVisible(false);
                }
            });
            buttonPanel.add(button);
        }

        add(buttonPanel, BorderLayout.CENTER);
        pack();
        setLocationRelativeTo(parent);
        setVisible(true);
    }

    public String getSelection() {
        return selection;
    }
}
