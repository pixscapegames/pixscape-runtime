package games.pixscape.runtime.api;

public interface TiledMapFacade {
    int width();
    int height();
    int tileWidth();
    int tileHeight();
    int chunkSize();
    int chunksX();
    int chunksY();

    String atlasTag();
    TiledMapFacade setAtlasTag(String atlasTag);

    Object projection();

    TiledMapFacade setVisible(boolean visible);
    TiledMapFacade setCollisionEnabled(boolean enabled);
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
     * Expensive operation: reallocates chunks and preserves only in-bounds cells.
     */
    TiledMapFacade resize(int width, int height);
}
