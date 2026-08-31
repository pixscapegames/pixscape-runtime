package games.pixscape.runtime.render;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.helper.ParallaxHelper;

/** Runtime display-space authority derived from Layer and Physics parallax. */
public final class LayerParallaxDisplayOffsetResolver implements LayerDisplayOffsetResolver {

    private final LayerStateSOA layerState;
    private final OrthographicCamera camera;

    public LayerParallaxDisplayOffsetResolver(LayerStateSOA layerState,
                                              OrthographicCamera camera) {
        this.layerState = layerState;
        this.camera = camera;
    }

    @Override
    public void resolveLayer(int layerIndex, Vector2 out) {
        out.set(0f, 0f);
        if (layerState == null || camera == null || layerState.enabled == null
                || layerIndex < 0 || layerIndex >= layerState.enabled.length
                || !layerState.enabled[layerIndex]) {
            return;
        }

        ParallaxHelper.computeParallaxOffset(
                camera.position.x,
                camera.position.y,
                safeParallax(layerState.parallaxX[layerIndex]),
                safeParallax(layerState.parallaxY[layerIndex]),
                out
        );
    }

    @Override
    public void resolvePhysics(Vector2 out) {
        out.set(0f, 0f);
        if (layerState == null || camera == null
                || (Float.isNaN(layerState.physicsParallaxX)
                && Float.isNaN(layerState.physicsParallaxY))) {
            return;
        }

        ParallaxHelper.computeParallaxOffset(
                camera.position.x,
                camera.position.y,
                safeParallax(layerState.physicsParallaxX),
                safeParallax(layerState.physicsParallaxY),
                out
        );
    }

    private static float safeParallax(float value) {
        return Float.isNaN(value) ? 1f : value;
    }
}
