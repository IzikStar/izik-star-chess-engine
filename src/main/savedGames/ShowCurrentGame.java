package main.savedGames;

import javax.swing.*;
import java.awt.*;
import java.util.Stack;

public class ShowCurrentGame extends JPanel {
    private Stack<String> movesStack;
    private JTextArea movesTextArea;

    public ShowCurrentGame(Stack<String> movesStack) {
        this.movesStack = movesStack;
        this.movesTextArea = new JTextArea();
        this.movesTextArea.setEditable(false);  // הפוך את הטקסט ללא ניתן לעריכה
        this.movesTextArea.setFont(new Font("Arial", Font.PLAIN, 14)); // ניתן להגדיר את הגופן

        setLayout(new BorderLayout());
        add(new JScrollPane(movesTextArea), BorderLayout.CENTER);

        updateMovesPanel();
    }

    public void updateMoves(Stack<String> newMovesStack) {
        this.movesStack = newMovesStack;
        updateMovesPanel();
    }

    private void updateMovesPanel() {
        movesTextArea.setText("");  // נקה את הטקסט הקודם

        for (String move : movesStack) {
            movesTextArea.append(move + "\n");
        }

        movesTextArea.setCaretPosition(movesTextArea.getDocument().getLength());  // גלול למטה בסיום
    }
}
