package games.pixscape.runtime.tiled.profile;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;

public final class RuntimeTilesetProfiles {
    public static final String FORMAT = "pixscape.tileset-profiles";
    public static final int VERSION = 1;

    private final Array<RuntimeTilesetProfile> profiles = new Array<>();
    private final IntMap<RuntimeTilesetProfile> byTilesetId = new IntMap<>();
    private final IntMap<RuntimeTilesetProfile> byTileAssetId = new IntMap<>();

    public static RuntimeTilesetProfiles empty() {
        return new RuntimeTilesetProfiles();
    }

    public void add(RuntimeTilesetProfile profile) {
        if (profile == null || profile.tilesetId <= 0) {
            return;
        }

        if (profile.tileAssetIds == null) {
            profile.tileAssetIds = new int[0];
        }
        com.badlogic.gdx.utils.IntSet members = new com.badlogic.gdx.utils.IntSet();
        for (int id : profile.tileAssetIds) if (id > 0) members.add(id);
        for (IntMap.Entry<String> normal : profile.normalMapSources)
            if (!members.contains(normal.key) || normal.value == null)
                throw new IllegalArgumentException("Normal resource belongs to an unknown tile: " + normal.key);
        profiles.add(profile);
        byTilesetId.put(profile.tilesetId, profile);
        for (int tileAssetId : profile.tileAssetIds) {
            if (tileAssetId > 0) {
                byTileAssetId.put(tileAssetId, profile);
            }
        }
    }

    public void clear() {
        profiles.clear();
        byTilesetId.clear();
        byTileAssetId.clear();
    }

    public int size() {
        return profiles.size;
    }

    public Array<RuntimeTilesetProfile> profiles() {
        return profiles;
    }

    public RuntimeTilesetProfile profileForTileset(int tilesetId) {
        return byTilesetId.get(tilesetId);
    }

    public RuntimeTilesetProfile profileForTileAsset(int tileAssetId) {
        return byTileAssetId.get(tileAssetId);
    }

    public String normalMapSourceForTileAsset(int tileAssetId) {
        RuntimeTilesetProfile profile = byTileAssetId.get(tileAssetId);
        return profile == null ? null : profile.normalMapSources.get(tileAssetId);
    }
}
