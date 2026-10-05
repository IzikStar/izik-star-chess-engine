package ai.board;

import ai.variant.Variant;

/**
 * A position read by piece type, for an evaluation that knows nothing but the variant (Phase 6
 * R4c): which squares each type stands on and how many squares each can go to. Types are numbered
 * as in {@link Variant#pieces()}.
 */
public interface PieceBoard extends Board {

    Variant variant();

    /** The squares {@code player}'s pieces of type {@code type} stand on. */
    long pieces(int player, int type);

    /** How many moves {@code player}'s pieces of type {@code type} have, ignoring checks. */
    int mobility(int player, int type);
}
