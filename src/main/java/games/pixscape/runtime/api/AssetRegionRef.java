package games.pixscape.runtime.api;

import com.badlogic.gdx.graphics.g2d.TextureRegion;

public interface AssetRegionRef {
    int assetId();

    String name();

    TextureRegion region();

    float width();

    float height();
}
