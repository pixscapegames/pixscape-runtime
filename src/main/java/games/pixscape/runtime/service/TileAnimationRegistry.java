package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.tiled.animation.TileAnimationDef;
import games.pixscape.runtime.tiled.animation.TileAnimationDefData;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;

/**
 * Default runtime registry for animated tile definitions.
 * <p>
 * This registry is the standard storage used by the runtime to resolve
 * animated tile definitions from logical asset ids.
 */
public final class TileAnimationRegistry implements TileAnimationLookup {

    private final IntMap<TileAnimationDef> defs = new IntMap<>();
    private final ObjectMap<String, Integer> idsByName = new ObjectMap<>();

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
        TileAnimationDef existing = defs.get(def.id());
        if (existing != null) {
            unindexName(existing);
        }
        defs.put(def.id(), def);
        indexName(def);
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
    public void put(int id, int[] frameAssetIds, int[] frameDurationsMs) {
        TileAnimationDefData data = new TileAnimationDefData();
        data.id = id;
        data.frameAssetIds = frameAssetIds;
        data.frameDurationsMs = frameDurationsMs;
        put(data);
    }

    /**
     * Removes a definition for the given logical asset id.
     */
    public void remove(int assetId) {
        TileAnimationDef existing = defs.get(assetId);
        if (existing != null) {
            unindexName(existing);
        }
        defs.remove(assetId);
    }

    /**
     * Clears all registered animated tile definitions.
     */
    public void clear() {
        defs.clear();
        idsByName.clear();
    }

    /**
     * Returns true if a definition exists for the given logical asset id.
     */
    public boolean contains(int assetId) {
        return defs.containsKey(assetId);
    }

    public boolean containsName(String name) {
        return idByName(name, 0) > 0;
    }

    public int idByName(String name) {
        int id = idByName(name, 0);
        if (id <= 0) {
            throw new IllegalArgumentException("Unknown tiled animation name '" + name + "'.");
        }
        return id;
    }

    public TileAnimationDef getByName(String name) {
        int id = idByName(name, 0);
        return id > 0 ? defs.get(id) : null;
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

    private void indexName(TileAnimationDef def) {
        String name = normalizeName(def.name());
        if (name == null) return;
        idsByName.put(name, def.id());
    }

    private void unindexName(TileAnimationDef def) {
        String name = normalizeName(def.name());
        if (name == null) return;
        Integer indexedId = idsByName.get(name);
        if (indexedId != null && indexedId.intValue() == def.id()) {
            idsByName.remove(name);
        }
    }

    private int idByName(String name, int defaultValue) {
        String normalized = normalizeName(name);
        if (normalized == null) return defaultValue;
        Integer id = idsByName.get(normalized);
        return id != null ? id : defaultValue;
    }

    private static String normalizeName(String name) {
        if (name == null) return null;
        String normalized = name.trim();
        return normalized.length() == 0 ? null : normalized;
    }
}
