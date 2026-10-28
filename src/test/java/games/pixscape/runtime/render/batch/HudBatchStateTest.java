package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Affine2;
import com.badlogic.gdx.math.Matrix4;
import org.junit.Assert;
import org.junit.Test;

public class HudBatchStateTest {

    @Test
    public void capacityRejectsValuesOutsideSignedShortIndexLimit() {
        assertInvalidCapacity(0);
        assertInvalidCapacity(-1);
        assertInvalidCapacity(HudBatch.MAX_CAPACITY + 1);

        HudBatch.validateCapacity(1);
        HudBatch.validateCapacity(HudBatch.MAX_CAPACITY);
    }

    @Test
    public void packedColorUpdatesPackedAndUnpackedState() {
        HudBatchState state = new HudBatchState();
        float packed = Color.toFloatBits(0.25f, 0.5f, 0.75f, 1f);

        state.setPackedColor(packed);

        Assert.assertEquals(packed, state.packedColor(), 0f);
        Assert.assertEquals(0.25f, state.color().r, 0.01f);
        Assert.assertEquals(0.5f, state.color().g, 0.01f);
        Assert.assertEquals(0.75f, state.color().b, 0.01f);
        Assert.assertEquals(1f, state.color().a, 0.01f);
    }

    @Test
    public void blendStateUsesSpriteBatchDefaultsAndTracksSeparateFunctions() {
        HudBatchState state = new HudBatchState();

        Assert.assertTrue(state.isBlendingEnabled());
        Assert.assertEquals(GL20.GL_SRC_ALPHA, state.blendSrcFunc());
        Assert.assertEquals(GL20.GL_ONE_MINUS_SRC_ALPHA, state.blendDstFunc());
        Assert.assertEquals(GL20.GL_SRC_ALPHA, state.blendSrcFuncAlpha());
        Assert.assertEquals(GL20.GL_ONE_MINUS_SRC_ALPHA, state.blendDstFuncAlpha());

        Assert.assertTrue(state.setBlendFunctionSeparate(1, 2, 3, 4));
        Assert.assertFalse(state.setBlendFunctionSeparate(1, 2, 3, 4));
        Assert.assertEquals(1, state.blendSrcFunc());
        Assert.assertEquals(2, state.blendDstFunc());
        Assert.assertEquals(3, state.blendSrcFuncAlpha());
        Assert.assertEquals(4, state.blendDstFuncAlpha());
    }

    @Test
    public void combinedMatrixIsProjectionTimesTransform() {
        HudBatchState state = new HudBatchState();
        Matrix4 projection = new Matrix4().setToOrtho2D(0f, 0f, 320f, 180f);
        Matrix4 transform = new Matrix4().translate(12f, 23f, 0f).scale(2f, 3f, 1f);
        Matrix4 expected = new Matrix4(projection).mul(transform);

        state.setProjectionMatrix(projection);
        state.setTransformMatrix(transform, false);

        Assert.assertArrayEquals(expected.val, state.combinedMatrix().val, 0f);
    }

    @Test
    public void identityVirtualTransformKeepsVertexAdjustmentDisabled() {
        HudBatchState state = new HudBatchState();

        state.setTransformMatrix(new Matrix4(), true);

        Assert.assertFalse(state.vertexAdjustmentNeeded());
    }

    @Test
    public void virtualTransformsAdjustVerticesForTranslationScaleRotationAndComposition() {
        Matrix4[] transforms = {
                new Matrix4().translate(7f, -3f, 0f),
                new Matrix4().scale(2f, 0.5f, 1f),
                new Matrix4().rotate(0f, 0f, 1f, 37f),
                new Matrix4().translate(4f, 9f, 0f)
                        .rotate(0f, 0f, 1f, -23f).scale(1.5f, 0.75f, 1f)
        };

        for (Matrix4 transform : transforms) {
            HudBatchState state = new HudBatchState();
            state.setTransformMatrix(transform, true);

            Assert.assertTrue(state.vertexAdjustmentNeeded());
            assertTransformsPointLike(transform, state.vertexAdjustment(), 2.25f, -1.5f);
            Assert.assertArrayEquals(transform.val, state.transformMatrix().val, 0f);
        }
    }

    @Test
    public void virtualTransformIsRelativeToTheRealGpuTransform() {
        HudBatchState state = new HudBatchState();
        Matrix4 real = new Matrix4().translate(10f, 5f, 0f).scale(2f, 3f, 1f);
        Matrix4 virtual = new Matrix4().translate(-4f, 8f, 0f)
                .rotate(0f, 0f, 1f, 25f).scale(0.75f, 1.25f, 1f);
        state.setTransformMatrix(real, false);

        state.setTransformMatrix(virtual, true);

        Affine2 adjustment = state.vertexAdjustment();
        Assert.assertArrayEquals(real.val, state.combinedMatrix().val, 0f);
        float adjustedX = adjustment.m00 * 3f + adjustment.m01 * -2f + adjustment.m02;
        float adjustedY = adjustment.m10 * 3f + adjustment.m11 * -2f + adjustment.m12;
        float renderedX = real.val[Matrix4.M00] * adjustedX
                + real.val[Matrix4.M01] * adjustedY + real.val[Matrix4.M03];
        float renderedY = real.val[Matrix4.M10] * adjustedX
                + real.val[Matrix4.M11] * adjustedY + real.val[Matrix4.M13];

        assertPointEquals(virtual, 3f, -2f, renderedX, renderedY);
        Assert.assertArrayEquals(virtual.val, state.transformMatrix().val, 0f);
    }

