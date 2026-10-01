package GUI;

import javax.sound.sampled.*;
import java.io.BufferedInputStream;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class AudioPlayer {

    private Clip audioClip;
    private static boolean warnedNoAudioDevice = false;

    /**
     * Resolves a sound either from the classpath (the packaged location, e.g.
     * {@code sounds/moveSound1.wav}) or, as a fallback, from a plain file on disk.
     * The historic call sites pass paths like {@code "src/res/sounds/x.wav"}; that
     * prefix is stripped so the same string resolves against src/main/resources.
     */
    private static AudioInputStream openAudioStream(String path)
            throws UnsupportedAudioFileException, IOException {
        String classpathName = path.startsWith("src/res/") ? path.substring("src/res/".length()) : path;
        InputStream in = AudioPlayer.class.getClassLoader().getResourceAsStream(classpathName);
        if (in != null) {
            return AudioSystem.getAudioInputStream(new BufferedInputStream(in));
        }
        File audioFile = new File(path);
        if (audioFile.exists()) {
            return AudioSystem.getAudioInputStream(audioFile);
        }
        return null;
    }

    public void playAudio(String audioFilePath) {
        try {
            AudioInputStream audioStream = openAudioStream(audioFilePath);
            if (audioStream == null) {
                System.out.println("Audio file not found: " + audioFilePath);
                return;
            }
            audioClip = AudioSystem.getClip();
            audioClip.open(audioStream);
            audioClip.start();
        } catch (UnsupportedAudioFileException e) {
            System.out.println("The specified audio file is not supported.");
            e.printStackTrace();
        } catch (LineUnavailableException e) {
            System.out.println("Audio line for playing back is unavailable.");
            e.printStackTrace();
        } catch (IOException e) {
            System.out.println("Error playing the audio file.");
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            // No output line can play this clip (e.g. a machine with no sound device). Sound is
            // cosmetic: carry on silently rather than aborting the mouse/move handler that called us.
            if (!warnedNoAudioDevice) {
                warnedNoAudioDevice = true;
                System.out.println("No usable audio output device; sounds are disabled.");
            }
        }
    }

    public void stopAudio() {
        if (audioClip != null && audioClip.isRunning()) {
            audioClip.stop();
        }
    }

    public void closeAudio() {
        if (audioClip != null) {
            audioClip.close();
        }
    }

    public void playSelectPieceSound() {
        String selectPieceSound = "src/res/sounds/selectPieceSound1.wav";
        playAudio(selectPieceSound);
    }

    public void playMovingPieceSound() {
        String movingPieceSound = "src/res/sounds/moveSound1.wav";
        playAudio(movingPieceSound);
    }

    public void playCheckSound() {
        String checkSound = "src/res/sounds/checkSound1.wav";
        String checkSound2 = "src/res/sounds/checkSound4.wav";
        playAudio(checkSound);
        playAudio(checkSound2);
    }

    public void playCaptureSound() {
        String checkSound = "src/res/sounds/captureSound1.wav";
        playAudio(checkSound);
    }

    public void playInvalidMoveSound() {
        String invalidMoveSound = "src/res/sounds/invalidMoveSound1.wav";
        playAudio(invalidMoveSound);
    }

    public void playInvalidMoveBecauseOfCheckSound() {
        String invalidMoveBecauseOfCheckSound = "src/res/sounds/invalidMoveSound2.wav";
        playAudio(invalidMoveBecauseOfCheckSound);
    }

    public void playCheckMateSound() {
        String checkMateSound = "src/res/sounds/winningSound1.wav";
        playAudio(checkMateSound);
    }

    public void playDrawSound() {
        String drawSound = "src/res/sounds/drawSound1.wav";
        playAudio(drawSound);
    }

    public void playLosingSound() {
        String losingSound = "src/res/sounds/losingSound1.wav";
        playAudio(losingSound);
    }

    public void playCastlingSound() {
        String castlingSound = "src/res/sounds/castlingSound1.wav";
        playAudio(castlingSound);
    }

    public void playHintSound() {
        String hintSound = "src/res/sounds/hintSound1.wav";
        playAudio(hintSound);
    }

    public void playGoBackSound() {
        String goBackSound = "src/res/sounds/goBackSound1.wav";
        playAudio(goBackSound);
    }

    public void playSwitchSound() {
        String switchSound = "src/res/sounds/switchSound1.wav";
        playAudio(switchSound);
    }


}

