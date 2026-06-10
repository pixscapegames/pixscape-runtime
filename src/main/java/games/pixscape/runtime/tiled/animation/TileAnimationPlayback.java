package games.pixscape.runtime.tiled.animation;

public final class TileAnimationPlayback {
    private TileAnimationPlayback() {
    }

    public static final byte NONE = 0;
    public static final byte PLAYING = 1;
    public static final byte PAUSED = 2;

    public static final byte MODE_LOOPING = 0;
    public static final byte MODE_PLAY_ONCE = 1;

    public static boolean isAnimated(byte state) {
        return state == PLAYING || state == PAUSED;
    }

    public static boolean isMode(byte mode) {
        return mode == MODE_LOOPING || mode == MODE_PLAY_ONCE;
    }
}
