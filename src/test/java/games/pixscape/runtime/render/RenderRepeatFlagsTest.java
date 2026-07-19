package games.pixscape.runtime.render;

import org.junit.Assert;
import org.junit.Test;

public class RenderRepeatFlagsTest {

    @Test
    public void sanitizeKeepsOnlyRepeatBits() {
        byte dirty = (byte) (RenderRepeatFlags.REPEAT_X | RenderRepeatFlags.REPEAT_Y | 0x40);

        Assert.assertEquals(RenderRepeatFlags.ANY, RenderRepeatFlags.sanitize(dirty));
        Assert.assertEquals(RenderRepeatFlags.NONE, RenderRepeatFlags.sanitize((byte) 0x40));
    }
}
