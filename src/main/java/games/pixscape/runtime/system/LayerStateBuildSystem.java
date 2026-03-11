package games.pixscape.runtime.system;

import com.artemis.ComponentMapper;
import com.artemis.annotations.All;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.LayerPostFXComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.LayerStateSOA;

/**
 * Construit LayerStateSOA à partir des entités "layer".
 *
 * Règles business (Pixscape):
 * - TYPE_CLASSIC : parallax via LayerParallaxComponent si présent, sinon NaN
 * - TYPE_PHYSICS : parallax = SceneMetaRuntime.physicsParallaxX/Y (commun à tous)
 * - TYPE_LIGHT   : pas de parallax (NaN forcé)
 */
@All(LayerComponent.class)
public final class LayerStateBuildSystem extends IteratingSystem {

    private static final String TAG = "LayerStateBuild";

    private final LayerStateSOA layerState;
    private final SceneMetaRuntime sceneMeta; // peut être null

    private ComponentMapper<LayerComponent>        mLayer;
    private ComponentMapper<LayerParallaxComponent> mParallax;
    private ComponentMapper<LayerPostFXComponent>   mPostFx;
    private ComponentMapper<VisibilityComponent>    mVis;

    public LayerStateBuildSystem(LayerStateSOA layerState, SceneMetaRuntime sceneMeta) {
        this.layerState = layerState;
        this.sceneMeta  = sceneMeta;
    }

    @Override
    protected void begin() {
        layerState.clear(); // remet parallax à NaN, enabled=false, etc.
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

        // postFX optionnel
        if (type == LayerComponent.TYPE_CLASSIC) {
            LayerPostFXComponent post = mPostFx.getSafe(e, null);
            if (post != null && post.passes != null && post.passes.length > 0) {
                // à toi de définir un vrai id de chain si tu en as un
                layerState.postFxChainId[idx] = post.passes[0].hashCode();
            } else {
                layerState.postFxChainId[idx] = 0;
            }
        } else {
            layerState.postFxChainId[idx] = 0;
        }
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
            case LayerComponent.TYPE_LIGHT -> {
                // règle: jamais de parallax sur layer light
                layerState.parallaxX[layerIdx] = Float.NaN;
                layerState.parallaxY[layerIdx] = Float.NaN;
            }
            case LayerComponent.TYPE_PHYSICS -> {
                // règle: parallax commun à tous les layers physics = scène
                float px = Float.NaN;
                float py = Float.NaN;

                if (sceneMeta != null) {
                    px = sceneMeta.physicsParallaxX;
                    py = sceneMeta.physicsParallaxY;
                }

                layerState.parallaxX[layerIdx] = px;
                layerState.parallaxY[layerIdx] = py;

                // Optionnel: log si un LayerParallaxComponent existe quand même (info dev)
                LayerParallaxComponent lp = mParallax.getSafe(entityId, null);
                if (lp != null) {
                    Gdx.app.log(TAG,
                            "Layer entity=" + entityId + " is TYPE_PHYSICS but has LayerParallaxComponent. " +
                                    "Ignored (sceneMeta physics parallax wins).");
                }
            }
            default -> { // TYPE_CLASSIC
                LayerParallaxComponent lp = mParallax.getSafe(entityId, null);
                if (lp != null) {
                    layerState.parallaxX[layerIdx] = lp.factorX;
                    layerState.parallaxY[layerIdx] = lp.factorY;
                } else {
                    layerState.parallaxX[layerIdx] = Float.NaN;
                    layerState.parallaxY[layerIdx] = Float.NaN;
                }
            }
        }
    }
}
