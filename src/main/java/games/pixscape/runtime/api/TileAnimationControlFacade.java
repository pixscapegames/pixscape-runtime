package games.pixscape.runtime.api;

/**
 * Per-cell playback control for animated tiles in one tiled layer.
 *
 * <p>These operations control runtime playback state for one cell. They do not modify the global
 * animated tile definition registry.</p>
 *
 * <p>On non-animated cells, mutating operations ({@code play/pause/stop/restart/setFrame/setElapsedMs})
 * are no-ops.</p>
 */
public interface TileAnimationControlFacade {
    boolean isAnimated(int x, int y);

    boolean isPlaying(int x, int y);

    boolean isPaused(int x, int y);

    TileAnimationControlFacade play(int x, int y);

    TileAnimationControlFacade pause(int x, int y);

    /**
     * Stops playback for the cell by clearing its per-cell playback state.
     *
     * <p>This does not remove the logical tile asset from the map. Dirty is raised only when the
     * visible rendered frame actually changes.</p>
     */
    TileAnimationControlFacade stop(int x, int y);

    TileAnimationControlFacade restart(int x, int y);

    TileAnimationControlFacade setFrame(int x, int y, int frameIndex);

    TileAnimationControlFacade setElapsedMs(int x, int y, int elapsedMs);
}
