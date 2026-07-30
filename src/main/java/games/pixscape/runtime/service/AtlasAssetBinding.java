package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;

/**
 * Complete, load-time binding for one Pixscape asset in an atlas.
 *
 * <p>The region array is built and ordered once by {@link AtlasAssetIndexBuilder}
 * and remains private. Consumers use indexed, allocation-free accessors.</p>
 */
public final class AtlasAssetBinding {

    private final int assetId;
    private final String regionGroup;
    private final TextureAtlas.AtlasRegion firstRegion;
    private final Array<TextureAtlas.AtlasRegion> regions;
    private final AtlasRegionMetadata metadata;

    AtlasAssetBinding(
            int assetId,
            String regionGroup,
            TextureAtlas.AtlasRegion firstRegion,
            Array<TextureAtlas.AtlasRegion> regions,
            AtlasRegionMetadata metadata) {
        this.assetId = assetId;
        this.regionGroup = regionGroup;
        this.firstRegion = firstRegion;
        this.regions = regions;
        this.metadata = metadata;
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

    public int regionCount() {
        return regions.size;
    }

    public TextureAtlas.AtlasRegion regionAt(int index) {
        return regions.get(index);
    }

    public AtlasRegionMetadata metadata() {
        return metadata;
    }
}
