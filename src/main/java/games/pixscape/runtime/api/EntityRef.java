package games.pixscape.runtime.api;

/**
 * Entity handle that bridges stableId identity and runtime ECS access.
 *
 * <p>The handle stores a runtime {@code entityId}, while {@link #stableId()}
 * resolves the preferred persistent/public identity.</p>
 */
public interface EntityRef {
    /**
     * Runtime ECS-oriented entity identifier (may be short-lived/recycled).
     */
    int entityId();

    /**
     * Preferred persistent/public entity identity.
     */
    long stableId();

    boolean exists();

    TransformFacade transform();

    SpriteFacade sprite();

    AnimationFacade animation();

    ParticleFacade particles();

    ShaderFacade shader();

    LightFacade light();

    void remove();

    /**
     * Expert ECS escape hatch.
     *
     * <p>Use this for low-level operations while keeping regular usage in the high-level API.</p>
     */
    ECSAPI ecs();
}
