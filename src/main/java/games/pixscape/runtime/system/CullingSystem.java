package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.component.AABBComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.helper.RenderSpaceMapper;
import games.pixscape.runtime.render.RenderStateSOA;

public final class CullingSystem extends IteratingSystem {

    private final OrthographicCamera cam;
    private final RenderStateSOA renderState;

    private ComponentMapper<AABBComponent> mAABB;
    private ComponentMapper<VisibilityComponent> mVis;

    private float frMinX;
    private float frMaxX;
    private float frMinY;
    private float frMaxY;

    private boolean cullingEnabled = true;

    public CullingSystem(OrthographicCamera worldCamera, RenderStateSOA renderState) {
        super(Aspect.all(AABBComponent.class, VisibilityComponent.class));
        this.cam = worldCamera;
        this.renderState = renderState;
    }

    @Override
    protected void begin() {
        cam.update(false);

        float halfW = cam.viewportWidth * 0.5f * cam.zoom;
        float halfH = cam.viewportHeight * 0.5f * cam.zoom;

        frMinX = cam.position.x - halfW;
        frMaxX = cam.position.x + halfW;
        frMinY = cam.position.y - halfH;
        frMaxY = cam.position.y + halfH;
    }

    @Override
    protected void process(int e) {
        // If the SOA entry is not active, keep visible=false for safety.
        if (e < 0 || e >= renderState.enabled.length || !renderState.enabled[e]) {
            if (e >= 0 && e < renderState.visible.length) renderState.visible[e] = false;
            return;
        }

        VisibilityComponent v = mVis.get(e);

        // Logic mask
        if (!v.visible) {
            v.inView = false;
            v.culledByFrustum = true;
            renderState.visible[e] = false;
            return;
        }

        // Culling off => logical visibility
        if (!cullingEnabled) {
            v.inView = true;
            v.culledByFrustum = false;
            renderState.visible[e] = true;
            return;
        }

        AABBComponent a = mAABB.get(e);

        float pad = v.padding;
        float minX = a.minX - pad;
        float minY = a.minY - pad;
        float maxX = a.maxX + pad;
        float maxY = a.maxY + pad;

        // offset display
        float ox = RenderSpaceMapper.offsetX(renderState, e);
        float oy = RenderSpaceMapper.offsetY(renderState, e);

        minX += ox;
        maxX += ox;
        minY += oy;
        maxY += oy;

        boolean overlap = !(maxX < frMinX || minX > frMaxX || maxY < frMinY || minY > frMaxY);

        v.inView = overlap;
        v.culledByFrustum = !overlap;

        // SOA visible = logical + frustum
        renderState.visible[e] = overlap;
    }
}

