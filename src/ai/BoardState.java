package ai;


import main.Board;
import main.CheckScanner;
import main.Move;
import main.setting.ChoosePlayFormat;
import pieces.*;

import java.util.ArrayList;
import java.util.stream.Collectors;

public class BoardState {
    public static int numOfNodes = 0;

    int cols = 8;
    int rows = 8;
    // board state
    public String fenCurrentPosition;
    ArrayList<Piece> pieceList = new ArrayList<>();
    Move lastMove;
    int fromC = -1, fromR = -1, toC = -1, toR = -1;
    public Piece lastToMove;
    ArrayList<BoardState> childNodes;

    public int enPassantTile = -1;
    // turn
    private boolean isWhiteToMove = true;
    public int numOfTurns = 0;
    public int numOfTurnWithoutCaptureOrPawnMove = 0;
    public boolean isGameOver = false;

    public CheckScanner checkScanner;

    // constructor
    public BoardState(String fenCurrentPosition, Move lastMove) {
        checkScanner = new CheckScanner(this);
        this.fenCurrentPosition = fenCurrentPosition;
        loadPiecesFromFen(fenCurrentPosition);
        setLastMove(lastMove);
        ++BoardState.numOfNodes;
    }

    // יצירת הלוח
    public void loadPiecesFromFen(String fenCurrentPosition) {
        String[] parts = fenCurrentPosition.split(" ");

        // pieceList.clear();
        ArrayList<Piece> newState = new ArrayList<>();
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
            } else {
                boolean isWhite = Character.isUpperCase(ch);
                ch = Character.toLowerCase(ch);
                switch (ch) {
                    case 'r':
                        newState.add(new Rook(this, col, row, isWhite));
                        break;
                    case 'n':
                        newState.add(new Knight(this, col, row, isWhite));
                        break;
                    case 'b':
                        newState.add(new Bishop(this, col, row, isWhite));
                        break;
                    case 'q':
                        newState.add(new Queen(this, col, row, isWhite));
                        break;
                    case 'k':
                        newState.add(new King(this, col, row, isWhite));
                        break;
                    case 'p':
                        newState.add(new Pawn(this, col, row, isWhite, col));
                        break;
                }
                col++;
            }
        }
        pieceList = newState;

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
    }

    public void setLastMove(Move lastMove) {
        this.lastMove = lastMove;
    }
    public Move getLastMove() {
        return lastMove;
    }
    private void setChildNodes() {
        if (childNodes != null) {
            childNodes.clear();
        }
        for (Move move : getAllPossibleMoves()) {
            BoardState boardState = new BoardState(makeMoveAndGetFen(move), move);
            childNodes.add(boardState);
        }
    }
    public Move[] getAllPossibleMoves() {
        ArrayList<Move> possibleMoves = new ArrayList<>();
        for (Piece piece : pieceList) {
            for (Move move : piece.getValidMoves(this)) {
                if (makeMoveToCheckIt(move)) {
                    possibleMoves.add(move);
                }
            }
        }
        return possibleMoves.toArray(new Move[0]);
    }



    // גטרים לכלים על הלוח
    public Piece getPiece(int col, int row) {

        for (Piece piece : pieceList) {
            if (piece.col == col && piece.row == row) {
                return piece;
            }
        }


        return null;
    }

    public Piece[] getAllPieces() {
        return pieceList.toArray(new Piece[0]);
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


    // פונקציות לבדיקת חוקיות מהלכים ומצב המשחק
    public boolean getIsWhiteToMove() {
        return this.isWhiteToMove;
    }

    public Piece findKing(boolean isWhite) {
        for (Piece piece : pieceList) {
            if (piece.isWhite == isWhite && piece.name.equals("King")) {
                return piece;
            }
        }
        return null;
    }

    public boolean isValidMove(Move move) {
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

    public boolean insufficientMaterial(boolean isWhite) {
        ArrayList<String> names = pieceList.stream()
                .filter(p -> p.isWhite == isWhite)
                .map(p -> p.name)
                .collect(Collectors.toCollection(ArrayList::new));
        if (names.contains("Queen") || names.contains("Rook") || names.contains("Pawn")) {
            return false;
        }
        return names.size() < 3;
    }


    // גטרים למצב הלוח
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
        // System.out.println(col + " " + row + " " + squareName);
        return String.valueOf(namesOfCols.charAt(col)) +
                namesOfRows.charAt(row);
    }





    // פונקציות לחישובים בלי פגיע אמיתית במצב הלוח
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

            // cancel move
            piece.col = tempMovePC;
            piece.row = tempMovePR;
            isWhiteToMove = !isWhiteToMove;
            loadPiecesFromFen(tempFen);
            //System.out.println(tempFen);
        }
        return success;
    }

    public int makeMoveAndGetValue(Move move) {
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

            // cancel move
            piece.col = tempMovePC;
            piece.row = tempMovePR;
            isWhiteToMove = !isWhiteToMove;
            loadPiecesFromFen(tempFen);
            //System.out.println(tempFen);
        }
        return 0;
    }

    private String makeMoveAndGetFen(Move move) {
        String newFen = null;
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

            newFen = convertPiecesToFEN();

            // cancel move
            piece.col = tempMovePC;
            piece.row = tempMovePR;
            isWhiteToMove = !isWhiteToMove;
            loadPiecesFromFen(tempFen);
            //System.out.println(tempFen);
        }
        return newFen;
    }

    private boolean movePawnForClone(Move move) {
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
                return false;
            }
