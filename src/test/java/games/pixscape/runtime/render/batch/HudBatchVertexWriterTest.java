package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.math.Affine2;
import org.junit.Assert;
import org.junit.Test;

public class HudBatchVertexWriterTest {

    @Test
    public void convertsLibGdxVerticesAndPreservesEveryPackedColor() {
        float[] source = {
                1f, 2f, 101f, 0.1f, 0.2f,
                3f, 4f, 202f, 0.3f, 0.4f,
                5f, 6f, 303f, 0.5f, 0.6f,
                7f, 8f, 404f, 0.7f, 0.8f
        };
        float[] target = new float[HudBatchVertexWriter.HUD_FLOATS_PER_QUAD];

        int written = HudBatchVertexWriter.copyWithLayer(
                source, 0, source.length, target, 0, 9f, null);

        Assert.assertEquals(target.length, written);
        Assert.assertArrayEquals(new float[] {
                1f, 2f, 101f, 0.1f, 0.2f, 9f,
                3f, 4f, 202f, 0.3f, 0.4f, 9f,
                5f, 6f, 303f, 0.5f, 0.6f, 9f,
                7f, 8f, 404f, 0.7f, 0.8f, 9f
        }, target, 0f);
    }

    @Test
    public void conversionHonorsSourceAndTargetOffsets() {
        float[] source = {-1f, 1f, 2f, 3f, 4f, 5f, -2f};
        float[] target = {-3f, -4f, -5f, -6f, -7f, -8f, -9f, -10f};

        int written = HudBatchVertexWriter.copyWithLayer(
                source, 1, 5, target, 1, 6f, null);

        Assert.assertEquals(6, written);
        Assert.assertArrayEquals(
                new float[] {-3f, 1f, 2f, 3f, 4f, 5f, 6f, -10f}, target, 0f);
    }

    @Test
    public void conversionAdjustsOnlyXyAndPreservesColorUvAndLayer() {
        float[] source = {
                1f, 2f, 101f, 0.1f, 0.2f,
                -3f, 4f, 202f, 0.3f, 0.4f
        };
        float[] target = new float[12];
        Affine2 adjustment = new Affine2().setToTrnRotScl(7f, -5f, 30f, 2f, 0.5f);

        int written = HudBatchVertexWriter.copyWithLayer(
                source, 0, source.length, target, 0, 9f, adjustment);

        Assert.assertEquals(12, written);
        assertAdjustedVertex(source, 0, target, 0, adjustment, 9f);
        assertAdjustedVertex(source, 5, target, 6, adjustment, 9f);
    }

    @Test
    public void bulkDrawValidationRequiresCompleteQuadsInsideTheSourceRange() {
        float[] source = new float[40];

        HudBatch.validateSpriteVertices(source, 0, 20);
        HudBatch.validateSpriteVertices(source, 20, 20);
        assertInvalidRange(source, -1, 20);
        assertInvalidRange(source, 30, 20);

        try {
            HudBatch.validateSpriteVertices(source, 0, 5);
            Assert.fail("Expected incomplete quad to be rejected");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("complete 20-float"));
        }
    }

    private static void assertInvalidRange(float[] source, int offset, int count) {
        try {
            HudBatch.validateSpriteVertices(source, offset, count);
            Assert.fail("Expected invalid range");
        } catch (IndexOutOfBoundsException expected) {
            Assert.assertTrue(expected.getMessage().contains("range"));
        }
    }

    private static void assertAdjustedVertex(float[] source, int sourceOffset,
                                             float[] target, int targetOffset,
                                             Affine2 adjustment, float layer) {
        float x = source[sourceOffset];
        float y = source[sourceOffset + 1];
        Assert.assertEquals(adjustment.m00 * x + adjustment.m01 * y + adjustment.m02,
                target[targetOffset], 0.0001f);
        Assert.assertEquals(adjustment.m10 * x + adjustment.m11 * y + adjustment.m12,
                target[targetOffset + 1], 0.0001f);
        Assert.assertEquals(source[sourceOffset + 2], target[targetOffset + 2], 0f);
        Assert.assertEquals(source[sourceOffset + 3], target[targetOffset + 3], 0f);
        Assert.assertEquals(source[sourceOffset + 4], target[targetOffset + 4], 0f);
        Assert.assertEquals(layer, target[targetOffset + 5], 0f);
    }
}

