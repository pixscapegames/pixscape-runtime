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
     * Returns this reference's defensive texture-region snapshot.
     *
     * <p>Mutating the returned object does not modify the indexed atlas binding.</p>
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
