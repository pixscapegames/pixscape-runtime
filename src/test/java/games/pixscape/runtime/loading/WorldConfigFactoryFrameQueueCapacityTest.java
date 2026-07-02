package games.pixscape.runtime.loading;

import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.VfxRenderState;
import org.junit.Assert;
import org.junit.Test;

public class WorldConfigFactoryFrameQueueCapacityTest {

    @Test
    public void frameQueueInitialCapacityIsIndependentFromGlobalLegacyCapacity() {
        RenderStateSOA renderState = new RenderStateSOA();
        DrawList drawList = new DrawList();
        FrameRenderQueue frameQueue = new FrameRenderQueue();
        VfxRenderState vfxState = new VfxRenderState();
        int tiledBudget = 600_000;
        int tiledEnd = 150_000 + tiledBudget;
        int vfxEnd = tiledEnd + WorldConfigFactory.DEFAULT_VFX_BUDGET;

        WorldConfigFactory.configureRenderStorageCapacities(
                renderState,
                drawList,
                frameQueue,
                vfxState,
                tiledEnd,
                vfxEnd
        );

        Assert.assertTrue(vfxEnd > WorldConfigFactory.DEFAULT_FRAME_QUEUE_CAPACITY);
        Assert.assertEquals(tiledEnd, renderState.getCapacity());
        Assert.assertEquals(vfxEnd, drawList.data().length);
        Assert.assertEquals(WorldConfigFactory.DEFAULT_VFX_BUDGET, vfxState.getCapacity());
        Assert.assertEquals(WorldConfigFactory.DEFAULT_FRAME_QUEUE_CAPACITY, frameQueue.getCapacity());
        Assert.assertEquals(0, frameQueue.getGrowthCount());
    }
}
