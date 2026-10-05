package game;

/**
 * How a game is being played. Immutable: change it with the {@code with*} methods
 * and hand the result to {@link GameSession#updateConfig}.
 *
 * @param mode             who plays each side
 * @param humanPlaysWhite  in {@link Mode#HUMAN_VS_ENGINE}, the human's colour (ignored otherwise)
 * @param skillLevel       engine strength, the level the player picks ({@code engine.Levels}: 0-13,
 *                         0 = random moves); in
 *                         {@link Mode#ENGINE_VS_ENGINE}, White's
 * @param blackSkillLevel  in {@link Mode#ENGINE_VS_ENGINE}, Black's strength (Phase 4c: each
 *                         side can play at its own level); ignored otherwise
 */
public record GameConfig(Mode mode, boolean humanPlaysWhite, int skillLevel, int blackSkillLevel) {

    public enum Mode { HUMAN_VS_ENGINE, HUMAN_VS_HUMAN, ENGINE_VS_ENGINE }

    /** One level for whichever side the engine plays. */
    public GameConfig(Mode mode, boolean humanPlaysWhite, int skillLevel) {
        this(mode, humanPlaysWhite, skillLevel, skillLevel);
    }

    /**
     * The application's defaults: play White against the engine at Level 5 (a 3-ply search): a
     * real opponent from the first screen, not random moves.
     */
    public static GameConfig defaults() {
        return new GameConfig(Mode.HUMAN_VS_ENGINE, true, 5);
    }

    public GameConfig withMode(Mode mode) {
        return new GameConfig(mode, humanPlaysWhite, skillLevel, blackSkillLevel);
    }

    public GameConfig withHumanPlaysWhite(boolean white) {
        return new GameConfig(mode, white, skillLevel, blackSkillLevel);
    }

    /** Sets the level of both sides. */
    public GameConfig withSkillLevel(int level) {
        return new GameConfig(mode, humanPlaysWhite, level, level);
    }

    /** The level the engine plays at when it moves for this colour. */
    public int skillLevelFor(boolean white) {
        return mode == Mode.ENGINE_VS_ENGINE && !white ? blackSkillLevel : skillLevel;
    }

    /** True if a human makes the moves for this colour. */
    public boolean isHuman(boolean white) {
        return switch (mode) {
            case HUMAN_VS_HUMAN -> true;
            case ENGINE_VS_ENGINE -> false;
            case HUMAN_VS_ENGINE -> white == humanPlaysWhite;
        };
    }
}
