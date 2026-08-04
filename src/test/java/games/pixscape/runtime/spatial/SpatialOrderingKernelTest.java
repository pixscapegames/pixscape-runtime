package games.pixscape.runtime.spatial;

import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderSourceDomain;
import org.junit.Assert;
import org.junit.Test;

public class SpatialOrderingKernelTest {
    @Test
    public void sameAnchorBehindDominatesFrontIndependentOfRelationOrder() {
        assertSameAnchorOpposition(new byte[]{SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                SpatialFaceRelationSolver.ACTOR_BEHIND_FACE});
        assertSameAnchorOpposition(new byte[]{SpatialFaceRelationSolver.ACTOR_BEHIND_FACE,
                SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE});
    }

    @Test
    public void frontUsesAfterBehindUsesBeforeAndDistinctAnchorsAggregate() {
        SpatialActorCollector actors = actors(3);
        SpatialProjectedFaceCache faces = faces(new int[][]{{0}, {1}});
        faces.anchorBeforeBucket[0] = 1; faces.anchorAfterBucket[0] = 2;
        faces.anchorBeforeBucket[1] = 4; faces.anchorAfterBucket[1] = 5;
        SpatialFaceRelationSolver relations = relations(3,
                new int[][]{{0}, {1}, {0, 1}},
                new byte[][]{{SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE},
                        {SpatialFaceRelationSolver.ACTOR_BEHIND_FACE},
                        {SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                                SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE}});
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, new int[]{0, 6, 3}, 7);
        planner.addRelations(actors, faces, relations);
        planner.finish(actors);

