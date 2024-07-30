package ai;

import ai.BitBoard.BitBoard;
import ai.BitBoard.BitBoardEvaluate;
import ai.BitBoard.BitBoardOperations;
import ai.BitBoard.BitMove;
import main.setting.ChoosePlayFormat;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Random;

public class Minimax {
    private static final Random random = new Random();
    public static ArrayList<BitMove> bestMoves;
    public static int maxDepth;

    public static BitMove getBestMove(BoardState board) {
        if (ChoosePlayFormat.isPlayingWhite != board.getIsWhiteToMove()) {
            BitBoard bitboard = new BitBoard(board);
            BitMove bestMove = null;
            Instant start, end;
            long timeElapsed;

            for (int depth = maxDepth; depth <= maxDepth; depth++) {
                start = Instant.now(); // התחלת מדידת זמן
                MinimaxResult result = minimax(bitboard, depth, true, Integer.MIN_VALUE, Integer.MAX_VALUE);
                end = Instant.now(); // סיום מדידת זמן
                timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
                System.out.printf("Depth: %d, Best Move: %s, Best Value: %d, Time Spend: %d ms\n", depth, result.move, result.value, timeElapsed);

                if (depth == maxDepth) {
                    // System.out.println("best moves: " + bestMoves + " size: " + bestMoves.size());
                    if (!bestMoves.isEmpty()) {
                        int randomIndex = random.nextInt(bestMoves.size());
                        result.move = bestMoves.get(randomIndex);
                    }
                }

                if (result.move != null) {
                    bestMove = result.move;
                }
            }
            return bestMove;
        }
        return null;
    }

    private static MinimaxResult minimax(BitBoard board, int depth, boolean isMaximizingPlayer, int alpha, int beta) {
        if (depth == 0 || board.getStatus() != 1) {
            return new MinimaxResult(board.lastMove, BitBoardEvaluate.evaluate(board));
        }
        BitMove bestMove = board.getRandomPossibleMove();
        boolean lastDepth = depth == maxDepth;
        if (lastDepth) {
            bestMoves = new ArrayList<>();
        }
        int bestValue;
        Instant start, end;
        long timeElapsed;

        if (isMaximizingPlayer) {
            bestValue = Integer.MIN_VALUE;
            for (BitBoard state : board.getNextStates()) {
                start = Instant.now(); // התחלת מדידת זמן
                MinimaxResult result = minimax(state, depth - 1, false, alpha, beta);
                end = Instant.now(); // סיום מדידת זמן
                timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
                // System.out.printf("Maximizing: Value: %d, Alpha: %d, Beta: %d, Time: %d ms\n", result.value, alpha, beta, timeElapsed);
                if (result.value > bestValue) {
                    // System.out.println("board:" + board);
                    // System.out.println("best state" + state);
                    bestValue = result.value;
                    bestMove = state.lastMove;
                    if (lastDepth) {
                        bestMoves.clear();
                        bestMoves.add(bestMove);
                        // System.out.println("move added, turn: " + (board.getIsWhiteToMove() ? "white." : "black.") + " depth: " + depth);
                    }
                } else if (lastDepth && result.value == bestValue && (!bestMoves.contains(result.move))) {
                    bestMoves.add(bestMove);
                }
                alpha = Math.max(alpha, bestValue);
                if (beta <= alpha) {
                    // System.out.printf("Pruning at state: %s Alpha: %d, Beta: %d\n", state, alpha, beta);
                    break; // אלפא-בטא גיזום
                }
            }
        }
        else {
            bestValue = Integer.MAX_VALUE;
            for (BitBoard state : board.getNextStates()) {
                start = Instant.now(); // התחלת מדידת זמן
                MinimaxResult result = minimax(state, depth - 1, true, alpha, beta);
                end = Instant.now(); // סיום מדידת זמן
                timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
                // System.out.printf("Minimizing: Value: %d, Alpha: %d, Beta: %d, Time: %d ms\n", result.value, alpha, beta, timeElapsed);
                if (result.value < bestValue) {
                    bestValue = result.value;
                    bestMove = state.lastMove;
                }
                beta = Math.min(beta, bestValue); // תיקון כאן
                if (beta <= alpha) {
                    // System.out.printf("Pruning at state: %s Alpha: %d, Beta: %d\n", state, alpha, beta);
                    break; // אלפא-בטא גיזום
                }
            }
        }
        return new MinimaxResult(bestMove, bestValue);
    }

