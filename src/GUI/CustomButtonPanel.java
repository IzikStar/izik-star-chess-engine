package GUI;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.util.function.Consumer;

public class CustomButtonPanel extends JPanel {

    public int id;

    public CustomButtonPanel(int id, String text, Consumer<Integer> action) {
        this.id = id;
        // הגדרת FlowLayout
        this.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4)); // מיקום לשמאל עם מרווחים של 4 פיקסלים

        // יצירת כפתור
        JButton button = new JButton(text);
        // הוספת מאזין לכפתור
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                action.accept(id);
            }
        });

        // הוספת הכפתור לפאנל הנוכחי (this)
        this.add(button);
    }
}