        Assert.assertEquals(2, planner.actorBucket[0]);
        Assert.assertEquals(4, planner.actorBucket[1]);
        Assert.assertEquals(5, planner.actorBucket[2]);
        Assert.assertEquals(0, planner.unresolvedConstraintCount());
    }

    @Test
    public void residualDistinctAnchorContradictionsCountAllActorsThenThrowWithoutPublishingBuckets() {
        SpatialActorCollector actors = actors(2);
        SpatialProjectedFaceCache faces = faces(new int[][]{{0}, {1}});
        faces.anchorBeforeBucket[0] = 1; faces.anchorAfterBucket[0] = 2;
        faces.anchorBeforeBucket[1] = 3; faces.anchorAfterBucket[1] = 4;
        SpatialFaceRelationSolver relations = relations(2,
                new int[][]{{1, 0}, {1, 0}},
                new byte[][]{{SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                        SpatialFaceRelationSolver.ACTOR_BEHIND_FACE},
                        {SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                                SpatialFaceRelationSolver.ACTOR_BEHIND_FACE}});
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, new int[]{2, 3}, 6);
        planner.addRelations(actors, faces, relations);
        try {
            planner.finish(actors);
            Assert.fail("Expected exact-anchor invariant failure");
        } catch (SpatialConstraintInvariantException expected) {
            Assert.assertEquals(2, expected.unresolvedConstraintCount());
            Assert.assertTrue(expected.getMessage().contains("anchor=(1,0)"));
            Assert.assertTrue(expected.getMessage().contains("anchor=(0,0)"));
            Assert.assertTrue(expected.getMessage().contains("face=1"));
            Assert.assertTrue(expected.getMessage().contains("face=0"));
        }
        Assert.assertEquals(2, planner.unresolvedConstraintCount());
        Assert.assertArrayEquals(new int[]{2, 3}, new int[]{planner.actorBucket[0], planner.actorBucket[1]});
    }

    @Test
    public void overlappingTileIntervalsPreserveActorOrderAcrossOriginalBuckets() {
        assertOverlappingTileIntervalsPreserveActorOrder(30f, 10f, new int[]{2, 1}, 0, 1);
    }

    @Test
    public void mirroredOverlappingTileIntervalsPreserveActorOrderAcrossOriginalBuckets() {
        assertOverlappingTileIntervalsPreserveActorOrder(10f, 30f, new int[]{1, 2}, 1, 0);
    }

    @Test
    public void unconstrainedActorsSeparatedByOrdinaryEntriesFollowComparator() {
        SpatialActorCollector actors = actors(2);
        actors.actorCircleY[0] = 30f;
        actors.actorCircleY[1] = 10f;
        DrawList drawList = new DrawList(8);
        drawList.addTiledSlot(100);
        drawList.addEcsSlot(1);
        drawList.addTiledSlot(101);
        drawList.addEcsSlot(0);
        drawList.addVfxSlot(200);
        SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
        snapshot.build(drawList, 2, actors);
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, snapshot.actorOriginalBucket, snapshot.bucketCount);

        planner.finish(actors);
        SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();
        composer.compose(drawList, actors, planner, snapshot);

        Assert.assertFalse(planner.actorHasConstraint(0));
        Assert.assertFalse(planner.actorHasConstraint(1));
        Assert.assertArrayEquals(new int[]{2, 1},
                new int[]{planner.actorOriginalBucket[0], planner.actorOriginalBucket[1]});
        Assert.assertArrayEquals(new int[]{Integer.MIN_VALUE, Integer.MIN_VALUE},
                new int[]{planner.actorLowerBound[0], planner.actorLowerBound[1]});
        Assert.assertArrayEquals(new int[]{Integer.MAX_VALUE, Integer.MAX_VALUE},
                new int[]{planner.actorUpperBound[0], planner.actorUpperBound[1]});
        Assert.assertArrayEquals(new int[]{1, 1},
                new int[]{planner.actorBucket[0], planner.actorBucket[1]});
        Assert.assertArrayEquals(new int[]{0, 1},
                new int[]{planner.actorComparatorPosition[0], planner.actorComparatorPosition[1]});
        Assert.assertArrayEquals(new int[]{1, 2},
                new int[]{planner.finalActorDrawIndex[0], planner.finalActorDrawIndex[1]});
        Assert.assertArrayEquals(new int[]{100, 0, 1, 101, 200}, first(composer.composedSlots, 5));
        Assert.assertArrayEquals(new byte[]{RenderSourceDomain.SOURCE_TILED,
                        RenderSourceDomain.SOURCE_ECS, RenderSourceDomain.SOURCE_ECS,
                        RenderSourceDomain.SOURCE_TILED, RenderSourceDomain.SOURCE_VFX},
                first(composer.composedDomains, 5));
    }

    @Test
    public void largeFootprintActorAndHeroesFollowCircleCenterOrderAcrossBuckets() {
        SpatialActorCollector actors = actors(4);
        actors.actorCircleY[0] = 25f;
        actors.actorCircleY[1] = 40f;
        actors.actorCircleY[2] = 20f;
        actors.actorCircleY[3] = 10f;
        actors.actorCircleRadius = new float[]{24f, 2f, 2f, 2f};
        DrawList drawList = new DrawList(12);
        drawList.addTiledSlot(100);
        drawList.addEcsSlot(3);
        drawList.addTiledSlot(101);
        drawList.addEcsSlot(0);
        drawList.addEcsSlot(2);
        drawList.addTiledSlot(102);
        drawList.addEcsSlot(1);
        drawList.addVfxSlot(200);
        SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
        snapshot.build(drawList, 4, actors);
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, snapshot.actorOriginalBucket, snapshot.bucketCount);

        planner.finish(actors);
        SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();
        composer.compose(drawList, actors, planner, snapshot);

        Assert.assertArrayEquals(new int[]{100, 1, 0, 2, 3, 101, 102, 200},
                first(composer.composedSlots, 8));
        Assert.assertArrayEquals(new int[]{1, 1, 1, 1},
                new int[]{planner.actorBucket[0], planner.actorBucket[1],
                        planner.actorBucket[2], planner.actorBucket[3]});
    }

    @Test
    public void jointActorPlacementPreservesNonActorSubsequenceAndIsDeterministic() {
        SpatialActorCollector actors = actors(2);
        actors.actorCircleY[0] = 30f;
        actors.actorCircleY[1] = 10f;
        DrawList drawList = new DrawList(8);
        drawList.addTiledSlot(100);
        drawList.addEcsSlot(1);
        drawList.addTiledSlot(101);
        drawList.addEcsSlot(0);
        drawList.addVfxSlot(200);
        SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
        snapshot.build(drawList, 2, actors);
        SpatialProjectedFaceCache faces = faces(new int[][]{{0}, {1}});
        faces.anchorBeforeBucket[0] = 0; faces.anchorAfterBucket[0] = 1;
        faces.anchorBeforeBucket[1] = 2; faces.anchorAfterBucket[1] = 3;
        SpatialFaceRelationSolver relations = relations(2,
                new int[][]{{0, 1}, {0, 1}},
                new byte[][]{{SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                        SpatialFaceRelationSolver.ACTOR_BEHIND_FACE},
                        {SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                                SpatialFaceRelationSolver.ACTOR_BEHIND_FACE}});
        SpatialOrderingKernel kernel = new SpatialOrderingKernel();
        kernel.begin(actors, snapshot);
        kernel.addRelations(actors, faces, relations);

        kernel.finish(drawList, actors, snapshot);

        Assert.assertArrayEquals(new int[]{100, 0, 1, 101, 200}, first(kernel.orderedSlots(), 5));
        Assert.assertArrayEquals(new byte[]{RenderSourceDomain.SOURCE_TILED,
                        RenderSourceDomain.SOURCE_ECS, RenderSourceDomain.SOURCE_ECS,
                        RenderSourceDomain.SOURCE_TILED, RenderSourceDomain.SOURCE_VFX},
                first(kernel.orderedDomains(), 5));

        kernel.reset();
        kernel.begin(actors, snapshot);
        kernel.addRelations(actors, faces, relations);
        kernel.finish(drawList, actors, snapshot);
        Assert.assertArrayEquals(new int[]{100, 0, 1, 101, 200}, first(kernel.orderedSlots(), 5));
    }

    @Test
    public void mandatoryDisjointIntervalsAlreadyInActorOrderRemainDistinct() {
        SpatialActorCollector actors = actors(2);
        actors.actorCircleY[0] = 30f;
        actors.actorCircleY[1] = 10f;
        SpatialBucketPlanner planner = plannerWithIntervals(actors,
                new int[]{1, 2}, new int[]{0, 2}, new int[]{1, 3}, 4);

        Assert.assertEquals(1, planner.actorBucket[0]);
        Assert.assertEquals(2, planner.actorBucket[1]);
    }

    @Test
    public void disjointMandatoryIntervalsOverrideReversedActorComparatorWithoutThrowing() {
        SpatialActorCollector actors = actors(2);
        actors.actorCircleY[0] = 10f;
        actors.actorCircleY[1] = 30f;
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, new int[]{1183, 1147}, 1201);
        setIntervals(planner, new int[]{1116, 1147}, new int[]{1122, 1200});

        planner.finish(actors);

        Assert.assertArrayEquals(new int[]{1122, 1147},
                new int[]{planner.actorBucket[0], planner.actorBucket[1]});
        Assert.assertEquals(0, planner.unresolvedConstraintCount());
    }

    @Test
    public void disjointFinalBucketsComposeInHardOrderWithoutMovingNonActors() {
        SpatialActorCollector actors = actors(2);
        actors.actorCircleY[0] = 10f;
        actors.actorCircleY[1] = 30f;
        DrawList drawList = new DrawList(8);
        drawList.addTiledSlot(100);
        drawList.addEcsSlot(1);
        drawList.addTiledSlot(101);
        drawList.addEcsSlot(0);
        drawList.addVfxSlot(200);
        SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
        snapshot.build(drawList, 2, actors);
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, snapshot.actorOriginalBucket, snapshot.bucketCount);
        setIntervals(planner, new int[]{0, 2}, new int[]{1, 3});

        planner.finish(actors);
        SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();
        composer.compose(drawList, actors, planner, snapshot);

        Assert.assertArrayEquals(new int[]{100, 0, 101, 1, 200}, first(composer.composedSlots, 5));
        Assert.assertArrayEquals(new byte[]{RenderSourceDomain.SOURCE_TILED,
                        RenderSourceDomain.SOURCE_ECS, RenderSourceDomain.SOURCE_TILED,
                        RenderSourceDomain.SOURCE_ECS, RenderSourceDomain.SOURCE_VFX},
                first(composer.composedDomains, 5));
    }

    @Test
    public void touchingIntervalsMayShareBoundarySelectedByActorComparator() {
        SpatialActorCollector actors = actors(2);
        actors.actorCircleY[0] = 10f;
        actors.actorCircleY[1] = 30f;
        SpatialBucketPlanner planner = plannerWithIntervals(actors,
                new int[]{2, 2}, new int[]{1, 2}, new int[]{2, 3}, 4);

        Assert.assertArrayEquals(new int[]{2, 2},
                new int[]{planner.actorBucket[0], planner.actorBucket[1]});
        Assert.assertArrayEquals(new int[]{1, 0},
                new int[]{planner.sortedActorIndex[0], planner.sortedActorIndex[1]});
    }

    @Test
    public void mixedHardPrecedenceAndComparatorPreferencesProduceDeterministicOrder() {
        SpatialActorCollector actors = actors(4);
        actors.actorCircleY[0] = 10f;
        actors.actorCircleY[1] = 40f;
        actors.actorCircleY[2] = 30f;
        actors.actorCircleY[3] = 20f;
        SpatialBucketPlanner planner = plannerWithIntervals(actors,
                new int[]{1, 4, 3, 2}, new int[]{0, 4, 2, 1}, new int[]{2, 5, 4, 3}, 6);

        Assert.assertArrayEquals(new int[]{2, 4, 2, 2},
                new int[]{planner.actorBucket[0], planner.actorBucket[1],
                        planner.actorBucket[2], planner.actorBucket[3]});
        Assert.assertEquals(0, planner.actorOrderingFallbackCount());
    }

    @Test
    public void invalidCandidatePublishesWholeTileValidBaselineAndRecordsFallback() {
        SpatialActorCollector actors = actors(2);
        SpatialBucketPlanner planner = plannerWithIntervals(actors,
                new int[]{1, 2}, new int[]{0, 1}, new int[]{2, 3}, 4);
        planner.actorBucket[0] = 99;
        planner.actorBucket[1] = 99;
        planner.candidateActorBucket[0] = 2;
        planner.candidateActorBucket[1] = 0;

        Assert.assertFalse(planner.validateAndPublishActorBuckets(2));

        Assert.assertArrayEquals(new int[]{1, 2},
                new int[]{planner.actorBucket[0], planner.actorBucket[1]});
        Assert.assertEquals(1, planner.actorOrderingFallbackCount());
    }

    @Test
    public void partiallyOverlappingIntervalsMayUseDifferentBuckets() {
        SpatialActorCollector actors = actors(2);
        actors.actorCircleY[0] = 30f;
        actors.actorCircleY[1] = 10f;
        SpatialBucketPlanner planner = plannerWithIntervals(actors,
                new int[]{1, 3}, new int[]{1, 2}, new int[]{2, 3}, 5);

        Assert.assertEquals(1, planner.actorBucket[0]);
        Assert.assertEquals(3, planner.actorBucket[1]);
    }

    @Test
    public void intervalChainWithoutGlobalIntersectionUsesMonotonicDistinctBuckets() {
        SpatialActorCollector actors = actors(3);
        actors.actorCircleY[0] = 30f;
        actors.actorCircleY[1] = 20f;
        actors.actorCircleY[2] = 10f;
        SpatialBucketPlanner planner = plannerWithIntervals(actors,
                new int[]{1, 2, 3}, new int[]{1, 2, 3}, new int[]{2, 3, 4}, 5);

        Assert.assertArrayEquals(new int[]{1, 2, 3},
                new int[]{planner.actorBucket[0], planner.actorBucket[1], planner.actorBucket[2]});
    }

    private static void assertSameAnchorOpposition(byte[] types) {
        SpatialActorCollector actors = actors(1);
        SpatialProjectedFaceCache faces = faces(new int[][]{{0}, {0}});
        faces.anchorBeforeBucket[0] = 2;
        faces.anchorAfterBucket[0] = 3;
        SpatialFaceRelationSolver relations = relations(1, new int[][]{{0, 1}}, new byte[][]{types});
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, new int[]{4}, 6);
        planner.addRelations(actors, faces, relations);
        planner.finish(actors);
        Assert.assertEquals(2, planner.actorBucket[0]);
        Assert.assertEquals(Integer.MIN_VALUE, planner.actorLowerBound[0]);
        Assert.assertEquals(2, planner.actorUpperBound[0]);
        Assert.assertEquals(0, planner.unresolvedConstraintCount());
    }

    private static void assertOverlappingTileIntervalsPreserveActorOrder(float firstY,
                                                                         float secondY,
                                                                         int[] originals,
                                                                         int expectedFirst,
                                                                         int expectedSecond) {
        SpatialActorCollector actors = actors(2);
        actors.actorCircleY[0] = firstY;
        actors.actorCircleY[1] = secondY;
        SpatialProjectedFaceCache faces = faces(new int[][]{{0}, {1}});
        faces.anchorBeforeBucket[0] = 0; faces.anchorAfterBucket[0] = 1;
        faces.anchorBeforeBucket[1] = 2; faces.anchorAfterBucket[1] = 3;
        SpatialFaceRelationSolver relations = relations(2,
                new int[][]{{0, 1}, {0, 1}},
                new byte[][]{{SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                        SpatialFaceRelationSolver.ACTOR_BEHIND_FACE},
                        {SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                                SpatialFaceRelationSolver.ACTOR_BEHIND_FACE}});
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, originals, 4);
        planner.addRelations(actors, faces, relations);

        planner.finish(actors);

        Assert.assertEquals(1, planner.actorBucket[0]);
        Assert.assertEquals(1, planner.actorBucket[1]);
        int start = planner.bucketActorStart(1);
        Assert.assertArrayEquals(new int[]{expectedFirst, expectedSecond},
                new int[]{planner.sortedActorIndex[start], planner.sortedActorIndex[start + 1]});
    }

    private static SpatialBucketPlanner plannerWithIntervals(SpatialActorCollector actors,
                                                              int[] originals,
                                                              int[] lowerBounds,
                                                              int[] upperBounds,
                                                              int bucketCount) {
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actors, originals, bucketCount);
        setIntervals(planner, lowerBounds, upperBounds);
        planner.finish(actors);
        return planner;
    }

    private static void setIntervals(SpatialBucketPlanner planner,
                                     int[] lowerBounds,
                                     int[] upperBounds) {
        for (int actor = 0; actor < planner.actorCount; actor++) {
            planner.actorHasConstraint[actor] = true;
            planner.actorLowerBound[actor] = lowerBounds[actor];
            planner.actorUpperBound[actor] = upperBounds[actor];
        }
    }

    private static int[] first(int[] values, int count) {
        int[] result = new int[count];
        System.arraycopy(values, 0, result, 0, count);
        return result;
    }

    private static byte[] first(byte[] values, int count) {
        byte[] result = new byte[count];
        System.arraycopy(values, 0, result, 0, count);
        return result;
    }

    private static SpatialActorCollector actors(int count) {
        SpatialActorCollector actors = new SpatialActorCollector();
        actors.actorCount = count;
        actors.actorSlot = new int[count]; actors.actorEntityId = new int[count];
        actors.actorStableOrder = new int[count]; actors.actorCircleX = new float[count];
        actors.actorCircleY = new float[count]; actors.actorDrawIndex = new int[count];
        for (int i = 0; i < count; i++) { actors.actorSlot[i] = i; actors.actorEntityId[i] = 100 + i; actors.actorStableOrder[i] = i; actors.actorDrawIndex[i] = i; }
        return actors;
    }

    private static SpatialProjectedFaceCache faces(int[][] memberships) {
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        faces.faceCount = memberships.length;
        faces.faceCompiledIndex = new int[memberships.length]; faces.faceStructureId = new int[memberships.length];
        faces.faceAnchorIndexStart = new int[memberships.length]; faces.faceAnchorIndexCount = new int[memberships.length];
        int total = 0; int maximum = -1;
        for (int[] membership : memberships) for (int anchor : membership) { total++; maximum = Math.max(maximum, anchor); }
        faces.faceAnchorIndices = new int[total];
        faces.faceAnchorScreenMinX = new float[total]; faces.faceAnchorScreenMaxX = new float[total];
        int write = 0;
        for (int face = 0; face < memberships.length; face++) {
            faces.faceCompiledIndex[face] = face; faces.faceStructureId[face] = face + 10;
            faces.faceAnchorIndexStart[face] = write; faces.faceAnchorIndexCount[face] = memberships[face].length;
            for (int anchor : memberships[face]) {
                faces.faceAnchorIndices[write] = anchor;
                faces.faceAnchorScreenMinX[write] = -Float.MAX_VALUE;
                faces.faceAnchorScreenMaxX[write] = Float.MAX_VALUE;
                write++;
            }
        }
        faces.anchorCount = maximum + 1; faces.anchorGx = new int[faces.anchorCount]; faces.anchorGy = new int[faces.anchorCount];
        faces.anchorResolved = new boolean[faces.anchorCount]; faces.anchorBeforeBucket = new int[faces.anchorCount]; faces.anchorAfterBucket = new int[faces.anchorCount];
        for (int anchor = 0; anchor < faces.anchorCount; anchor++) { faces.anchorGx[anchor] = anchor; faces.anchorResolved[anchor] = true; }
        return faces;
    }

    private static SpatialFaceRelationSolver relations(int actorCount, int[][] faces, byte[][] types) {
        SpatialFaceRelationSolver out = new SpatialFaceRelationSolver();
        out.actorRelationStart = new int[actorCount]; out.actorRelationCount = new int[actorCount];
        int total = 0; for (int[] actorFaces : faces) total += actorFaces.length;
        out.relationFaceIndex = new int[total]; out.relationType = new byte[total];
        int write = 0;
        for (int actor = 0; actor < actorCount; actor++) {
            out.actorRelationStart[actor] = write; out.actorRelationCount[actor] = faces[actor].length;
            for (int relation = 0; relation < faces[actor].length; relation++) {
                out.relationFaceIndex[write] = faces[actor][relation]; out.relationType[write] = types[actor][relation]; write++;
            }
        }
        out.relationCount = write;
        return out;
    }
}
