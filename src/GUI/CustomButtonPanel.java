package GUI;

import javax.swing.JButton;
import javax.swing.JPanel;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

public class CustomButtonPanel extends JPanel {

    public int counter;
    public CustomButtonPanel(int counter) {
        this.counter = counter;
        // הגדרת FlowLayout
        this.setLayout(new FlowLayout(FlowLayout.LEFT, 4, 4)); // מיקום לשמאל עם מרווחים של 20 פיקסלים

        // יצירת כפתור
        JButton button = new JButton("Click Me!" + counter);
        // הוספת מאזין לכפתור
        button.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.out.println("Button clicked!" + counter);
            }
        });

        // הוספת הכפתור לפאנל הנוכחי (this)
        this.add(button);
    }
}

