package games.pixscape.runtime.spatial;

import org.junit.Assert;
import org.junit.Test;

public final class SpatialCircleRelationTest {
    @Test
    public void junctionDiagnosticShowsOnlyCircleOverlapsExpandCoverage() {
        SpatialProjectedFaceCache faces = adjacentFaces();
        SpatialActorCollector actor = actor(.05f, 1f, .2f);
        SpatialFaceRelationSolver relations = solve(actor, faces);
        SpatialBucketPlanner planner = plan(actor, faces, relations, 2, 4);

        String diagnostic = "actorCenter=(" + actor.actorCircleX[0] + "," + actor.actorCircleY[0]
                + "), radius=" + actor.actorCircleRadius[0]
                + ", centerSlices=[1], circleSlices=[0,1]"
                + ", emittedFaces=" + emittedFaces(relations)
                + ", acceptedSlices=" + planner.acceptedLocalMembershipCount
                + ", rejectedSlices=" + planner.rejectedNonlocalMembershipCount;
        Assert.assertEquals(diagnostic, 2, relations.actorRelationCount[0]);
        Assert.assertEquals(diagnostic, 2, planner.acceptedLocalMembershipCount);
        Assert.assertEquals(diagnostic, 0, planner.rejectedNonlocalMembershipCount);
    }

