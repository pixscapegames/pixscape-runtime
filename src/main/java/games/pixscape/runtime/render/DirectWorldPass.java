package games.pixscape.runtime.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.utils.BufferUtils;
import java.nio.IntBuffer;

/** Draws ordinary world materials into the caller's target without an intermediate target.
 * Viewport, scissor and framebuffer are never changed. Restore only the state this pass uses. */
public final class DirectWorldPass {
    private final IntBuffer scratch = BufferUtils.newIntBuffer(1);
    private int source, destination, sourceAlpha, destinationAlpha, equation, equationAlpha;
    private boolean blend, depth, cull, depthWrite;

    public void begin() {
        source = read(GL20.GL_BLEND_SRC_RGB); destination = read(GL20.GL_BLEND_DST_RGB);
        sourceAlpha = read(GL20.GL_BLEND_SRC_ALPHA); destinationAlpha = read(GL20.GL_BLEND_DST_ALPHA);
        equation = read(GL20.GL_BLEND_EQUATION_RGB); equationAlpha = read(GL20.GL_BLEND_EQUATION_ALPHA);
        blend = Gdx.gl.glIsEnabled(GL20.GL_BLEND);
        depth = Gdx.gl.glIsEnabled(GL20.GL_DEPTH_TEST);
        depthWrite = games.pixscape.runtime.render.lighting.LightingGlState.readDepthWriteMask(scratch) != 0;
        cull = Gdx.gl.glIsEnabled(GL20.GL_CULL_FACE);
        Gdx.gl.glDisable(GL20.GL_DEPTH_TEST);
        Gdx.gl.glDisable(GL20.GL_CULL_FACE);
        Gdx.gl.glBlendEquationSeparate(GL20.GL_FUNC_ADD, GL20.GL_FUNC_ADD);
    }

    public void end() {
        Gdx.gl.glBlendFuncSeparate(source, destination, sourceAlpha, destinationAlpha);
        Gdx.gl.glBlendEquationSeparate(equation, equationAlpha);
        Gdx.gl.glDepthMask(depthWrite);
        enable(GL20.GL_BLEND, blend); enable(GL20.GL_DEPTH_TEST, depth); enable(GL20.GL_CULL_FACE, cull);
    }

    private int read(int name) {
        scratch.clear(); Gdx.gl.glGetIntegerv(name, scratch); return scratch.get(0);
    }

    private static void enable(int capability, boolean enabled) {
        if (enabled) Gdx.gl.glEnable(capability); else Gdx.gl.glDisable(capability);
    }
}
