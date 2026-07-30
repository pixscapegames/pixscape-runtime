package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;

/**
 * Complete, load-time binding for one Pixscape asset in an atlas.
 *
 * <p>The region array is built and ordered once by {@link AtlasAssetIndexBuilder}.
 * Callers must treat it as read-only.</p>
 */
public final class AtlasAssetBinding {

    private final int assetId;
    private final String regionGroup;
    private final TextureAtlas.AtlasRegion firstRegion;
    private final Array<TextureAtlas.AtlasRegion> regions;
    private final AtlasRuntimeService.CachedRegion cachedRegion;

    AtlasAssetBinding(
            int assetId,
            String regionGroup,
            TextureAtlas.AtlasRegion firstRegion,
            Array<TextureAtlas.AtlasRegion> regions,
            AtlasRuntimeService.CachedRegion cachedRegion) {
        this.assetId = assetId;
        this.regionGroup = regionGroup;
        this.firstRegion = firstRegion;
        this.regions = regions;
        this.cachedRegion = cachedRegion;
    }

    public int assetId() {
        return assetId;
    }

    public String regionGroup() {
        return regionGroup;
    }

    public TextureAtlas.AtlasRegion firstRegion() {
        return firstRegion;
    }

    /**
     * Returns the prebuilt region group. The returned array is shared and read-only.
     */
    public Array<TextureAtlas.AtlasRegion> regions() {
        return regions;
    }

    public AtlasRuntimeService.CachedRegion cachedRegion() {
        return cachedRegion;
    }
}
