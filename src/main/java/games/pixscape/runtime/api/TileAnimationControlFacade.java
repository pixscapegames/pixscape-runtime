package games.pixscape.runtime.api;

/**
 * Per-cell playback control for animated tiles in one Tiled Map.
 *
 * <p>Operations affect existing Tiled Map data only and never create a Map.</p>
 *
 * <p>These operations control runtime playback state for one cell. They do not modify the global
 * animated tile definition registry.</p>
 *
 * <p>One-shot playback is intended for simple visual map interactions, such as a door tile that
 * opens once and optionally holds its final frame. Gameplay-heavy doors with collision changes,
 * sounds, locks, persistence, or changing 2.5D footprints are usually better represented as
 * actors/gameObjects, or as a trigger/gameObject driving a tiled visual.</p>
 *
 * <p>Tiled animation playback is visual state. By default the runtime advances only chunks that
 * were visible during the previous frame, so authoritative gameplay timers should live in game
 * logic rather than in tiled animation playback.</p>
 *
 * <p>On non-animated cells, mutating operations ({@code play/pause/stop/restart/setFrame/setElapsedMs})
 * are no-ops.</p>
 */
public interface TileAnimationControlFacade {
    boolean isAnimated(int x, int y);

    boolean isPlaying(int x, int y);

    boolean isPaused(int x, int y);

    /**
     * Returns true after a one-shot animation reaches its terminal state.
     */
    boolean isFinished(int x, int y);

    /**
     * Current per-cell frame index, or 0 for missing/non-animated cells.
     */
    int currentFrame(int x, int y);

    /**
     * Elapsed milliseconds within the current per-cell frame, or 0 for missing/non-animated cells.
     */
    int elapsedMs(int x, int y);

    /**
     * Plays the cell as a normal looping animated tile.
     */
    TileAnimationControlFacade play(int x, int y);

    /**
     * Plays the cell once from frame 0 and holds the final frame when complete.
     */
    TileAnimationControlFacade playOnce(int x, int y);

    /**
     * Plays the cell once from frame 0.
     *
     * @param holdLastFrame when true, completion pauses on the final frame; when false, completion
     *                      returns to the same visual frame as {@link #stop(int, int)}
     */
    TileAnimationControlFacade playOnce(int x, int y, boolean holdLastFrame);

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
