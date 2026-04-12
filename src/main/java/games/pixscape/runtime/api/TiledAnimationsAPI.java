package games.pixscape.runtime.api;

public interface TiledAnimationsAPI {
    boolean contains(int animatedTileAssetId);
    TileAnimationDefView get(int animatedTileAssetId);

    TiledAnimationsAPI put(int animatedTileAssetId, int[] frameAssetIds, int[] frameDurationsMs);
    TiledAnimationsAPI remove(int animatedTileAssetId);
    void clear();
}
