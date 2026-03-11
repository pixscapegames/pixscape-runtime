package games.pixscape.runtime.render;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.GL20;
import games.pixscape.runtime.component.RenderMaterialComponent;

public final class Blend {
    public static void apply(BlendMode mode) {
        if (!mode.blending) {
            Gdx.gl.glDisable(GL20.GL_BLEND);
        } else {
            Gdx.gl.glEnable(GL20.GL_BLEND);
            Gdx.gl.glBlendFunc(mode.srcFactor, mode.dstFactor);
        }
    }

    public static void apply(int modeId) {
        apply(BlendMode.fromId(modeId));
    }
}
