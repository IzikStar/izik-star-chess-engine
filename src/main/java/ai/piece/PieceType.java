package ai.piece;

import java.util.List;

/**
 * A kind of piece, as data (Phase 6 R2): how it moves and captures ({@link Atom}s) and the few rule
 * properties the board needs. The six chess pieces are {@link StandardPieces}; an invented piece is
 * just another value.
 *
 * @param name        shown to people, e.g. "Knight"
 * @param letter      upper-case letter in FEN and SAN (lower case for player 1)
 * @param atoms       how it moves and captures
 * @param royal       losing it (or having it captured) loses the game: the chess king
 * @param promotesTo  letters of the pieces it may become on the last row; empty if it never promotes
 * @param enPassant   takes part in en passant: it may be captured just after a first-move
 *                    double step, and may capture that way
 * @param castling    its part in castling, if any
 * @param value       a starting value for the evaluation, in centipawns
 */
public record PieceType(String name, char letter, List<Atom> atoms, boolean royal, List<Character> promotesTo,
                        boolean enPassant, Castling castling, int value) {

    /** A piece's part in castling. */
    public enum Castling { NONE, KING, ROOK }

    public PieceType {
        if (!Character.isLetter(letter) || !Character.isUpperCase(letter)) {
            throw new IllegalArgumentException("a piece's letter is an upper-case letter: " + letter);
        }
        if (atoms.isEmpty()) {
            throw new IllegalArgumentException(name + " has no way to move");
        }
        atoms = List.copyOf(atoms);
        promotesTo = List.copyOf(promotesTo);
    }

    /** A plain piece: moves and captures, no rule properties. */
    public static PieceType of(String name, char letter, int value, Atom... atoms) {
        return new PieceType(name, letter, List.of(atoms), false, List.of(), false, Castling.NONE, value);
    }
}
