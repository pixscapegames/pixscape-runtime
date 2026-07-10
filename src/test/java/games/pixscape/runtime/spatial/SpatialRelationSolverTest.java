package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TileChunk;
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
        SpatialActorCollector actors = actors(actor(16f, 8f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(1, solver.relationCount());
        Assert.assertEquals(SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK, solver.relationType[0]);
    }

    @Test
    public void actorOutsideRelationSegmentProducesNoRelation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(120f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(0, solver.relationCount());
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
        SpatialActorCollector actors = actors(actor(16f, 16f, 2f, 0f, 2f));

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
    public void ascendingWallUsesLowerBaseSegments() {
        TiledMapLayerData map = isoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 0, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(45f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        assertSingleRelation(SpatialRelationSolver.ACTOR_BEHIND_BLOCK);
    }

    @Test
    public void descendingWallUsesLowerBaseSegments() {
        TiledMapLayerData map = isoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 0, 1, 2));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(45f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        assertSingleRelation(SpatialRelationSolver.ACTOR_BEHIND_BLOCK);
    }

    @Test
    public void actorOnWallThicknessSliceUsesSecondLowerSegment() {
        TiledMapLayerData map = isoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 0, 1, 2));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(45f, 20f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        assertSingleRelation(SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK);
    }

    @Test
    public void squareRectangularVolumeBaseUsesLowerBaseSegment() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 1, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(16f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        assertSingleRelation(SpatialRelationSolver.ACTOR_BEHIND_BLOCK);
    }

    @Test
    public void actorNearQuadrilateralCornerUsesSemiOpenLowerSegmentSeam() {
        TiledMapLayerData map = isoMap();
        SpatialBlocksComponent blocks = blocks(block(0, 0, 2, 1));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(90f, 44f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        assertSingleRelation(SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK);
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
    public void actorCenterOutsideBothLowerSegmentRangesProducesNoRelation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlockData left = blockWithRefs(0f, 1f, 2.4f, 1f,
                0, 1, 101,
                1, 1, 102,
                2, 1, 103);
        SpatialBlockData right = blockWithRefs(2.7f, 1f, 2.3f, 1f,
                2, 1, 103,
                3, 1, 104,
                4, 1, 105);
        SpatialBlocksComponent blocks = blocks(left, right);
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(39.5f, 40f, 3f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(0, solver.relationCount());
    }

    @Test
    public void rectangularAuthoredRefsDoNotFailValidation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlockData block = blockWithRefs(0f, 1f, 2f, 2f,
                0, 1, 101,
                1, 1, 102,
                0, 2, 103,
                1, 2, 104);
        SpatialBlocksComponent blocks = blocks(block);
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);

        solver.solve(actors(actor(16f, 48f, 2f, 0f, 2f)), cache, blocks, map);

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(4, cache.blockAnchorCount[0]);
        Assert.assertTrue(cache.hasResolvedBlock(0));
    }

    @Test
    public void blockWithNoAuthoredRefsCreatesNoRelation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlocksComponent blocks = blocks(unlinkedBlock(0f, 1f, 2f, 1f));
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(16f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(0, cache.blockAnchorCount[0]);
        Assert.assertFalse(cache.hasResolvedBlock(0));
        Assert.assertEquals(0, solver.relationCount());
    }

    @Test
    public void blockWithNullAuthoredRefEntryCreatesNoRelation() {
        TiledMapLayerData map = orthoMap();
        SpatialBlockData block = blockWithRefs(0f, 1f, 2f, 1f, 0, 1, 101);
        block.linkedTileRefs.add(null);
        SpatialBlocksComponent blocks = blocks(block);
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);

        solver.solve(actors(actor(16f, 40f, 2f, 0f, 2f)), cache, blocks, map);

        Assert.assertEquals(1, cache.blockCount);
        Assert.assertEquals(0, cache.blockAnchorCount[0]);
        Assert.assertFalse(cache.hasResolvedBlock(0));
        Assert.assertEquals(0, solver.relationCount());
    }

    @Test
    public void validBlockAfterInvalidBlockStillProducesRelationAtAlignedCacheIndex() {
        TiledMapLayerData map = orthoMap();
        SpatialBlockData invalid = blockWithRefs(3f, 1f, 2f, 1f,
                3, 1, 301,
                3, 1, 301);
        SpatialBlockData valid = block(0, 1, 2, 1);
        SpatialBlocksComponent blocks = blocks(invalid, valid);
        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        SpatialActorCollector actors = actors(actor(16f, 40f, 2f, 0f, 2f));

        solver.solve(actors, cache, blocks, map);

        Assert.assertEquals(2, cache.blockCount);
        Assert.assertFalse(cache.hasResolvedBlock(0));
        Assert.assertTrue(cache.hasResolvedBlock(1));
        Assert.assertEquals(1, solver.relationCount());
        Assert.assertEquals(1, solver.relationBlockIndex[0]);
        Assert.assertEquals(1, solver.relationAuthoredBlockIndex[0]);
        Assert.assertEquals(SpatialRelationSolver.ACTOR_BEHIND_BLOCK, solver.relationType[0]);
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

    @Test
    public void invalidAuthoredRefsAreNotMutatedByResolverOrSolver() {
        TiledMapLayerData map = orthoMap();
        SpatialBlockData block = blockWithRefs(0f, 1f, 2f, 1f,
                0, 1, 101,
                0, 1, 101);
        SpatialBlockData.LinkedTileRef first = block.linkedTileRefs.get(0);
        SpatialBlockData.LinkedTileRef second = block.linkedTileRefs.get(1);
        SpatialBlocksComponent blocks = blocks(block);

        SpatialBlocksRuntimeCache cache = resolve(map, blocks);
        solver.solve(actors(actor(16f, 40f, 2f, 0f, 2f)), cache, blocks, map);

        Assert.assertEquals(2, block.linkedTileRefs.size);
        Assert.assertSame(first, block.linkedTileRefs.get(0));
        Assert.assertSame(second, block.linkedTileRefs.get(1));
        Assert.assertEquals(0, block.linkedTileRefs.get(0).gx);
        Assert.assertEquals(1, block.linkedTileRefs.get(0).gy);
        Assert.assertEquals(0, block.linkedTileRefs.get(1).gx);
        Assert.assertEquals(1, block.linkedTileRefs.get(1).gy);
    }

    private static TiledMapLayerData orthoMap() {
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 16, 16, 8);
        assignRenderRefs(map, 300);
        return map;
    }

    private static TiledMapLayerData isoMap() {
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 90, 30, 8, SceneMetaRuntime.TiledProjection.ISO);
        assignRenderRefs(map, 300);
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

    private static SpatialBlockData blockWithRefs(float x,
                                                  float y,
                                                  float width,
                                                  float depth,
                                                  int... gxGyTileAssetIdTriples) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = (int) (x * 31f + y * 17f + 10f);
        block.enabled = true;
        block.actorOccluder = true;
        block.x = x;
        block.y = y;
        block.width = width;
        block.depth = depth;
        block.altitude = 0f;
        block.height = 10f;
        block.beginAuthoredLinkedTileRefs();
        for (int i = 0; i < gxGyTileAssetIdTriples.length; i += 3) {
            block.addLinkedTileRef(gxGyTileAssetIdTriples[i],
                    gxGyTileAssetIdTriples[i + 1],
                    gxGyTileAssetIdTriples[i + 2]);
        }
        return block;
    }

    private static SpatialBlockData unlinkedBlock(float x, float y, float width, float depth) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = (int) (x * 31f + y * 17f + 10f);
        block.enabled = true;
        block.actorOccluder = true;
        block.x = x;
        block.y = y;
        block.width = width;
        block.depth = depth;
        block.altitude = 0f;
        block.height = 10f;
        return block;
    }

    private static SpatialBlocksRuntimeCache resolve(TiledMapLayerData map, SpatialBlocksComponent blocks) {
        int[] tiledRefToDrawIndex = new int[512];
        Arrays.fill(tiledRefToDrawIndex, -1);
        int drawIndex = 0;
        for (int i = 0; i < blocks.blocks.size; i++) {
            SpatialBlockData block = blocks.blocks.get(i);
            for (int ref = 0; ref < block.linkedTileRefs.size; ref++) {
                SpatialBlockData.LinkedTileRef linked = block.linkedTileRefs.get(ref);
                if (linked == null) continue;
                map.setTile(linked.gx, linked.gy, linked.tileAssetId);
                int tiledRenderRef = map.tiledRenderRefForTile(linked.gx, linked.gy);
                tiledRefToDrawIndex[tiledRenderRef] = drawIndex++;
            }
        }
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        new SpatialBlockAnchorResolver().resolve(blocks, map, tiledRefToDrawIndex, cache);
        return cache;
    }

    private static void assignRenderRefs(TiledMapLayerData map, int startRef) {
        int nextRef = startRef;
        for (int cy = 0; cy < map.getChunksY(); cy++) {
            for (int cx = 0; cx < map.getChunksX(); cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                if (chunk == null) continue;
                chunk.renderRefStartIndex = nextRef;
                chunk.renderRefCount = chunk.cellCount();
                nextRef += chunk.cellCount();
            }
        }
    }

    private static Actor actor(float x, float y, float radius, float altitude, float height) {
        return new Actor(x, y, radius, altitude, height);
    }

    private void assertSingleRelation(int relationType) {
        Assert.assertEquals(1, solver.relationCount());
        Assert.assertEquals(relationType, solver.relationType[0]);
        Assert.assertEquals(0, solver.relationActorIndex[0]);
        Assert.assertEquals(0, solver.relationBlockIndex[0]);
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
