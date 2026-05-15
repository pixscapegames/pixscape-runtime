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
     * Runtime tiled layer access and tile animation APIs.
     */
    TiledAPI tiled();

    /**
     * Expert ECS access for low-level use cases.
     *
     * <p>This escape hatch coexists with the high-level API and does not replace it.</p>
     */
    ECSAPI ecs();

    PrefabsAPI prefabs();
}
