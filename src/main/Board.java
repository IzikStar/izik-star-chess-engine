package main;

import GUI.AudioPlayer;
import pieces.*;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Stack;
import java.util.stream.Collectors;

public class Board extends JPanel {

    public static int tileSize = 85;
    public static String fenStartingPosition = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR w KQkq - 0 1";
    public String fenCurrentPosition = fenStartingPosition;
    public Stack<String> savedStates = new Stack<>();

    private JFrame parentFrame;

    int cols = 8;
    int rows = 8;

    public Piece selectedPiece;
    Input input = new Input(this);
    public CheckScanner checkScanner = new CheckScanner(this);
    AudioPlayer audioPlayer = new AudioPlayer();
    ShowScore showScore = new ShowScore(this);

    int fromC = -1, fromR = -1, toC = -1, toR = -1;
    public Piece lastToMove;
    public int enPassantTile = -1;
    // public Move lastMove;
    private boolean isWhiteToMove = true;
    public int numOfTurns = 0;
    public int numOfTurnWithoutCaptureOrPawnMove = 0;
    public boolean isGameOver = false;
    // public static boolean isStatusChanged = false, isCheckMate = false, isStaleMate = false, isWhiteTurn;

    public Board() {
        this.setPreferredSize(new Dimension(cols * tileSize /* + tileSize / 2 */, rows * tileSize));

        this.addMouseListener(input);
        this.addMouseMotionListener(input);
        this.savedStates.push(fenCurrentPosition);

        // addPieces();
        loadPiecesFromFen(fenCurrentPosition);
    }

    ArrayList<Piece> pieceList = new ArrayList<>();

    public Piece getPiece(int col, int row) {

        for (Piece piece : pieceList) {
            if (piece.col == col && piece.row == row) {
                return piece;
            }
        }


        return null;
    }

    Piece findKing(boolean isWhite) {
        for (Piece piece : pieceList) {
            if (piece.isWhite == isWhite && piece.name.equals("King")) {
                return piece;
            }
        }
        return null;
    }

    public void loadPiecesFromFen(String fenCurrentPosition) {
        String[] parts = fenCurrentPosition.split(" ");

        pieceList.clear();

        // position
        String position = parts[0];
        int row = 0;
        int col = 0;
        for (int i = 0; i < position.length(); i++) {
            char ch = position.charAt(i);
            if (ch == '/') {
                row++;
                col = 0;
            } else if (Character.isDigit(ch)) {
                col += Character.getNumericValue(ch);
            }
            else {
                boolean isWhite = Character.isUpperCase(ch);
                ch = Character.toLowerCase(ch);
                switch (ch) {
                    case 'r' : pieceList.add(new Rook(this, col, row, isWhite)); break;
                    case 'n' : pieceList.add(new Knight(this, col, row, isWhite)); break;
                    case 'b' : pieceList.add(new Bishop(this, col, row, isWhite)); break;
                    case 'q' : pieceList.add(new Queen(this, col, row, isWhite)); break;
                    case 'k' : pieceList.add(new King(this, col, row, isWhite)); break;
                    case 'p' : pieceList.add(new Pawn(this, col, row, isWhite, col)); break;
                }
                col++;
            }
        }

        // turn
        isWhiteToMove = parts[1].equals("w");

        // castling
        Piece bqr = getPiece(0, 0);
        if (bqr instanceof Rook) {
            bqr.isFirstMove = parts[2].contains("q");
        }
        Piece bkr = getPiece(7, 0);
        if (bkr instanceof Rook) {
            bkr.isFirstMove = parts[2].contains("k");
        }
        Piece wqr = getPiece(0, 7);
        if (wqr instanceof Rook) {
            wqr.isFirstMove = parts[2].contains("Q");
        }
        Piece wkr = getPiece(7, 7);
        if (wkr instanceof Rook) {
            wkr.isFirstMove = parts[2].contains("K");
        }

        // en passant
        if (parts[3].equals("-")) {
            enPassantTile = -1;
        }
        else {
            enPassantTile = (7 - (parts[3].charAt(1) - '1')) * 8 + (parts[3].charAt(0) - 'a');
        }
        repaint();
    }

