package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.graphics.Color;
import org.junit.Assert;
import org.junit.Test;

/** Binary contract shared by the packed CPU staging array and the normalized GPU color attribute. */
public final class ForwardColorPackingTest {
    @Test public void preservesAbgrBytesIncludingTheNanSafeAlpha() {
        float[] colors={Color.toFloatBits(.25f,.5f,.75f,0f),Color.toFloatBits(.25f,.5f,.75f,.5f),Color.WHITE.toFloatBits()};
        int[][] expected={{63,127,191,0},{63,127,191,126},{255,255,255,254}};
        Assert.assertEquals(26,ForwardLightingRenderer.VERTEX_STRIDE);
        Assert.assertEquals(11,ForwardLightingRenderer.COLOR_OFFSET);
        for(int i=0;i<colors.length;i++){
            float packed=colors[i];
            int bits=Float.floatToRawIntBits(packed);
            Assert.assertEquals(expected[i][0],bits&255);Assert.assertEquals(expected[i][1],(bits>>>8)&255);
            Assert.assertEquals(expected[i][2],(bits>>>16)&255);Assert.assertEquals(expected[i][3],bits>>>24);
            Assert.assertEquals(expected[i][0]/255f,oldRed(packed),0f);Assert.assertEquals(expected[i][1]/255f,oldGreen(packed),0f);
            Assert.assertEquals(expected[i][2]/255f,oldBlue(packed),0f);Assert.assertEquals(expected[i][3]/255f,oldAlpha(packed),0f);
        }
        Assert.assertEquals(254,Float.floatToRawIntBits(Color.WHITE.toFloatBits())>>>24);
    }

    private static float oldRed(float packed){return (Float.floatToRawIntBits(packed)&255)/255f;}
    private static float oldGreen(float packed){return ((Float.floatToRawIntBits(packed)>>>8)&255)/255f;}
    private static float oldBlue(float packed){return ((Float.floatToRawIntBits(packed)>>>16)&255)/255f;}
    private static float oldAlpha(float packed){return ((Float.floatToRawIntBits(packed)>>>24)&255)/255f;}
}
