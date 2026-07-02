package games.pixscape.runtime.render;

import org.junit.Assert;
import org.junit.Test;

public class FrameRenderQueueTest {

    @Test
    public void setCapacityAllocatesAllFieldsAndClearsSize() {
        FrameRenderQueue queue = new FrameRenderQueue(4);

        Assert.assertEquals(4, queue.getCapacity());
        Assert.assertEquals(0, queue.size);
        Assert.assertEquals(4, queue.textureHandle.length);
        Assert.assertEquals(4, queue.shader.length);
        Assert.assertEquals(4, queue.blend.length);
        Assert.assertEquals(4, queue.layerIndex.length);
        Assert.assertEquals(4, queue.paramsId.length);
        Assert.assertEquals(4, queue.customParamsId.length);
        Assert.assertEquals(4, queue.sortKey.length);
        Assert.assertEquals(4, queue.x1.length);
        Assert.assertEquals(4, queue.y1.length);
        Assert.assertEquals(4, queue.x2.length);
        Assert.assertEquals(4, queue.y2.length);
        Assert.assertEquals(4, queue.x3.length);
        Assert.assertEquals(4, queue.y3.length);
        Assert.assertEquals(4, queue.x4.length);
        Assert.assertEquals(4, queue.y4.length);
        Assert.assertEquals(4, queue.u1.length);
        Assert.assertEquals(4, queue.v1.length);
        Assert.assertEquals(4, queue.u2.length);
        Assert.assertEquals(4, queue.v2.length);
        Assert.assertEquals(4, queue.colorPacked.length);
        Assert.assertEquals(4, queue.repeatFlags.length);
        Assert.assertEquals(4, queue.sourceDomain.length);
        Assert.assertEquals(4, queue.sourceSlot.length);
        Assert.assertEquals(4, queue.sourceEntity.length);
    }

    @Test
    public void clearDoesNotReallocateArrays() {
        FrameRenderQueue queue = new FrameRenderQueue(2);
        int[] textureHandle = queue.textureHandle;
        float[] x1 = queue.x1;
        byte[] sourceDomain = queue.sourceDomain;

        queue.addQuad(
                11, 2, 3, 4, 5, 6, 7L,
                1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f,
                0.1f, 0.2f, 0.3f, 0.4f,
                0.5f,
                RenderRepeatFlags.REPEAT_X,
                FrameRenderQueue.SOURCE_ECS,
                12,
                13
        );

        queue.clear();

        Assert.assertEquals(0, queue.size);
        Assert.assertSame(textureHandle, queue.textureHandle);
        Assert.assertSame(x1, queue.x1);
        Assert.assertSame(sourceDomain, queue.sourceDomain);
    }

    @Test
    public void addQuadWritesAllFields() {
        FrameRenderQueue queue = new FrameRenderQueue(1);

        queue.addQuad(
                101, 102, 103, 104, 105, 106, 107L,
                1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f,
                0.1f, 0.2f, 0.3f, 0.4f,
                0.75f,
                (byte) (RenderRepeatFlags.REPEAT_X | RenderRepeatFlags.REPEAT_Y),
                FrameRenderQueue.SOURCE_TILED,
                201,
                -1
        );

        Assert.assertEquals(1, queue.size);
        Assert.assertEquals(101, queue.textureHandle[0]);
        Assert.assertEquals(102, queue.shader[0]);
        Assert.assertEquals(103, queue.blend[0]);
        Assert.assertEquals(104, queue.layerIndex[0]);
        Assert.assertEquals(105, queue.paramsId[0]);
        Assert.assertEquals(106, queue.customParamsId[0]);
        Assert.assertEquals(107L, queue.sortKey[0]);
        Assert.assertEquals(1f, queue.x1[0], 0f);
        Assert.assertEquals(2f, queue.y1[0], 0f);
        Assert.assertEquals(3f, queue.x2[0], 0f);
        Assert.assertEquals(4f, queue.y2[0], 0f);
        Assert.assertEquals(5f, queue.x3[0], 0f);
        Assert.assertEquals(6f, queue.y3[0], 0f);
        Assert.assertEquals(7f, queue.x4[0], 0f);
        Assert.assertEquals(8f, queue.y4[0], 0f);
        Assert.assertEquals(0.1f, queue.u1[0], 0f);
        Assert.assertEquals(0.2f, queue.v1[0], 0f);
        Assert.assertEquals(0.3f, queue.u2[0], 0f);
        Assert.assertEquals(0.4f, queue.v2[0], 0f);
        Assert.assertEquals(0.75f, queue.colorPacked[0], 0f);
        Assert.assertEquals(RenderRepeatFlags.ANY, queue.repeatFlags[0]);
        Assert.assertEquals(FrameRenderQueue.SOURCE_TILED, queue.sourceDomain[0]);
        Assert.assertEquals(201, queue.sourceSlot[0]);
        Assert.assertEquals(-1, queue.sourceEntity[0]);
    }

