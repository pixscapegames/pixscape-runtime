package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.annotations.SkipWire;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.hierarchy.GameObjectCompositionState;

public final class CullingSystem extends BaseSystem implements ProfiledSystem {

    private final OrthographicCamera cam;
    private final DynamicEntityRenderState renderState;

    private ComponentMapper<AABBComponent> mAABB;
    private ComponentMapper<VisibilityComponent> mVis;
    @SkipWire
    private GameObjectCompositionSystem compositionSystem;
    @SkipWire
    private GameObjectHierarchySystem hierarchySystem;

    private float frMinX;
    private float frMaxX;
    private float frMinY;
    private float frMaxY;

    private boolean cullingEnabled = true;
    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private boolean profiling;
    private long profileStartNs;

    public CullingSystem(OrthographicCamera worldCamera, DynamicEntityRenderState renderState) {
        this.cam = worldCamera;
        this.renderState = renderState;
    }

    @Override
    protected void initialize() {
        compositionSystem = world.getSystem(GameObjectCompositionSystem.class);
        hierarchySystem = world.getSystem(GameObjectHierarchySystem.class);
    }

    @Override
    protected void begin() {
        profiling = profiler.enabled();
        if (profiling) {
            profileStartNs = profiler.begin(SystemProfilePhases.CULLING);
        }
        cam.update(false);

        float halfW = cam.viewportWidth * 0.5f * cam.zoom;
        float halfH = cam.viewportHeight * 0.5f * cam.zoom;

        frMinX = cam.position.x - halfW;
        frMaxX = cam.position.x + halfW;
        frMinY = cam.position.y - halfH;
        frMaxY = cam.position.y + halfH;
    }

    @Override
    protected void processSystem() {
        for (int renderSlot = 0; renderSlot < renderState.activeCount; renderSlot++) {
            processRenderSlot(renderSlot);
        }
    }

    private void processRenderSlot(int renderSlot) {
        int e = renderState.renderSlotToEntityId[renderSlot];
        if (e < 0 || !renderState.enabled[renderSlot]) {
            renderState.visible[renderSlot] = false;
            return;
        }

        VisibilityComponent v = mVis.getSafe(e, null);
        if (v == null) {
            renderState.visible[renderSlot] = false;
            return;
        }

        GameObjectCompositionState composition = compositionSystem != null
                ? compositionSystem.state() : null;
        if (composition != null && hierarchySystem != null
                && e < hierarchySystem.topology().getEntityCapacity()
                && hierarchySystem.topology().parented[e]
                && e < composition.getEntityCapacity()
                && !composition.hierarchyVisible[e]) {
            v.inView = false;
            v.culledByFrustum = true;
            renderState.visible[renderSlot] = false;
            return;
        }

        // Logic mask
        if (!v.visible) {
            v.inView = false;
            v.culledByFrustum = true;
            renderState.visible[renderSlot] = false;
            return;
        }

        // Culling off => logical visibility
        if (!cullingEnabled) {
            v.inView = true;
            v.culledByFrustum = false;
            renderState.visible[renderSlot] = true;
            return;
        }

        AABBComponent a = mAABB.getSafe(e, null);
        if (a == null) {
            renderState.visible[renderSlot] = false;
            return;
        }

        float pad = v.padding;
        float minX = a.minX - pad;
        float minY = a.minY - pad;
        float maxX = a.maxX + pad;
        float maxY = a.maxY + pad;

        // offset display
        float ox = renderState.offsetX[renderSlot];
        float oy = renderState.offsetY[renderSlot];

        minX += ox;
        maxX += ox;
        minY += oy;
        maxY += oy;

        byte repeatFlags = renderState.repeatFlags[renderSlot];
        boolean repeatX = (repeatFlags & RenderRepeatFlags.REPEAT_X) != 0;
        boolean repeatY = (repeatFlags & RenderRepeatFlags.REPEAT_Y) != 0;

        boolean overlapX = repeatX || !(maxX < frMinX || minX > frMaxX);
        boolean overlapY = repeatY || !(maxY < frMinY || minY > frMaxY);
        boolean overlap = overlapX && overlapY;

        v.inView = overlap;
        v.culledByFrustum = !overlap;

        // SOA visible = logical + frustum
        renderState.visible[renderSlot] = overlap;
    }

    @Override
    protected void end() {
        if (profiling) {
            profiler.end(SystemProfilePhases.CULLING, profileStartNs);
            profiling = false;
        }
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}

