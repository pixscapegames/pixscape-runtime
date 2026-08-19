package games.pixscape.runtime.api;

/**
 * Entity handle that bridges stableId identity and runtime ECS access.
 *
 * <p>The handle is bound to the entity incarnation and Runtime World in which
 * it was resolved. If that entity is removed or the World is replaced, the
 * handle becomes inert and never retargets a recycled {@code entityId}.</p>
 *
 * <p>Facades obtained from this reference remain bound to the same captured entity. When stale,
 * their queries return the safe absence/default values documented by each facade and mutations
 * have no effect. {@link #entityId()} still reports the captured short-lived ID for diagnostics;
 * {@link #stableId()} returns {@code -1} once stale.</p>
 */
public interface EntityRef {
    /**
     * Runtime ECS-oriented entity identifier (may be short-lived/recycled).
     */
    int entityId();

    /**
     * Returns the preferred persistent/public identity, or {@code -1} when stale.
     */
    int stableId();

    /**
     * Returns whether the same captured entity still exists in the same Runtime World.
     */
    boolean exists();

    TransformFacade transform();

    SpriteFacade sprite();

    AnimationFacade animation();

    ParticleFacade particles();

    ShaderFacade shader();

    LightFacade light();

    /**
     * Returns a non-null, read-only view of this entity's custom properties.
     *
     * <p>An entity without custom properties, or a stale entity reference, behaves like an empty
     * property set.</p>
     */
    CustomProperties properties();

    /**
     * Spatial render-order settings for this entity.
     */
    SpatialEntityFacade spatial();

    /**
     * Layer placement and local z-order controls for this entity.
     *
     * <p>For example, {@code entity.renderOrder().layerIndex(4)} preserves the
     * current z-index, while chaining {@code .zIndex(10)} changes it.</p>
     */
    RenderOrderFacade renderOrder();

    /**
     * Schedules removal only while this handle still identifies its captured entity.
     * Calling this method on a stale handle has no effect.
     */
    void remove();

    /**
     * Expert ECS escape hatch.
     *
     * <p>Use this for low-level operations while keeping regular usage in the high-level API.</p>
     */
    ECSAPI ecs();
}
