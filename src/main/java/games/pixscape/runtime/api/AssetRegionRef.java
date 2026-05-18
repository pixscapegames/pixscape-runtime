package games.pixscape.runtime.api;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

/**
 * Read-only handle to an asset region resolved from the current scene atlas.
 */
public interface AssetRegionRef {
    /**
     * Returns the Pixscape asset id encoded in the atlas region.
     */
    int assetId();

    /**
     * Returns the normalized asset name used by runtime lookup.
     */
    String name();

    /**
     * Returns the libGDX texture region backing this asset.
     */
    TextureRegion region();

    /**
     * Returns the exported pixel width of the region.
     */
    float width();

    /**
     * Returns the exported pixel height of the region.
     */
    float height();
}
