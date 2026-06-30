package games.pixscape.runtime.component;

import org.junit.Assert;
import org.junit.Test;

public class RenderRepeatComponentTest {

    @Test
    public void resetClearsRepeatAxes() {
        RenderRepeatComponent repeat = new RenderRepeatComponent();
        repeat.repeatX = true;
        repeat.repeatY = true;

        repeat.reset();

        Assert.assertFalse(repeat.repeatX);
        Assert.assertFalse(repeat.repeatY);
    }
}
