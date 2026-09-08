package games.pixscape.runtime.spatial;

import com.artemis.Entity;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialLayerRuntimeRegistryTest {

    @Test
    public void sameEntityAndSourceMapReturnSameRuntime() {
        SpatialLayerRuntimeRegistry registry = new SpatialLayerRuntimeRegistry();
        TiledMapLayerData map = mapWithRenderRefs(0);

        SpatialLayerFaceRuntime first = registry.forLayer(7, map);
        SpatialLayerFaceRuntime second = registry.forLayer(7, map);

        Assert.assertSame(first, second);
    }

    @Test
    public void sameEntityAndDifferentSourceMapReplaceRuntime() {
        SpatialLayerRuntimeRegistry registry = new SpatialLayerRuntimeRegistry();

        SpatialLayerFaceRuntime first = registry.forLayer(7, mapWithRenderRefs(0));
        SpatialLayerFaceRuntime second = registry.forLayer(7, mapWithRenderRefs(64));

        Assert.assertNotSame(first, second);
        Assert.assertEquals(7, second.layerEntity);
        Assert.assertEquals(0, second.compiled.compilationCount());
        Assert.assertEquals(0, second.projected.projectionCount());
        Assert.assertEquals(0, second.tileOrder.tileOrderCompileCount);
    }

    @Test
    public void pooledComponentAndEntityReuseRebuildEveryMapActivation() {
        World world = new World(new WorldConfiguration());
        SpatialLayerRuntimeRegistry registry = new SpatialLayerRuntimeRegistry();

        Entity firstEntity = world.createEntity();
        SpatialBlocksComponent pooled = firstEntity.edit().create(SpatialBlocksComponent.class);
        pooled.blocks.add(wall(1, 1, 1));
        TiledMapLayerData firstMap = mapWithRenderRefs(0);
        SpatialLayerFaceRuntime firstRuntime = compile(registry, firstEntity.getId(), firstMap, pooled);
        assertCurrentSource(firstRuntime, firstMap, 1, 1);

        int reusedEntityId = firstEntity.getId();
        world.delete(reusedEntityId);
        world.process();

        Entity secondEntity = world.createEntity();
        SpatialBlocksComponent secondSource = secondEntity.edit().create(SpatialBlocksComponent.class);
        secondSource.blocks.add(wall(2, 2, 2));
        TiledMapLayerData secondMap = mapWithRenderRefs(64);
        SpatialLayerFaceRuntime secondRuntime = compile(registry, secondEntity.getId(), secondMap, secondSource);

        Assert.assertEquals(reusedEntityId, secondEntity.getId());
        Assert.assertSame(pooled, secondSource);
        Assert.assertEquals(0, secondSource.revision);
        Assert.assertNotSame(firstRuntime, secondRuntime);
        assertCurrentSource(secondRuntime, secondMap, 2, 2);

        world.delete(secondEntity.getId());
        world.process();

        Entity returnEntity = world.createEntity();
        SpatialBlocksComponent returnSource = returnEntity.edit().create(SpatialBlocksComponent.class);
        returnSource.blocks.add(wall(1, 1, 1));
        TiledMapLayerData returnMap = mapWithRenderRefs(128);
        SpatialLayerFaceRuntime returnRuntime = compile(
                registry, returnEntity.getId(), returnMap, returnSource);

        Assert.assertEquals(reusedEntityId, returnEntity.getId());
        Assert.assertSame(pooled, returnSource);
        Assert.assertEquals(0, returnSource.revision);
        Assert.assertNotSame(secondRuntime, returnRuntime);
        Assert.assertNotSame(firstRuntime, returnRuntime);
        assertCurrentSource(returnRuntime, returnMap, 1, 1);
        world.dispose();
    }

    private static SpatialLayerFaceRuntime compile(SpatialLayerRuntimeRegistry registry,
                                                   int layerEntity,
                                                   TiledMapLayerData map,
                                                   SpatialBlocksComponent blocks) {
        SpatialLayerFaceRuntime runtime = registry.forLayer(layerEntity, map);
        Assert.assertTrue(runtime.compiled.ensure(blocks));
        Assert.assertTrue(runtime.projected.ensure(runtime.compiled, map));
        Assert.assertTrue(runtime.tileOrder.ensure(layerEntity, map, blocks, runtime.compiled));
        return runtime;
    }

    private static void assertCurrentSource(SpatialLayerFaceRuntime runtime,
                                            TiledMapLayerData map,
                                            int gx,
                                            int gy) {
        Assert.assertEquals(1, runtime.compiled.compilationCount());
        Assert.assertEquals(1, runtime.projected.projectionCount());
        Assert.assertEquals(1, runtime.tileOrder.tileOrderCompileCount);
        Assert.assertTrue(runtime.projected.anchorCount > 0);
        for (int anchor = 0; anchor < runtime.projected.anchorCount; anchor++) {
            int anchorGx = runtime.projected.anchorGx[anchor];
            int anchorGy = runtime.projected.anchorGy[anchor];
            Assert.assertEquals(map.tiledRenderRefForTile(anchorGx, anchorGy),
                    runtime.projected.anchorTiledRef[anchor]);
        }
        Assert.assertTrue(runtime.tileOrder.rank(gx, gy) >= 0);
    }

    private static TiledMapLayerData mapWithRenderRefs(int firstRef) {
        TiledMapLayerData map = new TiledMapLayerData(
                4, 4, 16, 8, 2, TiledProjection.ISO);
        map.spatialEnabled = true;
        for (int gy = 0; gy < map.mapHeight; gy++) {
            for (int gx = 0; gx < map.mapWidth; gx++) {
                map.setTile(gx, gy, 1);
            }
        }
        int nextRef = firstRef;
        for (int cy = 0; cy < 2; cy++) {
            for (int cx = 0; cx < 2; cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                chunk.renderRefStartIndex = nextRef;
                chunk.renderRefCount = chunk.cellCount();
                nextRef += chunk.cellCount();
            }
        }
        return map;
    }

    private static SpatialBlockData wall(int id, int gx, int gy) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = id;
        wall.x = gx;
        wall.y = gy;
        wall.width = 1f;
        wall.depth = 1f;
        wall.height = 16f;
        wall.beginAuthoredLinkedTileRefs();
        wall.addLinkedTileRef(gx, gy, 1);
        return wall;
    }
}