    private static class MinimaxResult {
        BitMove move;
        int value;

        MinimaxResult(BitMove move, int value) {
            this.move = move;
            this.value = value;
        }
    }

    public static void main(String[] args) {
        maxDepth = 2;
        String fen = "rnbqkbnr/pppppppp/8/8/8/8/PPPPPPPP/RNBQKBNR b KQkq - 0 1";
        BoardState boardState = new BoardState(fen, null);
        System.out.println("best move final: " + getBestMove(boardState));
    }







}


//package ai;
//
//import main.Move;
//import main.setting.ChoosePlayFormat;
//import pieces.Piece;
//
//import java.time.Duration;
//import java.time.Instant;
//import java.util.ArrayList;
//import java.util.HashMap;
//import java.util.Map;
//
//public class Minimax {
//
//    private static final Map<String, Double> transpositionTable = new HashMap<>();
//
//    public static Move getBestMove(BoardState board, int maxDepth) {
//        if (ChoosePlayFormat.isPlayingWhite != board.getIsWhiteToMove()) {
//            BoardState.numOfNodes = 1;
//            String tempFen = board.convertPiecesToFEN();
//            BoardState cloneBoard = new BoardState(tempFen, board.lastMove);
//            Move bestMove = null;
//            Instant start, end;
//            long timeElapsed;
//
//            for (int depth = 1; depth <= maxDepth; depth++) {
//                start = Instant.now(); // התחלת מדידת זמן
//                MinimaxResult result = minimax(cloneBoard, depth, true, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
//                end = Instant.now(); // סיום מדידת זמן
//                timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
//                System.out.printf("Depth: %d, Best Move: %s, Best Value: %.2f, Time Spend: %d ms\n", depth, result.move, result.value, timeElapsed);
//
//                if (result.move != null) {
//                    bestMove = result.move;
//                }
//            }
//            return bestMove;
//        }
//        return null;
//    }
//
//    private static MinimaxResult minimax(BoardState board, int depth, boolean isMaximizingPlayer, double alpha, double beta) {
//        System.out.printf("Entering minimax: Depth: %d, Maximizing: %b, Alpha: %.2f, Beta: %.2f\n", depth, isMaximizingPlayer, alpha, beta);
//
//        if (depth == 0) {
//            double evaluation = EvaluationLevel2.evaluate(board);
//            System.out.printf("Leaf node reached. Evaluation: %.2f\n", evaluation);
//            return new MinimaxResult(null, evaluation);
//        }
//
//        String fen = board.convertPiecesToFEN();
//        if (transpositionTable.containsKey(fen)) {
//            double cachedValue = transpositionTable.get(fen);
//            System.out.printf("Transposition table hit. Cached value: %.2f\n", cachedValue);
//            return new MinimaxResult(null, cachedValue);
//        }
//
//        Move bestMove = null;
//        double bestValue;
//        Instant start, end;
//        long timeElapsed;
//
//        if (isMaximizingPlayer) {
//            bestValue = Double.NEGATIVE_INFINITY;
//            for (Piece piece : board.getAllPieces()) {
//                if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
//                    ArrayList<Move> validMoves = piece.getValidMoves(board);
//                    sortMoves(validMoves); // מיון מהלכים
//                    for (Move move : validMoves) {
//                        start = Instant.now(); // התחלת מדידת זמן
//                        if (board.makeMoveToCheckIt(move)) {
//                            board.makeMove(move);
//                            System.out.printf("Maximizing: Trying move: %d, %d to %d, %d\n", move.piece.col, move.piece.row, move.newCol, move.newRow);
//                            System.out.printf("Board after move:\n%s\n", board.convertPiecesToFEN());
//                            MinimaxResult result = minimax(board, depth - 1, false, alpha, beta);
//                            board.loadPiecesFromFen(fen);
//                            end = Instant.now(); // סיום מדידת זמן
//                            timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
//                            System.out.printf("Maximizing: Move: %d, %d to %d, %d, Value: %.2f, Alpha: %.2f, Beta: %.2f, Time: %d ms\n", move.piece.col, move.piece.row, move.newCol, move.newRow, result.value, alpha, beta, timeElapsed);
//                            if (result.value > bestValue) {
//                                bestValue = result.value;
//                                bestMove = move;
//                            }
//                            alpha = Math.max(alpha, bestValue);
//                            if (beta <= alpha) {
//                                System.out.printf("Pruning at move: %d, %d to %d, %d, Alpha: %.2f, Beta: %.2f\n", move.piece.col, move.piece.row, move.newCol, move.newRow, alpha, beta);
//                                break; // אלפא-בטא גיזום
//                            }
//                        }
//                    }
//                }
//            }
//        } else {
//            bestValue = Double.POSITIVE_INFINITY;
//            for (Piece piece : board.getAllPieces()) {
//                if (piece.isWhite == ChoosePlayFormat.isPlayingWhite) {
//                    ArrayList<Move> validMoves = piece.getValidMoves(board);
//                    sortMoves(validMoves); // מיון מהלכים
//                    for (Move move : validMoves) {
//                        start = Instant.now(); // התחלת מדידת זמן
//                        if (board.makeMoveToCheckIt(move)) {
//                            board.makeMove(move);
//                            System.out.printf("Minimizing: Trying move: %d, %d to %d, %d\n", move.piece.col, move.piece.row, move.newCol, move.newRow);
//                            System.out.printf("Board after move:\n%s\n", board.convertPiecesToFEN());
//                            MinimaxResult result = minimax(board, depth - 1, true, alpha, beta);
//                            board.loadPiecesFromFen(fen);
//                            end = Instant.now(); // סיום מדידת זמן
//                            timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
//                            System.out.printf("Minimizing: Move: %d, %d to %d, %d, Value: %.2f, Alpha: %.2f, Beta: %.2f, Time: %d ms\n", move.piece.col, move.piece.row, move.newCol, move.newRow, result.value, alpha, beta, timeElapsed);
//                            if (result.value < bestValue) {
//                                bestValue = result.value;
//                                bestMove = move;
//                            }
//                            beta = Math.min(beta, bestValue);
//                            if (beta <= alpha) {
//                                System.out.printf("Pruning at move: %d, %d to %d, %d, Alpha: %.2f, Beta: %.2f\n", move.piece.col, move.piece.row, move.newCol, move.newRow, alpha, beta);
//                                break; // אלפא-בטא גיזום
//                            }
//                        }
//                    }
//                }
//            }
//        }
//
//        transpositionTable.put(fen, bestValue); // שמירת התוצאה בטבלת המעבר
//        System.out.printf("Exiting minimax: Depth: %d, Best Move: %s, Best Value: %.2f\n", depth, bestMove, bestValue);
//        return new MinimaxResult(bestMove, bestValue);
//    }
//
//    private static void sortMoves(ArrayList<Move> moves) {
//        // מיון מהלכים על פי סדר עדיפות, כגון מהלכים שמובילים לשח, מהלכים שמבצעים לכידה וכו'.
//        moves.sort((m1, m2) -> {
//            // תעדוף מהלכים שמבצעים לכידה
//            if (m1.captured != null && m2.captured == null) {
//                return -1;
//            } else if (m1.captured == null && m2.captured != null) {
//                return 1;
//            } else {
//                return 0;
//            }
//        });
//    }
//
//    private static class MinimaxResult {
//        Move move;
//        double value;
//
//        MinimaxResult(Move move, double value) {
//            this.move = move;
//            this.value = value;
//        }
//    }
//}
// ה"אופטימיזציות" של הצ'אט...


