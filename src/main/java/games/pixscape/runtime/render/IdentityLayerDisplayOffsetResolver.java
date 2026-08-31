package games.pixscape.runtime.render;

import com.badlogic.gdx.math.Vector2;

/** Identity display-space authority for authored-coordinate Worlds. */
public final class IdentityLayerDisplayOffsetResolver implements LayerDisplayOffsetResolver {

    @Override
    public void resolveLayer(int layerIndex, Vector2 out) {
        out.set(0f, 0f);
    }

    @Override
    public void resolvePhysics(Vector2 out) {
        out.set(0f, 0f);
    }
}
