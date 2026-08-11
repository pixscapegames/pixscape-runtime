package games.pixscape.runtime.api;

/**
 * Logical tile editing operations for one tiled layer.
 *
 * <p>Operations affect existing tiled map data only and never create a tiled layer.</p>
 *
 * <p>Mutations keep per-cell tile animation playback state in sync with the logical tile value.
 * Out-of-bounds or missing-capability getters return {@code 0} or
 * {@link games.pixscape.runtime.tiled.TileTransformFlags#NONE}; setters are inert. Use
 * {@link TiledMapFacade#isInside(int, int)} to distinguish a valid empty cell from an invalid
 * coordinate.</p>
 */
public interface TileEditFacade {
    int get(int x, int y);

    byte getFlags(int x, int y);

    TileEditFacade set(int x, int y, int assetId);

    TileEditFacade set(int x, int y, int assetId, byte flags);

    TileEditFacade set(int x, int y, String animationName);

    TileEditFacade setAnimated(int x, int y, String animationName);

    TileEditFacade clear(int x, int y);

    TileEditFacade fillRect(int x, int y, int width, int height, int assetId);

    TileEditFacade fillRect(int x, int y, int width, int height, int assetId, byte flags);

    TileEditFacade clearRect(int x, int y, int width, int height);

    TileEditFacade hLine(int x, int y, int length, int assetId);

    TileEditFacade hLine(int x, int y, int length, int assetId, byte flags);

    TileEditFacade vLine(int x, int y, int length, int assetId);

    TileEditFacade vLine(int x, int y, int length, int assetId, byte flags);

    TileEditFacade markAllDirty();
}