// the prev version:

//    public static Move getBestMove(BoardState board) {
//        if (ChoosePlayFormat.isPlayingWhite != board.getIsWhiteToMove()) {
//            String tempFen = board.convertPiecesToFEN();
//            BoardState cloneBoard = new BoardState(tempFen, board.lastMove);
//            Move bestMove = null;
//            Instant start, end;
//            long timeElapsed;
//
//            for (int depth = 1; depth <= maxDepth; depth++) {
//                start = Instant.now(); // התחלת מדידת זמן
//                MinimaxResult result = minimax(cloneBoard, depth, true, Double.NEGATIVE_INFINITY, Double.POSITIVE_INFINITY);
//                end = Instant.now(); // סיום מדידת זמן
//                timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
//                System.out.printf("Depth: %d, Best Move: %s, Best Value: %.2f, Time Spend: %d ms\n", depth, result.move, result.value, timeElapsed);
//
//                if (depth == maxDepth) {
//                    System.out.println("best moves: " + bestMoves);
//                    int randomIndex = random.nextInt(bestMoves.size());
//                    if (bestMoves.get(randomIndex) != null) {
//                        result.move = bestMoves.get(randomIndex);
//                    }
//                }
//
//                if (result.move != null) {
//                    bestMove = result.move;
//                }
//            }
//            return bestMove;
//        }
//        return null;
//    }
//
//    private static MinimaxResult minimax(BoardState board, int depth, boolean isMaximizingPlayer, double alpha, double beta) {
//        if (depth == 0 || board.getStatus() == 0) {
//            return new MinimaxResult(null, EvaluationLevel2.evaluate(board));
//        }
//        boolean lastDepth = depth == maxDepth;
//        if (lastDepth) {
//            bestMoves = new ArrayList<>();
//        }
//        String fen = board.convertPiecesToFEN();
//        Move bestMove = null;
//        double bestValue;
//        Instant start, end;
//        long timeElapsed;
//
//        if (isMaximizingPlayer) {
//            bestValue = Double.NEGATIVE_INFINITY;
//            for (Piece piece : board.getAllPieces()) {
//                if (piece.isWhite != ChoosePlayFormat.isPlayingWhite) {
//                    ArrayList<Move> validMoves = piece.getValidMoves(board);
//                    for (Move move : validMoves) {
//                        start = Instant.now(); // התחלת מדידת זמן
//                        if (board.makeMoveToCheckIt(move)) {
//                            board.makeMove(move);
//                            MinimaxResult result = minimax(board, depth - 1, false, alpha, beta);
//                            board.loadPiecesFromFen(fen);
//                            end = Instant.now(); // סיום מדידת זמן
//                            timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
//                            System.out.printf("Maximizing: %s, Value: %.2f, Alpha: %.2f, Beta: %.2f, Time: %d ms\n", move, result.value, alpha, beta, timeElapsed);
//                            if (result.value > bestValue) {
//                                bestValue = result.value;
//                                bestMove = move;
//                                if (lastDepth) {
//                                    bestMoves.clear();
//                                    bestMoves.add(move);
//                                }
//                            }
//                            else if (lastDepth && result.value == bestValue) {
//                                bestMoves.add(move);
//                            }
//                            alpha = Math.max(alpha, bestValue);
//                            if (beta <= alpha) {
//                                System.out.printf("Pruning at move: %s, Alpha: %.2f, Beta: %.2f\n", move, alpha, beta);
//                                break; // אלפא-בטא גיזום
//                            }
//                        }
//                    }
//                }
//            }
//        } else {
//            bestValue = Double.POSITIVE_INFINITY;
//            for (Piece piece : board.getAllPieces()) {
//                if (piece.isWhite == ChoosePlayFormat.isPlayingWhite) {
//                    ArrayList<Move> validMoves = piece.getValidMoves(board);
//                    for (Move move : validMoves) {
//                        start = Instant.now(); // התחלת מדידת זמן
//                        if (board.makeMoveToCheckIt(move)) {
//                            board.makeMove(move);
//                            MinimaxResult result = minimax(board, depth - 1, true, alpha, beta);
//                            board.loadPiecesFromFen(fen);
//                            end = Instant.now(); // סיום מדידת זמן
//                            timeElapsed = Duration.between(start, end).toMillis(); // זמן במילישניות
//                            System.out.printf("Minimizing: %s, Value: %.2f, Alpha: %.2f, Beta: %.2f, Time: %d ms\n", move, result.value, alpha, beta, timeElapsed);
//                            if (result.value < bestValue) {
//                                bestValue = result.value;
//                                bestMove = move;
//                                if (lastDepth) {
//                                    bestMoves.clear();
//                                    bestMoves.add(move);
//                                }
//                            }
//                            else if (lastDepth && result.value == bestValue) {
//                                bestMoves.add(move);
//                            }
//                            beta = Math.min(beta, bestValue);
//                            if (beta <= alpha) {
//                                System.out.printf("Pruning at move: %s, Alpha: %.2f, Beta: %.2f\n", move, alpha, beta);
//                                break; // אלפא-בטא גיזום
//                            }
//                        }
//
//                    }
//                }
//            }
//        }
//
//        return new MinimaxResult(bestMove, bestValue);
//    }
