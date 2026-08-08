package games.pixscape.runtime.loading;

/**
 * {@code HIGH_LEVEL} handle that drives one progressive scene load through READY.
 *
 * <p>Applications call {@link #update()} from their normal loop, normally on the LibGDX render
 * thread. Each call performs a bounded synchronous loading step; the handle does not create a
 * worker thread and Pixscape does not render a loading screen.</p>
 *
 * <p>{@link SceneLoadPhase#READY READY} means the requested scene is active and every known
 * dependency in its load plan has completed heavyweight Runtime preparation. Normal per-frame
 * simulation/render work remains, and dependencies introduced dynamically after READY may still
 * use their documented lazy paths.</p>
 *
 * <p>On failure, {@link #failure()} retains the original throwable, progress remains below one,
 * and later {@code update()} calls have no effect. A failure before scene construction leaves an
 * existing active scene untouched; a failure after construction begins leaves no active scene.</p>
 */
public interface SceneLoadHandle {
    /** Advances this load once; calls after READY or failure have no effect. */
    void update();

    /** Returns monotonic Pixscape-scoped progress in {@code [0, 1]}. */
    float progress();

    /** Returns the current work phase; failure does not introduce a separate phase value. */
    SceneLoadPhase phase();

    /** Returns whether the requested scene has been published active with progress {@code 1}. */
    boolean isReady();

    /** Returns whether a loading step failed. */
    boolean isFailed();

    /** Returns the original load failure, or {@code null} while no failure occurred. */
    Throwable failure();
}
