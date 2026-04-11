package games.pixscape.runtime.tiled.animation;

@FunctionalInterface
public interface TileAnimationLookup {

    /**
     * @param assetId logical asset placed in the map
     * @return the animation definition if this asset is an animated tile, otherwise null
     */
    TileAnimationDef get(int assetId);
}