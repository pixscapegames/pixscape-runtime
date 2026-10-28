package games.pixscape.runtime.render;

import games.pixscape.runtime.component.ShaderParamsComponent;
import org.junit.Assert;
import org.junit.Test;

public class AmbientContributionOptimizationTest {
    @Test public void onlyProvenIdentityOriginalsMayBeSkipped(){
        FrameRenderQueue q=new FrameRenderQueue();q.addQuad(1,0,4,0,0,0,0,0,0,0,1,1,1,1,0,0,0,1,1,0,(byte)0,FrameRenderQueue.SOURCE_VFX,0,-1);
        q.ignoreAmbientLighting[0]=true;Assert.assertTrue(AmbientContributionRenderer.isIdentityOriginal(q,0));
        q.blend[0]=1;Assert.assertFalse(AmbientContributionRenderer.isIdentityOriginal(q,0));q.blend[0]=4;
        q.sourceDomain[0]=FrameRenderQueue.SOURCE_ECS;Assert.assertFalse(AmbientContributionRenderer.isIdentityOriginal(q,0));q.sourceDomain[0]=FrameRenderQueue.SOURCE_VFX;
        q.repeatFlags[0]=1;Assert.assertFalse(AmbientContributionRenderer.isIdentityOriginal(q,0));q.repeatFlags[0]=0;
        q.shaderParameters[0]=new ShaderParamsComponent();Assert.assertFalse(AmbientContributionRenderer.isIdentityOriginal(q,0));q.shaderParameters[0]=null;
        q.lighting.diagnostic=1;Assert.assertFalse(AmbientContributionRenderer.isIdentityOriginal(q,0));q.lighting.diagnostic=0;
        q.ignoreAmbientLighting[0]=false;Assert.assertFalse(AmbientContributionRenderer.isIdentityOriginal(q,0));
    }
    @Test public void unknownCoverageAnywhereRetainsTheFullOrderedPass(){
        FrameRenderQueue q=new FrameRenderQueue();q.ensureCapacity(4);q.size=4;
        for(int i=0;i<4;i++)q.ambientMaterial[i]=1;q.ignoreAmbientLighting[2]=true;
        Assert.assertEquals(2,AmbientContributionRenderer.firstContribution(q));
        q.ambientMaterial[3]=2;Assert.assertEquals(0,AmbientContributionRenderer.firstContribution(q));
        q.ambientMaterial[3]=1;q.ambientMaterial[0]=2;Assert.assertEquals(0,AmbientContributionRenderer.firstContribution(q));
    }
}
