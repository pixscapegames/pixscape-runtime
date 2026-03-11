package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.CameraSettingsComponent;
import games.pixscape.runtime.render.CameraStateSOA;

/**
 * Synchronise les entités caméra vers le SOA runtime (CameraStateSOA).
 *
 * IMPORTANT :
 *  - Si aucune caméra logique n'existe encore dans la scène (projets anciens),
 *    on laisse la caméra 0 "par défaut" telle qu'initialisée dans WorldCanvas.
 *    Cela évite de casser complètement le rendu (maxIndex = -1).
 */
public final class RenderCameraSyncSystem extends BaseSystem {

    private final CameraStateSOA cameraState;
    private final boolean advancedRendering;

    private ComponentMapper<CameraSettingsComponent> mSettings;

    private EntitySubscription cameraSub;

    public RenderCameraSyncSystem(CameraStateSOA cameraState, boolean advancedRendering) {
        this.cameraState = cameraState;
        this.advancedRendering = advancedRendering;
    }

    @Override
    protected void initialize() {
        cameraSub = world.getAspectSubscriptionManager()
                .get(Aspect.all(CameraSettingsComponent.class));
    }

    @Override
    protected void processSystem() {
        if (!advancedRendering) {
            // Mode simple : on ne touche pas au cameraState
            return;
        }

        if (cameraSub == null) return;

        IntBag bag = cameraSub.getEntities();
        int size = bag.size();

        if (size == 0) {
            return;
        }

        int capacity = CameraStateSOA.capacity();
        int max = 0;
        int[] data = bag.getData();
        boolean hasActive = false;

        for (int i = 0; i < size && i < capacity; i++) {
            int e = data[i];

            CameraSettingsComponent cs = mSettings.get(e);

            float zoom      = 1f;
            boolean useOff  = false;
            int layerMask   = -1;
            boolean enabled = true;

            if (cs != null) {
                zoom      = cs.zoom;
                useOff    = cs.useOffscreen;
                layerMask = cs.layerMask;
                enabled   = cs.active;
            }

            if (enabled) {
                hasActive = true;
            }

            cameraState.zoom[i]         = zoom;
            cameraState.useOffscreen[i] = useOff;
            cameraState.layerMask[i]    = layerMask;
            cameraState.enabled[i]      = enabled;

            // 👉 on lie la caméra slot i à l'entité e
            cameraState.entityId[i]     = e;

            max = i + 1;
        }

        if (!hasActive && max > 0) {
            cameraState.enabled[0] = true;
        }

        // Slots au-delà désactivés proprement
        for (int i = max; i < capacity; i++) {
            cameraState.enabled[i]      = false;
            cameraState.useOffscreen[i] = false;
            cameraState.layerMask[i]    = -1;
            cameraState.entityId[i]     = -1;
        }

        cameraState.maxIndex = max - 1;
    }

}
