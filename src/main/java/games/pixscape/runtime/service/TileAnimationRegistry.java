package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.tiled.animation.TileAnimationDef;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;

/**
 * Default runtime registry for animated tile definitions.
 *
 * This registry is the standard storage used by the runtime to resolve
 * animated tile definitions from logical asset ids.
 */
public final class TileAnimationRegistry implements TileAnimationLookup {

    private final IntMap<TileAnimationDef> defs = new IntMap<>();

    @Override
    public TileAnimationDef get(int assetId) {
        return defs.get(assetId);
    }

    /**
     * Registers or replaces an animated tile definition.
     */
    public void put(TileAnimationDef def) {
        if (def == null) {
            throw new IllegalArgumentException("def must not be null");
        }
        defs.put(def.ownerAssetId(), def);
    }

    /**
     * Registers or replaces an animated tile definition from raw DTO data.
     */
    public void put(TileAnimationDefData data) {
        if (data == null) {
            throw new IllegalArgumentException("data must not be null");
        }
        put(new TileAnimationDef(data));
    }

    /**
     * Convenience overload to register an animated tile definition directly.
     */
    public void put(int ownerAssetId, int[] frameAssetIds, int[] frameDurationsMs) {
        TileAnimationDefData data = new TileAnimationDefData();
        data.ownerAssetId = ownerAssetId;
        data.frameAssetIds = frameAssetIds;
        data.frameDurationsMs = frameDurationsMs;
        put(data);
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
     * Useful for iteration or debugging.
     */
    public IntMap.Values<TileAnimationDef> values() {
        return defs.values();
    }

    /**
     * Returns all registry entries.
     * Useful for inspection.
     */
    public IntMap.Entries<TileAnimationDef> entries() {
        return defs.entries();
    }
}