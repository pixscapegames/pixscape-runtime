package games.pixscape.runtime.api;

/**
 * High-level API for runtime Tiled Maps.
 */
public interface TiledAPI {
    /**
     * Returns a tolerant tiled view bound to the current entity incarnation and World.
     */
    TiledMapRef ofEntityId(int entityId);

    /**
     * Returns a tolerant tiled view bound to the entity currently resolved by stable ID.
     */
    TiledMapRef ofStableId(int stableId);

    /**
     * Strictly resolves a tiled entity at acquisition time.
     * The returned reference can later become stale and inert.
     */
    TiledMapRef requireEntityId(int entityId);

    /**
     * Strictly resolves a tiled stable ID at acquisition time.
     * The returned reference can later become stale and inert.
     */
    TiledMapRef requireStableId(int stableId);

    /**
     * Global animated tile definition registry.
     *
     * <p>This manages animated tile definitions, not per-cell playback state.</p>
     */
    TiledAnimationsAPI animations();
}
