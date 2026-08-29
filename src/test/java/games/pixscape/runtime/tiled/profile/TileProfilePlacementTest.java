package games.pixscape.runtime.tiled.profile;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Assert;
import org.junit.Test;

public class TileProfilePlacementTest {
    private static final float EPSILON = 0.0001f;

    @Test
    public void topCenterProfileMatchesDefaultAnchorPlacement() {
        RuntimeTilesetProfile profile = profile(64, 32, RuntimeTilesetAnchor.TOP_CENTER, 0, 0);
        float[] quad = build(100f, 200f, 64, 32, 64, 32, profile);

        assertQuad(quad, 100f, 200f, 100f, 232f, 164f, 232f, 164f, 200f);
        assertMatchesTopCenterDefault(100f, 200f, 64, 32, 64, 32, quad);
    }

    @Test
    public void tallSpriteTopCenterProfileMatchesDefaultAnchorPlacement() {
        RuntimeTilesetProfile profile = profile(256, 128, RuntimeTilesetAnchor.TOP_CENTER, 0, 0);
        float[] quad = build(100f, 200f, 256, 128, 256, 512, profile);

        assertQuad(quad, 100f, -184f, 100f, 328f, 356f, 328f, 356f, -184f);
        assertMatchesTopCenterDefault(100f, 200f, 256, 128, 256, 512, quad);
    }

    @Test
    public void bottomCenterAlignsSpriteBottomToCellBottomCenter() {
        RuntimeTilesetProfile profile = profile(256, 128, RuntimeTilesetAnchor.BOTTOM_CENTER, 0, 0);
        float[] quad = build(100f, 200f, 256, 128, 256, 512, profile);

        assertQuad(quad, 100f, 200f, 100f, 712f, 356f, 712f, 356f, 200f);
    }

    @Test
    public void centerAlignsSpriteCenterToCellCenter() {
        RuntimeTilesetProfile profile = profile(256, 128, RuntimeTilesetAnchor.CENTER, 0, 0);
        float[] quad = build(100f, 200f, 256, 128, 256, 512, profile);

        assertQuad(quad, 100f, 8f, 100f, 520f, 356f, 520f, 356f, 8f);
    }

    @Test
    public void topLeftAlignsSpriteTopLeftToCellTopLeft() {
        RuntimeTilesetProfile profile = profile(256, 128, RuntimeTilesetAnchor.TOP_LEFT, 0, 0);
        float[] quad = build(100f, 200f, 256, 128, 256, 512, profile);

        assertQuad(quad, 100f, -184f, 100f, 328f, 356f, 328f, 356f, -184f);
    }

    @Test
    public void bottomLeftAlignsSpriteBottomLeftToCellBottomLeft() {
        RuntimeTilesetProfile profile = profile(256, 128, RuntimeTilesetAnchor.BOTTOM_LEFT, 0, 0);
        float[] quad = build(100f, 200f, 256, 128, 256, 512, profile);

        assertQuad(quad, 100f, 200f, 100f, 712f, 356f, 712f, 356f, 200f);
    }

    @Test
    public void offsetMovesQuadInYUpWorldCoordinates() {
        RuntimeTilesetProfile profile = profile(256, 128, RuntimeTilesetAnchor.BOTTOM_CENTER, 10, -20);
        float[] quad = build(100f, 200f, 256, 128, 256, 512, profile);

        assertQuad(quad, 110f, 180f, 110f, 692f, 366f, 692f, 366f, 180f);
    }

    @Test
    public void isometricTopCenterUsesDiamondTopVertex() {
        RuntimeTilesetProfile profile = profile(256, 128, RuntimeTilesetAnchor.TOP_CENTER, 0, 0);
        profile.projection = TiledProjection.ISO;

        float[] quad = build(100f, 200f, 256, 128, 256, 512, profile);

        assertQuad(quad, 100f, -184f, 100f, 328f, 356f, 328f, 356f, -184f);
    }

