package games.pixscape.runtime.spatial;

/** Recoverable preview diagnostic for a spatially participating tile that remains unranked after repair. */
public final class SpatialTileSyncInvariantException extends RuntimeException {
    public SpatialTileSyncInvariantException(String message) {
        super(message);
    }
}
