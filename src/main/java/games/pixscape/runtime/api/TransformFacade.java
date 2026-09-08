package games.pixscape.runtime.api;

/**
 * High-level authored transform access for one entity.
 * For standalone entities and top-level Game Objects this is the normal authored world pose.
 * For a Game Object member it is the authored local transform relative to its parent; this
 * facade does not expose the resolved world transform.
 * Setters establish a standalone authored transform when it is absent.
 * Non-finite position, rotation, scale, origin, and delta values are rejected before mutation.
 * Negative finite scale remains supported.
 * Generic mutation throws {@link IllegalStateException} while Runtime Physics owns this entity's
 * Body or a descendant Body.
 */
public interface TransformFacade {
    float x();

    float y();

    float rotationRad();

    float scaleX();

    float scaleY();

    TransformFacade setPosition(float x, float y);

    TransformFacade setX(float x);

    TransformFacade setY(float y);

    TransformFacade moveBy(float dx, float dy);

    TransformFacade setRotationRad(float radians);

    TransformFacade rotateByRad(float radians);

    TransformFacade setScale(float uniform);

    TransformFacade setScale(float sx, float sy);

    TransformFacade setScaleX(float sx);

    TransformFacade setScaleY(float sy);

    TransformFacade setOrigin(float ox, float oy);
}
