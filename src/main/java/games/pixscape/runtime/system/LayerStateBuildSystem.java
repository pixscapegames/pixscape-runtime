package games.pixscape.runtime.system;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.LayerStateSOA;

/**
 * Builds LayerStateSOA from layer entities.
 * <p>
 * Pixscape business rules:
 * - TYPE_CLASSIC: parallax is read from LayerParallaxComponent when present; otherwise NaN
 * - TYPE_TILED:   parallax is read from LayerParallaxComponent when present; otherwise NaN
 * - TYPE_LIGHT:   parallax is read from LayerParallaxComponent when present; otherwise NaN
 * - TYPE_PHYSICS: parallax is read from SceneMetaRuntime.physicsParallaxX/Y and is shared by all physics layers
 */
@All(LayerComponent.class)
public final class LayerStateBuildSystem extends IteratingSystem {

    private static final String TAG = "LayerStateBuild";

    private final LayerStateSOA layerState;
    private final SceneMetaRuntime sceneMeta; // can be null

    private ComponentMapper<LayerComponent>        mLayer;
    private ComponentMapper<LayerParallaxComponent> mParallax;
    private ComponentMapper<VisibilityComponent>    mVis;

    public LayerStateBuildSystem(LayerStateSOA layerState, SceneMetaRuntime sceneMeta) {
        this.layerState = layerState;
        this.sceneMeta  = sceneMeta;
    }

    @Override
    protected void begin() {
        layerState.clear(); // resets parallax to NaN, enabled=false, etc.
        if (sceneMeta != null) {
            layerState.physicsParallaxX = sceneMeta.physicsParallaxX;
            layerState.physicsParallaxY = sceneMeta.physicsParallaxY;
        } else {
            layerState.physicsParallaxX = Float.NaN;
            layerState.physicsParallaxY = Float.NaN;
        }
    }

    @Override
    protected void process(int e) {
        final LayerComponent lc = mLayer.get(e);
        final int idx = lc.layerIndex;

        if (idx < 0 || idx >= layerState.capacity()) {
            Gdx.app.error(TAG,
                    "Invalid layerIndex=" + idx + " for entity=" + e +
                            " (capacity=" + layerState.capacity() + "). Layer ignored.");
            return;
        }

        final int type = normalizeType(lc.type, e, idx);

        layerState.touchLayerIndex(idx);
        layerState.entityId[idx] = e;
        layerState.type[idx] = type;

        // enabled
        VisibilityComponent vis = mVis.getSafe(e, null);
        layerState.enabled[idx] = (vis == null) || vis.isVisible();

        // parallax (selon type)
        applyParallax(idx, type, e);

    }

    private int normalizeType(int type, int entityId, int layerIdx) {
        if (type == LayerComponent.TYPE_CLASSIC ||
                type == LayerComponent.TYPE_PHYSICS ||
                type == LayerComponent.TYPE_LIGHT ||
                type == LayerComponent.TYPE_TILED) {
            return type;
        }

        Gdx.app.error(TAG,
                "Invalid layer type=" + type + " for entity=" + entityId +
                        " layerIndex=" + layerIdx + ". Forcing TYPE_CLASSIC.");
        return LayerComponent.TYPE_CLASSIC;
    }

    private void applyParallax(int layerIdx, int type, int entityId) {
        switch (type) {
            case LayerComponent.TYPE_PHYSICS: {
                float px = Float.NaN;
                float py = Float.NaN;

                if (sceneMeta != null) {
                    px = sceneMeta.physicsParallaxX;
                    py = sceneMeta.physicsParallaxY;
                }

                layerState.parallaxX[layerIdx] = px;
                layerState.parallaxY[layerIdx] = py;

                LayerParallaxComponent lp = mParallax.getSafe(entityId, null);
                if (lp != null) {
                    Gdx.app.log(TAG,
                            "Layer entity=" + entityId + " is TYPE_PHYSICS but has LayerParallaxComponent. " +
                                    "Ignored (sceneMeta physics parallax wins).");
                }
                break;
            }

            case LayerComponent.TYPE_CLASSIC:
            case LayerComponent.TYPE_LIGHT:
            case LayerComponent.TYPE_TILED: {
                LayerParallaxComponent lp = mParallax.getSafe(entityId, null);
                if (lp != null) {
                    layerState.parallaxX[layerIdx] = lp.factorX;
                    layerState.parallaxY[layerIdx] = lp.factorY;
                } else {
                    layerState.parallaxX[layerIdx] = Float.NaN;
                    layerState.parallaxY[layerIdx] = Float.NaN;
                }
                break;
            }

            default: {
                layerState.parallaxX[layerIdx] = Float.NaN;
                layerState.parallaxY[layerIdx] = Float.NaN;
                break;
            }
        }
    }
}
