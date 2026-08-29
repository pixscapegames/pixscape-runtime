package games.pixscape.runtime.api;

/**
 * Spatial render-order settings for one Tiled Map.
 *
 * <p>Operations affect an existing tiled capability only and never create a layer or map.</p>
 *
 * <p>This facade exposes Tiled Map participation, default tile volume, and
 * per-cell volume overrides. Authored occluder blocks are not exposed by this
 * high-level facade; advanced authored/compiler access is a separate
 * {@code SUPPORTED_EXPERT} contract.</p>
 *
 * <p>Authored altitude and height values must be finite. Negative finite heights are clamped
 * to zero.</p>
 */
public interface TiledSpatialFacade {
    /**
     * Returns whether this Tiled Map participates in spatial render ordering.
     */
    boolean enabled();

    /**
     * Enables or disables spatial render ordering for this Tiled Map.
     */
    TiledSpatialFacade setEnabled(boolean enabled);

    /**
     * Default bottom altitude used by tiles without a per-cell override.
     */
    float defaultAltitude();

    /**
     * Default height used by tiles without a per-cell override.
     */
    float defaultHeight();

    /**
     * Sets the default spatial volume used by tiles without per-cell overrides.
     *
     * <p>Negative height values are clamped to zero.</p>
     */
    TiledSpatialFacade setDefaultVolume(float altitude, float height);

    /**
     * Returns whether the cell has an explicit spatial volume override.
     */
    boolean hasTileOverride(int x, int y);

    /**
     * Returns the effective tile altitude, including the layer default when no
     * per-cell override exists.
     */
    float tileAltitude(int x, int y);

    /**
     * Returns the effective tile height, including the layer default when no
     * per-cell override exists.
     */
    float tileHeight(int x, int y);

    /**
     * Sets an explicit spatial volume override for one tile cell.
     *
     * <p>Negative height values are clamped to zero.</p>
     */
    TiledSpatialFacade setTileVolume(int x, int y, float altitude, float height);

    /**
     * Clears the explicit spatial volume override for one tile cell so it uses
     * the layer default again.
     */
    TiledSpatialFacade clearTileOverride(int x, int y);
}
