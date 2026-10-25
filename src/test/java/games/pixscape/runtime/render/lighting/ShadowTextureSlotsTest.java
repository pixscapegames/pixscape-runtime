package games.pixscape.runtime.render.lighting;
import org.junit.Test;
import static org.junit.Assert.*;

public class ShadowTextureSlotsTest {
    @Test public void reservesIdentityUnitAndFallsBackToFour(){
        assertEquals(4,LightingRenderer.shadowColorSlotsFor(0));
        assertEquals(4,LightingRenderer.shadowColorSlotsFor(8));
        assertEquals(8,LightingRenderer.shadowColorSlotsFor(9));
        assertEquals(8,LightingRenderer.shadowColorSlotsFor(16));
    }
    @Test public void emitsStaticSamplerSelectionForBothShaderVariants(){
        for(int slots:new int[]{4,8}){
            String source=LightingRenderer.shadowImageSource(slots);
            for(int i=0;i<slots;i++)assertTrue(source.contains("texture(u_tex"+i+",uv)"));
            assertFalse(source.contains("u_tex"+slots));
            assertFalse(source.contains("sampler2D["));
        }
    }
}
