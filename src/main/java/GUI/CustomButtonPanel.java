package GUI;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class CustomButtonPanel extends JPanel {

    public int id;
    private String text;
    private JButton button;

    public CustomButtonPanel(int id, String text, Consumer<Integer> action) {
        this.id = id;
        this.text = text;
        // הגדרת FlowLayout
        this.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4)); // מיקום לשמאל עם מרווחים של 4 פיקסלים

        // יצירת כפתור
        this.button = new JButton(this.text);
        // הוספת מאזין לכפתור
        this.button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.accept(id);
            }
        });

        // הוספת הכפתור לפאנל הנוכחי (this)
        this.add(this.button);
    }

    public void changeText(String text) {
        this.text = text;
        this.button.setText(this.text);
    }
}
