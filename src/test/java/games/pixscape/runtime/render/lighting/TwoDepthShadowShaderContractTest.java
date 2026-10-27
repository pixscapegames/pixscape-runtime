package games.pixscape.runtime.render.lighting;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/** Locks the shader-level invariant; GL smoke remains the Preview witness. */
public class TwoDepthShadowShaderContractTest {
    @Test public void unmappedForwardOmitsNormalInputsAndSamplingButKeepsSharedLighting(){
        String vertex=ForwardLightingRenderer.vertexSource("",false),fragment=ForwardLightingRenderer.fragmentSource("",false);
        assertFalse(vertex.contains("a_normalUV"));assertFalse(vertex.contains("a_normalBasis"));
        assertFalse(fragment.contains("u_normal"));assertFalse(fragment.contains("normalSlot"));
        assertTrue(vertex.contains("a_curveNormal"));assertTrue(vertex.contains("a_normal"));
        assertTrue(fragment.contains(LightingShaderSources.SHADOW_DEPTH_SELECTION));
        assertTrue(fragment.contains(LightingShaderSources.MACRO_NORMAL));
    }
    @Test public void receiverSelectsSecondDepthForMatchingExclusionIdentity() {
        String source=LightingShaderSources.SHADOW_DEPTH_SELECTION;
        assertTrue(source.contains("exclusionId>.5&&id==exclusionId"));
        assertFalse(source.contains("u_twoDepth"));
        assertTrue(source.contains("texture(u_shadowSecond,uv).r"));
        assertTrue(LightingShaderSources.legacyReceiver("","","").contains("for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)"));
    }
    @Test public void forwardUsesTheSameDepthSelectionReference() {
        String source=ForwardLightingRenderer.fragmentSource("#version 330 core\n");
        assertTrue(source.contains("u_shadowIdentity,u_shadowSecond"));
        assertTrue(source.contains("id==exclusionId"));
        assertFalse(source.contains("u_twoDepth"));
    }
}
