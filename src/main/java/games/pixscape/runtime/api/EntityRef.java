package games.pixscape.runtime.api;

public interface EntityRef {
    int entityId();
    long stableId();
    boolean exists();

    TransformFacade transform();
    SpriteFacade sprite();
    AnimationFacade animation();
    ParticleFacade particles();
    ShaderFacade shader();
    LightFacade light();
    ECSAPI ecs();
}
