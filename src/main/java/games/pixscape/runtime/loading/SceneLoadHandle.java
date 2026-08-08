package games.pixscape.runtime.loading;

/**
 * Drives one complete progressive scene load from file availability through READY.
 *
 * <p>Applications call {@link #update()} from their normal loop. Pixscape does not
 * render a loading screen.</p>
 */
public interface SceneLoadHandle {
    void update();

    /** Returns monotonic Pixscape-scoped progress in {@code [0, 1]}. */
    float progress();

    SceneLoadPhase phase();

    boolean isReady();

    boolean isFailed();

    /** Returns the original load failure, or {@code null} while no failure occurred. */
    Throwable failure();
}
