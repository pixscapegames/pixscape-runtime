package games.pixscape.runtime.engine;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.system.RenderBuildDrawListSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Method;

public class PixscapeEngineTiledRebindVisibilityTest {

    @Test
    public void rebindMarksTiledChunksDirtyWithoutRenderState() throws Exception {
        DynamicEntityRenderState dynamicState = new DynamicEntityRenderState(64);
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

        PixscapeEngine engine = new PixscapeEngine();
        setWorld(engine, world);

        invokeMarkAllTiledChunksContentDirty(engine);

        Assert.assertTrue("Rebind should dirty tiled chunk content for the next tiled sync.",
                chunk.dirtyState == TileChunk.DirtyState.FULL);
        World drawWorld = new World(new WorldConfigurationBuilder()
                .with(new RenderBuildDrawListSystem(dynamicState, tiledState, layerState, drawList, stats, 64, -1, -1))
                .build());

        drawWorld.process();

        Assert.assertEquals("No domain source state should produce stale draw-list entries.",
                0, drawList.size);
    }

    private static void invokeMarkAllTiledChunksContentDirty(PixscapeEngine engine) throws Exception {
        Method method = PixscapeEngine.class.getDeclaredMethod("markAllTiledChunksContentDirty");
        method.setAccessible(true);
        method.invoke(engine);
    }

    private static void setWorld(PixscapeEngine engine, World world) throws Exception {
        java.lang.reflect.Field field = PixscapeEngine.class.getDeclaredField("world");
        field.setAccessible(true);
        field.set(engine, world);
    }
}
