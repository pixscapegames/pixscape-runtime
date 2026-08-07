package games.pixscape.runtime.api;

/**
 * High-level transform access for one entity.
 * Setters establish a standalone authored transform when it is absent.
 * Non-finite position, rotation, scale, origin, and delta values are rejected before mutation.
 * Negative finite scale remains supported.
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
