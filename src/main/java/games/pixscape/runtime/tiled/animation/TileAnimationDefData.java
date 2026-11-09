package games.pixscape.runtime.tiled.animation;

public class TileAnimationDefData {
    public int id;
    public String name;
    /** Original tileset definition, or 0 for an animation authored without a source tile. */
    public int baseTileAssetId;
    public int[] frameAssetIds;
    public int[] frameDurationsMs;
}
