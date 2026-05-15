package games.pixscape.runtime.tiled.animation;

public final class TileAnimationPlayback {
    private TileAnimationPlayback() {
    }

    public static final byte NONE = 0;
    public static final byte PLAYING = 1;
    public static final byte PAUSED = 2;

    public static boolean isAnimated(byte state) {
        return state == PLAYING || state == PAUSED;
    }
}