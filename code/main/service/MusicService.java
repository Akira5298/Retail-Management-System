package tokyoera.service;

import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;

import java.nio.file.Path;
// This class handles background music for the app.
// It loads the music file once and loops it indefinitely.
// The user can mute/unmute it using the sound button in the UI.
public class MusicService {
    private final Path loopTrack;   // path to the MP3 file
    private MediaPlayer currentPlayer;
    private boolean muted;

    // Try to find the music file on disk when the service is created
    public MusicService() {
        this.loopTrack = AssetPathResolver
                .resolveExisting("Akira - From Dust, a Future.mp3")
                .orElse(null);
    }

    // Start playing the background music (only if the file was found)
    public void start() {
        if (loopTrack == null) {
            return;
        }
        playLoop();
    }

    // Stop playback and release the media player resources
    public void stop() {
        if (currentPlayer != null) {
            currentPlayer.stop();
            currentPlayer.dispose();
            currentPlayer = null;
        }
    }

    // Returns true if the music is currently muted
    public boolean isMuted() {
        return muted;
    }

    // Set the mute state directly and apply it to the player if it's running
    public void setMuted(boolean muted) {
        this.muted = muted;
        if (currentPlayer != null) {
            currentPlayer.setMute(muted);
        }
    }

    // Flip mute on/off — used by the music toggle button in the UI
    public void toggleMute() {
        setMuted(!muted);
    }

    // Internal helper: stop any current track then start a fresh loop
    private void playLoop() {
        stop();

        Media media = new Media(loopTrack.toUri().toString());
        currentPlayer = new MediaPlayer(media);
        currentPlayer.setCycleCount(MediaPlayer.INDEFINITE); // loop forever
        currentPlayer.setMute(muted);
        currentPlayer.play();
    }
}
