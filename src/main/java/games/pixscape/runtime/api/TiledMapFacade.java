package games.pixscape.runtime.api;

/**
 * Runtime tiled map/layer properties and coordinate conversion helpers.
 * Operations affect existing tiled map data only and never create a tiled layer.
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
