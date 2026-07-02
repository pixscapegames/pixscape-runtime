package games.pixscape.runtime.render;

import org.junit.Assert;
import org.junit.Test;

public class TiledMapRenderStateTest {

    @Test
    public void initialCapacityAllocatesVisibleSlots() {
        TiledMapRenderState state = new TiledMapRenderState(4);

        Assert.assertEquals(4, state.getCapacity());
        Assert.assertEquals(0, state.getVisibleSlotCount());
        Assert.assertNotNull(state.getVisibleSlots());
        Assert.assertEquals(4, state.getVisibleSlots().length);
    }

    @Test
    public void clearVisibleSlotsKeepsExistingArray() {
        TiledMapRenderState state = new TiledMapRenderState(4);
        state.addVisibleSlot(12);
        state.addVisibleSlot(13);
        int[] before = state.getVisibleSlots();

        state.clearVisibleSlots();

        Assert.assertSame(before, state.getVisibleSlots());
        Assert.assertEquals(0, state.getVisibleSlotCount());
    }

    @Test
    public void addVisibleSlotPreservesInsertionOrder() {
        TiledMapRenderState state = new TiledMapRenderState(4);

        state.addVisibleSlot(30);
        state.addVisibleSlot(31);
        state.addVisibleSlot(40);

        Assert.assertEquals(3, state.getVisibleSlotCount());
        Assert.assertEquals(30, state.getVisibleSlots()[0]);
        Assert.assertEquals(31, state.getVisibleSlots()[1]);
        Assert.assertEquals(40, state.getVisibleSlots()[2]);
    }

    @Test
    public void ensureCapacityPreservesVisibleSlots() {
        TiledMapRenderState state = new TiledMapRenderState(2);
        state.addVisibleSlot(10);
        state.addVisibleSlot(11);

        state.ensureCapacity(5);

        Assert.assertTrue(state.getCapacity() >= 5);
        Assert.assertEquals(2, state.getVisibleSlotCount());
        Assert.assertEquals(10, state.getVisibleSlots()[0]);
        Assert.assertEquals(11, state.getVisibleSlots()[1]);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidCapacity() {
        new TiledMapRenderState(0);
    }
}
