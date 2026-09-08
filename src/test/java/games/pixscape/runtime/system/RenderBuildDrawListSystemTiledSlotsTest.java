package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import org.junit.Assert;
import org.junit.Test;

public class RenderBuildDrawListSystemTiledSlotsTest {

    @Test
    public void highReservedSlotIsExtractedWhenTouched() {
        // Arrange
        DynamicEntityRenderState ecsState = new DynamicEntityRenderState(4);
        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(8);
        DrawList drawList = new DrawList(256);
        RenderStats stats = new RenderStats();

        World world = new World(new WorldConfigurationBuilder()
                .with(
                        new RenderBuildDrawListSystem(
                                ecsState, tiledState, layerState, drawList, stats, 64, -1, -1),
                        new RenderSortSystem(ecsState, tiledState, drawList)
                )
                .build());

        int tiledSlot = 128;
        layerState.enabled[0] = true;

        int tiledRenderRef = tiledState.registerRef();
        tiledState.setRenderDataForRef(tiledRenderRef, 1, 1, 1, 0, 0, 0, 10L,
                0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f,
                0f, 0f, 1f, 1f, 1f, 1f, (byte) 0);
        tiledState.addVisibleRef(tiledRenderRef);
        tiledState.addVisibleMap(7, 0, 0, 10L, 0, 1);

        // Act
        world.process();

        // Assert
        Assert.assertEquals("One high reserved slot should be extracted", 1, drawList.size);
        Assert.assertEquals(RenderSourceDomain.SOURCE_TILED, drawList.getDomain(0));
        Assert.assertEquals("Draw list must carry the tiled render ref", tiledRenderRef, drawList.get(0));
        Assert.assertNotEquals("Draw list SOURCE_TILED must not carry the legacy slot", tiledSlot, drawList.get(0));
        Assert.assertEquals("ECS scan should only visit active dense render slots", 0, stats.buildDrawListScannedEcsSlots);
        Assert.assertEquals("Only tiled candidates should be scanned in tiled phase", 1, stats.buildDrawListScannedTiledSlots);
    }
}