    @Test
    public void isometricBottomCenterAndCenterUseDiamondAnchors() {
        RuntimeTilesetProfile bottom = profile(256, 128, RuntimeTilesetAnchor.BOTTOM_CENTER, 0, 0);
        bottom.projection = TiledProjection.ISO;
        RuntimeTilesetProfile center = profile(256, 128, RuntimeTilesetAnchor.CENTER, 0, 0);
        center.projection = TiledProjection.ISO;

        assertQuad(build(100f, 200f, 256, 128, 256, 512, bottom),
                100f, 200f, 100f, 712f, 356f, 712f, 356f, 200f);
        assertQuad(build(100f, 200f, 256, 128, 256, 512, center),
                100f, 8f, 100f, 520f, 356f, 520f, 356f, 8f);
    }

    @Test(expected = IllegalArgumentException.class)
    public void nullProfileIsRejected() {
        build(100f, 200f, 256, 128, 256, 512, null);
    }

    @Test
    public void nullRenderSizeFallsBackToNativeDimensions() {
        RuntimeTilesetProfile profile = profile(256, 128, RuntimeTilesetAnchor.TOP_CENTER, 0, 0);
        profile.renderSize = null;

        float[] quad = build(100f, 200f, 256, 128, 256, 512, profile);

        assertQuad(quad, 100f, -184f, 100f, 328f, 356f, 328f, 356f, -184f);
    }

    @Test
    public void boundsWrapComputedQuad() {
        RuntimeTilesetProfile profile = profile(256, 128, RuntimeTilesetAnchor.CENTER, 10, -20);
        float[] quad = build(100f, 200f, 256, 128, 256, 512, profile);
        float[] bounds = new float[4];

        TileProfilePlacement.computeSpriteBounds(quad, bounds);

        Assert.assertEquals(110f, bounds[0], EPSILON);
        Assert.assertEquals(-12f, bounds[1], EPSILON);
        Assert.assertEquals(366f, bounds[2], EPSILON);
        Assert.assertEquals(500f, bounds[3], EPSILON);
    }

    private static float[] build(float cellX,
                                 float cellY,
                                 int mapCellWidth,
                                 int mapCellHeight,
                                 int spriteWidth,
                                 int spriteHeight,
                                 RuntimeTilesetProfile profile) {
        float[] quad = new float[8];
        TileProfilePlacement.buildSpriteQuad(
                cellX,
                cellY,
                mapCellWidth,
                mapCellHeight,
                spriteWidth,
                spriteHeight,
                profile,
                quad
        );
        return quad;
    }

    private static RuntimeTilesetProfile profile(int referenceCellWidth,
                                                 int referenceCellHeight,
                                                 RuntimeTilesetAnchor anchor,
                                                 int offsetX,
                                                 int offsetY) {
        RuntimeTilesetProfile profile = new RuntimeTilesetProfile();
        profile.referenceCellWidth = referenceCellWidth;
        profile.referenceCellHeight = referenceCellHeight;
        profile.anchor = anchor;
        profile.offsetX = offsetX;
        profile.offsetY = offsetY;
        profile.renderSize = RuntimeTilesetRenderSize.NATIVE;
        return profile;
    }

    private static void assertMatchesTopCenterDefault(float cellX,
                                                      float cellY,
                                                      int mapCellWidth,
                                                      int mapCellHeight,
                                                      int spriteWidth,
                                                      int spriteHeight,
                                                      float[] actual) {
        float[] topCenterDefault = new float[8];
        TileProfilePlacement.buildTopCenterDefaultSpriteQuad(
                cellX,
                cellY,
                mapCellWidth,
                mapCellHeight,
                spriteWidth,
                spriteHeight,
                topCenterDefault
        );
        assertQuad(actual, topCenterDefault);
    }

    private static void assertQuad(float[] actual,
                                   float blX,
                                   float blY,
                                   float tlX,
                                   float tlY,
                                   float trX,
                                   float trY,
                                   float brX,
                                   float brY) {
        assertQuad(actual, new float[]{blX, blY, tlX, tlY, trX, trY, brX, brY});
    }

    private static void assertQuad(float[] actual, float[] expected) {
        Assert.assertEquals(expected.length, actual.length);
        for (int i = 0; i < expected.length; i++) {
            Assert.assertEquals("quad[" + i + "]", expected[i], actual[i], EPSILON);
        }
    }
}