//            else {
//                if (SettingPanel.skillLevel != 0) {
//                    promotePawnToForClone(move, input.engine.promotionChoice);
//                }
//                else {
//                    promotePawnToForClone(move, input.randomMoveEngine.promotionChoice);
//                }
//                pieceList.remove(move.piece);
//            }
        }
        numOfTurnWithoutCaptureOrPawnMove = -1;
        return true;
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

    public void setLastMove(int fromC, int fromR, int toC, int toR, Piece lastToMove) {
        this.fromC = fromC;
        this.fromR = fromR;
        this.toC = toC;
        this.toR = toR;
        this.lastToMove = lastToMove;
    }

    public void capture(Piece piece) {
        pieceList.remove(piece);
        numOfTurnWithoutCaptureOrPawnMove = 0;
    }

    // ביצוע מהלכים על אמת
    public int makeEngineMove(Move move, String promotionChoice, Board realBoard) {
        int moveKind = 0;
        Piece piece = getPiece(move.piece.col, move.piece.row);
        boolean pawnMoveSuccess = true;
        if (piece.name.equals("Pawn")) {
            pawnMoveSuccess = moveEnginePawn(move, promotionChoice);
            if (pawnMoveSuccess) {
                moveKind = 2;
            }
        } else if (piece.name.equals("King")) {
            if(moveKing(move)) {
                moveKind = 1;
            }
        }

        if (pawnMoveSuccess) {
            if (!piece.name.equals("Pawn") || !(Math.abs(piece.row - move.newRow) == 2)) {
                enPassantTile = -1;
            }
            if (move.captured != null && getPiece(move.captured.col, move.captured.row) != null) {
                capture(getPiece(move.captured.col, move.captured.row));
            }
            setLastMove(piece.col, piece.row, move.newCol, move.newRow, move.piece);

            piece.col = move.newCol;
            piece.row = move.newRow;
            piece.isFirstMove = false;
            isWhiteToMove = !isWhiteToMove;
            if (isWhiteToMove) {
                ++numOfTurns;
            }
            ++numOfTurnWithoutCaptureOrPawnMove;
        }
        return moveKind;
    }

    public void makePlayerMove(Move move, Board realBoard) {
        Piece piece = getPiece(move.piece.col, move.piece.row);
        boolean pawnMoveSuccess = true;
        if (piece.name.equals("Pawn")) {
            pawnMoveSuccess = movePlayerPawn(move, realBoard);
        } else if (piece.name.equals("King")) {
            realBoard.moveKing(move);
        }

        if (pawnMoveSuccess) {
            if (!piece.name.equals("Pawn") || !(Math.abs(piece.row - move.newRow) == 2)) {
                enPassantTile = -1;
            }
            if (move.captured != null && getPiece(move.captured.col, move.captured.row) != null) {
                capture(getPiece(move.captured.col, move.captured.row));
            }
            setLastMove(piece.col, piece.row, move.newCol, move.newRow, move.piece);

            piece.col = move.newCol;
            piece.row = move.newRow;
            piece.isFirstMove = false;
            isWhiteToMove = !isWhiteToMove;
            if (isWhiteToMove) {
                ++numOfTurns;
            }
            ++numOfTurnWithoutCaptureOrPawnMove;
        }
    }

    public boolean moveKing(Move move) {
        if (Math.abs(move.piece.col - move.newCol) == 2) {
            Piece rook;
            int rookEndCol;
            if (move.piece.col < move.newCol) {
                rook = getPiece(7, move.piece.row);
                rookEndCol = 5;
            } else {
                rook = getPiece(0, move.piece.row);
                rookEndCol = 3;
            }

            move.piece.col = move.newCol;
            move.piece.row = move.newRow;
            rook.col = rookEndCol;
            rook.row = move.newRow;
            return true;
        }
        return false;
    }

    public boolean moveEnginePawn(Move move, String promotionChoice) {
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
            promotePawnTo(move, promotionChoice);
        }
        numOfTurnWithoutCaptureOrPawnMove = -1;
        return true;
    }

    public boolean movePlayerPawn(Move move, Board realBoard) {
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
            realBoard.promotePawn(move);
            promotePawnTo(move, realBoard.promotionChoice);
        }
        numOfTurnWithoutCaptureOrPawnMove = -1;
        return true;
    }

    public void promotePawnTo(Move move, String choice) {
        Piece piece = getPiece(move.piece.col, move.piece.row);
        switch (choice) {
            case "q":
                pieceList.add(new Queen(this, move.newCol, move.newRow, piece.isWhite));
                break;
            case "r":
                pieceList.add(new Rook(this, move.newCol, move.newRow, piece.isWhite));
                break;
            case "b":
                pieceList.add(new Bishop(this, move.newCol, move.newRow, piece.isWhite));
                break;
            case "n":
                pieceList.add(new Knight(this, move.newCol, move.newRow, piece.isWhite));
                break;
        }
        capture(piece);
    }




}
