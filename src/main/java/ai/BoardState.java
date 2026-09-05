package ai;


import main.Move;
import main.setting.ChoosePlayFormat;
import pieces.*;
import rules.ChessMove;
import rules.GameStatus;
import rules.Rules;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class BoardState {
    public static int numOfNodes = 0;

    public static final int COLS = 8;
    public static final int ROWS = 8;
    // board state
    public String fenCurrentPosition;
    ArrayList<Piece> pieceList = new ArrayList<>();
    Move lastMove;
    public int fromC = -1, fromR = -1, toC = -1, toR = -1;
    public boolean isLastMoveCastling = false;
    public boolean canWhiteCastleKingSide, canWhiteCastleQueenSide, canBlackCastleKingSide, canBlackCastleQueenSide;
    public boolean isLastMovePawn = false;
    public Piece lastToMove;
    ArrayList<BoardState> childNodes;

    public int enPassantTile = -1;
    // turn
    private boolean isWhiteToMove = true;
    public int numOfTurns = 0;
    public int numOfTurnWithoutCaptureOrPawnMove = 0;
    public boolean isGameOver = false;

    // constructor
    public BoardState(String fenCurrentPosition, Move lastMove) {
        this.fenCurrentPosition = fenCurrentPosition;
        loadPiecesFromFen(fenCurrentPosition);
        setLastMove(lastMove);
        // System.out.println(BoardState.numOfNodes);
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
        canWhiteCastleKingSide = canWhiteCastleQueenSide = canBlackCastleKingSide = canBlackCastleQueenSide = false;
        Piece bqr = getPiece(0, 0);
        if (bqr instanceof Rook) {
            bqr.isFirstMove = parts[2].contains("q");
            canBlackCastleQueenSide = parts[2].contains("q");
        }
        Piece bkr = getPiece(7, 0);
        if (bkr instanceof Rook) {
            bkr.isFirstMove = parts[2].contains("k");
            canBlackCastleKingSide = parts[2].contains("k");
        }
        Piece wqr = getPiece(0, 7);
        if (wqr instanceof Rook) {
            wqr.isFirstMove = parts[2].contains("Q");
            canWhiteCastleQueenSide = parts[2].contains("Q");
        }
        Piece wkr = getPiece(7, 7);
        if (wkr instanceof Rook) {
            wkr.isFirstMove = parts[2].contains("K");
            canWhiteCastleKingSide = parts[2].contains("K");
        }

        // en passant
        if (parts[3].equals("-")) {
            enPassantTile = -1;
        } else {
            enPassantTile = (7 - (parts[3].charAt(1) - '1')) * 8 + (parts[3].charAt(0) - 'a');
        }

        // half-move clock (plies since the last capture or pawn move) — parse the whole
        // number, not only its first digit (the old code read parts[4].charAt(0))
        numOfTurnWithoutCaptureOrPawnMove = parts.length > 4 ? Integer.parseInt(parts[4]) : 0;

        // full-move number
        numOfTurns = parts.length > 5 ? Integer.parseInt(parts[5]) : 1;
    }

    public void setLastMove(Move lastMove) {
        this.lastMove = lastMove;
    }

    public Move getLastMove() {
        return lastMove;
    }

    /**
     * Legal moves for the side to move, via the unified rules authority. Replaces the old
     * {@code getAllPossibleMovesForASide()} (list-based generator + {@code makeMoveToCheckIt},
     * which missed the two-square pawn push and threw {@code ConcurrentModificationException}
     * whenever a capture was available).
     */
    public List<ChessMove> getLegalMoves() {
        return Rules.legalMoves(toRulesFen());
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
            if (piece.isWhite == isWhite && piece instanceof King) {
                return piece;
            }
        }
        return null;
    }

    /**
     * Phase 2: delegates to the single rules authority ({@link rules.Rules}) instead of the
     * old {@code isValidMovement} / {@code moveCollidesWithPiece} / {@code CheckScanner}
     * pipeline. The legal-move set is cached per position (see {@link #ensureRulesCache()}) so
     * the 64-square sweeps in {@code Board.paintComponent} cost one generation, not 64.
     */
    public boolean isValidMove(Move move) {
        if (isGameOver) {
            return false;
        }
        if (move.piece == null || move.piece.isWhite != isWhiteToMove) {
            return false;
        }
        ensureRulesCache();
        int from = move.piece.row * 8 + move.piece.col;
        int to = move.newRow * 8 + move.newCol;
        return legalFromToCache.contains(from * 64 + to);
    }

    // --- Phase 2: unified rules authority ------------------------------------

    private String rulesFenCache;
    private Set<Integer> legalFromToCache;
    private GameStatus rulesStatusCache;

    /**
     * A FEN for {@link rules.Rules}, built from the live fields (piece positions, turn, the
     * four castling first-move flags, {@code enPassantTile}, and the move clocks). Independent
     * of {@code convertPiecesToFEN} / {@code loadPiecesFromFen} and their historical quirks.
     */
    public String toRulesFen() {
        char[] sq = new char[64];
        for (Piece p : pieceList) {
            if (p.col >= 0 && p.col < 8 && p.row >= 0 && p.row < 8) {
                sq[p.row * 8 + p.col] = p.getRepresentation();
            }
        }
        StringBuilder fen = new StringBuilder();
        for (int row = 0; row < 8; row++) {
            int empty = 0;
            for (int col = 0; col < 8; col++) {
                char c = sq[row * 8 + col];
                if (c == 0) {
                    empty++;
                    continue;
                }
                if (empty > 0) {
                    fen.append(empty);
                    empty = 0;
                }
                fen.append(c);
            }
            if (empty > 0) {
                fen.append(empty);
            }
            if (row < 7) {
                fen.append('/');
            }
        }
        fen.append(isWhiteToMove ? " w " : " b ");

        StringBuilder castle = new StringBuilder();
        Piece wKing = getPiece(4, 7);
        if (wKing instanceof King && wKing.isFirstMove) {
            Piece r = getPiece(7, 7);
            if (r instanceof Rook && r.isFirstMove) castle.append('K');
            r = getPiece(0, 7);
            if (r instanceof Rook && r.isFirstMove) castle.append('Q');
        }
        Piece bKing = getPiece(4, 0);
        if (bKing instanceof King && bKing.isFirstMove) {
            Piece r = getPiece(7, 0);
            if (r instanceof Rook && r.isFirstMove) castle.append('k');
            r = getPiece(0, 0);
            if (r instanceof Rook && r.isFirstMove) castle.append('q');
        }
        fen.append(castle.length() == 0 ? "-" : castle.toString());

        fen.append(' ');
        if (enPassantTile >= 0 && enPassantTile < 64) {
            fen.append(squareToLetters(enPassantTile % 8, enPassantTile / 8).toLowerCase());
        } else {
            fen.append('-');
        }
        fen.append(' ').append(Math.max(0, numOfTurnWithoutCaptureOrPawnMove));
        fen.append(' ').append(Math.max(1, numOfTurns));
        return fen.toString();
    }

    private void ensureRulesCache() {
        String fen = toRulesFen();
        if (fen.equals(rulesFenCache)) {
            return;
        }
        Rules.Evaluation eval = Rules.evaluate(fen);
        Set<Integer> set = new HashSet<>(eval.legalMoves.size() * 2);
        for (ChessMove m : eval.legalMoves) {
            set.add(m.from() * 64 + m.to());
        }
        rulesFenCache = fen;
        legalFromToCache = set;
        rulesStatusCache = eval.status;
    }

    /** The unified {@link rules.GameStatus} for the current position (threefold not included). */
    public GameStatus getRulesStatus() {
        ensureRulesCache();
        return rulesStatusCache;
    }

    public boolean sameTeam(Piece p1, Piece p2) {
        assert (p1 != null && p2 != null) : "pieces are null";
        if (p1 == null || p2 == null) return false;
        return p1.isWhite == p2.isWhite;
    }

    public int getTileNum(int col, int row) {
        return COLS * row + col;
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
        if ((!(bqr instanceof Rook && bqr.isFirstMove && bKing instanceof King && bKing.isFirstMove) && !(bkr instanceof Rook && bkr.isFirstMove && bKing instanceof King && bKing.isFirstMove)) || !((wqr instanceof Rook && wqr.isFirstMove && wKing instanceof King && wKing.isFirstMove) || !(wkr instanceof Rook && wkr.isFirstMove && wKing instanceof King && wKing.isFirstMove))) {
            fen.append('-');
        }
        fen.append(" ");

        // en passant
        int colorIndex = isWhiteToMove ? 1 : -1;
        // System.out.println(lastToMove + " " + );
        if (lastToMove instanceof Pawn && Math.abs(toR - fromR) == 2) {
            int col = fromC % 8; // נועד למנוע שגיאות
            int row = fromR + colorIndex % 8; // כנ"ל
            fen.append(squareToLetters(col, row));
        } else {
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

    public String convertPiecesToDrawFEN() {
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
        if ((!(bqr instanceof Rook && bqr.isFirstMove && bKing instanceof King && bKing.isFirstMove) && !(bkr instanceof Rook && bkr.isFirstMove && bKing instanceof King && bKing.isFirstMove)) || !((wqr instanceof Rook && wqr.isFirstMove && wKing instanceof King && wKing.isFirstMove) || !(wkr instanceof Rook && wkr.isFirstMove && wKing instanceof King && wKing.isFirstMove))) {
            fen.append('-');
        }
        fen.append(" ");

        // en passant
        int colorIndex = isWhiteToMove ? 1 : -1;
        // System.out.println(lastToMove + " " + );
        if (lastToMove instanceof Pawn && Math.abs(toR - fromR) == 2) {
            int col = fromC % 8; // נועד למנוע שגיאות
            int row = fromR + colorIndex % 8; // כנ"ל
            fen.append(squareToLetters(col, row));
        } else {
            fen.append('-');
        }

        // System.out.println(fen);
        return fen.toString();
    }

    public String squareToLetters(int col, int row) {
        String namesOfRows = "87654321";
        // System.out.println(col + " " + row + " " + squareName);
        return colToLetter(col) + namesOfRows.charAt(row);
    }

    public String colToLetter(int col) {
        String namesOfCols = "abcdefgh";
        return Character.toString(namesOfCols.charAt(col));
    }

    public BoardState cloneBoard() {
        return new BoardState(this.convertPiecesToFEN(), null);
    }

    // סטרים
    public void setIsWhiteToMove(boolean isWhiteToMove) {
        this.isWhiteToMove = isWhiteToMove;
    }

    public void addPiece(Piece piece) {
        pieceList.add(piece);
    }


    // Phase 2: makeMoveToCheckIt / makeMoveAndGetStatus / makeMoveAndGetValue / makeMoveAndGetFen
    // (mutate the live board, read a fact, loadPiecesFromFen to revert) and getAllPossibleMoves*
    // are gone. Legality and status now come from rules.Rules (see isValidMove / getLegalMoves /
    // getRulesStatus), which needs no simulate-and-revert.

    public int getAccurateStatus() {
        return switch (getRulesStatus()) {
            case CHECKMATE -> Integer.MAX_VALUE;
            case STALEMATE, DRAW_FIFTY_MOVE, DRAW_THREEFOLD, DRAW_INSUFFICIENT_MATERIAL -> 0;
            case CHECK -> 2;
            case IN_PROGRESS -> 1;
        };
    }

    public int getGameState() {
        if (pieceList.size() <= 8) return 10;
        if (pieceList.size() <= 12) return 2;
        if (numOfTurns < 10) return 0;
        return 1;
    }

    private boolean movePawnForClone(Move move) {
        // en passant:
        int colorIndex = move.piece.isWhite ? 1 : -1;
        fenCurrentPosition = convertPiecesToFEN();
        isLastMovePawn = true;

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
            } else {
                String promotionChoice = "q"; // צריך לקבל את זה ממינימקס
                promotePawnToForClone(move, promotionChoice);
                pieceList.remove(move.piece);
            }
        }
        numOfTurnWithoutCaptureOrPawnMove = -1;
        return true;
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

    public void moveKingForClone(Move move) {
        if (Math.abs(move.piece.col - move.newCol) == 2) {
            fenCurrentPosition = convertPiecesToFEN();
            Piece rook;
            if (move.piece.col < move.newCol) {
                rook = getPiece(7, move.piece.row);
                rook.col = 5;
            } else {
                rook = getPiece(0, move.piece.row);
                rook.col = 3;
            }
            isLastMoveCastling = true;
        }
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

    // ביצוע מהלך אמיתי
    public void makeMove(Move move) {
        isLastMoveCastling = false;
        isLastMovePawn = false;
        // System.out.printf("\nMove: %d, %d to %d, %d\n\n", move.piece.col, move.piece.row, move.newCol, move.newRow);
        Piece piece = getPiece(move.piece.col, move.piece.row);
        if (piece == null) {
            return;
        }
        boolean pawnMoveSuccess = true;
        if (piece.name.equals("Pawn")) {
            pawnMoveSuccess = movePawnForClone(move);
        } else if (piece.name.equals("King")) {
            moveKingForClone(move);
        }

        if (pawnMoveSuccess) {
            fromC = piece.col;
            fromR = piece.row;
            toC = move.newCol;
            toR = move.newRow;
            if (!piece.name.equals("Pawn") || !(Math.abs(piece.row - move.newRow) == 2)) {
                enPassantTile = -1;
            }
            if (move.captured != null && getPiece(move.captured.col, move.captured.row) != null) {
                capture(getPiece(move.captured.col, move.captured.row));
            }
            piece.col = move.newCol;
            piece.row = move.newRow;
            isWhiteToMove = !isWhiteToMove;
            if (isWhiteToMove) {
                ++numOfTurns;
            }
            ++numOfTurnWithoutCaptureOrPawnMove;
            //setLastMove(move);
        }
    }

    public int getStatus() {
        return getRulesStatus().isGameOver() ? 0 : 1;
    }

    public boolean getIsCheck() {
        GameStatus s = getRulesStatus();
        return s == GameStatus.CHECK || s == GameStatus.CHECKMATE;
    }
}




