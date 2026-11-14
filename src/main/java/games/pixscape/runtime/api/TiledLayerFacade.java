package games.pixscape.runtime.api;

import games.pixscape.runtime.tiled.TiledProjection;

/**
 * Runtime tile layer properties and coordinate conversion helpers.
 * Operations affect existing tile layer data only and never create a layer.
 * Missing or stale capabilities report zero dimensions, an empty atlas tag, a null projection,
 * zero coordinate-conversion results, and {@code false} from {@link #isInside(int, int)}.
 */
public interface TiledLayerFacade {
    int width();

    int height();

    int tileWidth();

    int tileHeight();

    int chunkSize();

    int chunksX();

    int chunksY();

    /**
     * Returns whether the cell coordinate is inside the existing logical layer.
     * Missing or stale tiled capabilities return {@code false}.
     */
    boolean isInside(int x, int y);

    String atlasTag();

    TiledLayerFacade setAtlasTag(String atlasTag);

    /**
     * Returns this layer's authored projection, or {@code null} when stale or missing.
     */
    TiledProjection projection();

    TiledLayerFacade setVisible(boolean visible);

    /**
     * Controls native collision participation for this loaded layer.
     *
     * <p>Disabling collisions removes the layer's native Box2D body and fixtures while preserving
     * its authored physics body, shapes, settings, links, and persistent shape identities.
     * Enabling collisions allows the normal physics synchronization path to recreate native
     * state from those existing authored components. This runtime-only toggle never creates or
     * deletes authored physics, changes Spatial data or owning-layer state, affects another layer,
     * or enables global scene physics.</p>
     *
     * @throws IllegalStateException when enabling collisions while the active scene explicitly
     *                               has physics disabled
     */
    TiledLayerFacade setCollisionEnabled(boolean enabled);

    /**
     * Sets the layer origin in world units.
     *
     * @throws IllegalArgumentException when either coordinate is NaN or infinite
     */
    TiledLayerFacade setOrigin(float x, float y);

    int worldToTileX(float worldX);

    int worldToTileY(float worldY);

    int worldToTileX(float worldX, float worldY);

    int worldToTileY(float worldX, float worldY);

    float tileToWorldX(int gx);

    float tileToWorldY(int gy);

    float tileToWorldX(int gx, int gy);

    float tileToWorldY(int gx, int gy);

    /**
     * Resizes the logical layer in cells.
     *
     * <p>This is expensive and rebuilds tiled chunk data. Current runtime behavior preserves
     * only cells that remain in bounds after resize. Tile size, chunk size, and projection are
     * unchanged.</p>
     *
     * @throws IllegalArgumentException when width or height is not positive
     */
    TiledLayerFacade resize(int width, int height);
}
