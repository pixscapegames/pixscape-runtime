package games.pixscape.runtime.api;

/**
 * Runtime tiled map/layer properties and coordinate conversion helpers.
 * Operations affect existing Tiled Map data only and never create a Map.
 * Missing or stale capabilities report zero dimensions, an empty atlas tag, a null projection,
 * zero coordinate-conversion results, and {@code false} from {@link #isInside(int, int)}.
 */
public interface TiledMapFacade {
    int width();

    int height();

    int tileWidth();

    int tileHeight();

    int chunkSize();

    int chunksX();

    int chunksY();

    /**
     * Returns whether the cell coordinate is inside the existing logical map.
     * Missing or stale tiled capabilities return {@code false}.
     */
    boolean isInside(int x, int y);

    String atlasTag();

    TiledMapFacade setAtlasTag(String atlasTag);

    Object projection();

    TiledMapFacade setVisible(boolean visible);

    /**
     * Controls native collision participation for this loaded map.
     *
     * <p>Disabling collisions removes the map's native Box2D body and fixtures while preserving
     * its authored physics body, shapes, settings, links, and persistent shape identities.
     * Enabling collisions allows the normal physics synchronization path to recreate native
     * state from those existing authored components. This runtime-only toggle never creates or
     * deletes authored physics, changes Spatial data or owning-layer state, affects another map,
     * or enables global scene physics.</p>
     *
     * @throws IllegalStateException when enabling collisions while the active scene explicitly
     *                               has physics disabled
     */
    TiledMapFacade setCollisionEnabled(boolean enabled);

    /**
     * Sets the map origin in world units.
     *
     * @throws IllegalArgumentException when either coordinate is NaN or infinite
     */
    TiledMapFacade setOrigin(float x, float y);

    int worldToTileX(float worldX);

    int worldToTileY(float worldY);

    int worldToTileX(float worldX, float worldY);

    int worldToTileY(float worldX, float worldY);

    float tileToWorldX(int gx);

    float tileToWorldY(int gy);

    float tileToWorldX(int gx, int gy);

    float tileToWorldY(int gx, int gy);

    /**
     * Resizes the logical map in cells.
     *
     * <p>This is expensive and rebuilds tiled chunk data. Current runtime behavior preserves
     * only cells that remain in bounds after resize. Tile size, chunk size, and projection are
     * unchanged.</p>
     *
     * @throws IllegalArgumentException when width or height is not positive
     */
    TiledMapFacade resize(int width, int height);
}
