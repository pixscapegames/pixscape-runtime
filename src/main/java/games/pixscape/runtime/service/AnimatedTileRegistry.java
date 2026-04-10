package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.tiled.animation.AnimatedTileDef;
import games.pixscape.runtime.tiled.animation.AnimatedTileLookup;

/**
 * Default runtime registry for animated tile definitions.
 *
 * This registry is the standard storage used by the runtime to resolve
 * animated tile definitions from logical asset ids.
 */
public final class AnimatedTileRegistry implements AnimatedTileLookup {

    private final IntMap<AnimatedTileDef> defs = new IntMap<>();

    @Override
    public AnimatedTileDef get(int assetId) {
        return defs.get(assetId);
    }

    /**
     * Registers or replaces an animated tile definition.
     */
    public void put(AnimatedTileDef def) {
        if (def == null) {
            throw new IllegalArgumentException("def must not be null");
        }
        defs.put(def.ownerAssetId(), def);
    }

    /**
     * Convenience overload to register an animated tile definition directly.
     */
    public void put(int ownerAssetId, int[] frameAssetIds, int[] frameDurationsMs) {
        put(new AnimatedTileDef(ownerAssetId, frameAssetIds, frameDurationsMs));
    }

    /**
     * Removes a definition for the given logical asset id.
     */
    public void remove(int assetId) {
        defs.remove(assetId);
    }

    /**
     * Clears all registered animated tile definitions.
     */
    public void clear() {
        defs.clear();
    }

    /**
     * Returns true if a definition exists for the given logical asset id.
     */
    public boolean contains(int assetId) {
        return defs.containsKey(assetId);
    }

    /**
     * Returns the number of registered animated tile definitions.
     */
    public int size() {
        return defs.size;
    }

    /**
     * Returns all registry values.
     * Useful for iteration, debugging or serialization.
     */
    public IntMap.Values<AnimatedTileDef> values() {
        return defs.values();
    }

    /**
     * Returns all registry entries.
     * Useful for serialization or inspection.
     */
    public IntMap.Entries<AnimatedTileDef> entries() {
        return defs.entries();
    }
}