package games.pixscape.runtime.render.lighting;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.gwt.GwtGraphics;
import com.badlogic.gdx.graphics.GL20;
import com.google.gwt.typedarrays.shared.Int32Array;
import java.nio.IntBuffer;

/** GwtGL20 does not expose SCISSOR_BOX through glGetIntegerv. Keep the same GL contract. */
final class LightingScissorState {
    static void read(IntBuffer scratch, int[] bounds) {
        Int32Array values = ((GwtGraphics) Gdx.graphics).getContext().getParameterv(GL20.GL_SCISSOR_BOX);
        for (int i = 0; i < 4; i++) bounds[i] = values.get(i);
    }
}
