package games.pixscape.runtime.api;

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