    public void addPieces() {
        pieceList.add(new Rook(this, 0, 0, false));
        pieceList.add(new Rook(this, 7, 0, false));
        pieceList.add(new Rook(this, 0, 7, true));
        pieceList.add(new Rook(this, 7, 7, true));

        pieceList.add(new Knight(this, 1, 0, false));
        pieceList.add(new Knight(this, 6, 0, false));
        pieceList.add(new Knight(this, 1, 7, true));
        pieceList.add(new Knight(this, 6, 7, true));

        pieceList.add(new Bishop(this, 2, 0, false));
        pieceList.add(new Bishop(this, 5, 0, false));
        pieceList.add(new Bishop(this, 2, 7, true));
        pieceList.add(new Bishop(this, 5, 7, true));

        pieceList.add(new King(this, 4, 0, false));
        pieceList.add(new King(this, 4, 7, true));

        pieceList.add(new Queen(this, 3, 0, false));
        pieceList.add(new Queen(this, 3, 7, true));

        for (int i = 0; i < 8; i++) {
            pieceList.add(new Pawn(this, i, 1, false, 1));
            pieceList.add(new Pawn(this, i, 6, true, 1));
        }
    }

    public void paintComponent(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        // paint board
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                g2d.setColor((c + r) % 2 != 0 ? new Color(152, 97, 42) : new Color(208, 182, 164));
                g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
            }
        }



        // paint last move
        g.setColor(new Color(72, 255, 0, 158));
        g.fillRect(fromC * tileSize, fromR * tileSize, tileSize, tileSize);
        g.setColor(new Color(55, 255, 0, 186));
        g.fillRect(toC * tileSize, toR * tileSize, tileSize, tileSize);

        // paint the border of the king red if it's under attack
        if (checkScanner.isChecking(this)) {
            Piece king = findKing(isWhiteToMove);
            //g2d.setColor(new Color(255, 0, 0, 237)); // אדום חצי שקוף
            //g2d.fillRect(king.col * tileSize, king.row * tileSize, tileSize, tileSize);
            drawSquareWithCircle(g ,king.col, king.row);
        }

        // paint highLights
        if (selectedPiece != null) {
            if (selectedPiece.isWhite == isWhiteToMove) {
                g2d.setColor(new Color(0, 0, 255, 128)); // כחול חצי שקוף
                g2d.fillRect(selectedPiece.col * tileSize, selectedPiece.row * tileSize, tileSize, tileSize);
                if (checkScanner.isChecking(this)) {
                    Piece king = findKing(isWhiteToMove);
                    //g2d.setColor(new Color(255, 0, 0, 237)); // אדום חצי שקוף
                    //g2d.fillRect(king.col * tileSize, king.row * tileSize, tileSize, tileSize);
                    drawSquareWithCircle(g ,king.col, king.row);
                }
            }
            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    if (isValidMove(new Move(this, selectedPiece, c, r), false)) {
                        if (this.getPiece(c, r) == null) {
                            // ציור עיגול במרכז הריבוע
                            g.setColor(new Color(72, 255, 0, 158));
                            int diameter = tileSize / 3;
                            int circleX = c * tileSize + (tileSize - diameter) / 2;
                            int circleY = r * tileSize + (tileSize - diameter) / 2;
                            g.fillOval(circleX, circleY, diameter, diameter);
                        } else {
                            drawSquareWithCircle(g, c, r);
//                            g.setColor(new Color(255, 0, 0, 90));
//                            int diameter = tileSize / 3;
//                            int circleX = c * tileSize + (tileSize - diameter) / 2;
//                            int circleY = r * tileSize + (tileSize - diameter) / 2;
//                            g.fillOval(circleX, circleY, diameter, diameter);
//                            g2d.setColor(new Color(255, 0, 0, 90)); // אדום חצי שקוף
//                            g2d.fillRect(c * tileSize, r * tileSize, tileSize, tileSize);
                        }
                    }
                }
            }
        }

        // paint pieces
        for (Piece piece : pieceList) {
            piece.paint(g2d);
        }

    }

    private void drawSquareWithCircle(Graphics g, int col, int row) {
        int x = col * tileSize;
        int y = row * tileSize;
        int borderThickness = 5; // עובי המסגרת

        // צביעת מסגרת הריבוע
        g.setColor(Color.RED);
        g.fillRect(x, y, tileSize, borderThickness); // עליון
        g.fillRect(x, y, borderThickness, tileSize); // שמאל
        g.fillRect(x + tileSize - borderThickness, y, borderThickness, tileSize); // ימין
        g.fillRect(x, y + tileSize - borderThickness, tileSize, borderThickness); // תחתון
    }

    public void makeMove(Move move) {
        boolean pawnMoveSuccess = true;
        if (move.piece.name.equals("Pawn")) {
            pawnMoveSuccess = movePawn(move);
        } else if (move.piece.name.equals("King")) {
            moveKing(move);
        }

        if (pawnMoveSuccess) {
            if (!move.piece.name.equals("Pawn") || !(Math.abs(move.piece.row - move.newRow) == 2)) {
                enPassantTile = -1;
            }
            setLastMove(move.piece.col, move.piece.row, move.newCol, move.newRow, move.piece);
            move.piece.col = move.newCol;
            move.piece.row = move.newRow;
            move.piece.xPos = move.newCol * tileSize;
            move.piece.yPos = move.newRow * tileSize;
            move.piece.isFirstMove = false;
            if (move.captured != null) {
                capture(move.captured);
                audioPlayer.playCaptureSound();
            }
            else {
                audioPlayer.playMovingPieceSound();
            }
            if (isWhiteToMove) {
                savedStates.push(fenCurrentPosition);
                fenCurrentPosition = convertPiecesToFEN();
            }
            isWhiteToMove = !isWhiteToMove;
            if (isWhiteToMove) {
                ++numOfTurns;
            }
            ++numOfTurnWithoutCaptureOrPawnMove;
            updateGameState(true);
            showScore.calculateScore();
        }
    }

    public void setLastMove(int fromC, int fromR, int toC, int toR, Piece lastToMove) {
        this.fromC = fromC;
        this.fromR = fromR;
        this.toC = toC;
        this.toR = toR;
        this.lastToMove = lastToMove;
        repaint();
    }

    public void capture(Piece piece) {
        pieceList.remove(piece);
        numOfTurnWithoutCaptureOrPawnMove = 0;
    }

    public boolean isValidMove(Move move, boolean isExecuting) {

        if (isGameOver) {
            return false;
        }
        if (move.piece.isWhite != isWhiteToMove) {
            return false;
        }
        if (sameTeam(move.piece, move.captured)) {
            return false;
        }
        if (!move.piece.isValidMovement(move.newCol, move.newRow)) {
            return false;
        }
        if (move.piece.moveCollidesWithPiece(move.newCol, move.newRow)) {
            return false;
        }
        if (checkScanner.isMoveCausesCheck(move)) {
            if (isExecuting) {
                audioPlayer.playInvalidMoveBecauseOfCheckSound();
            }
            return false;
        }

        return true;
    }

    public boolean sameTeam(Piece p1, Piece p2) {
        if (p1 == null || p2 == null) {
            return false;
        }
        return p1.isWhite == p2.isWhite;
    }

    public int getTileNum(int col, int row) {
        return rows * row + col;
    }

    public void moveKing(Move move) {
        if (Math.abs(move.piece.col - move.newCol) == 2) {
            Piece rook;
            if (move.piece.col < move.newCol) {
                rook = getPiece(7, move.piece.row);
                rook.col = 5;
            } else {
                rook = getPiece(0, move.piece.row);
                rook.col = 3;
            }
            rook.xPos = rook.col * tileSize;
            audioPlayer.playCastlingSound();
        }
    }

    public boolean movePawn(Move move) {

        // en passant:
        int colorIndex = move.piece.isWhite ? 1 : -1;

        if (getTileNum(move.newCol, move.newRow) == enPassantTile) {
            move.captured = getPiece(move.newCol, move.newRow + colorIndex);
        }
        if (Math.abs(move.piece.row - move.newRow) == 2) {
            enPassantTile = getTileNum(move.newCol, move.newRow + colorIndex);
        } else {
            enPassantTile = -1;
        }

        // promotions:
        colorIndex = move.piece.isWhite ? 0 : 7;
        if (move.newRow == colorIndex) {
            return promotePawn(move);
        }
        numOfTurnWithoutCaptureOrPawnMove = -1;
        return true;
    }

    private boolean promotePawn(Move move) {
        PromotionDialog promotionDialog = new PromotionDialog(parentFrame, move.piece.isWhite);
        String choice = promotionDialog.getSelection();
        if (choice != null) {
            promotePawnTo(move, choice);
            capture(move.piece);
        } else {
            System.out.println("No selection made");
            // promotePawn(move);
            selectedPiece.xPos = selectedPiece.col * tileSize;
            selectedPiece.yPos = selectedPiece.row * tileSize;
            selectedPiece = null;
            repaint();
            input.selectedX = -1;
            input.selectedY = -1;
            return false;
        }
        return true;
    }

    private void promotePawnTo(Move move, String choice) {
        switch (choice) {
            case "Queen":
                pieceList.add(new Queen(this, move.newCol, move.newRow, move.piece.isWhite));
                repaint();
                break;
            case "Rook":
                pieceList.add(new Rook(this, move.newCol, move.newRow, move.piece.isWhite));
                repaint();
                break;
            case "Bishop":
                pieceList.add(new Bishop(this, move.newCol, move.newRow, move.piece.isWhite));
                repaint();
                break;
            case "Knight":
                pieceList.add(new Knight(this, move.newCol, move.newRow, move.piece.isWhite));
                repaint();
                break;
        }
    }

    public void updateGameState(boolean isRealBoard) {
        Piece king = findKing(isWhiteToMove);
        if (checkScanner.isGameOver(king)) {
            if (checkScanner.isChecking(this)) {
                // System.out.println(isWhiteToMove ? "black wins!" : "white wins!");
                if (isRealBoard){
                    input.isStatusChanged = true;
                    input.isCheckMate = true;
                    input.isStaleMate = false;
                    input.isWhiteTurn = isWhiteToMove;
                    audioPlayer.playCheckMateSound();
                }
            } else {
                // System.out.println("stale mate! draw!");
                if (isRealBoard){
                    input.isStatusChanged = true;
                    input.isCheckMate = false;
                    input.isStaleMate = true;
                    input.isWhiteTurn = isWhiteToMove;
                    audioPlayer.playDrawSound();
                }
            }
//            isGameOver = true;
        } else if (insufficientMaterial(true) && insufficientMaterial(false)) {
            // System.out.println("insufficientMaterial! draw!");
//            isGameOver = true;
            if (isRealBoard){
                input.isStatusChanged = true;
                input.isCheckMate = false;
                input.isStaleMate = false;
                input.isWhiteTurn = isWhiteToMove;
                audioPlayer.playDrawSound();
            }
        }
        else if (checkScanner.isChecking(this)) {
            audioPlayer.playCheckSound();
            repaint();
        }
    }

    private boolean insufficientMaterial(boolean isWhite) {
        ArrayList<String> names = pieceList.stream()
                .filter(p -> p.isWhite == isWhite)
                .map(p -> p.name)
                .collect(Collectors.toCollection(ArrayList::new));
        if (names.contains("Queen") || names.contains("Rook") || names.contains("Pawn")) {
            return false;
        }
        return names.size() < 3;
    }

    public boolean getIsWhiteToMove() {
        return this.isWhiteToMove;
    }

    public double getNumOfPieces() {
        return pieceList.size();
    }

    public Piece getPieceByNumber(int randomNum) {
        return pieceList.get(randomNum);
    }

    public String convertPiecesToFEN() {
        StringBuilder fen = new StringBuilder();
        for (int row = 0; row < 8; row++) {
            int emptySquares = 0;
            for (int col = 0; col < 8; col++) {
                Piece piece = getPiece(col, row);
                if (piece == null) {
                    emptySquares++;
                } else {
                    if (emptySquares > 0) {
                        fen.append(emptySquares);
                        emptySquares = 0;
                    }
                    fen.append(piece.getRepresentation());
                }
            }
            if (emptySquares > 0) {
                fen.append(emptySquares);
            }
            if (row < 7) {
                fen.append('/');
            }
        }
        fen.append(isWhiteToMove ? " w " : " b "); // תור השחקן הבא

        // castling
        Piece wKing = getPiece(4, 7);
        Piece bKing = getPiece(4, 0);

        Piece bqr = getPiece(0, 0);
        if (bqr instanceof Rook && bqr.isFirstMove && bKing instanceof King && bKing.isFirstMove) {
             fen.append("q");
        }
        Piece bkr = getPiece(7, 0);
        if (bkr instanceof Rook && bkr.isFirstMove && bKing instanceof King && bKing.isFirstMove) {
             fen.append("k");
        }
        Piece wqr = getPiece(0, 7);
        if (wqr instanceof Rook && wqr.isFirstMove && wKing instanceof King && wKing.isFirstMove) {
             fen.append("Q");
        }
        Piece wkr = getPiece(7, 7);
        if (wkr instanceof Rook && wkr.isFirstMove && wKing instanceof King && wKing.isFirstMove) {
             fen.append("K");
        }
        if ((!(bqr instanceof Rook && bqr.isFirstMove && bKing instanceof King && bKing.isFirstMove) && !(bkr instanceof Rook && bkr.isFirstMove && bKing instanceof King && bKing.isFirstMove)) || !((wqr instanceof Rook && wqr.isFirstMove && wKing instanceof King && wKing.isFirstMove) || !(wkr instanceof Rook && wkr.isFirstMove && wKing instanceof King && wKing.isFirstMove))){
            fen.append('-');
        }
        fen.append(" ");

        // en passant
        int colorIndex = isWhiteToMove ? 1 : -1;
        // System.out.println(lastToMove + " " + );
        if (lastToMove instanceof Pawn && Math.abs(toR - fromR) == 2) {
            int col = fromC;
            int row = fromR + colorIndex;
            fen.append(squareToLetters(col, row));
        }
        else {
            fen.append('-');
        }

        // מספר החצאים
        fen.append(" ");
        fen.append(numOfTurnWithoutCaptureOrPawnMove);

        // מספר התורות
        fen.append(" ");
        fen.append(numOfTurns);

        // System.out.println(fen);
        return fen.toString();
    }

    public String squareToLetters(int col, int row) {
        String namesOfRows = "87654321";
        String namesOfCols = "abcdefgh";
        StringBuilder squareName = new StringBuilder();
        squareName.append (namesOfCols.charAt(col));
        squareName.append (namesOfRows.charAt(row));
        // System.out.println(col + " " + row + " " + squareName);
        return squareName.toString();
    }

    public void goBack() {
        fenCurrentPosition = savedStates.pop();
        input.engine.stopEngine();
        fromC = -1;
        fromR = -1;
        toC = -1;
        toR = -1;
        lastToMove = null;
        selectedPiece = null;
        loadPiecesFromFen(fenCurrentPosition);
    }

}

