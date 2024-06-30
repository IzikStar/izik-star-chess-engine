package ai;

import main.Board;
import main.Move;
import main.setting.ChoosePlayFormat;
import pieces.Piece;

import java.awt.*;

public class BoardClone extends Board {


    private boolean isWhiteToMove;

    public BoardClone(String fen) {
        // קונסטרקטור שמקבל תיאור FEN ויוצר לוח חדש על פיו
        // מימוש בהתאם ל-FEN המתקבל
        loadPiecesFromFen(fen);

    }

    @Override
    public void paintComponent(Graphics g) {
    }

    public void makeMoveOnCloneBoard(Move move) {
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
            if (ChoosePlayFormat.isPlayingWhite) {
                move.piece.xPos = move.newCol * tileSize;
                move.piece.yPos = move.newRow * tileSize;
            }
            else {
                move.piece.xPos = (7 - move.newCol) * tileSize;
                move.piece.yPos = (7 - move.newRow) * tileSize;
            }
            move.piece.isFirstMove = false;
            if (move.captured != null) {
                capture(move.captured);
            }
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
        }
    }

}
