package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialTileOrderCompilerTest {
    @Test
    public void fallbackUsesDescendingHorizontalThenDescendingRemainingCoordinate() {
        TiledMapLayerData map = map(20, 20);
        map.setTile(10, 12, 1);
        map.setTile(7, 16, 1);
        map.setTile(10, 9, 1);

        SpatialTileOrderCache order = compile(map, new SpatialBlocksComponent());

        Assert.assertEquals(0, order.rank(10, 12));
        Assert.assertEquals(1, order.rank(10, 9));
        Assert.assertEquals(2, order.rank(7, 16));
        Assert.assertEquals(-1, order.rank(0, 0));
    }

    @Test
    public void tileInsertionAndTransactionOrderDoNotChangeRanks() {
        TiledMapLayerData first = map(12, 12);
        first.beginContentMutation();
        first.setTile(2, 8, 1); first.setTile(9, 1, 1); first.setTile(4, 4, 1);
        first.endContentMutation();
        TiledMapLayerData second = map(12, 12);
        second.beginContentMutation();
        second.setTile(4, 4, 1); second.setTile(2, 8, 1); second.setTile(9, 1, 1);
        second.endContentMutation();

        Assert.assertEquals(1, first.contentRevision());
        SpatialTileOrderCache one = compile(first, new SpatialBlocksComponent());
        SpatialTileOrderCache two = compile(second, new SpatialBlocksComponent());
        Assert.assertEquals(one.rank(2, 8), two.rank(2, 8));
        Assert.assertEquals(one.rank(9, 1), two.rank(9, 1));
        Assert.assertEquals(one.rank(4, 4), two.rank(4, 4));
    }

    @Test
    public void deterministicKahnHonorsDependenciesAndRejectsCycles() {
        SpatialTileOrderCompiler compiler = new SpatialTileOrderCompiler();
        int[] ranks = compiler.compileGraphForTest(3,
                new int[]{10, 7, 5}, new int[]{12, 16, 1},
                new int[]{0, 1}, new int[]{1, 2});
        Assert.assertArrayEquals(new int[]{0, 1, 2}, ranks);

        try {
            compiler.compileGraphForTest(9,
                    new int[]{10, 7}, new int[]{12, 16},
                    new int[]{0, 1}, new int[]{1, 0});
            Assert.fail("Expected cycle rejection");
        } catch (SpatialTileOrderInvariantException expected) {
            Assert.assertTrue(expected.getMessage().contains("layer 9"));
            Assert.assertTrue(expected.getMessage().contains("(10,12)"));
            Assert.assertTrue(expected.getMessage().contains("emitted=0"));
        }
    }

    @Test
    public void order30PackingIsMonotonicAcrossFormerZBoundary() {
        long before = SortKey64.packForBlendOrder30(2, BlendMode.ALPHA.id, 4, 6, 16383);
        long after = SortKey64.packForBlendOrder30(2, BlendMode.ALPHA.id, 4, 6, 16384);
        Assert.assertTrue(Long.compareUnsigned(before, after) < 0);
        Assert.assertEquals(-32768, SortKey64.unpackZOrdered(before));
        Assert.assertEquals(-32767, SortKey64.unpackZOrdered(after));
        Assert.assertEquals(16383, SortKey64.unpackTieOrdered(before));
        Assert.assertEquals(0, SortKey64.unpackTieOrdered(after));
    }

    @Test(expected = IllegalArgumentException.class)
    public void order30PackingRejectsOverflowInsteadOfClamping() {
        SortKey64.packForBlendOrder30(0, BlendMode.ALPHA.id, 1, 0, -1);
    }

    @Test
    public void actorAndStaticLineConventionAgree() {
        float high = 20f;
        float low = 10f;
        float witness = 15f;
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                SpatialLineRelation.relation(high, witness));
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE,
                SpatialLineRelation.relation(low, witness));
    }

    @Test
    public void mergedFacesAreClippedIntoExactOneCellAnchorSegments() {
        TiledMapLayerData map = map(8, 8);
        map.setTile(2, 2, 1);
        map.setTile(3, 2, 1);
        SpatialBlockData wall = wall(1, 1, 2, 2, 2, 1, 0, new int[][]{{2, 2}, {3, 2}});
        SpatialBlocksComponent blocks = component(wall);

        SpatialTileOrderCache order = compile(map, blocks);

        Assert.assertEquals(2, order.tileOrderNodeCount);
        Assert.assertEquals(6, order.tileOrderSegmentCount);
        Assert.assertEquals(1, order.tileOrderCompileCount);
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(blocks);
        Assert.assertFalse(order.ensure(1, map, blocks, compiled));
        Assert.assertEquals(1, order.tileOrderCompileCount);
    }

    @Test
    public void auditedPairReceivesGeometricEdgeAndMonotonicPackedOrder() {
        TiledMapLayerData map = map(24, 24);
        map.setTile(10, 12, 1);
        map.setTile(7, 16, 1);
        SpatialBlockData a = wall(1, 1, 10, 12, 1, 1, 100, new int[][]{{10, 12}});
        SpatialBlockData b = wall(2, 2, 14, 16, 1, 1, 0, new int[][]{{7, 16}});
        SpatialTileOrderCache order = compile(map, component(a, b));

        int rankA = order.rank(10, 12);
        int rankB = order.rank(7, 16);
        Assert.assertTrue(order.tileOrderEdgeCount > 0);
        Assert.assertTrue(rankA < rankB);
        long keyA = SortKey64.packForBlendOrder30(1, BlendMode.ALPHA.id, 1, 0, rankA);
        long keyB = SortKey64.packForBlendOrder30(1, BlendMode.ALPHA.id, 1, 0, rankB);
        Assert.assertTrue(Long.compareUnsigned(keyA, keyB) < 0);
    }

    @Test
    public void onlyStaticSourceChangesRecompileAndAdvanceOrderRevision() {
        TiledMapLayerData map = map(8, 8);
        map.setTile(1, 1, 1);
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(blocks);
        SpatialTileOrderCache order = new SpatialTileOrderCache();
        Assert.assertTrue(order.ensure(4, map, blocks, compiled));
        int initialRevision = order.orderRevision();
        order.markKeysApplied();

        Assert.assertFalse(order.ensure(4, map, blocks, compiled));
        Assert.assertFalse(order.needsKeyRefresh());
        Assert.assertEquals(1, order.tileOrderCompileCount);

        map.setTile(2, 2, 1);
        Assert.assertTrue(order.ensure(4, map, blocks, compiled));
        Assert.assertEquals(initialRevision + 1, order.orderRevision());
        Assert.assertTrue(order.needsKeyRefresh());
        Assert.assertEquals(2, order.tileOrderNodeCount);

        map.originX += 2f;
        Assert.assertTrue(order.ensure(4, map, blocks, compiled));
        Assert.assertEquals(3, order.tileOrderCompileCount);
    }

    @Test
    public void openBrushMutationInvalidatesPreviewOrderBeforeCommit() {
        TiledMapLayerData map = map(8, 8);
        map.setTile(2, 3, 1);
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(blocks);
        SpatialTileOrderCache order = new SpatialTileOrderCache();
        Assert.assertTrue(order.ensure(4, map, blocks, compiled));

        int committedRevision = map.contentRevision();
        map.beginContentMutation();
        try {
            map.setTile(3, 3, 1);

            Assert.assertEquals("bulk revision remains transactional", committedRevision, map.contentRevision());
            Assert.assertTrue("preview-visible state must invalidate canonical order",
                    order.ensure(4, map, blocks, compiled));
            Assert.assertTrue(order.rank(3, 3) >= 0);
            Assert.assertFalse("unowned render tile is not a required spatial participant",
                    order.requiresCanonicalRank(map, 3, 3));
        } finally {
            map.endContentMutation();
        }
    }

    @Test
    public void linkedOwnerAndExplicitMetadataRequireCanonicalRanks() {
        TiledMapLayerData map = map(8, 8);
        map.setTile(2, 2, 1);
        map.setTile(5, 5, 1);
        map.setTileSpatialOverride(5, 5, 3f, 16f, 1);
        SpatialBlockData wall = wall(17, 3, 2, 2, 1, 1, 0, new int[][]{{2, 2}});
        SpatialBlocksComponent blocks = component(wall);
        SpatialTileOrderCache order = compile(map, blocks);

        Assert.assertTrue(order.requiresCanonicalRank(map, 2, 2));
        Assert.assertEquals(17, order.ownerBlockId(2, 2));
        Assert.assertTrue(order.requiresCanonicalRank(map, 5, 5));
        Assert.assertFalse(order.requiresCanonicalRank(map, 4, 5));
    }

    @Test
    public void auditedActorWitnessFitsBetweenCanonicalAAndB() {
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        faces.faceCount = 2; faces.structureCount = 1; faces.anchorCount = 2;
        faces.faceStructureId = new int[]{1, 2}; faces.faceCompiledIndex = new int[]{0, 0};
        faces.faceAltitude = new float[]{0, 0}; faces.faceHeight = new float[]{32, 32};
        faces.screenMinX = new float[]{0, 0}; faces.screenMaxX = new float[]{10, 10};
        faces.slope = new float[]{0, 0}; faces.intercept = new float[]{20, 10};
        faces.structureFaceStart = new int[]{0}; faces.structureFaceCount = new int[]{2};
        faces.structureMinX = new float[]{0}; faces.structureMaxX = new float[]{10};
        faces.faceAnchorIndexStart = new int[]{0, 1}; faces.faceAnchorIndexCount = new int[]{1, 1};
        faces.faceAnchorIndices = new int[]{0, 1};
        faces.faceAnchorScreenMinX = new float[]{0, 0};
        faces.faceAnchorScreenMaxX = new float[]{10, 10};
        faces.anchorGx = new int[]{10, 7}; faces.anchorGy = new int[]{12, 16};
        faces.anchorResolved = new boolean[]{true, true};
        faces.anchorBeforeBucket = new int[]{0, 1}; faces.anchorAfterBucket = new int[]{1, 2};

        SpatialActorCollector actor = new SpatialActorCollector();
        actor.actorCount = 1; actor.actorEntityId = new int[]{99}; actor.actorStableOrder = new int[]{0};
        actor.actorCircleX = new float[]{5.001f}; actor.actorCircleY = new float[]{15};
        actor.actorAltitude = new float[]{0}; actor.actorHeight = new float[]{16};
        SpatialFaceRelationSolver relations = new SpatialFaceRelationSolver();
        relations.solve(actor, faces);
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE, relations.relationType[0]);
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE, relations.relationType[1]);

        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actor, new int[]{1}, 3);
        planner.addRelations(actor, faces, relations);
        planner.finish(actor);
        Assert.assertEquals(1, planner.actorLowerBound[0]);
        Assert.assertEquals(1, planner.actorUpperBound[0]);
        Assert.assertEquals(1, planner.actorBucket[0]);
        Assert.assertEquals(0, planner.unresolvedConstraintCount());
    }

    private static SpatialTileOrderCache compile(TiledMapLayerData map, SpatialBlocksComponent blocks) {
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
        compiled.ensure(blocks);
        SpatialTileOrderCache order = new SpatialTileOrderCache();
        order.ensure(1, map, blocks, compiled);
        return order;
    }

    private static TiledMapLayerData map(int width, int height) {
        return new TiledMapLayerData(width, height, 32, 16, 4, SceneMetaRuntime.TiledProjection.ISO);
    }

    private static SpatialBlocksComponent component(SpatialBlockData... walls) {
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        for (int i = 0; i < walls.length; i++) blocks.blocks.add(walls[i]);
        blocks.revision = 1;
        return blocks;
    }

    private static SpatialBlockData wall(int id, int structure, float x, float y,
                                         float width, float depth, float altitude, int[][] anchors) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = structure;
        wall.x = x; wall.y = y; wall.width = width; wall.depth = depth;
        wall.altitude = altitude; wall.height = 32;
        for (int i = 0; i < anchors.length; i++) wall.addLinkedTileRef(anchors[i][0], anchors[i][1], 1);
        return wall;
    }
}
