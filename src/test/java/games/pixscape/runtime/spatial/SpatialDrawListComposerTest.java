package games.pixscape.runtime.spatial;

import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;

public class SpatialDrawListComposerTest {
    private final SpatialDrawListComposer composer = new SpatialDrawListComposer();
    private final SpatialDrawListComposer.SlotClassifier actorSlots = slot -> slot >= 0 && slot < 100;

    @Test
    public void insertsActorBeforeTargetIndex() {
        int[] input = {100, 101, 1, 102};

        int size = composer.compose(input, input.length, plans(plan(1, 1)), actorSlots);

        assertOutput(size, 100, 1, 101, 102);
        assertTileSubsequencePreserved(input, composer.composedSlots, size);
    }

    @Test
    public void insertsActorAfterLastAnchor() {
        int[] input = {100, 1, 101, 102};

        int size = composer.compose(input, input.length, plans(plan(1, input.length)), actorSlots);

        assertOutput(size, 100, 101, 102, 1);
        assertTileSubsequencePreserved(input, composer.composedSlots, size);
    }

    @Test
    public void removesActorFromOriginalPositionBeforeInserting() {
        int[] input = {100, 1, 101, 102};

        int size = composer.compose(input, input.length, plans(plan(1, 3)), actorSlots);

        assertOutput(size, 100, 101, 1, 102);
        Assert.assertEquals(1, countSlot(composer.composedSlots, size, 1));
        assertTileSubsequencePreserved(input, composer.composedSlots, size);
    }

    @Test
    public void multipleActorsSameTargetFollowPlannerOrder() {
        int[] input = {100, 1, 101, 2, 102};

        int size = composer.compose(input, input.length, plans(plan(2, 1), plan(1, 1)), actorSlots);

        assertOutput(size, 100, 2, 1, 101, 102);
        assertTileSubsequencePreserved(input, composer.composedSlots, size);
    }

    @Test
    public void multipleActorsDifferentTargetsUseOriginalTargetIndices() {
        int[] input = {100, 1, 101, 2, 102, 103};

        int size = composer.compose(input, input.length, plans(plan(2, 1), plan(1, 5)), actorSlots);

        assertOutput(size, 100, 2, 101, 102, 1, 103);
        assertTileSubsequencePreserved(input, composer.composedSlots, size);
    }

    @Test
    public void unplannedActorsRemainInOriginalOrder() {
        int[] input = {100, 1, 101, 2, 102};

        int size = composer.compose(input, input.length, plans(plan(2, 1)), actorSlots);

        assertOutput(size, 100, 2, 1, 101, 102);
        assertTileSubsequencePreserved(input, composer.composedSlots, size);
    }

    @Test
    public void tileSubsequenceIsPreservedForMixedList() {
        int[] input = {100, 1, 101, 102, 2, 103, 3, 104, 105};

        int size = composer.compose(input, input.length, plans(plan(3, 1), plan(1, 6), plan(2, 9)), actorSlots);

        Assert.assertArrayEquals(tileSubsequence(input, input.length), tileSubsequence(composer.composedSlots, size));
    }

    @Test
    public void rejectsNonActorPlan() {
        int[] input = {100, 1, 101};

        try {
            composer.compose(input, input.length, plans(plan(100, 1)), actorSlots);
            Assert.fail("Expected non-actor plan to fail.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("actor slot"));
        }
    }

    @Test
    public void rejectsDuplicateActorPlan() {
        int[] input = {100, 1, 101};

        try {
            composer.compose(input, input.length, plans(plan(1, 1), plan(1, 2)), actorSlots);
            Assert.fail("Expected duplicate actor plan to fail.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("Duplicate"));
        }
    }

    @Test
    public void doesNotMutateInputSlots() {
        int[] input = {100, 1, 101, 102};
        int[] before = Arrays.copyOf(input, input.length);

        composer.compose(input, input.length, plans(plan(1, 3)), actorSlots);

        Assert.assertArrayEquals(before, input);
    }

    private void assertOutput(int size, int... expected) {
        Assert.assertEquals(expected.length, size);
        Assert.assertArrayEquals(expected, Arrays.copyOf(composer.composedSlots, size));
    }

    private static int countSlot(int[] slots, int size, int slot) {
        int count = 0;
        for (int i = 0; i < size; i++) {
            if (slots[i] == slot) count++;
        }
        return count;
    }

    private static void assertTileSubsequencePreserved(int[] before, int[] after, int afterSize) {
        Assert.assertArrayEquals(tileSubsequence(before, before.length), tileSubsequence(after, afterSize));
    }

    private static int[] tileSubsequence(int[] slots, int size) {
        int[] out = new int[size];
        int count = 0;
        for (int i = 0; i < size; i++) {
            int slot = slots[i];
            if (slot >= 100) {
                out[count++] = slot;
            }
        }
        return Arrays.copyOf(out, count);
    }

    private static Plan plan(int actorSlot, int targetDrawIndex) {
        return new Plan(actorSlot, targetDrawIndex);
    }

    private static SpatialInsertionPlanner plans(Plan... specs) {
        SpatialInsertionPlanner planner = new SpatialInsertionPlanner();
        planner.planCount = specs.length;
        planner.planActorIndex = new int[specs.length];
        planner.planActorSlot = new int[specs.length];
        planner.planTargetDrawIndex = new int[specs.length];
        planner.planStableOrder = new int[specs.length];
        planner.planOriginalDrawIndex = new int[specs.length];
        for (int i = 0; i < specs.length; i++) {
            planner.planActorIndex[i] = i;
            planner.planActorSlot[i] = specs[i].actorSlot;
            planner.planTargetDrawIndex[i] = specs[i].targetDrawIndex;
            planner.planStableOrder[i] = i;
            planner.planOriginalDrawIndex[i] = i;
        }
        return planner;
    }

    private static final class Plan {
        final int actorSlot;
        final int targetDrawIndex;

        Plan(int actorSlot, int targetDrawIndex) {
            this.actorSlot = actorSlot;
            this.targetDrawIndex = targetDrawIndex;
        }
    }
}
