package games.pixscape.runtime.loading;

import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.VfxRenderState;
import org.junit.Assert;
import org.junit.Test;

public class WorldConfigFactoryFrameQueueCapacityTest {

    @Test
    public void frameQueueInitialCapacityIsIndependentFromDomainCapacities() {
        DynamicEntityRenderState dynamicEntityState = new DynamicEntityRenderState();
        DrawList drawList = new DrawList();
        FrameRenderQueue frameQueue = new FrameRenderQueue();
        VfxRenderState vfxState = new VfxRenderState();
        TiledMapRenderState tiledState = new TiledMapRenderState();
        int ecsCapacity = WorldConfigFactory.DEFAULT_DYNAMIC_ECS_RENDER_CAPACITY;
        int domainCapacityTotal = ecsCapacity + WorldConfigFactory.DEFAULT_VFX_BUDGET
                + WorldConfigFactory.DEFAULT_TILED_VISIBLE_SLOTS_CAPACITY;

        WorldConfigFactory.configureRenderStorageCapacities(
                dynamicEntityState,
                drawList,
                frameQueue,
                vfxState,
                tiledState,
                ecsCapacity
        );

        Assert.assertTrue(domainCapacityTotal > WorldConfigFactory.DEFAULT_FRAME_QUEUE_CAPACITY);
        Assert.assertEquals(ecsCapacity, dynamicEntityState.getRenderCapacity());
        Assert.assertEquals(ecsCapacity, drawList.data().length);
        Assert.assertEquals(WorldConfigFactory.DEFAULT_VFX_BUDGET, vfxState.getCapacity());
        Assert.assertEquals(WorldConfigFactory.DEFAULT_TILED_VISIBLE_SLOTS_CAPACITY, tiledState.getCapacity());
        Assert.assertEquals(WorldConfigFactory.DEFAULT_FRAME_QUEUE_CAPACITY, frameQueue.getCapacity());
        Assert.assertEquals(0, frameQueue.getGrowthCount());
    }
}
