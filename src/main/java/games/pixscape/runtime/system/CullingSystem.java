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
    private final RenderStateSOA     renderState;

    private ComponentMapper<AABBComponent>       mAABB;
    private ComponentMapper<VisibilityComponent> mVis;

    private boolean cullingEnabled = true;

    public CullingSystem(OrthographicCamera worldCamera, RenderStateSOA renderState) {
        super(Aspect.all(AABBComponent.class, VisibilityComponent.class));
        this.cam         = worldCamera;
        this.renderState = renderState;
    }

    @Override
    protected void begin() {
        cam.update(false);
    }

    @Override
    protected void process(int e) {
        // Si l’entrée SOA n’est pas active, on garde visible=false par sécurité.
        if (e < 0 || e >= renderState.enabled.length || !renderState.enabled[e]) {
            if (e >= 0 && e < renderState.visible.length) renderState.visible[e] = false;
            return;
        }

        VisibilityComponent v = mVis.get(e);

        // Masquage logique
        if (!v.visible) {
            v.inView = false;
            v.culledByFrustum = true;
            renderState.visible[e] = false;
            return;
        }

        // Culling off => visible logique seulement
        if (!cullingEnabled) {
            v.inView = true;
            v.culledByFrustum = false;
            renderState.visible[e] = true;
            return;
        }

        AABBComponent a = mAABB.get(e);

        float pad  = v.padding;
        float minX = a.minX - pad;
        float minY = a.minY - pad;
        float maxX = a.maxX + pad;
        float maxY = a.maxY + pad;

        // offset display (parallax & co) depuis RenderStateSOA
        float ox = RenderSpaceMapper.offsetX(renderState, e);
        float oy = RenderSpaceMapper.offsetY(renderState, e);

        minX += ox; maxX += ox;
        minY += oy; maxY += oy;

        float frMinX = cam.position.x - cam.viewportWidth  * 0.5f * cam.zoom;
        float frMaxX = cam.position.x + cam.viewportWidth  * 0.5f * cam.zoom;
        float frMinY = cam.position.y - cam.viewportHeight * 0.5f * cam.zoom;
        float frMaxY = cam.position.y + cam.viewportHeight * 0.5f * cam.zoom;

        boolean overlap = !(maxX < frMinX || minX > frMaxX || maxY < frMinY || minY > frMaxY);

        v.inView = overlap;
        v.culledByFrustum = !overlap;

        // IMPORTANT: SOA visible = logique + frustum
        renderState.visible[e] = overlap;
    }
}

