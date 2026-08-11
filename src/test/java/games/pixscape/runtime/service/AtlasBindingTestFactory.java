package games.pixscape.runtime.service;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;

/**
 * Test-only factory for service doubles outside the atlas service package.
 */
public final class AtlasBindingTestFactory {

    private AtlasBindingTestFactory() {
    }

    public static AtlasAssetBinding single(
            int assetId,
            String regionName,
            float u1,
            float v1,
            float u2,
            float v2,
            int textureHandle,
            int pixelWidth,
            int pixelHeight) {
        TestTexture texture = new TestTexture(pixelWidth, pixelHeight);
        TextureAtlas.AtlasRegion region =
                new TextureAtlas.AtlasRegion(texture, 0, 0, pixelWidth, pixelHeight);
        region.name = regionName;
        region.index = -1;
        region.setRegion(u1, v1, u2, v2);
        region.packedWidth = pixelWidth;
        region.packedHeight = pixelHeight;
        region.originalWidth = pixelWidth;
        region.originalHeight = pixelHeight;
        Array<TextureAtlas.AtlasRegion> regions = new Array<>();
        regions.add(region);
        AtlasRegionMetadata metadata = new AtlasRegionMetadata(
                regionName,
                u1,
                v1,
                u2,
                v2,
                textureHandle,
                pixelWidth,
                pixelHeight);
        return new AtlasAssetBinding(
                assetId,
                regionName,
                region,
                regions,
                metadata);
    }

    public static AtlasAssetBinding frames(
            int assetId,
            String regionName,
            int frameCount,
            int textureHandle,
            int pixelWidth,
            int pixelHeight) {
        TestTexture texture = new TestTexture(pixelWidth * frameCount, pixelHeight);
        Array<TextureAtlas.AtlasRegion> regions = new Array<>();
        for (int i = 0; i < frameCount; i++) {
            TextureAtlas.AtlasRegion region = new TextureAtlas.AtlasRegion(
                    texture,
                    i * pixelWidth,
                    0,
                    pixelWidth,
                    pixelHeight);
            region.name = regionName;
            region.index = i;
            region.packedWidth = pixelWidth;
            region.packedHeight = pixelHeight;
            region.originalWidth = pixelWidth;
            region.originalHeight = pixelHeight;
            regions.add(region);
        }
        TextureAtlas.AtlasRegion first = regions.first();
        AtlasRegionMetadata metadata = new AtlasRegionMetadata(
                regionName,
                first.getU(),
                first.getV(),
                first.getU2(),
                first.getV2(),
                textureHandle,
                pixelWidth,
                pixelHeight);
        return new AtlasAssetBinding(
                assetId,
                regionName,
                first,
                regions,
                metadata);
    }

    private static final class TestTexture extends Texture {
        private final int width;
        private final int height;

        TestTexture(int width, int height) {
            super();
            this.width = width;
            this.height = height;
        }

        @Override
        public int getWidth() {
            return width;
        }

        @Override
        public int getHeight() {
            return height;
        }
    }
}
