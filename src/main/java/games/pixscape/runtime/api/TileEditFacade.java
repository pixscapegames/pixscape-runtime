package games.pixscape.runtime.api;

/**
 * Logical tile editing operations for one Tiled Map.
 *
 * <p>Operations affect existing Tiled Map data only and never create a Map.</p>
 *
 * <p>Mutations keep per-cell tile animation playback state in sync with the logical tile value.
 * Out-of-bounds or missing-capability getters return {@code 0} or
 * {@link games.pixscape.runtime.tiled.TileTransformFlags#NONE}; setters are inert. Use
 * {@link TiledMapFacade#isInside(int, int)} to distinguish a valid empty cell from an invalid
 * coordinate.</p>
 */
public interface TileEditFacade {
    int get(int x, int y);

    /**
     * Returns the shared definition referenced by this cell, or null for an empty/out-of-bounds
     * cell, stale map or missing exported definition. Imported animated tiles resolve their
     * authored base definition, never the current animation frame. O(1); no data is created.
     * Animations authored without a source tile (including older imports) have no definition
     * and return null; no identity is inferred from their name or first frame.
     */
    TileDefinition definition(int x, int y);

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
