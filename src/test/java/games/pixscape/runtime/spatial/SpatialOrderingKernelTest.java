package games.pixscape.runtime.spatial;

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
