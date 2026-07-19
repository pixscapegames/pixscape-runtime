package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public final class SpatialLocalAnchorMembershipTest {
    private static final float AUDITED_ACTOR_X = -2500f;

    @Test
    public void auditedSceneRejectsDistantAnchorAndPreservesStaticRanks() {
        Fixture fixture = auditedFixture();
        int face6 = projectedFace(fixture.faces, 1, 6);
        int anchorA = anchor(fixture.faces, 2, 28);
        int localMembership = containingMembership(fixture.faces, face6, AUDITED_ACTOR_X);

        Assert.assertTrue(AUDITED_ACTOR_X >= fixture.faces.screenMinX[face6]);
        Assert.assertTrue(AUDITED_ACTOR_X < fixture.faces.screenMaxX[face6]);
        Assert.assertFalse(contains(fixture.faces, membership(fixture.faces, face6, anchorA), AUDITED_ACTOR_X));
        Assert.assertEquals(8, fixture.faces.anchorGx[fixture.faces.faceAnchorIndices[localMembership]]);
        Assert.assertEquals(28, fixture.faces.anchorGy[fixture.faces.faceAnchorIndices[localMembership]]);

        SpatialActorCollector actor = actor(AUDITED_ACTOR_X, 2000f);
        SpatialFaceRelationSolver relations = new SpatialFaceRelationSolver();
        relations.solve(actor, fixture.faces);
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                relationType(relations, face6));

        SpatialBucketPlanner planner = plan(actor, fixture.faces, relations, 35, 65);
        Assert.assertEquals(0, planner.unresolvedConstraintCount());
        Assert.assertNotEquals(2, planner.lowerSourceAnchorGx(0));
        Assert.assertTrue(AUDITED_ACTOR_X >= planner.lowerSourceMinX(0));
        Assert.assertTrue(AUDITED_ACTOR_X < planner.lowerSourceMaxX(0));
        Assert.assertTrue(AUDITED_ACTOR_X >= planner.upperSourceMinX(0));
        Assert.assertTrue(AUDITED_ACTOR_X < planner.upperSourceMaxX(0));
        Assert.assertEquals(33, fixture.order.rank(5, 25));
        Assert.assertEquals(37, fixture.order.rank(2, 28));
    }

    @Test
    public void adjacentMembershipSeamIsSemiOpenAndOrderIndependent() {
        assertSeam(.999f, new int[][]{{0, 1}}, 0);
        assertSeam(1f, new int[][]{{0, 1}}, 1);
        assertSeam(1.001f, new int[][]{{0, 1}}, 1);
        assertSeam(-.001f, new int[][]{{0, 1}}, -1);
        assertSeam(.999f, new int[][]{{1, 0}}, 0);
        assertSeam(1f, new int[][]{{1, 0}}, 1);
    }

    @Test
    public void oneMillimetreMovementChangesOnlyLocalIntent() {
        Fixture fixture = auditedFixture();
        int face6 = projectedFace(fixture.faces, 1, 6);
        int anchor7 = anchor(fixture.faces, 7, 28);
        int seamMembership = membership(fixture.faces, face6, anchor7);
        float seam = fixture.faces.faceAnchorScreenMaxX[seamMembership];
        int nodes = fixture.order.tileOrderNodeCount;
        int segments = fixture.order.tileOrderSegmentCount;
        int edges = fixture.order.tileOrderEdgeCount;
        int compileCount = fixture.order.tileOrderCompileCount;
        int[] ranksBefore = allRanks(fixture.order, 50, 50);
        long[] keysBefore = occupiedKeys(ranksBefore);

        SpatialBucketPlanner before = plan(actor(seam - .05f, 2000f), fixture.faces, relation(face6), 35, 65);
        SpatialBucketPlanner after = plan(actor(seam + .05f, 2000f), fixture.faces, relation(face6), 35, 65);

        Assert.assertEquals(7, before.lowerSourceAnchorGx(0));
        Assert.assertEquals(8, after.lowerSourceAnchorGx(0));
        Assert.assertEquals(28, before.lowerSourceAnchorGy(0));
        Assert.assertEquals(28, after.lowerSourceAnchorGy(0));
        Assert.assertEquals(1, before.acceptedLocalMembershipCount);
        Assert.assertEquals(1, after.acceptedLocalMembershipCount);
        Assert.assertEquals(11, before.rejectedNonlocalMembershipCount);
        Assert.assertEquals(11, after.rejectedNonlocalMembershipCount);
        Assert.assertEquals(nodes, fixture.order.tileOrderNodeCount);
        Assert.assertEquals(segments, fixture.order.tileOrderSegmentCount);
        Assert.assertEquals(edges, fixture.order.tileOrderEdgeCount);
        Assert.assertEquals(compileCount, fixture.order.tileOrderCompileCount);
        Assert.assertArrayEquals(ranksBefore, allRanks(fixture.order, 50, 50));
        Assert.assertArrayEquals(keysBefore, occupiedKeys(allRanks(fixture.order, 50, 50)));
        Assert.assertEquals(0, before.unresolvedConstraintCount());
        Assert.assertEquals(0, after.unresolvedConstraintCount());
    }

    @Test
    public void longMergedFaceSelectsExactlyOneMonotonicLocalAnchor() {
        int anchorCount = 64;
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        faces.faceCount = 1; faces.anchorCount = anchorCount;
        faces.faceCompiledIndex = new int[]{0}; faces.faceStructureId = new int[]{1};
        faces.faceAnchorIndexStart = new int[]{0}; faces.faceAnchorIndexCount = new int[]{anchorCount};
        faces.faceAnchorIndices = new int[anchorCount];
        faces.faceAnchorScreenMinX = new float[anchorCount];
        faces.faceAnchorScreenMaxX = new float[anchorCount];
        faces.anchorGx = new int[anchorCount]; faces.anchorGy = new int[anchorCount];
        faces.anchorResolved = new boolean[anchorCount];
        faces.anchorBeforeBucket = new int[anchorCount]; faces.anchorAfterBucket = new int[anchorCount];
        for (int i = 0; i < anchorCount; i++) {
            faces.faceAnchorIndices[i] = i; faces.faceAnchorScreenMinX[i] = i; faces.faceAnchorScreenMaxX[i] = i + 1f;
            faces.anchorGx[i] = i; faces.anchorResolved[i] = true;
            faces.anchorBeforeBucket[i] = i; faces.anchorAfterBucket[i] = i + 1;
        }

        SpatialActorCollector actor = actor(.5f, 0f);
        SpatialFaceRelationSolver relation = relation(0);
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        int[] originals = {0};
        planner.begin(actor, originals, anchorCount + 1);
        planner.addRelations(actor, faces, relation);
        planner.finish(actor);

        int previous = -1;
        for (int sample = 0; sample < anchorCount; sample++) {
            actor.actorCircleX[0] = sample + .5f;
            planner.begin(actor, originals, anchorCount + 1);
            planner.addRelations(actor, faces, relation);
            planner.finish(actor);
            Assert.assertEquals(sample, planner.lowerSourceAnchorGx(0));
            Assert.assertTrue(planner.lowerSourceAnchorGx(0) > previous);
            Assert.assertEquals(1, planner.acceptedLocalMembershipCount);
            Assert.assertEquals(anchorCount - 1, planner.rejectedNonlocalMembershipCount);
            Assert.assertEquals(0, planner.unresolvedConstraintCount());
            previous = planner.lowerSourceAnchorGx(0);
        }
    }

    @Test
    public void compiledMembershipIntervalsAreIndependentOfWallOrderAndIds() {
        Fixture original = auditedFixture();
        Fixture reversed = auditedFixture(true, 100);
        Assert.assertEquals(original.faces.faceCount, reversed.faces.faceCount);
        Assert.assertEquals(original.faces.anchorCount, reversed.faces.anchorCount);
        for (int face = 0; face < original.faces.faceCount; face++) {
            Assert.assertEquals(original.faces.faceStructureId[face], reversed.faces.faceStructureId[face]);
            Assert.assertEquals(original.faces.faceCompiledIndex[face], reversed.faces.faceCompiledIndex[face]);
            int count = original.faces.faceAnchorIndexCount[face];
            Assert.assertEquals(count, reversed.faces.faceAnchorIndexCount[face]);
            int a = original.faces.faceAnchorIndexStart[face];
            int b = reversed.faces.faceAnchorIndexStart[face];
            for (int i = 0; i < count; i++) {
                Assert.assertEquals(original.faces.anchorGx[original.faces.faceAnchorIndices[a + i]],
                        reversed.faces.anchorGx[reversed.faces.faceAnchorIndices[b + i]]);
                Assert.assertEquals(original.faces.anchorGy[original.faces.faceAnchorIndices[a + i]],
                        reversed.faces.anchorGy[reversed.faces.faceAnchorIndices[b + i]]);
                Assert.assertEquals(original.faces.faceAnchorScreenMinX[a + i], reversed.faces.faceAnchorScreenMinX[b + i], 0f);
                Assert.assertEquals(original.faces.faceAnchorScreenMaxX[a + i], reversed.faces.faceAnchorScreenMaxX[b + i], 0f);
            }
        }
    }

    private static void assertSeam(float actorX, int[][] order, int expectedAnchor) {
        SpatialProjectedFaceCache faces = seamFaces(order);
        SpatialBucketPlanner planner = plan(actor(actorX, 0f), faces, relation(0), 1, 3);
        if (expectedAnchor < 0) {
            Assert.assertEquals(0, planner.acceptedLocalMembershipCount);
            Assert.assertEquals(1, planner.actorBucket[0]);
        } else {
            Assert.assertEquals(expectedAnchor, planner.lowerSourceAnchorGx(0));
            Assert.assertEquals(1, planner.acceptedLocalMembershipCount);
        }
    }

    private static SpatialProjectedFaceCache seamFaces(int[][] memberships) {
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        faces.faceCount = 1; faces.anchorCount = 2;
        faces.faceCompiledIndex = new int[]{0}; faces.faceStructureId = new int[]{1};
        faces.faceAnchorIndexStart = new int[]{0}; faces.faceAnchorIndexCount = new int[]{2};
        faces.faceAnchorIndices = new int[]{memberships[0][0], memberships[0][1]};
        faces.faceAnchorScreenMinX = new float[2]; faces.faceAnchorScreenMaxX = new float[2];
        for (int membership = 0; membership < 2; membership++) {
            int anchor = faces.faceAnchorIndices[membership];
            faces.faceAnchorScreenMinX[membership] = anchor;
            faces.faceAnchorScreenMaxX[membership] = anchor + 1f;
        }
        faces.anchorGx = new int[]{0, 1}; faces.anchorGy = new int[2];
        faces.anchorResolved = new boolean[]{true, true};
        faces.anchorBeforeBucket = new int[]{0, 1}; faces.anchorAfterBucket = new int[]{1, 2};
        return faces;
    }

    private static SpatialBucketPlanner plan(SpatialActorCollector actor, SpatialProjectedFaceCache faces,
                                             SpatialFaceRelationSolver relations, int original, int buckets) {
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actor, new int[]{original}, buckets);
        planner.addRelations(actor, faces, relations);
        planner.finish(actor);
        return planner;
    }

    private static SpatialFaceRelationSolver relation(int face) {
        SpatialFaceRelationSolver relations = new SpatialFaceRelationSolver();
        relations.relationCount = 1; relations.actorRelationStart = new int[]{0}; relations.actorRelationCount = new int[]{1};
        relations.relationFaceIndex = new int[]{face};
        relations.relationType = new byte[]{SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE};
        return relations;
    }

    private static SpatialActorCollector actor(float x, float y) {
        SpatialActorCollector actor = new SpatialActorCollector();
        actor.actorCount = 1; actor.actorSlot = new int[]{0}; actor.actorEntityId = new int[]{4};
        actor.actorStableOrder = new int[]{0}; actor.actorDrawIndex = new int[]{0};
        actor.actorCircleX = new float[]{x}; actor.actorCircleY = new float[]{y};
        actor.actorAltitude = new float[]{0}; actor.actorHeight = new float[]{1};
        return actor;
    }

    private static int[] allRanks(SpatialTileOrderCache order, int width, int height) {
        int[] ranks = new int[width * height];
        for (int gy = 0; gy < height; gy++) for (int gx = 0; gx < width; gx++) ranks[gy * width + gx] = order.rank(gx, gy);
        return ranks;
    }

    private static long[] occupiedKeys(int[] ranks) {
        int count = 0;
        for (int i = 0; i < ranks.length; i++) if (ranks[i] >= 0) count++;
        long[] keys = new long[count];
        int write = 0;
        for (int i = 0; i < ranks.length; i++) if (ranks[i] >= 0) {
            keys[write++] = SortKey64.packForBlendOrder30(0, BlendMode.ALPHA.id, 1, 2, ranks[i]);
        }
        return keys;
    }

    private static byte relationType(SpatialFaceRelationSolver relations, int face) {
        for (int i = 0; i < relations.relationCount; i++) if (relations.relationFaceIndex[i] == face) return relations.relationType[i];
        throw new AssertionError("Missing relation for face " + face);
    }

    private static int containingMembership(SpatialProjectedFaceCache faces, int face, float x) {
        int start = faces.faceAnchorIndexStart[face];
        int end = start + faces.faceAnchorIndexCount[face];
        int found = -1;
        for (int membership = start; membership < end; membership++) {
            if (!contains(faces, membership, x)) continue;
            Assert.assertEquals(-1, found);
            found = membership;
        }
        Assert.assertTrue(found >= 0);
        return found;
    }

    private static boolean contains(SpatialProjectedFaceCache faces, int membership, float x) {
        return x >= faces.faceAnchorScreenMinX[membership] && x < faces.faceAnchorScreenMaxX[membership];
    }

    private static int membership(SpatialProjectedFaceCache faces, int face, int anchor) {
        int start = faces.faceAnchorIndexStart[face];
        int end = start + faces.faceAnchorIndexCount[face];
        for (int i = start; i < end; i++) if (faces.faceAnchorIndices[i] == anchor) return i;
        throw new AssertionError("Missing membership");
    }

    private static int projectedFace(SpatialProjectedFaceCache faces, int structure, int compiledFace) {
        for (int face = 0; face < faces.faceCount; face++) {
            if (faces.faceStructureId[face] == structure && faces.faceCompiledIndex[face] == compiledFace) return face;
        }
        throw new AssertionError("Missing projected face");
    }

    private static int anchor(SpatialProjectedFaceCache faces, int gx, int gy) {
        for (int anchor = 0; anchor < faces.anchorCount; anchor++) {
            if (faces.anchorGx[anchor] == gx && faces.anchorGy[anchor] == gy) return anchor;
        }
        throw new AssertionError("Missing anchor");
    }

    private static Fixture auditedFixture() { return auditedFixture(false, 0); }

    private static Fixture auditedFixture(boolean reverse, int idOffset) {
        TiledMapLayerData map = new TiledMapLayerData(50, 50, 256, 128, 16, SceneMetaRuntime.TiledProjection.ISO);
        SpatialBlockData[] walls = new SpatialBlockData[]{
                wall(1 + idOffset, 1, 1f, 4f, .29454708f, 25f, 154.57202f, vertical(1, 4, 28)),
                wall(2 + idOffset, 1, 1f, 28.705452f, 12f, .29454708f, 154.57202f, horizontal(1, 12, 28)),
                wall(3 + idOffset, 1, 12.705453f, 5f, .29454708f, 24f, 154.57202f, vertical(12, 5, 28)),
                wall(4 + idOffset, 2, 5f, 24f, 2f, 2f, 148.18042f, new int[][]{{5,24},{6,24},{5,25},{6,25}})
        };
        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        if (reverse) for (int i = walls.length - 1; i >= 0; i--) blocks.blocks.add(walls[i]);
        else for (int i = 0; i < walls.length; i++) blocks.blocks.add(walls[i]);
        blocks.revision = 1;
        for (int i = 0; i < walls.length; i++) for (int j = 0; j < walls[i].linkedTileRefs.size; j++) {
            SpatialBlockData.LinkedTileRef ref = walls[i].linkedTileRefs.get(j);
            map.setTile(ref.gx, ref.gy, 1);
        }
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache(); compiled.ensure(blocks);
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache(); faces.ensure(compiled, map);
        SpatialTileOrderCache order = new SpatialTileOrderCache(); order.ensure(2, map, blocks, compiled);
        for (int anchor = 0; anchor < faces.anchorCount; anchor++) {
            int rank = order.rank(faces.anchorGx[anchor], faces.anchorGy[anchor]);
            faces.anchorResolved[anchor] = true; faces.anchorBeforeBucket[anchor] = rank; faces.anchorAfterBucket[anchor] = rank + 1;
        }
        return new Fixture(faces, order);
    }

    private static SpatialBlockData wall(int id, int structure, float x, float y, float width, float depth,
                                         float height, int[][] anchors) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id; wall.structureId = structure; wall.x = x; wall.y = y; wall.width = width; wall.depth = depth; wall.height = height;
        wall.beginAuthoredLinkedTileRefs();
        for (int i = 0; i < anchors.length; i++) wall.addLinkedTileRef(anchors[i][0], anchors[i][1], 1);
        return wall;
    }

    private static int[][] vertical(int x, int minY, int maxY) {
        int[][] cells = new int[maxY - minY + 1][2];
        for (int y = minY; y <= maxY; y++) cells[y - minY] = new int[]{x, y};
        return cells;
    }

    private static int[][] horizontal(int minX, int maxX, int y) {
        int[][] cells = new int[maxX - minX + 1][2];
        for (int x = minX; x <= maxX; x++) cells[x - minX] = new int[]{x, y};
        return cells;
    }

    private static final class Fixture {
        final SpatialProjectedFaceCache faces;
        final SpatialTileOrderCache order;
        Fixture(SpatialProjectedFaceCache faces, SpatialTileOrderCache order) { this.faces = faces; this.order = order; }
    }
}
