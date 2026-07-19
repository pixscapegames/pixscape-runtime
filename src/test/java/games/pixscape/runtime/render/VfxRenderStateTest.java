package games.pixscape.runtime.render;

import org.junit.Assert;
import org.junit.Test;

public class VfxRenderStateTest {

    @Test
    public void setCapacityAllocatesAllFieldsAndClearsActiveCount() {
        VfxRenderState state = new VfxRenderState(4);

        Assert.assertEquals(4, state.getCapacity());
        Assert.assertEquals(0, state.activeCount);
        Assert.assertEquals(4, state.textureHandle.length);
        Assert.assertEquals(4, state.shader.length);
        Assert.assertEquals(4, state.blend.length);
        Assert.assertEquals(4, state.layerIndex.length);
        Assert.assertEquals(4, state.z.length);
        Assert.assertEquals(4, state.paramsId.length);
        Assert.assertEquals(4, state.customParamsId.length);
        Assert.assertEquals(4, state.sortKey.length);
        Assert.assertEquals(4, state.x1.length);
        Assert.assertEquals(4, state.y1.length);
        Assert.assertEquals(4, state.x2.length);
        Assert.assertEquals(4, state.y2.length);
        Assert.assertEquals(4, state.x3.length);
        Assert.assertEquals(4, state.y3.length);
        Assert.assertEquals(4, state.x4.length);
        Assert.assertEquals(4, state.y4.length);
        Assert.assertEquals(4, state.u1.length);
        Assert.assertEquals(4, state.v1.length);
        Assert.assertEquals(4, state.u2.length);
        Assert.assertEquals(4, state.v2.length);
        Assert.assertEquals(4, state.colorPacked.length);
        Assert.assertEquals(4, state.repeatFlags.length);
        Assert.assertEquals(4, state.sourceEmitter.length);
    }

    @Test
    public void clearFrameDoesNotReallocateArrays() {
        VfxRenderState state = new VfxRenderState(2);
        int[] textureHandle = state.textureHandle;
        float[] x1 = state.x1;
        byte[] repeatFlags = state.repeatFlags;

        addParticle(state, 10);
        state.clearFrame();

        Assert.assertEquals(0, state.activeCount);
        Assert.assertSame(textureHandle, state.textureHandle);
        Assert.assertSame(x1, state.x1);
        Assert.assertSame(repeatFlags, state.repeatFlags);
    }

    @Test
    public void addParticleQuadWritesAllFields() {
        VfxRenderState state = new VfxRenderState(1);

        addParticle(state, 20);

        assertParticle(state, 0, 20);
    }

    @Test
    public void ensureCapacityGrowsAndPreservesExistingParticles() {
        VfxRenderState state = new VfxRenderState(1);
        addParticle(state, 30);

        int oldCapacity = state.getCapacity();
        state.ensureCapacity(2);

        Assert.assertTrue(state.getCapacity() > oldCapacity);
        Assert.assertEquals(1, state.activeCount);
        assertParticle(state, 0, 30);
        Assert.assertEquals(1, state.getGrowthCount());
    }

    @Test(expected = IllegalArgumentException.class)
    public void setCapacityRejectsZero() {
        new VfxRenderState(0);
    }

    private static void addParticle(VfxRenderState state, int base) {
        state.addParticleQuad(
                base + 1,
                base + 2,
                base + 3,
                base + 4,
                base + 5,
                base + 6,
                base + 7,
                base + 8L,
                base + 0.1f,
                base + 0.2f,
                base + 0.3f,
                base + 0.4f,
                base + 0.5f,
                base + 0.6f,
                base + 0.7f,
                base + 0.8f,
                base + 0.9f,
                base + 1.1f,
                base + 1.2f,
                base + 1.3f,
                base + 1.4f,
                (byte) (RenderRepeatFlags.REPEAT_X | RenderRepeatFlags.REPEAT_Y),
                base + 9
        );
    }

    private static void assertParticle(VfxRenderState state, int index, int base) {
        Assert.assertEquals(base + 1, state.textureHandle[index]);
        Assert.assertEquals(base + 2, state.shader[index]);
        Assert.assertEquals(base + 3, state.blend[index]);
        Assert.assertEquals(base + 4, state.layerIndex[index]);
        Assert.assertEquals(base + 5, state.z[index]);
        Assert.assertEquals(base + 6, state.paramsId[index]);
        Assert.assertEquals(base + 7, state.customParamsId[index]);
        Assert.assertEquals(base + 8L, state.sortKey[index]);
        Assert.assertEquals(base + 0.1f, state.x1[index], 0f);
        Assert.assertEquals(base + 0.2f, state.y1[index], 0f);
        Assert.assertEquals(base + 0.3f, state.x2[index], 0f);
        Assert.assertEquals(base + 0.4f, state.y2[index], 0f);
        Assert.assertEquals(base + 0.5f, state.x3[index], 0f);
        Assert.assertEquals(base + 0.6f, state.y3[index], 0f);
        Assert.assertEquals(base + 0.7f, state.x4[index], 0f);
        Assert.assertEquals(base + 0.8f, state.y4[index], 0f);
        Assert.assertEquals(base + 0.9f, state.u1[index], 0f);
        Assert.assertEquals(base + 1.1f, state.v1[index], 0f);
        Assert.assertEquals(base + 1.2f, state.u2[index], 0f);
        Assert.assertEquals(base + 1.3f, state.v2[index], 0f);
        Assert.assertEquals(base + 1.4f, state.colorPacked[index], 0f);
        Assert.assertEquals(RenderRepeatFlags.ANY, state.repeatFlags[index]);
        Assert.assertEquals(base + 9, state.sourceEmitter[index]);
    }
}
