package main.setting;

import GUI.CustomButtonPanel;
import game.GameConfig;
import game.GameListener;
import game.GameSession;

import javax.swing.*;
import java.awt.*;

/** Game settings: one/two players, the human's colour, and the engine level (Phase 3: edits the session's {@link GameConfig}). */
public class SettingPanel extends JPanel implements GameListener {

    private final GameSession session;
    private final CustomButtonPanel chooseIsOnePlayer;
    private final CustomButtonPanel chooseIsPlayingWhite;
    private final CustomButtonPanel[] levelButtons = new CustomButtonPanel[10];

    public SettingPanel(GameSession session) {
        this.session = session;
        this.setPreferredSize(new Dimension(670, 670));
        this.setLayout(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.insets = new Insets(10, 10, 10, 10);

        // כפתור לשינוי פורמט המשחק (שחקן יחיד/שני שחקנים)
        chooseIsOnePlayer = new CustomButtonPanel(1, "", (Integer id) -> {
            GameConfig config = session.config();
            session.updateConfig(config.withMode(config.mode() == GameConfig.Mode.HUMAN_VS_HUMAN
                    ? GameConfig.Mode.HUMAN_VS_ENGINE : GameConfig.Mode.HUMAN_VS_HUMAN));
        });
        styleButton(chooseIsOnePlayer);
        gbc.gridx = 0;
        gbc.gridy = 0;
        this.add(chooseIsOnePlayer, gbc);

        // כפתור לשינוי צבע השחקן
        chooseIsPlayingWhite = new CustomButtonPanel(2, "", (Integer id) -> {
            GameConfig config = session.config();
            if (config.mode() == GameConfig.Mode.HUMAN_VS_ENGINE) {
                session.updateConfig(config.withHumanPlaysWhite(!config.humanPlaysWhite()));
            }
        });
        styleButton(chooseIsPlayingWhite);
        gbc.gridx = 1;
        this.add(chooseIsPlayingWhite, gbc);

        // כפתורים לבחירת רמת המשחק (0-18 עם שמות 1-10) בשתי שורות
        gbc.gridx = 0;
        gbc.gridy = 1;
        gbc.gridwidth = 2;
        JPanel levelPanel = new JPanel(new GridLayout(2, 5, 10, 10));
        for (int i = 0; i < 10; i++) {
            int level = i * 2;
            levelButtons[i] = new CustomButtonPanel(level, "Level " + (i + 1), (Integer id) ->
                    session.updateConfig(session.config().withSkillLevel(level)));
            styleLevelButton(levelButtons[i]);
            levelPanel.add(levelButtons[i]);
        }
        this.add(levelPanel, gbc);

        session.addListener(this);
        configChanged(session.config());
    }

    @Override
    public void configChanged(GameConfig config) {
        chooseIsOnePlayer.changeText(config.mode() == GameConfig.Mode.HUMAN_VS_HUMAN ? "Play with computer" : "Two players");
        chooseIsPlayingWhite.changeText(config.humanPlaysWhite() ? "Play as black" : "Play as white");
    }

    private void styleButton(CustomButtonPanel button) {
        button.setBackground(Color.green);
        button.setForeground(Color.black);
        button.setFont(new Font("Arial", Font.BOLD, 14));
    }

    private void styleLevelButton(CustomButtonPanel button) {
        button.setBackground(Color.orange);
        button.setForeground(Color.black);
        button.setFont(new Font("Arial", Font.PLAIN, 12));
    }
}
