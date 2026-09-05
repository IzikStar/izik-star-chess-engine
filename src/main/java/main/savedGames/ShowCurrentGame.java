package main.savedGames;

import javax.swing.*;
import java.awt.*;
import java.util.Stack;

public class ShowCurrentGame extends JPanel {
    public static Stack<String> movesStack;
    private JTextArea movesTextArea;

    public ShowCurrentGame(Stack<String> movesStack) {
        this.movesStack = movesStack;
        this.movesTextArea = new JTextArea();
        this.movesTextArea.setEditable(false);  // הפוך את הטקסט ללא ניתן לעריכה
        this.movesTextArea.setFont(new Font("Arial", Font.PLAIN, 14)); // ניתן להגדיר את הגופן

        setLayout(new BorderLayout());

        JScrollPane scrollPane = new JScrollPane(movesTextArea);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);

        // הגדרת גובה ורוחב קבועים ל-JScrollPane
        scrollPane.setPreferredSize(new Dimension(500, 500)); // שנה את הערכים לפי הצורך

        add(scrollPane, BorderLayout.CENTER);

        updateMovesPanel();
    }

    public void updateMoves(Stack<String> newMovesStack) {
        this.movesStack = newMovesStack;
        updateMovesPanel();
    }

    public void updateMovesPanel() {
        movesTextArea.setText("");  // נקה את הטקסט הקודם

        for (String move : movesStack) {
            movesTextArea.append(move);
        }

        movesTextArea.setCaretPosition(movesTextArea.getDocument().getLength());  // גלול למטה בסיום
    }


}
