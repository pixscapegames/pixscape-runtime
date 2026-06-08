package games.pixscape.runtime.spatial;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SpatialInsertionPlannerTest {
    private final SpatialInsertionPlanner planner = new SpatialInsertionPlanner();

    @Test
    public void behindRelationMapsToBeforeAnchorStart() {
        SpatialActorCollector actors = actors(actor(10, 100, 7, 100));
        SpatialBlocksRuntimeCache cache = cache(range(20, 30));
        SpatialRelationSolver relations = relations(relation(0, 0, SpatialRelationSolver.ACTOR_BEHIND_BLOCK));

        planner.plan(actors, cache, relations);

        Assert.assertEquals(1, planner.planCount());
        Assert.assertEquals(20, planner.planTargetDrawIndex[0]);
        Assert.assertEquals(100, planner.planActorSlot[0]);
    }

    @Test
    public void inFrontRelationMapsToAfterAnchorEnd() {
        SpatialActorCollector actors = actors(actor(10, 100, 7, 100));
        SpatialBlocksRuntimeCache cache = cache(range(20, 30));
        SpatialRelationSolver relations = relations(relation(0, 0, SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK));

        planner.plan(actors, cache, relations);

        Assert.assertEquals(1, planner.planCount());
        Assert.assertEquals(31, planner.planTargetDrawIndex[0]);
    }

    @Test
    public void multipleInFrontConstraintsChooseMaxLowerBound() {
        SpatialActorCollector actors = actors(actor(0, 100, 7, 100));
        SpatialBlocksRuntimeCache cache = cache(range(0, 10), range(20, 25), range(15, 18));
        SpatialRelationSolver relations = relations(
                relation(0, 0, SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK),
                relation(0, 1, SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK),
                relation(0, 2, SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK));

        planner.plan(actors, cache, relations);

        Assert.assertEquals(1, planner.planCount());
        Assert.assertEquals(26, planner.planTargetDrawIndex[0]);
    }

    @Test
    public void multipleBehindConstraintsChooseMinUpperBound() {
        SpatialActorCollector actors = actors(actor(100, 100, 7, 100));
        SpatialBlocksRuntimeCache cache = cache(range(40, 45), range(22, 30), range(35, 39));
        SpatialRelationSolver relations = relations(
                relation(0, 0, SpatialRelationSolver.ACTOR_BEHIND_BLOCK),
                relation(0, 1, SpatialRelationSolver.ACTOR_BEHIND_BLOCK),
                relation(0, 2, SpatialRelationSolver.ACTOR_BEHIND_BLOCK));

        planner.plan(actors, cache, relations);

        Assert.assertEquals(1, planner.planCount());
        Assert.assertEquals(22, planner.planTargetDrawIndex[0]);
    }

    @Test
    public void mixedCompatibleConstraintsClampOriginalDrawIndexIntoRange() {
        SpatialActorCollector actors = actors(actor(15, 100, 7, 100));
        SpatialBlocksRuntimeCache cache = cache(range(0, 10), range(30, 40));
        SpatialRelationSolver relations = relations(
                relation(0, 0, SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK),
                relation(0, 1, SpatialRelationSolver.ACTOR_BEHIND_BLOCK));

        planner.plan(actors, cache, relations);

        Assert.assertEquals(1, planner.planCount());
        Assert.assertEquals(15, planner.planTargetDrawIndex[0]);
    }

    @Test
    public void mixedCompatibleConstraintsUseNearestBoundaryWhenOriginalIsOutsideRange() {
        SpatialActorCollector actors = actors(actor(5, 100, 7, 100));
        SpatialBlocksRuntimeCache cache = cache(range(0, 10), range(30, 40));
        SpatialRelationSolver relations = relations(
                relation(0, 0, SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK),
                relation(0, 1, SpatialRelationSolver.ACTOR_BEHIND_BLOCK));

        planner.plan(actors, cache, relations);

        Assert.assertEquals(1, planner.planCount());
        Assert.assertEquals(11, planner.planTargetDrawIndex[0]);
    }

    @Test
    public void mixedConflictingConstraintsFailVisibly() {
        SpatialActorCollector actors = actors(actor(30, 100, 7, 100));
        SpatialBlocksRuntimeCache cache = cache(range(45, 50), range(20, 25));
        SpatialRelationSolver relations = relations(
                relation(0, 0, SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK),
                relation(0, 1, SpatialRelationSolver.ACTOR_BEHIND_BLOCK));

        try {
            planner.plan(actors, cache, relations);
            Assert.fail("Expected conflicting constraints to fail.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("Conflicting"));
        }
    }

    @Test
    public void sameTargetActorOrderingIsDeterministic() {
        SpatialActorCollector actors = actors(
                actor(0, 200, 20, 200),
                actor(0, 100, 10, 100));
        SpatialBlocksRuntimeCache cache = cache(range(20, 30));
        SpatialRelationSolver relations = relations(
                relation(0, 0, SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK),
                relation(1, 0, SpatialRelationSolver.ACTOR_IN_FRONT_OF_BLOCK));

        planner.plan(actors, cache, relations);

        Assert.assertEquals(2, planner.planCount());
        Assert.assertEquals(1, planner.planActorIndex[0]);
        Assert.assertEquals(0, planner.planActorIndex[1]);
        Assert.assertEquals(31, planner.planTargetDrawIndex[0]);
        Assert.assertEquals(31, planner.planTargetDrawIndex[1]);
    }

    @Test
    public void actorWithNoRelationIsNotPlanned() {
        SpatialActorCollector actors = actors(
                actor(0, 100, 7, 100),
                actor(0, 200, 8, 200));
        SpatialBlocksRuntimeCache cache = cache(range(20, 30));
        SpatialRelationSolver relations = relations(relation(0, 0, SpatialRelationSolver.ACTOR_BEHIND_BLOCK));

        planner.plan(actors, cache, relations);

        Assert.assertEquals(1, planner.planCount());
        Assert.assertEquals(0, planner.planActorIndex[0]);
    }

    @Test
    public void plannerDoesNotMutateInputs() {
        SpatialActorCollector actors = actors(actor(10, 100, 7, 100));
        SpatialBlocksRuntimeCache cache = cache(range(20, 30));
        SpatialRelationSolver relations = relations(relation(0, 0, SpatialRelationSolver.ACTOR_BEHIND_BLOCK));
        int[] actorSlots = Arrays.copyOf(actors.actorSlot, actors.actorCount);
        int[] relationTypes = Arrays.copyOf(relations.relationType, relations.relationCount);
        int[] blockStarts = Arrays.copyOf(cache.blockAnchorStartDrawIndex, cache.blockCount);
        int[] blockEnds = Arrays.copyOf(cache.blockAnchorEndDrawIndex, cache.blockCount);

        planner.plan(actors, cache, relations);

        Assert.assertArrayEquals(actorSlots, Arrays.copyOf(actors.actorSlot, actors.actorCount));
        Assert.assertArrayEquals(relationTypes, Arrays.copyOf(relations.relationType, relations.relationCount));
        Assert.assertArrayEquals(blockStarts, Arrays.copyOf(cache.blockAnchorStartDrawIndex, cache.blockCount));
        Assert.assertArrayEquals(blockEnds, Arrays.copyOf(cache.blockAnchorEndDrawIndex, cache.blockCount));
    }

    @Test
    public void plannerProducesOnlyActorSlotPlans() {
        SpatialActorCollector actors = actors(actor(10, 123, 7, 100));
        SpatialBlocksRuntimeCache cache = cache(range(20, 30));
        SpatialRelationSolver relations = relations(relation(0, 0, SpatialRelationSolver.ACTOR_BEHIND_BLOCK));

        planner.plan(actors, cache, relations);

        Assert.assertEquals(1, planner.planCount());
        Assert.assertEquals(123, planner.planActorSlot[0]);
        Assert.assertNotEquals(cache.anchorDrawSlot[0], planner.planActorSlot[0]);
    }

    private static Actor actor(int drawIndex, int slot, int stableOrder, int entityId) {
        return new Actor(drawIndex, slot, stableOrder, entityId);
    }

    private static SpatialActorCollector actors(Actor... specs) {
        SpatialActorCollector actors = new SpatialActorCollector();
        actors.actorCount = specs.length;
        actors.actorSlot = new int[specs.length];
        actors.actorEntityId = new int[specs.length];
        actors.actorDrawIndex = new int[specs.length];
        actors.actorLayerIndex = new int[specs.length];
        actors.actorStableOrder = new int[specs.length];
        for (int i = 0; i < specs.length; i++) {
            Actor actor = specs[i];
            actors.actorSlot[i] = actor.slot;
            actors.actorEntityId[i] = actor.entityId;
            actors.actorDrawIndex[i] = actor.drawIndex;
            actors.actorLayerIndex[i] = 0;
            actors.actorStableOrder[i] = actor.stableOrder;
        }
        return actors;
    }

    private static BlockRange range(int start, int end) {
        return new BlockRange(start, end);
    }

    private static SpatialBlocksRuntimeCache cache(BlockRange... ranges) {
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        for (int i = 0; i < ranges.length; i++) {
            int block = cache.addBlock(1);
            cache.setAnchor(block, 0, 300 + i, ranges[i].start);
            cache.finalizeBlockRange(block);
            cache.blockAnchorStartDrawIndex[block] = ranges[i].start;
            cache.blockAnchorEndDrawIndex[block] = ranges[i].end;
        }
        return cache;
    }

    private static Relation relation(int actor, int block, int type) {
        return new Relation(actor, block, type);
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

    private static final class Actor {
        final int drawIndex;
        final int slot;
        final int stableOrder;
        final int entityId;

        Actor(int drawIndex, int slot, int stableOrder, int entityId) {
            this.drawIndex = drawIndex;
            this.slot = slot;
            this.stableOrder = stableOrder;
            this.entityId = entityId;
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
