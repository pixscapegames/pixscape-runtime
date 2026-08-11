package games.pixscape.runtime.service;

import com.badlogic.gdx.utils.IntMap;

/**
 * Immutable lookup index for the Pixscape assets of one loaded atlas.
 */
public final class AtlasAssetIndex {

    private final IntMap<AtlasAssetBinding> byAssetId;
    private final int buildRegionVisits;

    AtlasAssetIndex(IntMap<AtlasAssetBinding> byAssetId, int buildRegionVisits) {
        this.byAssetId = byAssetId;
        this.buildRegionVisits = buildRegionVisits;
    }

    /**
     * Resolves an asset in O(1) average time without scanning atlas regions.
     */
    public AtlasAssetBinding get(int assetId) {
        return byAssetId.get(assetId);
    }

    public int size() {
        return byAssetId.size;
    }

    int buildRegionVisits() {
        return buildRegionVisits;
    }
}
