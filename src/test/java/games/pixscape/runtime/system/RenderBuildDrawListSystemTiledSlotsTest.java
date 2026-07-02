package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.Assert;
import org.junit.Test;

public class RenderBuildDrawListSystemTiledSlotsTest {

    @Test
    public void highReservedSlotIsExtractedWhenTouched() {
        // Arrange
        RenderStateSOA state = new RenderStateSOA(256);
        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(8);
        DrawList drawList = new DrawList(256);
        RenderStats stats = new RenderStats();

        World world = new World(new WorldConfigurationBuilder()
                .with(new RenderBuildDrawListSystem(state, tiledState, layerState, drawList, stats, 64, -1, -1))
                .build());

        int tiledSlot = 128;
        layerState.enabled[0] = true;

        state.kind[tiledSlot] = RenderStateSOA.KIND_SPRITE;
        state.enabled[tiledSlot] = true;
        state.visible[tiledSlot] = true;
        state.layerIndex[tiledSlot] = 0;
        state.touch(tiledSlot);
        int tiledRenderRef = tiledState.registerLegacySlot(tiledSlot);
        tiledState.addVisibleRef(tiledRenderRef);

        // Act
        world.process();

        // Assert
        Assert.assertEquals("One high reserved slot should be extracted", 1, drawList.size);
        Assert.assertEquals(RenderSourceDomain.SOURCE_TILED, drawList.getDomain(0));
        Assert.assertEquals("Draw list must carry the tiled render ref", tiledRenderRef, drawList.get(0));
        Assert.assertNotEquals("Draw list SOURCE_TILED must not carry the legacy slot", tiledSlot, drawList.get(0));
        Assert.assertEquals("ECS scan should remain bounded", 64, stats.buildDrawListScannedEcsSlots);
        Assert.assertEquals("Only tiled candidates should be scanned in tiled phase", 1, stats.buildDrawListScannedTiledSlots);
    }
}
