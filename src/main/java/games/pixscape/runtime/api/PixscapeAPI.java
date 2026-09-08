package games.pixscape.runtime.api;

/**
 * High-level API entry point for runtime code.
 */
public interface PixscapeAPI {
    /**
     * Entity-oriented access using stableId/entityId bridging facades.
     */
    EntitiesAPI entities();

    /**
     * Runtime Tiled Map access and tile animation APIs.
     */
    TiledAPI tiled();

    /**
     * Runtime spatial render-order API.
     */
    SpatialAPI spatial();

    /**
     * Expert ECS access for low-level use cases.
     *
     * <p>This escape hatch coexists with the high-level API and does not replace it.</p>
     */
    ECSAPI ecs();

    /**
     * Asset lookup in the current scene atlas.
     *
     * <p>Only assets included in Runtime Availability during export are available here.</p>
     */
    AssetsAPI assets();

    /**
     * Sprite entity factory API.
     */
    SpritesAPI sprites();

    /**
     * Sprite animation factory and control API.
     */
    AnimationsAPI animations();

    /**
     * Particle entity factory API.
     */
    ParticlesAPI particles();

    /**
     * Common physics queries and coordinate conversion with a borrowed native Box2D bridge.
     * Expert ECS, component, system, and service access remains available separately.
     */
    PhysicsAPI physics();

    /**
     * GameObject spawning API.
     */
    GameObjectsAPI gameObjects();
}
