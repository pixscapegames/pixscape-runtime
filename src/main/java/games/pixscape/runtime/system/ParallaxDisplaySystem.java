package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.helper.ParallaxHelper;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;

/**
 * Computes display-space {@code offsetX/offsetY} for each renderable entity.
 * Authored Physics bodies use scene Physics parallax; other entities use their owning layer
 * parallax.
 * <p>
 * Pipeline:
 * - {@code UpdateWorldGeometrySystemOld} / {@code ECS->SOA} systems fill dynamic ECS render state &amp; {@code LayerStateSOA}
 * - {@code ParallaxDisplaySystem} fills dynamic ECS {@code offsetX/offsetY}
 * - Culling / Gizmo / Picking / RenderSubmit use {@code xN + offsetX}, {@code yN + offsetY}
 */
public final class ParallaxDisplaySystem extends BaseSystem implements ProfiledSystem {

    private final DynamicEntityRenderState renderState;
    private final LayerStateSOA layerState;
    private final OrthographicCamera worldCam;
    private EntitySubscription spriteSubscription;
    private ComponentMapper<PhysicsBodyComponent> mPhysicsBody;

    private final Vector2 tmpOffset = new Vector2();
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public ParallaxDisplaySystem(DynamicEntityRenderState renderState,
                                 LayerStateSOA layerState,
                                 OrthographicCamera worldCam) {
        this.renderState = renderState;
        this.layerState = layerState;
        this.worldCam = worldCam;
    }

    @Override
    protected void initialize() {
        spriteSubscription = world.getAspectSubscriptionManager().get(
                Aspect.all(
                        OrientedBoundsComponent.class,
                        RenderMaterialComponent.class,
                        EntityIndexComponent.class,
                        VisibilityComponent.class
                ).one(
                        TextureRegionComponent.class,
                        PointLightComponent.class,
                        ConeLightComponent.class
                )
        );

        spriteSubscription.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) { /* no-op */ }

            @Override
            public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    int e = data[i];
                    int renderSlot = renderState.renderSlotForEntity(e);
                    if (renderSlot != DynamicEntityRenderState.NO_SLOT) {
                        renderState.offsetX[renderSlot] = 0f;
                        renderState.offsetY[renderSlot] = 0f;
                    }
                }
            }
        });
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.PARALLAX_DISPLAY);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.PARALLAX_DISPLAY, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        if (renderState == null || layerState == null) return;

        final float camX = worldCam.position.x;
        final float camY = worldCam.position.y;

        final int layerCapacity = layerState.parallaxX.length; // or layerState.capacity()

        for (int renderSlot = 0, n = renderState.activeCount; renderSlot < n; renderSlot++) {
            if (!renderState.enabled[renderSlot]) {
                // par safety on reset offset to zero,
                // useful if the entity is recycled
                renderState.offsetX[renderSlot] = 0f;
                renderState.offsetY[renderSlot] = 0f;
                continue;
            }

            int layerIdx = renderState.layerIndex[renderSlot];

            // invalid layer -> no parallax
            if (layerIdx < 0 || layerIdx >= layerCapacity || !layerState.enabled[layerIdx]) {
                renderState.offsetX[renderSlot] = 0f;
                renderState.offsetY[renderSlot] = 0f;
                continue;
            }

            int entityId = renderState.entityIdForSlot(renderSlot);
            boolean physical = entityId >= 0 && mPhysicsBody.has(entityId);
            float px = physical ? layerState.physicsParallaxX : layerState.parallaxX[layerIdx];
            float py = physical ? layerState.physicsParallaxY : layerState.parallaxY[layerIdx];

            if (Float.isNaN(px) && Float.isNaN(py)) {
                renderState.offsetX[renderSlot] = 0f;
                renderState.offsetY[renderSlot] = 0f;
                continue;
            }

            ParallaxHelper.computeParallaxOffset(camX, camY, px, py, tmpOffset);

            renderState.offsetX[renderSlot] = tmpOffset.x;
            renderState.offsetY[renderSlot] = tmpOffset.y;
        }
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
