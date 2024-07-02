package main;

import GUI.AudioPlayer;
import main.setting.ChoosePlayFormat;
import main.setting.SettingPanel;
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
    public Input input = new Input(this);
    public CheckScanner checkScanner = new CheckScanner(this);
    AudioPlayer audioPlayer = new AudioPlayer();
    ShowScore showScore = new ShowScore(this);

    int fromC = -1, fromR = -1, toC = -1, toR = -1;
    int hintFromC = -1, hintFromR = -1, hintToC = -1, hintToR = -1;
    public Piece lastToMove;
    public int enPassantTile = -1;
    public int enPassantTileClone = -1;
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
        loadPiecesFromFen(fenCurrentPosition, true);
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

    public void loadPiecesFromFen(String fenCurrentPosition, boolean isRealBoard) {
        String[] parts = fenCurrentPosition.split(" ");

        pieceList.clear();

        // position
        String position = parts[0];
        if (ChoosePlayFormat.isPlayingWhite) {
            int row = 0;
            int col = 0;
            for (int i = 0; i < position.length(); i++) {
                char ch = position.charAt(i);
                if (ch == '/') {
                    row++;
                    col = 0;
                } else if (Character.isDigit(ch)) {
                    col += Character.getNumericValue(ch);
                } else {
                    boolean isWhite = Character.isUpperCase(ch);
                    ch = Character.toLowerCase(ch);
                    switch (ch) {
                        case 'r':
                            pieceList.add(new Rook(this, col, row, isWhite));
                            break;
                        case 'n':
                            pieceList.add(new Knight(this, col, row, isWhite));
                            break;
                        case 'b':
                            pieceList.add(new Bishop(this, col, row, isWhite));
                            break;
                        case 'q':
                            pieceList.add(new Queen(this, col, row, isWhite));
                            break;
                        case 'k':
                            pieceList.add(new King(this, col, row, isWhite));
                            break;
                        case 'p':
                            pieceList.add(new Pawn(this, col, row, isWhite, col));
                            break;
                    }
                    col++;
                }
            }
        }
        else {
            int row = 0;
            int col = 0;
            for (int i = 0; i < position.length(); i++) {
                char ch = position.charAt(i);
                if (ch == '/') {
                    row++;
                    col = 0;
                } else if (Character.isDigit(ch)) {
                    col += Character.getNumericValue(ch);
                } else {
                    boolean isWhite = Character.isUpperCase(ch);
                    ch = Character.toLowerCase(ch);
                    switch (ch) {
                        case 'r':
                            pieceList.add(new Rook(this, col, row, isWhite));
                            break;
                        case 'n':
                            pieceList.add(new Knight(this, col, row, isWhite));
                            break;
                        case 'b':
                            pieceList.add(new Bishop(this, col, row, isWhite));
                            break;
                        case 'q':
                            pieceList.add(new Queen(this, col, row, isWhite));
                            break;
                        case 'k':
                            pieceList.add(new King(this, col, row, isWhite));
                            break;
                        case 'p':
                            pieceList.add(new Pawn(this, col, row, isWhite, col));
                            break;
                    }
                    col++;
                }
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

        // num Of Turn Without Capture Or Pawn Move
        numOfTurnWithoutCaptureOrPawnMove = Character.getNumericValue(parts[4].charAt(0));

        // num of turns
        numOfTurns = Character.getNumericValue(parts[5].charAt(0));

        if (isRealBoard) {repaint();}
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
        if (ChoosePlayFormat.isPlayingWhite) {
            g.fillRect(fromC * tileSize, fromR * tileSize, tileSize, tileSize);
        }
        else {
            g.fillRect((cols - 1 - fromC) * tileSize, (rows - 1 - fromR) * tileSize, tileSize, tileSize);
        }
        g.setColor(new Color(55, 255, 0, 186));
        if (ChoosePlayFormat.isPlayingWhite) {
            g.fillRect(toC * tileSize, toR * tileSize, tileSize, tileSize);
        }
        else {
            g.fillRect((cols - 1 - toC) * tileSize, (rows - 1 - toR) * tileSize, tileSize, tileSize);
        }


        // paint engine hint
        g.setColor(new Color(0, 255, 215, 158));
        if (ChoosePlayFormat.isPlayingWhite) {
            g.fillRect(hintFromC * tileSize, hintFromR * tileSize, tileSize, tileSize);
        }
        else {
            g.fillRect((cols - 1 - hintFromC) * tileSize, (rows - 1 - hintFromR) * tileSize, tileSize, tileSize);
        }
        g.setColor(new Color(47, 206, 255, 237));
        if (ChoosePlayFormat.isPlayingWhite) {
            g.fillRect(hintToC * tileSize, hintToR * tileSize, tileSize, tileSize);
        }
        else {
            g.fillRect((cols - 1 - hintToC) * tileSize, (rows - 1 - hintToR) * tileSize, tileSize, tileSize);
        }

        // paint the border of the king red if it's under attack
        if (checkScanner.isChecking(this)) {
            Piece king = findKing(isWhiteToMove);
            //g2d.setColor(new Color(255, 0, 0, 237)); // אדום חצי שקוף
            //g2d.fillRect(king.col * tileSize, king.row * tileSize, tileSize, tileSize);
            if (ChoosePlayFormat.isPlayingWhite) {
                drawSquareWithCircle(g ,king.col, king.row);
            }
            else {
                drawSquareWithCircle(g ,cols - 1 - king.col, rows - 1 - king.row);
            }
        }

        // paint highLights
        if (selectedPiece != null) {
            if (selectedPiece.isWhite == isWhiteToMove) {
                g2d.setColor(new Color(0, 0, 255, 128)); // כחול חצי שקוף
                if (ChoosePlayFormat.isPlayingWhite) {
                    g2d.fillRect(selectedPiece.col * tileSize, selectedPiece.row * tileSize, tileSize, tileSize);
                }
                else {
                    g2d.fillRect((cols - 1 - selectedPiece.col) * tileSize, (rows - 1 - selectedPiece.row) * tileSize, tileSize, tileSize);
                }
                if (checkScanner.isChecking(this)) {
                    Piece king = findKing(isWhiteToMove);
                    //g2d.setColor(new Color(255, 0, 0, 237)); // אדום חצי שקוף
                    //g2d.fillRect(king.col * tileSize, king.row * tileSize, tileSize, tileSize);
                    if (ChoosePlayFormat.isPlayingWhite) {
                        drawSquareWithCircle(g ,king.col, king.row);
                    }
                    else {
                        drawSquareWithCircle(g ,cols - 1 - king.col, rows - 1 - king.row);
                    }
                }
            }

            for (int c = 0; c < cols; c++) {
                for (int r = 0; r < rows; r++) {
                    if (isValidMove(new Move(this, selectedPiece, c, r), false)) {
                        if (getPiece(c, r) == null) {
                            // ציור עיגול במרכז הריבוע
                            g.setColor(new Color(72, 255, 0, 158));
                            int diameter = tileSize / 3;
                            int circleX;
                            int circleY;
                            if (ChoosePlayFormat.isPlayingWhite) {
                                circleX = c * tileSize + (tileSize - diameter) / 2;
                                circleY = r * tileSize + (tileSize - diameter) / 2;
                            }
                            else {
                                circleX = (cols - 1 - c) * tileSize + (tileSize - diameter) / 2;
                                circleY = (rows - 1 - r) * tileSize + (tileSize - diameter) / 2;
                            }
                            g.fillOval(circleX, circleY, diameter, diameter);
                        } else {
                            if (ChoosePlayFormat.isPlayingWhite) {
                                drawSquareWithCircle(g, c, r);
                            }
                            else {
                                drawSquareWithCircle(g, (cols - 1 - c), (rows - 1 - r));
                            }
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
        //System.out.println(convertPiecesToFEN());
        Piece piece = getPiece(move.piece.col, move.piece.row);
        boolean pawnMoveSuccess = true;
        if (piece.name.equals("Pawn")) {
            pawnMoveSuccess = movePawn(move);
        } else if (piece.name.equals("King")) {
            moveKing(move);
        }

        if (pawnMoveSuccess) {
            if (!piece.name.equals("Pawn") || !(Math.abs(piece.row - move.newRow) == 2)) {
                enPassantTile = -1;
            }
            if (move.captured != null && getPiece(move.captured.col, move.captured.row) != null) {
                capture(getPiece(move.captured.col, move.captured.row));
                audioPlayer.playCaptureSound();
            }
            else {
                audioPlayer.playMovingPieceSound();
            }
            setLastMove(piece.col, piece.row, move.newCol, move.newRow, move.piece);
            piece.col = move.newCol;
            piece.row = move.newRow;
            //System.out.println(move.piece.col + " " + move.piece.row);
            //System.out.println(convertPiecesToFEN());
            if (ChoosePlayFormat.isPlayingWhite) {
                piece.xPos = move.newCol * tileSize;
                piece.yPos = move.newRow * tileSize;
                //System.out.println(move.piece.xPos + " " + move.piece.yPos);
            }
            else {
                piece.xPos = (cols - 1 - move.newCol) * tileSize;
                piece.yPos = (rows - 1 - move.newRow) * tileSize;
            }
            piece.isFirstMove = false;
            isWhiteToMove = !isWhiteToMove;
            if (isWhiteToMove) {
                ++numOfTurns;
            }
            ++numOfTurnWithoutCaptureOrPawnMove;
            updateGameState(true);
            if (isWhiteToMove) {
                savedStates.push(fenCurrentPosition);
                fenCurrentPosition = convertPiecesToFEN();
            }
            showScore.calculateScore();
        }
    }

    public boolean makeMoveToCheckIt(Move move) {
        String tempFen = convertPiecesToFEN();
        Piece piece = getPiece(move.piece.col, move.piece.row);
        boolean success = false;
        boolean pawnMoveSuccess = true;
        if (piece.name.equals("Pawn")) {
            pawnMoveSuccess = movePawnForClone(move);
        } else if (piece.name.equals("King")) {
            moveKingForClone(move);
        }

         if (pawnMoveSuccess) {
            if (!piece.name.equals("Pawn") || !(Math.abs(piece.row - move.newRow) == 2)) {
                enPassantTile = -1;
            }
            if (move.captured != null && getPiece(move.captured.col, move.captured.row) != null) {
                capture(getPiece(move.captured.col, move.captured.row));
            }
            int tempMovePC = piece.col;
            int tempMovePR = piece.row;
            piece.col = move.newCol;
            piece.row = move.newRow;
            isWhiteToMove = !isWhiteToMove;
            if (isWhiteToMove) {
                ++numOfTurns;
            }
            ++numOfTurnWithoutCaptureOrPawnMove;
            if (!checkScanner.isCheckingForClone(this)) {
                success = true;
            }

            piece.col = tempMovePC;
            piece.row = tempMovePR;
            loadPiecesFromFen(tempFen, true);
            //System.out.println(tempFen);
        }
        return success;
    }

    private boolean movePawnForClone(Move move) {
        // en passant:
        int colorIndex = move.piece.isWhite ? 1 : -1;

        if (getTileNum(move.newCol, move.newRow) == enPassantTile) {
            move.captured = getPiece(move.newCol, move.newRow + colorIndex);
        }
        if (Math.abs(move.piece.row - move.newRow) == 2) {
            enPassantTileClone = getTileNum(move.newCol, move.newRow + colorIndex);
        } else {
            enPassantTile = -1;
        }

        // promotions:
        colorIndex = move.piece.isWhite ? 0 : 7;
        if (move.newRow == colorIndex) {
            if (!ChoosePlayFormat.isOnePlayer || ChoosePlayFormat.isPlayingWhite == isWhiteToMove) { // כאן אמור להיות אם זה שני שחקנים
                return false;
            }
            else {
                if (SettingPanel.skillLevel != 0) {
                    promotePawnToForClone(move, input.engine.promotionChoice);
                }
                else {
                    promotePawnToForClone(move, input.randomMoveEngine.promotionChoice);
                }
                pieceList.remove(move.piece);
            }
        }
        numOfTurnWithoutCaptureOrPawnMove = -1;
        return true;
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
            //if (isExecuting) System.out.println(1);
            return false;
        }
        if (move.piece.isWhite != isWhiteToMove) {
            //if (isExecuting) System.out.println(2);
            return false;
        }
        if (sameTeam(move.piece, move.captured)) {
            //if (isExecuting) System.out.println(3);
            return false;
        }
        if (!move.piece.isValidMovement(move.newCol, move.newRow)) {
            //if (isExecuting) System.out.println(4);
            return false;
        }
        if (move.piece.moveCollidesWithPiece(move.newCol, move.newRow)) {
            //if (isExecuting) System.out.println(5);
            return false;
        }
        if (checkScanner.isMoveCausesCheck(move)) {
            if (isExecuting) {
                audioPlayer.playInvalidMoveBecauseOfCheckSound();
            }
            //if (isExecuting) System.out.println(6);
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
            rook.xPos = getXFromCol(rook.col);
            audioPlayer.playCastlingSound();
        }
    }

    public void moveKingForClone(Move move) {
        if (Math.abs(move.piece.col - move.newCol) == 2) {
            Piece rook;
            if (move.piece.col < move.newCol) {
                rook = getPiece(7, move.piece.row);
                rook.col = 5;
            } else {
                rook = getPiece(0, move.piece.row);
                rook.col = 3;
            }
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
            if (!ChoosePlayFormat.isOnePlayer || ChoosePlayFormat.isPlayingWhite == isWhiteToMove) { // כאן אמור להיות אם זה שני שחקנים
                if(!promotePawn(move)) {
                    return false;
                }
            }
            else {
                if (SettingPanel.skillLevel != 0) {
                    promotePawnTo(move, input.engine.promotionChoice);
                }
                else {
                    promotePawnTo(move, input.randomMoveEngine.promotionChoice);
                }
                capture(move.piece);
            }
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
            selectedPiece.xPos = getXFromCol(selectedPiece.col);
            selectedPiece.yPos = getYFromRow(selectedPiece.row);
            selectedPiece = null;
            repaint();
            input.selectedX = -1;
            input.selectedY = -1;
            return false;
        }
        return true;
    }

    private void promotePawnTo(Move move, String choice) {
        Piece piece = getPiece(move.piece.col, move.piece.row);
        switch (choice) {
            case "q":
                pieceList.add(new Queen(this, move.newCol, move.newRow, piece.isWhite));
                repaint();
                break;
            case "r":
                pieceList.add(new Rook(this, move.newCol, move.newRow, piece.isWhite));
                repaint();
                break;
            case "b":
                pieceList.add(new Bishop(this, move.newCol, move.newRow, piece.isWhite));
                repaint();
                break;
            case "n":
                pieceList.add(new Knight(this, move.newCol, move.newRow, piece.isWhite));
                repaint();
                break;
        }
        capture(piece);
    }

    private void promotePawnToForClone(Move move, String choice) {
        switch (choice) {
            case "q":
                pieceList.add(new Queen(this, move.newCol, move.newRow, move.piece.isWhite));
                break;
            case "r":
                pieceList.add(new Rook(this, move.newCol, move.newRow, move.piece.isWhite));
                break;
            case "b":
                pieceList.add(new Bishop(this, move.newCol, move.newRow, move.piece.isWhite));
                break;
            case "n":
                pieceList.add(new Knight(this, move.newCol, move.newRow, move.piece.isWhite));
                break;
        }
        capture(move.piece);
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
                    if (ChoosePlayFormat.isOnePlayer && ChoosePlayFormat.isPlayingWhite == isWhiteToMove) {
                        audioPlayer.playLosingSound();
                    }
                    else {
                        audioPlayer.playCheckMateSound();
                    }
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

    public int getNumOfPieces(boolean isWhite) {
        int counter = 0;
        for (Piece piece : pieceList) {
            if (piece.isWhite == isWhite) {
                ++counter;
            }
        }
        return counter;
    }

    public Piece getPieceByNumber(int randomNum, boolean isWhite) {
        ArrayList<Piece> onlyWantedColorPieces = new ArrayList<>();
        for (Piece piece : pieceList) {
            if (piece.isWhite == isWhite) {
                onlyWantedColorPieces.add(piece);
            }
        }
        return onlyWantedColorPieces.get(randomNum);
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
        if(!ChoosePlayFormat.isOnePlayer || ChoosePlayFormat.isPlayingWhite == isWhiteToMove) {
            //System.out.println("current position: " + fenCurrentPosition);
            fenCurrentPosition = savedStates.pop();
            //System.out.println("changing position to: " + fenCurrentPosition);
            input.engine.stopEngine();
            fromC = -1;
            fromR = -1;
            toC = -1;
            toR = -1;
            lastToMove = null;
            input.selectedX = input.selectedY = -1;
            selectedPiece = null;
            audioPlayer.playGoBackSound();
            loadPiecesFromFen(fenCurrentPosition, true);
            if (ChoosePlayFormat.isOnePlayer && ChoosePlayFormat.isPlayingWhite != isWhiteToMove) {
                input.makeEngineMove();
            }
        }
        else {
            System.out.println("doing nothing");
        }
    }

    public int getXFromCol(int col){
        if (ChoosePlayFormat.isPlayingWhite) {
            return col * tileSize;
        }
        else {
            return (cols - 1 - col) * tileSize;
        }
    }
    public int getYFromRow(int row){
        if (ChoosePlayFormat.isPlayingWhite) {
            return row * tileSize;
        }
        else {
            return (rows - 1 - row) * tileSize;
        }
    }
    public int getColFromX(int x){
        if (ChoosePlayFormat.isPlayingWhite) {
            return x / tileSize;
        }
        else {
            return (cols - 1) - x / tileSize;
        }
    }
    public int getRowFromY(int y){
        if (ChoosePlayFormat.isPlayingWhite) {
            return y / tileSize;
        }
        else {
            return (rows - 1) - y / tileSize;
        }
    }

    public void restart() {
        isGameOver = false;
        input.isStatusChanged = false;
        fromC = -1; fromR = -1; toC = -1; toR = -1;
        hintFromC = -1; hintFromR = -1; hintToC = -1; hintToR = -1;
        loadPiecesFromFen(fenStartingPosition, true);
        Main.updateScores(0, 0);
        audioPlayer.playHintSound();
        if (ChoosePlayFormat.isOnePlayer && ChoosePlayFormat.isPlayingWhite != isWhiteToMove) {
            input.makeEngineMove();
        }
    }

    public void refresh() {
        savedStates.push(fenCurrentPosition);
        fenCurrentPosition = convertPiecesToFEN();
        loadPiecesFromFen(fenCurrentPosition, true);
        if (ChoosePlayFormat.isOnePlayer && ChoosePlayFormat.isPlayingWhite != isWhiteToMove) {
            input.makeEngineMove();
        }
    }

    public Piece[] getAllPieces() {
        return pieceList.toArray(new Piece[0]);
    }



}

