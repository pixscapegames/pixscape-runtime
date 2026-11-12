package games.pixscape.runtime.tiled.profile;

import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.api.CustomProperties;
import games.pixscape.runtime.api.TileDefinition;

public final class RuntimeTilesetProfiles {
    public static final String FORMAT = "pixscape.tileset-profiles";
    public static final int VERSION = 1;

    private final Array<RuntimeTilesetProfile> profiles = new Array<>();
    private final IntMap<RuntimeTilesetProfile> byTilesetId = new IntMap<>();
    private final IntMap<RuntimeTilesetProfile> byTileAssetId = new IntMap<>();
    private final IntMap<IntMap<TileDefinition>> tilesByLocalId = new IntMap<>();
    private final IntMap<TileDefinition> tilesByAssetId = new IntMap<>();

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
        // Older render-only profiles can publish several fragments for the same tileset.
        // Definition-bearing profiles must be unambiguous by (tileset ID, local ID).
        if (byTilesetId.containsKey(profile.tilesetId)
                && (profile.tiles.size > 0 || tilesByLocalId.get(profile.tilesetId).size > 0))
            throw new IllegalArgumentException("Duplicate tileset ID: " + profile.tilesetId);
        IntMap<TileDefinition> localTiles = new IntMap<>();
        com.badlogic.gdx.utils.IntSet tileIds = new com.badlogic.gdx.utils.IntSet();
        for (TileDefinitionData tile : profile.tiles) {
            if (tile == null || tile.localId < 0 || !members.contains(tile.assetId)
                    || localTiles.containsKey(tile.localId) || !tileIds.add(tile.assetId)
                    || tilesByAssetId.containsKey(tile.assetId) || tile.properties == null)
                throw new IllegalArgumentException("Invalid or duplicate tile definition in tileset " + profile.tilesetId);
            localTiles.put(tile.localId, new Definition(profile.tilesetId, tile));
        }
        profiles.add(profile);
        byTilesetId.put(profile.tilesetId, profile);
        tilesByLocalId.put(profile.tilesetId, localTiles);
        for (TileDefinition tile : localTiles.values()) tilesByAssetId.put(tile.assetId(), tile);
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
        tilesByLocalId.clear();
        tilesByAssetId.clear();
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

    /**
     * O(1), including absent local IDs. Indexes are built only at profile publication.
     */
    public TileDefinition tile(int tilesetId, int localId) {
        IntMap<TileDefinition> tiles = tilesByLocalId.get(tilesetId);
        return tiles == null ? null : tiles.get(localId);
    }

    public TileDefinition tileForAsset(int assetId) {
        return tilesByAssetId.get(assetId);
    }

    private static final class Definition implements TileDefinition {
        private final int tilesetId, localId, assetId;
        private final CustomProperties properties;

        Definition(int tilesetId, TileDefinitionData source) {
            this.tilesetId = tilesetId;
            localId = source.localId;
            assetId = source.assetId;
            properties = source.properties.copy().readOnlyView();
        }

        @Override
        public int tilesetId() {
            return tilesetId;
        }

        @Override
        public int localId() {
            return localId;
        }

        @Override
        public int assetId() {
            return assetId;
        }

        @Override
        public CustomProperties properties() {
            return properties;
        }
    }

    public String normalMapSourceForTileAsset(int tileAssetId) {
        RuntimeTilesetProfile profile = byTileAssetId.get(tileAssetId);
        return profile == null ? null : profile.normalMapSources.get(tileAssetId);
    }
}
