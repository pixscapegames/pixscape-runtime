package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderStateSOA;

public final class SpatialActorFinalDrawOrderDebugSystem extends BaseSystem {
    private static final String TAG = "SpatialRenderOrder";

    private final RenderStateSOA state;
    private final DrawList drawList;

    private ComponentMapper<PixscapeIdentityComponent> mIdentity;
    private ComponentMapper<SpatialHeightComponent> mSpatialHeight;
    private ComponentMapper<TransformComponent> mTransform;

    private int frameId;

    public SpatialActorFinalDrawOrderDebugSystem(RenderStateSOA state, DrawList drawList) {
        this.state = state;
        this.drawList = drawList;
    }

    @Override
    protected void processSystem() {
        if (!SpatialActorDebugState.ENABLED || state == null || drawList == null || drawList.size <= 0) return;

        frameId++;
        int[] slots = drawList.data();
        int logged = 0;

        for (int i = 0; i < drawList.size && logged < 20; i++) {
            int slot = slots[i];
            if (!isActorSlot(slot)) continue;

            String name = actorName(slot);
            if (!SpatialActorDebugState.nameAllowed(name)) continue;

            int previousActorSlot = previousActorSlot(slots, i);
            int nextActorSlot = nextActorSlot(slots, drawList.size, i);

            String message = "submitSpatialActor frame=" + frameId
                    + " entity=" + state.entityId[slot]
                    + " slot=" + slot
                    + " name=" + name
                    + " layerIndex=" + state.layerIndex[slot]
                    + " footY=" + actorFootY(slot)
                    + " finalDrawIndex=" + i
                    + " previousActorSlot=" + previousActorSlot
                    + " previousActorName=" + actorName(previousActorSlot)
                    + " nextActorSlot=" + nextActorSlot
                    + " nextActorName=" + actorName(nextActorSlot)
                    + " spatialEnabledLayer=" + SpatialActorDebugState.spatialEnabledLayer(slot)
                    + " hasSpatialHeight=" + hasSpatialHeight(slot)
                    + " originalDrawIndex=" + SpatialActorDebugState.originalDrawIndex(slot)
                    + " finalAfterSpatialIndex=" + i;

            log(message);
            logged++;
        }
    }

    private void log(String message) {
        if (Gdx.app != null) {
            Gdx.app.log(TAG, message);
        }
        System.out.println(TAG + ": " + message);
    }

    private boolean isActorSlot(int slot) {
        if (slot < 0 || slot >= state.getCapacity()) return false;
        int entity = state.entityId[slot];
        return entity >= 0
                && entity < state.getCapacity()
                && mSpatialHeight != null
                && mSpatialHeight.has(entity);
    }

    private boolean hasSpatialHeight(int slot) {
        int entity = slot >= 0 && slot < state.getCapacity() ? state.entityId[slot] : -1;
        return entity >= 0 && mSpatialHeight != null && mSpatialHeight.has(entity);
    }

    private float actorFootY(int slot) {
        int entity = slot >= 0 && slot < state.getCapacity() ? state.entityId[slot] : -1;
        if (entity < 0 || mTransform == null) return 0f;
        TransformComponent transform = mTransform.getSafe(entity, null);
        if (transform == null) return 0f;
        return transform.y - transform.originY * transform.scaleY;
    }

    private String actorName(int slot) {
        int entity = slot >= 0 && slot < state.getCapacity() ? state.entityId[slot] : -1;
        if (entity < 0 || mIdentity == null) return "none";
        PixscapeIdentityComponent identity = mIdentity.getSafe(entity, null);
        return identity != null && identity.name != null ? identity.name : "unnamed";
    }

    private int previousActorSlot(int[] slots, int index) {
        for (int i = index - 1; i >= 0; i--) {
            if (isActorSlot(slots[i])) return slots[i];
        }
        return -1;
    }

    private int nextActorSlot(int[] slots, int size, int index) {
        for (int i = index + 1; i < size; i++) {
            if (isActorSlot(slots[i])) return slots[i];
        }
        return -1;
    }
}
