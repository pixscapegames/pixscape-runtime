package games.pixscape.runtime.api;

/**
 * Spatial render-order settings for one runtime entity.
 *
 * <p>Actor footprint geometry is derived from the entity physics circle fixture.
 * This facade only exposes the vertical volume used by the spatial renderer.</p>
 */
public interface SpatialEntityFacade {
    /**
     * Returns whether the entity has a spatial height component.
     */
    boolean enabled();

    /**
     * Ensures the entity has a spatial height component.
     */
    SpatialEntityFacade enable();

    /**
     * Removes the entity spatial height component when present.
     */
    SpatialEntityFacade disable();

    /**
     * Bottom altitude of the entity spatial volume, in runtime spatial units.
     */
    float altitude();

    /**
     * Height of the entity spatial volume, in runtime spatial units.
     */
    float height();

    /**
     * Sets the bottom altitude of the entity spatial volume.
     */
    SpatialEntityFacade setAltitude(float altitude);

    /**
     * Sets the height of the entity spatial volume.
     *
     * <p>Negative values are clamped to zero.</p>
     */
    SpatialEntityFacade setHeight(float height);

    /**
     * Sets the bottom altitude and height of the entity spatial volume.
     *
     * <p>Negative height values are clamped to zero.</p>
     */
    SpatialEntityFacade setVolume(float altitude, float height);

    /**
     * Returns whether this entity has a positive-height spatial volume.
     *
     * <p>Layer participation and physics fixture presence are still required by
     * the renderer for actor sorting.</p>
     */
    boolean participatesInRenderOrder();
}
