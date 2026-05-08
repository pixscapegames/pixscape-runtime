package games.pixscape.runtime.api;

/**
 * High-level sprite/material access for one entity.
 */
public interface SpriteFacade {
    int assetId();

    SpriteFacade setAssetId(int assetId);

    SpriteFacade setAsset(int assetId, String atlasTag);

    SpriteFacade setVisible(boolean visible);

    SpriteFacade setTint(float r, float g, float b, float a);

    SpriteFacade setAlpha(float alpha);

    SpriteFacade setSize(float width, float height);
}
