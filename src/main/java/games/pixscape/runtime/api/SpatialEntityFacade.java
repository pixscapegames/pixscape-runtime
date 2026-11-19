package games.pixscape.runtime.api;

/** Independent footprint ownership and explicit per-visual representation settings.
 * Footprint getters/setters address this entity only. Use resolvedOwner() explicitly to edit
 * an inherited footprint. Rectangle coordinates are logical image pixels before deformation,
 * measured from its bottom-left corner; storage is normalized by DimensionsComponent.
 * Ownership changes publish before return; derived render geometry updates next frame.
 * Stale handles are inert.
 */
public interface SpatialEntityFacade {
    boolean hasOwnFootprint();

    /** Cached incarnation-safe handle of the nearest footprint owner, or null. */
    EntityRef resolvedOwner();

    /** Adds the default footprint if absent and initializes missing visual representations in its subtree. */
    SpatialEntityFacade addFootprint();

    /** Removes only the own footprint. Inheritance and dormant settings remain available. */
    SpatialEntityFacade removeFootprint();

    float footprintRadius();
    float footprintOffsetX();
    float footprintOffsetY();
    float altitude();
    SpatialEntityFacade setFootprint(float offsetX, float offsetY, float radius);
    SpatialEntityFacade setAltitude(float altitude);

    /** Geometry getters require authored settings on live entities; they never synthesize an image-sized rectangle. */
    float rectangleX();
    float rectangleY();
    float rectangleWidth();
    float rectangleHeight();
    float shadowPivotX();
    float shadowPivotY();

    /** Creates settings for the active clip on first edit. Configured dimensions must be finite
     * and strictly positive, including after normalization. Rejected values leave settings intact.
     * A valid zone may produce empty geometry after clipping or visual deformation.
     */
    SpatialEntityFacade setRectangle(float x, float y, float width, float height);

    /** Projected pixel offset relative to the resolved footprint; independent of image TRS. */
    SpatialEntityFacade setShadowPivot(float x, float y);
    boolean participatesInRenderOrder();
}