    @Test
    public void ensureCapacityGrowsAndPreservesExistingCommands() {
        FrameRenderQueue queue = new FrameRenderQueue(1);

        queue.addQuad(
                1, 2, 3, 4, 5, 6, 7L,
                1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f,
                0f, 0f, 1f, 1f,
                1f,
                RenderRepeatFlags.NONE,
                FrameRenderQueue.SOURCE_VFX,
                8,
                -1
        );

        int oldCapacity = queue.getCapacity();
        queue.ensureCapacity(2);

        Assert.assertTrue(queue.getCapacity() > oldCapacity);
        Assert.assertEquals(1, queue.size);
        Assert.assertEquals(1, queue.textureHandle[0]);
        Assert.assertEquals(7L, queue.sortKey[0]);
        Assert.assertEquals(FrameRenderQueue.SOURCE_VFX, queue.sourceDomain[0]);
    }

    @Test
    public void swapExchangesAllColumns() {
        FrameRenderQueue queue = new FrameRenderQueue(2);

        addDistinctQuad(queue, 10, FrameRenderQueue.SOURCE_ECS);
        addDistinctQuad(queue, 20, FrameRenderQueue.SOURCE_TILED);

        queue.swap(0, 1);

        assertDistinctQuad(queue, 0, 20, FrameRenderQueue.SOURCE_TILED);
        assertDistinctQuad(queue, 1, 10, FrameRenderQueue.SOURCE_ECS);
    }

    @Test
    public void swapSameIndexKeepsAllColumnsUnchanged() {
        FrameRenderQueue queue = new FrameRenderQueue(1);
        addDistinctQuad(queue, 30, FrameRenderQueue.SOURCE_VFX);

        queue.swap(0, 0);

        assertDistinctQuad(queue, 0, 30, FrameRenderQueue.SOURCE_VFX);
    }

    @Test(expected = IndexOutOfBoundsException.class)
    public void swapRejectsOutOfRangeIndex() {
        FrameRenderQueue queue = new FrameRenderQueue(1);
        addDistinctQuad(queue, 40, FrameRenderQueue.SOURCE_ECS);

        queue.swap(0, 1);
    }

    @Test(expected = IllegalArgumentException.class)
    public void setCapacityRejectsZero() {
        new FrameRenderQueue(0);
    }

    private static void addDistinctQuad(FrameRenderQueue queue, int base, byte sourceDomain) {
        queue.addQuad(
                base + 1,
                base + 2,
                base + 3,
                base + 4,
                base + 5,
                base + 6,
                base + 7L,
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
                RenderRepeatFlags.REPEAT_Y,
                sourceDomain,
                base + 8,
                base + 9
        );
    }

    private static void assertDistinctQuad(FrameRenderQueue queue, int index, int base, byte sourceDomain) {
        Assert.assertEquals(base + 1, queue.textureHandle[index]);
        Assert.assertEquals(base + 2, queue.shader[index]);
        Assert.assertEquals(base + 3, queue.blend[index]);
        Assert.assertEquals(base + 4, queue.layerIndex[index]);
        Assert.assertEquals(base + 5, queue.paramsId[index]);
        Assert.assertEquals(base + 6, queue.customParamsId[index]);
        Assert.assertEquals(base + 7L, queue.sortKey[index]);
        Assert.assertEquals(base + 0.1f, queue.x1[index], 0f);
        Assert.assertEquals(base + 0.2f, queue.y1[index], 0f);
        Assert.assertEquals(base + 0.3f, queue.x2[index], 0f);
        Assert.assertEquals(base + 0.4f, queue.y2[index], 0f);
        Assert.assertEquals(base + 0.5f, queue.x3[index], 0f);
        Assert.assertEquals(base + 0.6f, queue.y3[index], 0f);
        Assert.assertEquals(base + 0.7f, queue.x4[index], 0f);
        Assert.assertEquals(base + 0.8f, queue.y4[index], 0f);
        Assert.assertEquals(base + 0.9f, queue.u1[index], 0f);
        Assert.assertEquals(base + 1.1f, queue.v1[index], 0f);
        Assert.assertEquals(base + 1.2f, queue.u2[index], 0f);
        Assert.assertEquals(base + 1.3f, queue.v2[index], 0f);
        Assert.assertEquals(base + 1.4f, queue.colorPacked[index], 0f);
        Assert.assertEquals(RenderRepeatFlags.REPEAT_Y, queue.repeatFlags[index]);
        Assert.assertEquals(sourceDomain, queue.sourceDomain[index]);
        Assert.assertEquals(base + 8, queue.sourceSlot[index]);
        Assert.assertEquals(base + 9, queue.sourceEntity[index]);
    }
}
