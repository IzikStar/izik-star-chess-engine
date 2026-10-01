package game;

/**
 * How a game is being played — replaces the {@code main.setting.ChoosePlayFormat} statics and
 * {@code SettingPanel.skillLevel} (Phase 3). Immutable: change it with the {@code with*} methods
 * and hand the result to {@link GameSession#updateConfig}.
 *
 * @param mode             who plays each side
 * @param humanPlaysWhite  in {@link Mode#HUMAN_VS_ENGINE}, the human's colour (ignored otherwise)
 * @param skillLevel       engine strength on the UI's 0-18 scale (0 = random moves)
 */
public record GameConfig(Mode mode, boolean humanPlaysWhite, int skillLevel) {

    public enum Mode { HUMAN_VS_ENGINE, HUMAN_VS_HUMAN, ENGINE_VS_ENGINE }

    /** The application's defaults: play White against the engine at level 0. */
    public static GameConfig defaults() {
        return new GameConfig(Mode.HUMAN_VS_ENGINE, true, 0);
    }

    public GameConfig withMode(Mode mode) {
        return new GameConfig(mode, humanPlaysWhite, skillLevel);
    }

    public GameConfig withHumanPlaysWhite(boolean white) {
        return new GameConfig(mode, white, skillLevel);
    }

    public GameConfig withSkillLevel(int level) {
        return new GameConfig(mode, humanPlaysWhite, level);
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
