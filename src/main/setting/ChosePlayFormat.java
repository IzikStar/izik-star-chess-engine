package main.setting;

public class ChosePlayFormat {
    private boolean isOwnPlayer = false;
    private boolean isPlayingWhite = true;

    public ChosePlayFormat() {
    }

    public void setOwnPlayer (boolean isOwnPlayer) {
        this.isOwnPlayer = isOwnPlayer;
    }
    public void setPlayingWhite (boolean isPlayingWhite) {
        this.isPlayingWhite = isPlayingWhite;
    }
    public boolean getIsOnePlayer() {
        return isOwnPlayer;
    }
    public boolean getIsPlayingWhite() {
        return isPlayingWhite;
    }

}
