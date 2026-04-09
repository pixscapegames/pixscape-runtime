package games.pixscape.runtime.engine;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.system.RenderBuildDrawListSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PixscapeEngineTiledRebindVisibilityTest {

    @Test
    public void rebindMasksStaleTiledSlotsBeforeDirtyMarkSoOffscreenSlotsDoNotLeakToDrawList() throws Exception {
        RenderStateSOA state = new RenderStateSOA(64);
        LayerStateSOA layerState = new LayerStateSOA(2);
        DrawList drawList = new DrawList(64);
        RenderStats stats = new RenderStats();
        layerState.enabled[0] = true;

        World world = new World(new WorldConfigurationBuilder().build());
        int tiledEntity = world.create();
        TiledLayerComponent tiled = world.edit(tiledEntity).create(TiledLayerComponent.class);
        tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
        tiled.data.initSlotRange(10, 11);

        int tiledSlot = 10;
        state.kind[tiledSlot] = RenderStateSOA.KIND_SPRITE;
        state.enabled[tiledSlot] = true;
        state.visible[tiledSlot] = true; // stale value from previous frame
        state.layerIndex[tiledSlot] = 0;
        state.touch(tiledSlot);

        PixscapeEngine engine = new PixscapeEngine();
        setField(engine, "world", world);
        setField(engine, "renderState", state);

        invokeMarkAllTiledChunksContentDirty(engine);

        Assert.assertFalse("Rebind must immediately hide old tiled slot visibility cache.", state.visible[tiledSlot]);

        World drawWorld = new World(new WorldConfigurationBuilder()
                .with(new RenderBuildDrawListSystem(state, layerState, drawList, stats))
                .build());

        // Camera can move before next tiled sync; stale slot must still not leak in draw list.
        drawWorld.process();

        Assert.assertEquals("No stale offscreen tiled slot should leak into draw list after atlas rebind.", 0, drawList.size);
    }

    private static void invokeMarkAllTiledChunksContentDirty(PixscapeEngine engine) throws Exception {
        Method method = PixscapeEngine.class.getDeclaredMethod("markAllTiledChunksContentDirty");
        method.setAccessible(true);
        method.invoke(engine);
    }

    private static void setField(PixscapeEngine engine, String fieldName, Object value) throws Exception {
        Field field = PixscapeEngine.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(engine, value);
    }
}
