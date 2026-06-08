package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SpatialRelationSolverTest {
    private final SpatialRelationSolver solver = new SpatialRelationSolver();

    @Test
    public void actorBehindBlockEmitsBehindRelation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(16f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(1, solver.relationCount());
        Assert.assertEquals(SpatialRelationSolver.ACTOR_BEHIND_BLOCK, solver.relationType[0]);
        Assert.assertEquals(0, solver.relationActorIndex[0]);
        Assert.assertEquals(0, solver.relationBlockIndex[0]);
    }

    @Test
    public void actorInFrontOfBlockEmitsInFrontRelation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(16f, 24f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(1, solver.relationCount());
        Assert.assertEquals(SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK, solver.relationType[0]);
    }

    @Test
    public void actorOutsideBlockInfluenceProducesNoRelation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(120f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(0, solver.relationCount());
        Assert.assertEquals(SpatialRelationSolver.NO_RELATION, solver.relationForActorAndBlock(0, 0));
    }

    @Test
    public void verticalNoOverlapProducesNoRelation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(16f, 40f, 2f, 20f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(0, solver.relationCount());
    }

    @Test
    public void boundaryTieBehaviorIsDeterministic() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(16f, 32f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);
        int firstCount = solver.relationCount();
        int[] firstTypes = Arrays.copyOf(solver.relationType, firstCount);

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(firstCount, solver.relationCount());
        Assert.assertArrayEquals(firstTypes, Arrays.copyOf(solver.relationType, solver.relationCount()));
        Assert.assertEquals(1, solver.relationCount());
        Assert.assertEquals(SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK, solver.relationType[0]);
    }

    @Test
    public void isoProjectionBottomSegmentProducesRelation() {
        TiledMapLayerData map = isoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 0, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(45f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(1, solver.relationCount());
        Assert.assertEquals(SpatialRelationSolver.ACTOR_BEHIND_BLOCK, solver.relationType[0]);
    }

    @Test
    public void orthoProjectionBottomSegmentProducesRelation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(16f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(1, solver.relationCount());
        Assert.assertEquals(SpatialRelationSolver.ACTOR_BEHIND_BLOCK, solver.relationType[0]);
    }

    @Test
    public void multipleActorsAndBlocksEmitDeterministicActorMajorOrder() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1), block(3, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(
                actor(16f, 40f, 2f, 0f, 2f),
                actor(64f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(2, solver.relationCount());
        Assert.assertEquals(0, solver.relationActorIndex[0]);
        Assert.assertEquals(0, solver.relationBlockIndex[0]);
        Assert.assertEquals(1, solver.relationActorIndex[1]);
        Assert.assertEquals(1, solver.relationBlockIndex[1]);
        Assert.assertEquals(SpatialRelationSolver.ACTOR_BEHIND_BLOCK, solver.relationType[0]);
        Assert.assertEquals(SpatialRelationSolver.ACTOR_BEHIND_BLOCK, solver.relationType[1]);
    }

    @Test
    public void invalidNonStraightV1BlockFailsVisibly() {
        TiledMapLayerData map = orthoMap();
        SpatialBlockData invalid = block(0, 1, 2, 1);
        invalid.linkedTileRefs.clear();
        invalid.addLinkedTileRef(0, 1, 101);
        invalid.addLinkedTileRef(1, 2, 102);
        SpatialBlocksComponent blocks = blocks(invalid);
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        int cached = cache.addBlock(2);
        cache.setAnchor(cached, 0, 300, 0);
        cache.setAnchor(cached, 1, 301, 1);
        cache.finalizeRanges();

        try {
            solver.solve(actors(actor(16f, 40f, 2f, 0f, 2f)), cache, blocks, map);
            Assert.fail("Expected invalid V1 block refs to fail.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("straight continuous"));
        }
    }

    @Test
    public void solverDoesNotMutateInputs() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(16f, 40f, 2f, 0f, 2f));
        int[] actorSlots = Arrays.copyOf(actors.actorSlot, actors.actorCount);
        float[] actorFootY = Arrays.copyOf(actors.actorFootY, actors.actorCount);
        int[] cacheAnchorSlots = Arrays.copyOf(cache.anchorDrawSlot, cache.anchorCount);
        int[] cacheAnchorIndices = Arrays.copyOf(cache.anchorDrawIndex, cache.anchorCount);

        solver.solve(actors, cache, blocks, map);

        Assert.assertArrayEquals(actorSlots, Arrays.copyOf(actors.actorSlot, actors.actorCount));
        Assert.assertArrayEquals(actorFootY, Arrays.copyOf(actors.actorFootY, actors.actorCount), 0.0001f);
        Assert.assertArrayEquals(cacheAnchorSlots, Arrays.copyOf(cache.anchorDrawSlot, cache.anchorCount));
        Assert.assertArrayEquals(cacheAnchorIndices, Arrays.copyOf(cache.anchorDrawIndex, cache.anchorCount));
    }

    private static TiledMapLayerData orthoMap() {
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 16, 16, 8);
        map.initSlotRange(300, 364);
        return map;
    }

    private static TiledMapLayerData isoMap() {
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 90, 30, 8, SceneMetaRuntime.TiledProjection.ISO);
        map.initSlotRange(300, 364);
        return map;
    }

    private static SpatialBlocksComponent blocks(SpatialBlockData... blocks) {
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        for (SpatialBlockData block : blocks) {
            component.blocks.add(block);
        }
        return component;
    }

    private static SpatialBlockData block(int x, int y, int width, int depth) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = x * 31 + y * 17 + 10;
        block.enabled = true;
        block.actorOccluder = true;
        block.x = x;
        block.y = y;
        block.width = width;
        block.depth = depth;
        block.altitude = 0f;
        block.height = 10f;
        block.beginAuthoredLinkedTileRefs();
        if (width >= depth) {
            for (int gx = x; gx < x + width; gx++) {
                block.addLinkedTileRef(gx, y, 100 + gx);
            }
        } else {
            for (int gy = y; gy < y + depth; gy++) {
                block.addLinkedTileRef(x, gy, 100 + gy);
            }
        }
        return block;
    }

    private static SpatialBlocksRuntimeCache resolve(TiledMapLayerData map, SpatialBlocksComponent blocks) {
        int[] slotToDrawIndex = new int[512];
        Arrays.fill(slotToDrawIndex, -1);
        int drawIndex = 0;
        for (int i = 0; i < blocks.blocks.size; i++) {
            SpatialBlockData block = blocks.blocks.get(i);
            for (int ref = 0; ref < block.linkedTileRefs.size; ref++) {
                SpatialBlockData.LinkedTileRef linked = block.linkedTileRefs.get(ref);
                map.setTile(linked.gx, linked.gy, linked.tileAssetId);
                int slot = map.slotForTile(linked.gx, linked.gy);
                slotToDrawIndex[slot] = drawIndex++;
            }
        }
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        new SpatialBlockAnchorResolver().resolve(blocks, map, slotToDrawIndex, cache);
        return cache;
    }

    private static Actor actor(float x, float y, float radius, float altitude, float height) {
        return new Actor(x, y, radius, altitude, height);
    }

    private static SpatialActorCollector actors(Actor... specs) {
        SpatialActorCollector actors = new SpatialActorCollector();
        actors.actorCount = specs.length;
        actors.actorSlot = new int[specs.length];
        actors.actorEntityId = new int[specs.length];
        actors.actorDrawIndex = new int[specs.length];
        actors.actorLayerIndex = new int[specs.length];
        actors.actorStableOrder = new int[specs.length];
        actors.actorFootX = new float[specs.length];
        actors.actorFootY = new float[specs.length];
        actors.actorAltitude = new float[specs.length];
        actors.actorHeight = new float[specs.length];
        actors.actorCircleX = new float[specs.length];
        actors.actorCircleY = new float[specs.length];
        actors.actorCircleRadius = new float[specs.length];
        actors.actorBaseStartX = new float[specs.length];
        actors.actorBaseStartY = new float[specs.length];
        actors.actorBaseEndX = new float[specs.length];
        actors.actorBaseEndY = new float[specs.length];
        for (int i = 0; i < specs.length; i++) {
            Actor spec = specs[i];
            actors.actorSlot[i] = i;
            actors.actorEntityId[i] = i;
            actors.actorDrawIndex[i] = i;
            actors.actorLayerIndex[i] = 0;
            actors.actorStableOrder[i] = i;
            actors.actorFootX[i] = spec.x;
            actors.actorFootY[i] = spec.y;
            actors.actorAltitude[i] = spec.altitude;
            actors.actorHeight[i] = spec.height;
            actors.actorCircleX[i] = spec.x;
            actors.actorCircleY[i] = spec.y;
            actors.actorCircleRadius[i] = spec.radius;
            actors.actorBaseStartX[i] = spec.x - spec.radius;
            actors.actorBaseStartY[i] = spec.y + spec.radius;
            actors.actorBaseEndX[i] = spec.x + spec.radius;
            actors.actorBaseEndY[i] = spec.y + spec.radius;
        }
        return actors;
    }

    private static final class Actor {
        final float x;
        final float y;
        final float radius;
        final float altitude;
        final float height;

        Actor(float x, float y, float radius, float altitude, float height) {
            this.x = x;
            this.y = y;
            this.radius = radius;
            this.altitude = altitude;
            this.height = height;
        }
    }
}
