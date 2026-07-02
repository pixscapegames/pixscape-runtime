package games.pixscape.runtime.engine;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.system.RenderBuildDrawListSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

public class PixscapeEngineTiledRebindVisibilityTest {

    @Test
    public void rebindMarksTiledChunksDirtyWithoutTouchingRenderStateSlots() throws Exception {
        RenderStateSOA state = new RenderStateSOA(64);
        TiledMapRenderState tiledState = new TiledMapRenderState(16);
        LayerStateSOA layerState = new LayerStateSOA(2);
        DrawList drawList = new DrawList(64);
        RenderStats stats = new RenderStats();
        layerState.enabled[0] = true;

        World world = new World(new WorldConfigurationBuilder().build());
        int tiledEntity = world.create();
        TiledLayerComponent tiled = world.edit(tiledEntity).create(TiledLayerComponent.class);
        tiled.data = new TiledMapLayerData(1, 1, 16, 16, 1);
        TileChunk chunk = tiled.data.getChunk(0, 0);
        chunk.dirtyState = TileChunk.DirtyState.CLEAN;

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

        Assert.assertTrue("Rebind should dirty tiled chunk content for the next tiled sync.",
                chunk.dirtyState == TileChunk.DirtyState.FULL);
        Assert.assertTrue("Tiled rebind no longer owns RenderStateSOA visibility.",
                state.visible[tiledSlot]);

        World drawWorld = new World(new WorldConfigurationBuilder()
                .with(new RenderBuildDrawListSystem(state, tiledState, layerState, drawList, stats, 64, -1, -1))
                .build());

        // Camera can move before next tiled sync; stale slot must still not leak in draw list.
        drawWorld.process();

        Assert.assertEquals("Draw list should only see RenderStateSOA data that still belongs to ECS.",
                1, drawList.size);
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