    @Test
    public void circleNearWallCornerEmitsBothSupportFacesWhenCenterHitsOne() {
        SpatialProjectedFaceCache faces = cornerFaces();
        SpatialActorCollector actor = actor(.05f, .25f, .2f);
        SpatialFaceRelationSolver relations = solve(actor, faces);

        Assert.assertEquals(2, relations.actorRelationCount[0]);
        Assert.assertEquals(0, relations.relationFaceIndex[relations.actorRelationStart[0]]);
        Assert.assertEquals(1, relations.relationFaceIndex[relations.actorRelationStart[0] + 1]);
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE,
                relations.relationType[relations.actorRelationStart[0]]);
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE,
                relations.relationType[relations.actorRelationStart[0] + 1]);
    }

    @Test
    public void centerInOneSliceWhileCircleOverlapsNeighborSelectsBothSlices() {
        SpatialProjectedFaceCache faces = oneFaceWithAdjacentSlices();
        SpatialActorCollector actor = actor(.1f, 1f, .2f);
        SpatialFaceRelationSolver relations = oneRelation(SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE);
        SpatialBucketPlanner planner = plan(actor, faces, relations, 0, 3);

        Assert.assertEquals(2, planner.acceptedLocalMembershipCount);
        Assert.assertEquals(0, planner.rejectedNonlocalMembershipCount);
        Assert.assertEquals(2, planner.actorLowerBound[0]);
    }

    @Test
    public void smallRadiusRetainsPointLikeFaceAndSliceSelection() {
        SpatialProjectedFaceCache faces = adjacentFaces();
        SpatialActorCollector actor = actor(.5f, 1f, .001f);
        SpatialFaceRelationSolver relations = solve(actor, faces);
        SpatialBucketPlanner planner = plan(actor, faces, relations, 2, 4);

        Assert.assertEquals(1, relations.actorRelationCount[0]);
        Assert.assertEquals(1, planner.acceptedLocalMembershipCount);
        Assert.assertEquals(0, planner.rejectedNonlocalMembershipCount);
        Assert.assertEquals(1, relations.relationFaceIndex[relations.actorRelationStart[0]]);
    }

    @Test
    public void largeRadiusEmitsEachRelevantFaceOnce() {
        SpatialProjectedFaceCache faces = adjacentFaces();
        SpatialActorCollector actor = actor(.05f, 1f, 2f);
        SpatialFaceRelationSolver relations = solve(actor, faces);

        Assert.assertEquals(2, relations.actorRelationCount[0]);
        Assert.assertEquals(0, relations.relationFaceIndex[relations.actorRelationStart[0]]);
        Assert.assertEquals(1, relations.relationFaceIndex[relations.actorRelationStart[0] + 1]);
    }

    @Test
    public void movementAroundJunctionKeepsRelationsAndBucketStable() {
        SpatialProjectedFaceCache faces = adjacentFaces();
        for (int step = -5; step <= 5; step++) {
            SpatialActorCollector actor = actor(step * .01f, 1f, .2f);
            SpatialFaceRelationSolver relations = solve(actor, faces);
            SpatialBucketPlanner planner = plan(actor, faces, relations, 3, 4);
            Assert.assertEquals("step=" + step, 2, relations.actorRelationCount[0]);
            Assert.assertEquals("step=" + step, 2, planner.acceptedLocalMembershipCount);
            Assert.assertEquals("step=" + step, 0, planner.actorBucket[0]);
        }
    }

    @Test
    public void circleOverlappingSupportLineUsesStableCenterFallback() {
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_BEHIND_FACE,
                SpatialLineRelation.circleRelation(0f, .05f, 1f, .2f));
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                SpatialLineRelation.circleRelation(0f, -.05f, 1f, .2f));
        Assert.assertEquals(SpatialFaceRelationSolver.ACTOR_IN_FRONT_OF_FACE,
                SpatialLineRelation.circleRelation(0f, 0f, 1f, .2f));
    }

    private static SpatialFaceRelationSolver solve(SpatialActorCollector actor,
                                                   SpatialProjectedFaceCache faces) {
        SpatialFaceRelationSolver relations = new SpatialFaceRelationSolver();
        relations.solve(actor, faces);
        return relations;
    }

    private static SpatialBucketPlanner plan(SpatialActorCollector actor,
                                             SpatialProjectedFaceCache faces,
                                             SpatialFaceRelationSolver relations,
                                             int original,
                                             int bucketCount) {
        SpatialBucketPlanner planner = new SpatialBucketPlanner();
        planner.begin(actor, new int[]{original}, bucketCount);
        planner.addRelations(actor, faces, relations);
        planner.finish(actor);
        return planner;
    }

    private static SpatialActorCollector actor(float x, float y, float radius) {
        SpatialActorCollector actor = new SpatialActorCollector();
        actor.actorCount = 1;
        actor.actorSlot = new int[]{0};
        actor.actorEntityId = new int[]{7};
        actor.actorStableOrder = new int[]{0};
        actor.actorDrawIndex = new int[]{0};
        actor.actorCircleX = new float[]{x};
        actor.actorCircleY = new float[]{y};
        actor.actorCircleRadius = new float[]{radius};
        actor.actorAltitude = new float[]{0f};
        actor.actorHeight = new float[]{1f};
        return actor;
    }

    private static SpatialProjectedFaceCache adjacentFaces() {
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        faces.faceCount = 2;
        faces.structureCount = 1;
        faces.faceStructureId = new int[]{1, 1};
        faces.faceCompiledIndex = new int[]{0, 1};
        faces.faceAltitude = new float[]{0f, 0f};
        faces.faceHeight = new float[]{2f, 2f};
        faces.screenMinX = new float[]{-1f, 0f};
        faces.screenMaxX = new float[]{0f, 1f};
        faces.slope = new float[]{0f, 0f};
        faces.intercept = new float[]{0f, 0f};
        faces.structureFaceStart = new int[]{0};
        faces.structureFaceCount = new int[]{2};
        faces.structureMinX = new float[]{-1f};
        faces.structureMaxX = new float[]{1f};
        faces.faceAnchorIndexStart = new int[]{0, 1};
        faces.faceAnchorIndexCount = new int[]{1, 1};
        faces.faceAnchorIndices = new int[]{0, 1};
        faces.faceAnchorScreenMinX = new float[]{-1f, 0f};
        faces.faceAnchorScreenMaxX = new float[]{0f, 1f};
        faces.anchorCount = 2;
        faces.anchorGx = new int[]{0, 1};
        faces.anchorGy = new int[]{0, 0};
        faces.anchorResolved = new boolean[]{true, true};
        faces.anchorBeforeBucket = new int[]{0, 1};
        faces.anchorAfterBucket = new int[]{1, 2};
        return faces;
    }

    private static SpatialProjectedFaceCache oneFaceWithAdjacentSlices() {
        SpatialProjectedFaceCache faces = adjacentFaces();
        faces.faceCount = 1;
        faces.faceStructureId = new int[]{1};
        faces.faceCompiledIndex = new int[]{0};
        faces.faceAnchorIndexStart = new int[]{0};
        faces.faceAnchorIndexCount = new int[]{2};
        faces.faceAnchorIndices = new int[]{0, 1};
        return faces;
    }

    private static SpatialProjectedFaceCache cornerFaces() {
        SpatialProjectedFaceCache faces = adjacentFaces();
        faces.slope = new float[]{1f, -1f};
        faces.intercept = new float[]{0f, 0f};
        float inverseNormalLength = 1f / (float) Math.sqrt(2f);
        faces.inverseNormalLength = new float[]{inverseNormalLength, inverseNormalLength};
        return faces;
    }

    private static SpatialFaceRelationSolver oneRelation(byte type) {
        SpatialFaceRelationSolver relations = new SpatialFaceRelationSolver();
        relations.relationCount = 1;
        relations.actorRelationStart = new int[]{0};
        relations.actorRelationCount = new int[]{1};
        relations.relationFaceIndex = new int[]{0};
        relations.relationType = new byte[]{type};
        return relations;
    }

    private static String emittedFaces(SpatialFaceRelationSolver relations) {
        StringBuilder out = new StringBuilder("[");
        int start = relations.actorRelationStart[0];
        int end = start + relations.actorRelationCount[0];
        for (int i = start; i < end; i++) {
            if (i > start) out.append(',');
            out.append(relations.relationFaceIndex[i]);
        }
        return out.append(']').toString();
    }
}