    @Test
    public void returningToTheRealTransformDisablesAdjustment() {
        HudBatchState state = new HudBatchState();
        Matrix4 real = new Matrix4().translate(6f, 2f, 0f);
        state.setTransformMatrix(real, false);
        state.setTransformMatrix(new Matrix4().translate(20f, 30f, 0f), true);

        state.setTransformMatrix(real, true);

        Assert.assertFalse(state.vertexAdjustmentNeeded());
        Assert.assertArrayEquals(real.val, state.transformMatrix().val, 0f);
    }

    @Test
    public void sequentialVirtualTransformsRemainRelativeToTheSameRealTransform() {
        HudBatchState state = new HudBatchState();
        Matrix4 real = new Matrix4().translate(10f, 0f, 0f);
        Matrix4 first = new Matrix4().translate(20f, 0f, 0f);
        Matrix4 second = new Matrix4().translate(40f, 5f, 0f);
        state.setTransformMatrix(real, false);

        state.setTransformMatrix(first, true);
        assertAdjustedThenRenderedPoint(state.vertexAdjustment(), real, first, 1f, 2f);
        state.setTransformMatrix(second, true);
        assertAdjustedThenRenderedPoint(state.vertexAdjustment(), real, second, 1f, 2f);
    }

    @Test
    public void projectionChangeMakesTheVirtualTransformTheNewRealTransform() {
        HudBatchState state = new HudBatchState();
        Matrix4 virtual = new Matrix4().translate(17f, 19f, 0f).scale(2f, 2f, 1f);
        Matrix4 projection = new Matrix4().setToOrtho2D(0f, 0f, 640f, 360f);
        state.setTransformMatrix(virtual, true);

        state.setProjectionMatrix(projection);

        Assert.assertFalse(state.vertexAdjustmentNeeded());
        Assert.assertArrayEquals(new Matrix4(projection).mul(virtual).val,
                state.combinedMatrix().val, 0f);
    }

    @Test
    public void singularRealTransformFailsClearlyWhenRelativeAdjustmentIsRequired() {
        HudBatchState state = new HudBatchState();
        state.setTransformMatrix(new Matrix4().scale(0f, 1f, 1f), false);

        try {
            state.setTransformMatrix(new Matrix4().translate(3f, 4f, 0f), true);
            Assert.fail("Expected singular real transform to be rejected");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("singular"));
        }
        Assert.assertFalse(state.vertexAdjustmentNeeded());
    }

    private static void assertAdjustedThenRenderedPoint(Affine2 adjustment, Matrix4 real,
                                                        Matrix4 expected, float x, float y) {
        float adjustedX = adjustment.m00 * x + adjustment.m01 * y + adjustment.m02;
        float adjustedY = adjustment.m10 * x + adjustment.m11 * y + adjustment.m12;
        float renderedX = real.val[Matrix4.M00] * adjustedX
                + real.val[Matrix4.M01] * adjustedY + real.val[Matrix4.M03];
        float renderedY = real.val[Matrix4.M10] * adjustedX
                + real.val[Matrix4.M11] * adjustedY + real.val[Matrix4.M13];
        assertPointEquals(expected, x, y, renderedX, renderedY);
    }

    private static void assertTransformsPointLike(Matrix4 expected, Affine2 actual,
                                                  float x, float y) {
        float actualX = actual.m00 * x + actual.m01 * y + actual.m02;
        float actualY = actual.m10 * x + actual.m11 * y + actual.m12;
        assertPointEquals(expected, x, y, actualX, actualY);
    }

    private static void assertPointEquals(Matrix4 expected, float x, float y,
                                          float actualX, float actualY) {
        float expectedX = expected.val[Matrix4.M00] * x
                + expected.val[Matrix4.M01] * y + expected.val[Matrix4.M03];
        float expectedY = expected.val[Matrix4.M10] * x
                + expected.val[Matrix4.M11] * y + expected.val[Matrix4.M13];
        Assert.assertEquals(expectedX, actualX, 0.0001f);
        Assert.assertEquals(expectedY, actualY, 0.0001f);
    }

    private static void assertInvalidCapacity(int capacity) {
        try {
            HudBatch.validateCapacity(capacity);
            Assert.fail("Expected invalid capacity " + capacity);
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("capacity"));
        }
    }
}

