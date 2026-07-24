package games.pixscape.runtime.system;

import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.RenderRepeatRangeCalculator;
import org.junit.Assert;
import org.junit.Test;

public class RenderRepeatRangeCalculatorTest {
    private final int[] range = new int[4];

    @Test
    public void noRepeatVisibleBaseEmitsOneQuad() {
        Assert.assertTrue(calculate(0f, 30f, 0f, 30f, 0f, 10f, 0f, 10f, RenderRepeatFlags.NONE, 1024));
        Assert.assertEquals(1, RenderRepeatRangeCalculator.visibleCount(range));
        assertRangeEquals(range, 0, 0, 0, 0);
    }

    @Test
    public void repeatXEmitsExpectedVisibleCopies() {
        Assert.assertTrue(calculate(0f, 30f, 0f, 10f, 0f, 10f, 0f, 10f, RenderRepeatFlags.REPEAT_X, 1024));
        Assert.assertEquals(5, RenderRepeatRangeCalculator.visibleCount(range));
        assertRangeEquals(range, -1, 3, 0, 0);
    }

    @Test
    public void repeatYEmitsExpectedVisibleCopies() {
        Assert.assertTrue(calculate(0f, 10f, 0f, 30f, 0f, 10f, 0f, 10f, RenderRepeatFlags.REPEAT_Y, 1024));
        Assert.assertEquals(5, RenderRepeatRangeCalculator.visibleCount(range));
        assertRangeEquals(range, 0, 0, -1, 3);
    }

    @Test
    public void repeatXYEmitsExpectedGrid() {
        Assert.assertTrue(calculate(0f, 30f, 0f, 30f, 0f, 10f, 0f, 10f, RenderRepeatFlags.ANY, 1024));
        Assert.assertEquals(25, RenderRepeatRangeCalculator.visibleCount(range));
        assertRangeEquals(range, -1, 3, -1, 3);
    }

    @Test
    public void negativeViewportCoordinatesUseFloor() {
        Assert.assertTrue(calculate(-25f, -5f, 0f, 10f, 0f, 10f, 0f, 10f, RenderRepeatFlags.REPEAT_X, 1024));
        Assert.assertEquals(4, RenderRepeatRangeCalculator.visibleCount(range));
        assertRangeEquals(range, -4, -1, 0, 0);
    }

    @Test
    public void baseOutsideViewportStillFindsRepeatedCopy() {
        Assert.assertTrue(calculate(0f, 10f, 0f, 10f, 100f, 110f, 0f, 10f, RenderRepeatFlags.REPEAT_X, 1024));
        Assert.assertEquals(3, RenderRepeatRangeCalculator.visibleCount(range));
        assertRangeEquals(range, -11, -9, 0, 0);
    }

    @Test
    public void guardCapsTooManyCopies() {
        Assert.assertTrue(calculate(0f, 2000f, 0f, 1f, 0f, 1f, 0f, 1f, RenderRepeatFlags.REPEAT_X, 1024));
        Assert.assertEquals(1024, RenderRepeatRangeCalculator.visibleCount(range));
        assertRangeEquals(range, -1, 1022, 0, 0);
    }

    @Test
    public void nonRepeatingAxisMustOverlapViewport() {
        Assert.assertFalse(calculate(0f, 30f, 100f, 130f, 0f, 10f, 0f, 10f, RenderRepeatFlags.REPEAT_X, 1024));
    }

    private boolean calculate(
            float viewportMinX,
            float viewportMaxX,
            float viewportMinY,
            float viewportMaxY,
            float baseMinX,
            float baseMaxX,
            float baseMinY,
            float baseMaxY,
            byte repeatFlags,
            int maxDraws) {
        return RenderRepeatRangeCalculator.calculateVisibleRange(
                viewportMinX,
                viewportMaxX,
                viewportMinY,
                viewportMaxY,
                baseMinX,
                baseMaxX,
                baseMinY,
                baseMaxY,
                repeatFlags,
                maxDraws,
                range
        );
    }

    private static void assertRangeEquals(int[] range, int minIx, int maxIx, int minIy, int maxIy) {
        Assert.assertEquals(minIx, range[0]);
        Assert.assertEquals(maxIx, range[1]);
        Assert.assertEquals(minIy, range[2]);
        Assert.assertEquals(maxIy, range[3]);
    }
}
