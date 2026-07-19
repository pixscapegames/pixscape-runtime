package games.pixscape.runtime.render;

import org.junit.Assert;
import org.junit.Test;

public class DynamicEntityRenderStateTest {

    @Test
    public void highEntityIdMapsToDenseLowSlot() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(2);

        int slot = state.acquireSlotForEntity(98_234);

        Assert.assertEquals(0, slot);
        Assert.assertEquals(1, state.activeCount);
        Assert.assertEquals(slot, state.renderSlotForEntity(98_234));
        Assert.assertEquals(98_234, state.entityIdForSlot(slot));
        Assert.assertTrue(state.getEntityMappingCapacity() > 98_234);
        Assert.assertEquals(2, state.getRenderCapacity());
    }

    @Test
    public void acquireSameEntityTwiceReturnsSameSlot() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(2);

        int first = state.acquireSlotForEntity(7);
        int second = state.acquireSlotForEntity(7);

        Assert.assertEquals(first, second);
        Assert.assertEquals(1, state.activeCount);
    }

    @Test
    public void releaseUsesSwapRemoveAndUpdatesMovedMapping() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(4);
        int first = state.acquireSlotForEntity(10);
        int second = state.acquireSlotForEntity(20);
        int third = state.acquireSlotForEntity(30);
        state.textureHandle[third] = 77;

        state.releaseSlotForEntity(10);

        Assert.assertEquals(2, state.activeCount);
        Assert.assertEquals(DynamicEntityRenderState.NO_SLOT, state.renderSlotForEntity(10));
        Assert.assertEquals(first, state.renderSlotForEntity(30));
        Assert.assertEquals(30, state.entityIdForSlot(first));
        Assert.assertEquals(77, state.textureHandle[first]);
        Assert.assertEquals(second, state.renderSlotForEntity(20));
    }

    @Test
    public void doubleReleaseIsSafe() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(2);
        state.acquireSlotForEntity(5);

        state.releaseSlotForEntity(5);
        state.releaseSlotForEntity(5);

        Assert.assertEquals(0, state.activeCount);
        Assert.assertEquals(DynamicEntityRenderState.NO_SLOT, state.renderSlotForEntity(5));
    }

    @Test
    public void clearResetsMappingsWithoutReallocatingArrays() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(2);
        state.acquireSlotForEntity(3);
        state.acquireSlotForEntity(50);
        int[] entityMap = state.entityIdToRenderSlot;
        int[] slotMap = state.renderSlotToEntityId;
        int[] textureHandle = state.textureHandle;

        state.clear();

        Assert.assertEquals(0, state.activeCount);
        Assert.assertEquals(DynamicEntityRenderState.NO_SLOT, state.renderSlotForEntity(3));
        Assert.assertEquals(DynamicEntityRenderState.NO_SLOT, state.renderSlotForEntity(50));
        Assert.assertSame(entityMap, state.entityIdToRenderSlot);
        Assert.assertSame(slotMap, state.renderSlotToEntityId);
        Assert.assertSame(textureHandle, state.textureHandle);
    }

    @Test
    public void ensureRenderCapacityPreservesData() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(1);
        int slot = state.acquireSlotForEntity(4);
        state.textureHandle[slot] = 12;

        state.ensureRenderCapacity(3);

        Assert.assertEquals(slot, state.renderSlotForEntity(4));
        Assert.assertEquals(4, state.entityIdForSlot(slot));
        Assert.assertEquals(12, state.textureHandle[slot]);
        Assert.assertTrue(state.getRenderCapacity() >= 3);
    }
}
