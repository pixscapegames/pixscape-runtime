package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import java.nio.IntBuffer;

/** Native state queries; GWT supersource uses the equivalent WebGL queries. */
final class LightingGlState {
    static void readScissor(IntBuffer scratch, int[] bounds) {
        scratch.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX, scratch);
        for (int i = 0; i < 4; i++) bounds[i] = scratch.get(i);
    }
    static int readDepthWriteMask(IntBuffer scratch) {
        scratch.clear();
        Gdx.gl.glGetIntegerv(GL20.GL_DEPTH_WRITEMASK, scratch);
        return scratch.get(0);
    }
}
