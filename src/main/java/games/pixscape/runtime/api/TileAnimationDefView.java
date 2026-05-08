package games.pixscape.runtime.api;

/**
 * Read-only animated tile definition view.
 *
 * <p>Implementations may return an ephemeral reused object. Read values immediately and do not
 * keep references as stable snapshots across subsequent API calls.</p>
 */
public interface TileAnimationDefView {
    int id();

    int frameCount();

    int frameAssetId(int index);

    int frameDurationMs(int index);
}
