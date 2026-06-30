package games.pixscape.runtime.render;

import org.junit.Assert;
import org.junit.Test;

public class RenderStateSOATest {

    @Test
    public void disableClearsTextureHandleAndSortKey() {
        RenderStateSOA state = new RenderStateSOA(8);
        int slot = 3;

        state.enabled[slot] = true;
        state.visible[slot] = true;
        state.kind[slot] = RenderStateSOA.KIND_SPRITE;
        state.textureHandle[slot] = 42;
        state.sortKey[slot] = 1234L;
        state.repeatFlags[slot] = RenderRepeatFlags.REPEAT_X;

        state.disable(slot);

        Assert.assertFalse(state.enabled[slot]);
        Assert.assertFalse(state.visible[slot]);
        Assert.assertEquals(RenderStateSOA.KIND_NONE, state.kind[slot]);
        Assert.assertEquals(0, state.textureHandle[slot]);
        Assert.assertEquals(0L, state.sortKey[slot]);
        Assert.assertEquals(RenderRepeatFlags.NONE, state.repeatFlags[slot]);
    }

    @Test
    public void repeatFlagsAllocatedAndCleared() {
        RenderStateSOA state = new RenderStateSOA(4);

        Assert.assertNotNull(state.repeatFlags);
        Assert.assertEquals(4, state.repeatFlags.length);
        for (int i = 0; i < state.repeatFlags.length; i++) {
            Assert.assertEquals(RenderRepeatFlags.NONE, state.repeatFlags[i]);
        }

        state.repeatFlags[1] = RenderRepeatFlags.REPEAT_Y;
        state.clearAll();

        Assert.assertEquals(RenderRepeatFlags.NONE, state.repeatFlags[1]);
    }
}
