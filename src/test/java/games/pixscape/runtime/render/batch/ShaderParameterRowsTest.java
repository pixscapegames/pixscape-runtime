package games.pixscape.runtime.render.batch;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.ShaderFloatParam;
import org.junit.Assert;
import org.junit.Test;

public class ShaderParameterRowsTest {
    @Test
    public void keepsDistinctValuesAndDefaultsAcrossFlushes() {
        Array<ShaderFloatParam> defaults = params(new ShaderFloatParam("gain", 1f));
        ShaderParameterRows rows = new ShaderParameterRows(3);
        rows.setLayout(new ShaderParameterLayout("test", defaults));

        Assert.assertEquals(1, rows.setEntityParameters(params(new ShaderFloatParam("gain", 2f))));
        Assert.assertEquals(2, rows.setEntityParameters(params(new ShaderFloatParam("gain", 3f))));
        Assert.assertEquals(2f, rows.data()[ShaderParameterRows.FLOATS_PER_ROW], 0f);
        Assert.assertEquals(3f, rows.data()[2 * ShaderParameterRows.FLOATS_PER_ROW], 0f);
        Assert.assertTrue(rows.isFull());

        rows.afterFlush();
        Assert.assertEquals(1, rows.currentId());
        Assert.assertEquals(3f, rows.data()[ShaderParameterRows.FLOATS_PER_ROW], 0f);
        Assert.assertEquals(0, rows.setEntityParameters(null));
        Assert.assertEquals(1f, rows.data()[0], 0f);

        rows.setLayout(new ShaderParameterLayout("test", defaults));
        Assert.assertEquals(1, rows.rowCount());
        Assert.assertEquals(0, rows.currentId());
    }

    @Test
    public void rejectsUnknownAndNonFiniteValues() {
        ShaderParameterRows rows = new ShaderParameterRows(3);
        rows.setLayout(new ShaderParameterLayout("test", params(new ShaderFloatParam("gain", 1f))));
        try {
            rows.setEntityParameters(params(new ShaderFloatParam("missing", 2f)));
            Assert.fail("Unknown parameter was accepted");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("missing"));
        }
        try {
            rows.setEntityParameters(params(new ShaderFloatParam("gain", Float.NaN)));
            Assert.fail("NaN was accepted");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("test"));
        }
    }

    @Test
    public void reusesEncodedRowsIncludingDefaultsWhenFull() {
        ShaderParameterRows rows = new ShaderParameterRows(3);
        rows.setLayout(new ShaderParameterLayout("test", params(new ShaderFloatParam("gain", 1f))));
        Assert.assertEquals(0, rows.setEntityParameters(params(new ShaderFloatParam("gain", 1f))));
        Assert.assertEquals(1, rows.rowCount());
        Assert.assertEquals(1, rows.setEntityParameters(params(new ShaderFloatParam("gain", 2f))));
        Assert.assertEquals(1, rows.setEntityParameters(params(new ShaderFloatParam("gain", 2f))));
        Assert.assertEquals(2, rows.setEntityParameters(params(new ShaderFloatParam("gain", 3f))));
        Assert.assertEquals(1, rows.setEntityParameters(params(new ShaderFloatParam("gain", 2f))));
        Assert.assertEquals(-1, rows.setEntityParameters(params(new ShaderFloatParam("gain", 4f))));
        Assert.assertEquals(1, rows.currentId());
        rows.afterFlush();
        Assert.assertEquals(1, rows.setEntityParameters(params(new ShaderFloatParam("gain", 2f))));
        Assert.assertEquals(0, rows.setEntityParameters(params(new ShaderFloatParam("gain", 1f))));
        rows.setLayout(new ShaderParameterLayout("test", params(new ShaderFloatParam("gain", 5f))));
        Assert.assertEquals(0, rows.setEntityParameters(params(new ShaderFloatParam("gain", 5f))));
        Assert.assertEquals(1, rows.setEntityParameters(params(new ShaderFloatParam("gain", 1f))));
    }

    private static Array<ShaderFloatParam> params(ShaderFloatParam value) {
        Array<ShaderFloatParam> result = new Array<>();
        result.add(value);
        return result;
    }
}
