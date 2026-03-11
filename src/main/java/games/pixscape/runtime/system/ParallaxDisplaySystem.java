package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.helper.ParallaxHelper;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;

/**
 * Computes display-space {@code offsetX/offsetY} for each renderable entity,
 * based on editor camera position and layer parallax.
 *
 * Pipeline:
 *   - {@code UpdateWorldGeometrySystemOld} / {@code ECS->SOA} systems fill {@code RenderStateSOA} &amp; {@code LayerStateSOA}
 *   - {@code ParallaxDisplaySystem} fills {@code RenderStateSOA.offsetX/offsetY}
 *   - Culling / Gizmo / Picking / RenderSubmit use {@code xN + offsetX}, {@code yN + offsetY}
 */
public final class ParallaxDisplaySystem extends BaseSystem {

    private final RenderStateSOA     renderState;
    private final LayerStateSOA      layerState;
    private final OrthographicCamera worldCam;
    private EntitySubscription spriteSubscription;

    private final Vector2 tmpOffset = new Vector2();

    public ParallaxDisplaySystem(RenderStateSOA renderState,
                                 LayerStateSOA layerState,
                                 OrthographicCamera worldCam) {
        this.renderState = renderState;
        this.layerState  = layerState;
        this.worldCam    = worldCam;
    }

    @Override
    protected void initialize() {
        spriteSubscription = world.getAspectSubscriptionManager().get(
                Aspect.all(
                        OrientedBoundsComponent.class,
                        TextureRegionComponent.class,
                        RenderMaterialComponent.class,
                        EntityIndexComponent.class,
                        VisibilityComponent.class
                )
        );

        spriteSubscription.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override public void inserted(IntBag entities) { /* no-op */ }

            @Override public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    int e = data[i];
                    renderState.offsetX[e] = 0f;
                    renderState.offsetY[e] = 0f;
                }
            }
        });
    }

    @Override
    protected void processSystem() {
        if (renderState == null || layerState == null) return;

        final float camX = worldCam.position.x;
        final float camY = worldCam.position.y;

        final int layerCapacity = layerState.parallaxX.length; // ou layerState.capacity()

        IntBag bag = spriteSubscription.getEntities();
        int[] data = bag.getData();

        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            if (!renderState.enabled[e]) {
                // par sécurité on remet l'offset à zéro,
                // utile si l'entité est recyclée
                renderState.offsetX[e] = 0f;
                renderState.offsetY[e] = 0f;
                continue;
            }

            int layerIdx = renderState.layerIndex[e];

            // layer invalide → pas de parallax
            if (layerIdx < 0 || layerIdx >= layerCapacity || !layerState.enabled[layerIdx]) {
                renderState.offsetX[e] = 0f;
                renderState.offsetY[e] = 0f;
                continue;
            }

            // parallax désactivé sur ce layer ?
            if (!layerState.hasParallax(layerIdx)) {
                renderState.offsetX[e] = 0f;
                renderState.offsetY[e] = 0f;
                continue;
            }

            float px = layerState.parallaxX[layerIdx];
            float py = layerState.parallaxY[layerIdx];

            ParallaxHelper.computeParallaxOffset(camX, camY, px, py, tmpOffset);

            renderState.offsetX[e] = tmpOffset.x;
            renderState.offsetY[e] = tmpOffset.y;
        }
    }
}
