package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.Assert;
import org.junit.Test;

public class RenderBuildDrawListSystemTiledSlotsTest {

    @Test
    public void highReservedSlotIsExtractedWhenTouched() {
        // Arrange
        RenderStateSOA state = new RenderStateSOA(256);
        LayerStateSOA layerState = new LayerStateSOA(8);
        DrawList drawList = new DrawList(256);
        RenderStats stats = new RenderStats();

        World world = new World(new WorldConfigurationBuilder()
                .with(new RenderBuildDrawListSystem(state, layerState, drawList, stats, 64))
                .build());

        int tiledSlot = 128;
        layerState.enabled[0] = true;

        state.kind[tiledSlot] = RenderStateSOA.KIND_SPRITE;
        state.enabled[tiledSlot] = true;
        state.visible[tiledSlot] = true;
        state.layerIndex[tiledSlot] = 0;
        state.touch(tiledSlot);
        state.appendTiledVisibleRange(tiledSlot, 1);

        // Act
        world.process();

        // Assert
        Assert.assertEquals("One high reserved slot should be extracted", 1, drawList.size);
        Assert.assertEquals("Draw list must keep the reserved slot index", tiledSlot, drawList.get(0));
        Assert.assertEquals("ECS scan should remain bounded", 64, stats.buildDrawListScannedEcsSlots);
        Assert.assertEquals("Only tiled candidates should be scanned in tiled phase", 1, stats.buildDrawListScannedTiledSlots);
    }
}
