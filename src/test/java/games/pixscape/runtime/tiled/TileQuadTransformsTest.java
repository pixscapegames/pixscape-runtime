package games.pixscape.runtime.tiled;

import org.junit.Assert;
import org.junit.Test;

public class TileQuadTransformsTest {

    private static final String BL = "BL";
    private static final String TL = "TL";
    private static final String TR = "TR";
    private static final String BR = "BR";

    @Test
    public void buildSpriteQuadMapsAllTiledTransformFlagCombinations() {
        assertSourceToDestinationMapping(TileTransformFlags.NONE, BL, TL, TR, BR);
        assertSourceToDestinationMapping(TileTransformFlags.FLIP_H, BR, TR, TL, BL);
        assertSourceToDestinationMapping(TileTransformFlags.FLIP_V, TL, BL, BR, TR);
        assertSourceToDestinationMapping(
                (byte) (TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_V),
                TR,
                BR,
                BL,
                TL
        );
        assertSourceToDestinationMapping(TileTransformFlags.FLIP_D, TR, TL, BL, BR);
        assertSourceToDestinationMapping(
                (byte) (TileTransformFlags.FLIP_D | TileTransformFlags.FLIP_H),
                TL,
                TR,
                BR,
                BL
        );
        assertSourceToDestinationMapping(
                (byte) (TileTransformFlags.FLIP_D | TileTransformFlags.FLIP_V),
                BR,
                BL,
                TL,
                TR
        );
        assertSourceToDestinationMapping(
                (byte) (TileTransformFlags.FLIP_D | TileTransformFlags.FLIP_H | TileTransformFlags.FLIP_V),
                BL,
                BR,
                TR,
                TL
        );
    }

    private static void assertSourceToDestinationMapping(byte flags,
                                                         String expectedBlDestination,
                                                         String expectedTlDestination,
                                                         String expectedTrDestination,
                                                         String expectedBrDestination) {
        TiledMapLayerData map = new TiledMapLayerData(1, 1, 16, 16, 1);
        float[] quad = new float[8];

        TileQuadTransforms.buildSpriteQuad(map, 0, 0, 16, 16, flags, quad);

        Assert.assertEquals("BL source destination for flags " + flags, expectedBlDestination, destinationCorner(quad, 0));
        Assert.assertEquals("TL source destination for flags " + flags, expectedTlDestination, destinationCorner(quad, 2));
        Assert.assertEquals("TR source destination for flags " + flags, expectedTrDestination, destinationCorner(quad, 4));
        Assert.assertEquals("BR source destination for flags " + flags, expectedBrDestination, destinationCorner(quad, 6));
    }

    private static String destinationCorner(float[] quad, int index) {
        float x = quad[index];
        float y = quad[index + 1];

        if (x == 0f && y == 0f) return BL;
        if (x == 0f && y == 16f) return TL;
        if (x == 16f && y == 16f) return TR;
        if (x == 16f && y == 0f) return BR;

        throw new AssertionError("Unexpected transformed corner coordinate: (" + x + ", " + y + ")");
    }
}
