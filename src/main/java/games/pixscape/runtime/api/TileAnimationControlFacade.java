package games.pixscape.runtime.api;

public interface TileAnimationControlFacade {
    boolean isAnimated(int x, int y);
    boolean isPlaying(int x, int y);
    boolean isPaused(int x, int y);

    TileAnimationControlFacade play(int x, int y);
    TileAnimationControlFacade pause(int x, int y);
    TileAnimationControlFacade stop(int x, int y);
    TileAnimationControlFacade restart(int x, int y);

    TileAnimationControlFacade setFrame(int x, int y, int frameIndex);
    TileAnimationControlFacade setElapsedMs(int x, int y, int elapsedMs);
}
