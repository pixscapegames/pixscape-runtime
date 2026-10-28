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

    /** Width of the existing active rectangle in projected pixels, or zero when absent. */
    float rectangleWidth();
    /** Local rectangle X relative to the resolved footprint center, or zero when absent. */
    float rectangleX();
    /** Local rectangle Y relative to the resolved footprint center, or zero when absent. */
    float rectangleY();
    /** Local shadow pivot X, independent of sorting/altitude, or zero when absent. */
    float shadowPivotX();
    /** Local shadow pivot Y, independent of sorting/altitude, or zero when absent. */
    float shadowPivotY();
    /**
     * Edits the existing active static/animation-clip rectangle. Dimensions must be positive and
     * every value finite. Throws when a live entity has no prepared active rectangle; does not
     * create per-clip state in gameplay. Stale references do nothing. Render thread before World
     * processing; indexed and allocation-free, with final values consumed on the next frame.
     * Changing the rectangle can change shadow replay capacity requirements at frame publication.
     */
    SpatialEntityFacade setRectangle(float x, float y, float width, float height);
    /** Same lifecycle/cost as setRectangle; changes only the independent local shadow pivot. */
    SpatialEntityFacade setShadowPivot(float x, float y);

    /**
     * Returns whether the entity currently satisfies the Runtime conditions for
     * Spatial actor render ordering.
     */
    boolean participatesInRenderOrder();
}
