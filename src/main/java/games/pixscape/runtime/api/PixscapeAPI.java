package games.pixscape.runtime.api;

/**
 * High-level public runtime API entry point.
 */
public interface PixscapeAPI {
    EntitiesAPI entities();
    TiledAPI tiled();
    ECSAPI ecs();
}
