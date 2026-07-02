package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.Assert;
import org.junit.Test;

public class RenderExtractFrameQueueSystemTest {

    @Test
    public void extractsDrawListOrderAndCopiesDrawReadyFields() {
        RenderStateSOA state = new RenderStateSOA(256);
        VfxRenderState vfxState = new VfxRenderState(16);
        DrawList drawList = new DrawList(16);
        FrameRenderQueue queue = new FrameRenderQueue(1);
        RenderStats stats = new RenderStats();

        writeSlot(state, 120, -1, 10, 1f, 2f);
        writeSlot(state, 4, 4, 20, 3f, 4f);
        writeVfx(vfxState, 30);

        drawList.addTiledSlot(120);
        drawList.addEcsSlot(4);
        drawList.addVfxSlot(0);

        World world = new World(new WorldConfigurationBuilder()
                .with(new RenderExtractFrameQueueSystem(
                        state,
                        vfxState,
                        drawList,
                        queue,
                        stats,
                        64,
                        160,
                        200
                ))
                .build());

        world.process();

        Assert.assertEquals(drawList.size, queue.size);
        assertQueueEntry(queue, 0, 120, -1, 10, 1f, 2f, FrameRenderQueue.SOURCE_TILED);
        assertQueueEntry(queue, 1, 4, 4, 20, 3f, 4f, FrameRenderQueue.SOURCE_ECS);
        assertQueueEntry(queue, 2, 0, -1, 30, 0f, 0f, FrameRenderQueue.SOURCE_VFX);
        Assert.assertEquals(3, stats.frameQueueQuads);
        Assert.assertTrue(stats.frameQueuePeakCapacity >= 3);
        Assert.assertTrue(stats.frameQueueGrowthCount > 0);

        vfxState.textureHandle[0] = 999;
        vfxState.x1[0] = 999f;
        Assert.assertEquals(31, queue.textureHandle[2]);
        Assert.assertEquals(30.1f, queue.x1[2], 0f);
    }

    private static void writeSlot(RenderStateSOA state,
                                  int slot,
                                  int entityId,
                                  int base,
                                  float offsetX,
                                  float offsetY) {
        state.textureHandle[slot] = base + 1;
        state.shader[slot] = base + 2;
        state.blend[slot] = base + 3;
        state.layerIndex[slot] = base + 4;
        state.paramsId[slot] = base + 5;
        state.customParamsId[slot] = base + 6;
        state.sortKey[slot] = base + 7L;
        state.x1[slot] = base + 0.1f;
        state.y1[slot] = base + 0.2f;
        state.x2[slot] = base + 0.3f;
        state.y2[slot] = base + 0.4f;
        state.x3[slot] = base + 0.5f;
        state.y3[slot] = base + 0.6f;
        state.x4[slot] = base + 0.7f;
        state.y4[slot] = base + 0.8f;
        state.offsetX[slot] = offsetX;
        state.offsetY[slot] = offsetY;
        state.u1[slot] = base + 0.9f;
        state.v1[slot] = base + 1.1f;
        state.u2[slot] = base + 1.2f;
        state.v2[slot] = base + 1.3f;
        state.colorPacked[slot] = base + 1.4f;
        state.repeatFlags[slot] = RenderRepeatFlags.REPEAT_X;
        state.entityId[slot] = entityId;
    }

    private static void writeVfx(VfxRenderState state, int base) {
        state.addParticleQuad(
                base + 1,
                base + 2,
                base + 3,
                base + 4,
                base + 6,
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
                RenderRepeatFlags.REPEAT_X,
                99
        );
    }

    private static void assertQueueEntry(FrameRenderQueue queue,
                                         int index,
                                         int sourceSlot,
                                         int sourceEntity,
                                         int base,
                                         float offsetX,
                                         float offsetY,
                                         byte sourceDomain) {
        Assert.assertEquals(base + 1, queue.textureHandle[index]);
        Assert.assertEquals(base + 2, queue.shader[index]);
        Assert.assertEquals(base + 3, queue.blend[index]);
        Assert.assertEquals(base + 4, queue.layerIndex[index]);
        Assert.assertEquals(base + 5, queue.paramsId[index]);
        Assert.assertEquals(base + 6, queue.customParamsId[index]);
        Assert.assertEquals(base + 7L, queue.sortKey[index]);
        Assert.assertEquals(base + 0.1f + offsetX, queue.x1[index], 0f);
        Assert.assertEquals(base + 0.2f + offsetY, queue.y1[index], 0f);
        Assert.assertEquals(base + 0.3f + offsetX, queue.x2[index], 0f);
        Assert.assertEquals(base + 0.4f + offsetY, queue.y2[index], 0f);
        Assert.assertEquals(base + 0.5f + offsetX, queue.x3[index], 0f);
        Assert.assertEquals(base + 0.6f + offsetY, queue.y3[index], 0f);
        Assert.assertEquals(base + 0.7f + offsetX, queue.x4[index], 0f);
        Assert.assertEquals(base + 0.8f + offsetY, queue.y4[index], 0f);
        Assert.assertEquals(base + 0.9f, queue.u1[index], 0f);
        Assert.assertEquals(base + 1.1f, queue.v1[index], 0f);
        Assert.assertEquals(base + 1.2f, queue.u2[index], 0f);
        Assert.assertEquals(base + 1.3f, queue.v2[index], 0f);
        Assert.assertEquals(base + 1.4f, queue.colorPacked[index], 0f);
        Assert.assertEquals(RenderRepeatFlags.REPEAT_X, queue.repeatFlags[index]);
        Assert.assertEquals(sourceDomain, queue.sourceDomain[index]);
        Assert.assertEquals(sourceSlot, queue.sourceSlot[index]);
        Assert.assertEquals(sourceEntity, queue.sourceEntity[index]);
    }
}
