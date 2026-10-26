package games.pixscape.runtime.render.lighting;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/** Locks the shader-level invariant; GL smoke remains the Preview witness. */
public class TwoDepthShadowShaderContractTest {
    @Test public void secondPassRejectsEveryFragmentOfTheClosestValidIdentity() {
        String source=LightingRenderer.RECEIVER;
        assertTrue(source.contains("exclusionId>.5&&id==exclusionId"));
        assertTrue(!source.contains("u_twoDepth"));
        assertTrue(source.contains("texture(u_shadowSecond,uv).r"));
        assertTrue(source.contains("for(int x=-1;x<=1;x++)for(int y=-1;y<=1;y++)"));
    }
    @Test public void forwardUsesTheSameDepthSelectionReference() {
        String source=ForwardLightingRenderer.fragmentSource("#version 330 core\n");
        assertTrue(source.contains("u_shadowIdentity,u_shadowSecond"));
        assertTrue(source.contains("id==exclusionId"));
        assertTrue(!source.contains("u_twoDepth"));
    }
}
