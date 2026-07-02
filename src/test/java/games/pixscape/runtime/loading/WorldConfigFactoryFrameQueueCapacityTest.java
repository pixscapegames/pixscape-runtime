package games.pixscape.runtime.loading;

import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
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
        TiledMapRenderState tiledState = new TiledMapRenderState();
        int ecsCapacity = WorldConfigFactory.DEFAULT_ECS_RENDER_CAPACITY;
        int legacyGlobalCapacity = ecsCapacity + 600_000 + WorldConfigFactory.DEFAULT_VFX_BUDGET;

        WorldConfigFactory.configureRenderStorageCapacities(
                renderState,
                drawList,
                frameQueue,
                vfxState,
                tiledState,
                ecsCapacity
        );

        Assert.assertTrue(legacyGlobalCapacity > WorldConfigFactory.DEFAULT_FRAME_QUEUE_CAPACITY);
        Assert.assertEquals(ecsCapacity, renderState.getCapacity());
        Assert.assertEquals(ecsCapacity, drawList.data().length);
        Assert.assertEquals(WorldConfigFactory.DEFAULT_VFX_BUDGET, vfxState.getCapacity());
        Assert.assertEquals(WorldConfigFactory.DEFAULT_TILED_VISIBLE_SLOTS_CAPACITY, tiledState.getCapacity());
        Assert.assertEquals(WorldConfigFactory.DEFAULT_FRAME_QUEUE_CAPACITY, frameQueue.getCapacity());
        Assert.assertEquals(0, frameQueue.getGrowthCount());
    }
}
