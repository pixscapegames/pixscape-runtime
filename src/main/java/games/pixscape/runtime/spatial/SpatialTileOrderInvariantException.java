package games.pixscape.runtime.spatial;

/** Rejects Spatial V3 geometry that cannot be represented by one static tile order. */
public final class SpatialTileOrderInvariantException extends RuntimeException {
    public SpatialTileOrderInvariantException(String message) {
        super(message);
    }
}
