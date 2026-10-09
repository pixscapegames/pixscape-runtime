package games.pixscape.runtime.render;

import com.badlogic.gdx.graphics.GL20;
import org.junit.*;

public class LightCompositionPassTest {
    @Test public void additiveImagesAreDistinctFromLightsAndAmbientNeverEntersTheMask() {
        Assert.assertFalse(LightCompositionPass.skip(LightCompositionPass.ORIGINAL, false, BlendMode.ADDITIVE.id));
        Assert.assertTrue(LightCompositionPass.skip(LightCompositionPass.FIELD, false, BlendMode.ADDITIVE.id));
        Assert.assertTrue(LightCompositionPass.skip(LightCompositionPass.ORIGINAL, true, BlendMode.ADDITIVE.id));
        Assert.assertEquals(GL20.GL_ONE_MINUS_SRC_ALPHA, LightCompositionPass.destination(LightCompositionPass.FIELD, false, BlendMode.PREMULT_ALPHA));
        Assert.assertEquals(GL20.GL_ZERO, LightCompositionPass.destination(LightCompositionPass.FIELD, false, BlendMode.CUTOUT));
        Assert.assertEquals(GL20.GL_ONE, LightCompositionPass.destination(LightCompositionPass.FIELD, true, BlendMode.ADDITIVE));
    }
    @Test public void lightIdentitySurvivesGrowthCompactionAndReuse() {
        DynamicEntityRenderState state = new DynamicEntityRenderState(2);
        int a=state.acquireSlotForEntity(10), b=state.acquireSlotForEntity(11);
        state.light[b]=1; state.releaseSlotForEntity(10);
        Assert.assertEquals(1,state.light[a]);
        int c=state.acquireSlotForEntity(12); Assert.assertEquals(0,state.light[c]);
        FrameRenderQueue queue=new FrameRenderQueue(1);
        queue.light[0]=1; queue.size=1; queue.ensureCapacity(32);
        Assert.assertEquals(1,queue.light[0]);
    }
    @Test(expected=IllegalArgumentException.class) public void multiplicativeDecorIsRejectedExplicitly() {
        LightCompositionPass.validate(false,BlendMode.MULTIPLY.id);
    }
    @Test(expected=IllegalArgumentException.class) public void nonAdditiveLightsAreRejectedExplicitly() {
        LightCompositionPass.validate(true,BlendMode.ALPHA.id);
    }
}
