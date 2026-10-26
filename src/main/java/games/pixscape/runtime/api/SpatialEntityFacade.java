package games.pixscape.runtime.api;

/** Spatial actor placement. Height accessors address the active spatial rectangle, never a separate sorting height. */
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
     * Height of the active spatial rectangle, in runtime spatial units.
     */
    float height();

    /**
     * Sets the bottom altitude of the entity spatial volume.
     */
    SpatialEntityFacade setAltitude(float altitude);

    /**
     * Sets the height of the active spatial rectangle.
     *
     * <p>Nonpositive values are rejected.</p>
     */
    SpatialEntityFacade setHeight(float height);

    /**
     * Sets the bottom altitude and height of the entity spatial volume.
     *
     * <p>Nonpositive height values are rejected.</p>
     */
    SpatialEntityFacade setVolume(float altitude, float height);

    /**
     * Returns whether the entity currently satisfies the Runtime conditions for
     * Spatial actor render ordering.
     */
    boolean participatesInRenderOrder();
}
