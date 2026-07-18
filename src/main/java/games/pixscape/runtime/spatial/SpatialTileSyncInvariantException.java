package games.pixscape.runtime.spatial;

/** Reports an occupied Spatial tiled-layer cell without its required canonical rank. */
public final class SpatialTileSyncInvariantException extends RuntimeException {
    public SpatialTileSyncInvariantException(String message) {
        super(message);
    }
}
