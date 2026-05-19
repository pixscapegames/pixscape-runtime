package games.pixscape.runtime.api;

/**
 * Logical tile editing operations for one tiled layer.
 *
 * <p>Mutations keep per-cell tile animation playback state in sync with the logical tile value.</p>
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
