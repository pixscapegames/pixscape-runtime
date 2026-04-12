package games.pixscape.runtime.api;

/**
 * Read-only animated tile definition view.
 *
 * <p>Implementations may return an ephemeral reused view object.
 * Callers should read values immediately and avoid retaining references
 * across subsequent API calls unless explicitly documented otherwise.</p>
 */
public interface TileAnimationDefView {
    int id();
    int frameCount();
    int frameAssetId(int index);
    int frameDurationMs(int index);
}
