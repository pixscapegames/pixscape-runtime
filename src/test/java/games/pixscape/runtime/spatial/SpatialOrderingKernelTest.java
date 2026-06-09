package games.pixscape.runtime.spatial;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SpatialOrderingKernelTest {
    private final SpatialOrderingKernel kernel = new SpatialOrderingKernel();

    @Test
    public void nonActorOrderIsFullyPreserved() {
        int[] input = {100, 1, 101, 2, 102};
        SpatialActorCollector actors = actors(actor(1, 0, 20f), actor(2, 1, 10f));
        SpatialFrameSnapshotBuilder snapshot = snapshot(input, actors);

        kernel.begin(actors, snapshot);
        kernel.finish(input, input.length, actors, snapshot);

        Assert.assertArrayEquals(new int[]{100, 101, 102}, nonActors(kernel.orderedSlots(), kernel.orderedSize()));
    }

    @Test
    public void originalBucketComesFromStableSourceDrawList() {
        int[] input = {100, 1, 101, 102, 2};
        SpatialActorCollector actors = actors(actor(1, 0, 20f), actor(2, 1, 10f));
        SpatialFrameSnapshotBuilder snapshot = snapshot(input, actors);

        Assert.assertEquals(1, snapshot.actorOriginalBucket[0]);
        Assert.assertEquals(3, snapshot.actorOriginalBucket[1]);
    }

    @Test
    public void blockRangesUseStableNonActorOrdinals() {
        SpatialBlocksRuntimeCache cache = cache(range(0, 2));
        int[] before = {0, 1, 1, 2};
        int[] after = {1, 1, 2, 2};

        cache.convertDrawIndexRangesToBuckets(before, after, before.length);

        Assert.assertEquals(0, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(1, cache.blockAnchorEndDrawIndex[0]);
    }

    @Test
    public void relationUsesVerticalInterpolationNotClosestSegmentPoint() {
        SpatialRelationKernel relation = new SpatialRelationKernel();

        int result = relation.relation(70f, 0f, 0f, 0f, 100f, 100f);

        Assert.assertEquals(SpatialRelationKernel.ACTOR_IN_FRONT_OF_BLOCK, result);
    }

    @Test
    public void xRangeExclusionProducesNoRelation() {
        SpatialRelationKernel relation = new SpatialRelationKernel();

        Assert.assertEquals(SpatialRelationKernel.NO_RELATION,
                relation.relation(120f, 0f, 0f, 0f, 100f, 100f));
    }

    @Test
    public void adjacentSegmentsUseSemiOpenSeam() {
        SpatialRelationKernel relation = new SpatialRelationKernel();

        Assert.assertEquals(SpatialRelationKernel.NO_RELATION,
                relation.relation(10f, 0f, 0f, 0f, 10f, 0f));
        Assert.assertEquals(SpatialRelationKernel.ACTOR_BEHIND_BLOCK,
                relation.relation(10f, 1f, 10f, 0f, 20f, 0f));
    }

    @Test
    public void actorsSortOnlyInsideTheirBuckets() {
        int[] input = {100, 1, 101, 2, 3, 102};
        SpatialActorCollector actors = actors(actor(1, 0, 50f), actor(2, 1, 10f), actor(3, 2, 40f));
        SpatialFrameSnapshotBuilder snapshot = snapshot(input, actors);
        SpatialRelationSolver relations = relations(
                relation(0, 0, SpatialRelationKernel.ACTOR_BEHIND_BLOCK),
                relation(1, 1, SpatialRelationKernel.ACTOR_IN_FRONT_OF_BLOCK),
                relation(2, 1, SpatialRelationKernel.ACTOR_IN_FRONT_OF_BLOCK));
        SpatialBlocksRuntimeCache cache = cache(range(0, 0), range(1, 1));

        kernel.begin(actors, snapshot);
        kernel.addRelations(actors, cache, relations);
        kernel.finish(input, input.length, actors, snapshot);

        Assert.assertArrayEquals(new int[]{1, 100, 101, 3, 2, 102},
                Arrays.copyOf(kernel.orderedSlots(), kernel.orderedSize()));
    }

    @Test
    public void equalCenterYOrderingIsDeterministicAcrossFrames() {
        int[] input = {100, 2, 1, 101};
        SpatialActorCollector actors = actors(actor(1, 10, 10f), actor(2, 5, 10f));
        SpatialFrameSnapshotBuilder snapshot = snapshot(input, actors);

        kernel.begin(actors, snapshot);
        kernel.finish(input, input.length, actors, snapshot);
        int[] first = Arrays.copyOf(kernel.orderedSlots(), kernel.orderedSize());
        kernel.begin(actors, snapshot);
        kernel.finish(input, input.length, actors, snapshot);

        Assert.assertArrayEquals(first, Arrays.copyOf(kernel.orderedSlots(), kernel.orderedSize()));
        Assert.assertArrayEquals(new int[]{100, 1, 2, 101}, first);
    }

    private static SpatialFrameSnapshotBuilder snapshot(int[] input, SpatialActorCollector actors) {
        SpatialFrameSnapshotBuilder snapshot = new SpatialFrameSnapshotBuilder();
        snapshot.build(input, input.length, 200, actors);
        return snapshot;
    }

    private static int[] nonActors(int[] slots, int size) {
        int[] out = new int[size];
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (slots[i] >= 100) out[count++] = slots[i];
        }
        return Arrays.copyOf(out, count);
    }

    private static SpatialActorCollector actors(Actor... specs) {
        SpatialActorCollector actors = new SpatialActorCollector();
        actors.actorCount = specs.length;
        actors.actorSlot = new int[specs.length];
        actors.actorEntityId = new int[specs.length];
        actors.actorDrawIndex = new int[specs.length];
        actors.actorStableOrder = new int[specs.length];
        actors.actorCircleY = new float[specs.length];
        for (int i = 0; i < specs.length; i++) {
            actors.actorSlot[i] = specs[i].slot;
            actors.actorEntityId[i] = specs[i].slot;
            actors.actorDrawIndex[i] = i;
            actors.actorStableOrder[i] = specs[i].stableOrder;
            actors.actorCircleY[i] = specs[i].centerY;
        }
        return actors;
    }

    private static SpatialBlocksRuntimeCache cache(BlockRange... ranges) {
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        for (int i = 0; i < ranges.length; i++) {
            int block = cache.addBlock(1);
            cache.setAnchor(block, 0, 100 + i, ranges[i].start);
            cache.finalizeBlockRange(block);
            cache.blockAnchorStartDrawIndex[block] = ranges[i].start;
            cache.blockAnchorEndDrawIndex[block] = ranges[i].end;
        }
        return cache;
    }

    private static SpatialRelationSolver relations(Relation... specs) {
        SpatialRelationSolver relations = new SpatialRelationSolver();
        relations.relationCount = specs.length;
        relations.relationActorIndex = new int[specs.length];
        relations.relationBlockIndex = new int[specs.length];
        relations.relationType = new int[specs.length];
        for (int i = 0; i < specs.length; i++) {
            relations.relationActorIndex[i] = specs[i].actor;
            relations.relationBlockIndex[i] = specs[i].block;
            relations.relationType[i] = specs[i].type;
        }
        return relations;
    }

    private static Actor actor(int slot, int stableOrder, float centerY) {
        return new Actor(slot, stableOrder, centerY);
    }

    private static BlockRange range(int start, int end) {
        return new BlockRange(start, end);
    }

    private static Relation relation(int actor, int block, int type) {
        return new Relation(actor, block, type);
    }

    private static final class Actor {
        final int slot;
        final int stableOrder;
        final float centerY;

        Actor(int slot, int stableOrder, float centerY) {
            this.slot = slot;
            this.stableOrder = stableOrder;
            this.centerY = centerY;
        }
    }

    private static final class BlockRange {
        final int start;
        final int end;

        BlockRange(int start, int end) {
            this.start = start;
            this.end = end;
        }
    }

    private static final class Relation {
        final int actor;
        final int block;
        final int type;

        Relation(int actor, int block, int type) {
            this.actor = actor;
            this.block = block;
            this.type = type;
        }
    }
}
