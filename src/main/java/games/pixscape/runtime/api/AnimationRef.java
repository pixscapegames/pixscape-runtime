package games.pixscape.runtime.api;

public interface AnimationRef {
    int entityId();

    EntityRef entity();

    TransformFacade transform();

    SpriteFacade sprite();

    AnimationFacade animation();

    ShaderFacade shader();

    AnimationRef play();

    AnimationRef play(String clip);

    AnimationRef loop(boolean loop);

    AnimationRef fps(float fps);

    AnimationRef scale(float scale);

    AnimationRef rotationRad(float radians);

    void remove();
}
