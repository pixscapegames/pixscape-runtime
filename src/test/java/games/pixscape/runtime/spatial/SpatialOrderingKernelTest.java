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
    public void residualDistinctAnchorContradictionsUseOriginalBucketsWithoutThrowing() {
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
        planner.finish(actors);

        Assert.assertEquals(2, planner.unresolvedConstraintCount());
        Assert.assertArrayEquals(new int[]{2, 3}, new int[]{planner.actorBucket[0], planner.actorBucket[1]});
    }

    @Test
    public void contradictoryActorFallsBackIndividuallyWithoutDisturbingValidActorsOrNonActors() {
        SpatialActorCollector actors = actors(3);
        actors.actorCircleY[0] = 20f;
        actors.actorCircleY[1] = 40f;
        actors.actorCircleY[2] = 30f;
        DrawList drawList = new DrawList(8);
        drawList.addTiledSlot(100);
        drawList.addEcsSlot(0);
        drawList.addTiledSlot(101);
        drawList.addEcsSlot(1);
        drawList.addEcsSlot(2);
        drawList.addVfxSlot(200);
        SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
        snapshot.build(drawList, 3, actors);
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        SpatialBucketDrawListComposer composer = new SpatialBucketDrawListComposer();
        int[] firstSlots = null;

        for (int pass = 0; pass < 2; pass++) {
            planner.begin(actors, snapshot.actorOriginalBucket, snapshot.bucketCount);
            setIntervals(planner, new int[]{3, 2, 0}, new int[]{0, 3, 2});
            planner.finish(actors);
            composer.compose(drawList, actors, planner, snapshot);

            Assert.assertEquals(1, planner.unresolvedConstraintCount());
            Assert.assertEquals(0, planner.actorOrderingFallbackCount());
            Assert.assertEquals(1, planner.actorBucket[0]);
            Assert.assertTrue(planner.actorBucket[1] >= 2 && planner.actorBucket[1] <= 3);
            Assert.assertTrue(planner.actorBucket[2] >= 0 && planner.actorBucket[2] <= 2);
            Assert.assertEquals(1, occurrences(composer.composedSlots, composer.composedSize, 0));
            Assert.assertEquals(1, occurrences(composer.composedSlots, composer.composedSize, 1));
            Assert.assertEquals(1, occurrences(composer.composedSlots, composer.composedSize, 2));
            Assert.assertArrayEquals(new int[]{100, 101, 200},
                    nonActorSlots(composer.composedSlots, composer.composedDomains, composer.composedSize));
            if (firstSlots == null) firstSlots = first(composer.composedSlots, composer.composedSize);
            else Assert.assertArrayEquals(firstSlots, first(composer.composedSlots, composer.composedSize));
        }
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
    public void crossLayerIntervalsContradictingLayerOrderUseStructuralBaselineWithoutThrowing() {
        SpatialActorCollector actors = actors(2);
        actors.actorLayerIndex[0] = 1;
        actors.actorLayerIndex[1] = 2;
        actors.actorCircleY[0] = 10f;
        actors.actorCircleY[1] = 30f;
        SpatialBucketPlanner planner = plannerWithIntervals(actors,
                new int[]{0, 1}, new int[]{2, 0}, new int[]{3, 1}, 4);

        Assert.assertArrayEquals(new int[]{0, 1},
                new int[]{planner.actorBucket[0], planner.actorBucket[1]});
        Assert.assertEquals(0, planner.unresolvedConstraintCount());
        Assert.assertEquals(1, planner.actorOrderingFallbackCount());
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
    public void incompleteCandidatePublishesWholeStructuralBaselineAndRecordsFallback() {
        SpatialActorCollector actors = actors(2);
        SpatialBucketPlanner planner = plannerWithIntervals(actors,
                new int[]{1, 2}, new int[]{0, 1}, new int[]{2, 3}, 4);
        planner.actorBucket[0] = 99;
        planner.actorBucket[1] = 99;

        Assert.assertFalse(planner.validateAndPublishActorBuckets(1));

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

    @Test
    public void exhaustiveSmallValidIntervalsPreserveBoundsPrecedenceComparatorAndDeterminism() {
        final int bucketCount = 4;
        int[] intervalLower = new int[10];
        int[] intervalUpper = new int[10];
        int intervalCount = 0;
        for (int lower = 0; lower < bucketCount; lower++) {
            for (int upper = lower; upper < bucketCount; upper++) {
                intervalLower[intervalCount] = lower;
                intervalUpper[intervalCount++] = upper;
            }
        }
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        for (int actorCount = 1; actorCount <= 4; actorCount++) {
            SpatialActorCollector actors = actors(actorCount);
            int[] originals = new int[actorCount];
            int[] lowerBounds = new int[actorCount];
            int[] upperBounds = new int[actorCount];
            int[] expectedOrder = new int[actorCount];
            int[] firstBuckets = new int[actorCount];
            int[] firstOrder = new int[actorCount];
            int combinations = 1;
            for (int actor = 0; actor < actorCount; actor++) combinations *= intervalCount;
            for (int encoded = 0; encoded < combinations; encoded++) {
                int remaining = encoded;
                for (int actor = 0; actor < actorCount; actor++) {
                    int interval = remaining % intervalCount;
                    remaining /= intervalCount;
                    lowerBounds[actor] = intervalLower[interval];
                    upperBounds[actor] = intervalUpper[interval];
                }
                for (int variant = 0; variant < 4; variant++) {
                    for (int actor = 0; actor < actorCount; actor++) {
                        originals[actor] = (variant & 1) == 0
                                ? actor % bucketCount : bucketCount - 1 - actor % bucketCount;
                        actors.actorCircleY[actor] = (variant & 2) == 0
                                ? actor + 1f : actorCount - actor;
                    }
                    expectedActorOrder(actors, lowerBounds, upperBounds, expectedOrder);
                    for (int pass = 0; pass < 2; pass++) {
                        planner.begin(actors, originals, bucketCount);
                        setIntervals(planner, lowerBounds, upperBounds);
                        planner.finish(actors);

                        Assert.assertEquals(0, planner.unresolvedConstraintCount());
                        Assert.assertEquals(0, planner.actorOrderingFallbackCount());
                        assertPlannerOrder(planner, expectedOrder, lowerBounds, upperBounds,
                                pass == 0 ? firstBuckets : null, pass == 0 ? firstOrder : null);
                        if (pass != 0) {
                            for (int actor = 0; actor < actorCount; actor++) {
                                Assert.assertEquals(firstBuckets[actor], planner.actorBucket[actor]);
                            }
                            Assert.assertArrayEquals(firstOrder, finalActorOrder(planner));
                        }
                    }
                }
            }
        }
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

    private static int occurrences(int[] values, int count, int value) {
        int occurrences = 0;
        for (int index = 0; index < count; index++) if (values[index] == value) occurrences++;
        return occurrences;
    }

    private static int[] nonActorSlots(int[] slots, byte[] domains, int count) {
        int nonActorCount = 0;
        for (int index = 0; index < count; index++) {
            if (domains[index] != RenderSourceDomain.SOURCE_ECS) nonActorCount++;
        }
        int[] result = new int[nonActorCount];
        int write = 0;
        for (int index = 0; index < count; index++) {
            if (domains[index] != RenderSourceDomain.SOURCE_ECS) result[write++] = slots[index];
        }
        return result;
    }

    private static void expectedActorOrder(SpatialActorCollector actors,
                                           int[] lowerBounds,
                                           int[] upperBounds,
                                           int[] result) {
        boolean[] remaining = new boolean[result.length];
        for (int actor = 0; actor < result.length; actor++) remaining[actor] = true;
        for (int position = 0; position < result.length; position++) {
            int selected = -1;
            for (int candidate = 0; candidate < result.length; candidate++) {
                if (!remaining[candidate] || hasIntervalPredecessor(
                        candidate, remaining, lowerBounds, upperBounds)) continue;
                if (selected < 0 || SpatialActorBucketSorter.compareActors(
                        actors, candidate, selected) < 0) selected = candidate;
            }
            Assert.assertTrue(selected >= 0);
            result[position] = selected;
            remaining[selected] = false;
        }
    }

    private static boolean hasIntervalPredecessor(int actor,
                                                  boolean[] remaining,
                                                  int[] lowerBounds,
                                                  int[] upperBounds) {
        for (int other = 0; other < remaining.length; other++) {
            if (remaining[other] && upperBounds[other] < lowerBounds[actor]) return true;
        }
        return false;
    }

    private static void assertPlannerOrder(SpatialBucketPlanner planner,
                                           int[] expectedOrder,
                                           int[] lowerBounds,
                                           int[] upperBounds,
                                           int[] bucketCopy,
                                           int[] orderCopy) {
        boolean[] seen = new boolean[expectedOrder.length];
        int[] actualOrder = finalActorOrder(planner);
        Assert.assertArrayEquals(expectedOrder, actualOrder);
        int previousBucket = 0;
        for (int position = 0; position < actualOrder.length; position++) {
            int actor = actualOrder[position];
            Assert.assertFalse(seen[actor]);
            seen[actor] = true;
            int bucket = planner.actorBucket[actor];
            Assert.assertTrue(bucket >= lowerBounds[actor]);
            Assert.assertTrue(bucket <= upperBounds[actor]);
            if (position > 0) Assert.assertTrue(previousBucket <= bucket);
            previousBucket = bucket;
        }
        if (bucketCopy != null) {
            for (int actor = 0; actor < expectedOrder.length; actor++) {
                bucketCopy[actor] = planner.actorBucket[actor];
            }
        }
        if (orderCopy != null) System.arraycopy(actualOrder, 0, orderCopy, 0, actualOrder.length);
    }

    private static int[] finalActorOrder(SpatialBucketPlanner planner) {
        int[] result = new int[planner.actorCount];
        int write = 0;
        for (int bucket = 0; bucket < planner.bucketCount; bucket++) {
            int start = planner.bucketActorStart(bucket);
            int count = planner.bucketActorCount(bucket);
            for (int index = 0; index < count; index++) {
                result[write++] = planner.sortedActorIndex[start + index];
            }
        }
        Assert.assertEquals(planner.actorCount, write);
        return result;
    }

    private static SpatialActorCollector actors(int count) {
        SpatialActorCollector actors = new SpatialActorCollector();
        actors.actorCount = count;
        actors.actorSlot = new int[count]; actors.actorEntityId = new int[count];
        actors.actorStableOrder = new int[count]; actors.actorLayerIndex = new int[count];
        actors.actorCircleX = new float[count];
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
