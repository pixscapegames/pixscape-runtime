package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderStateSOA;

public final class SpatialRenderOrderSystem extends BaseSystem {
    private static final String TAG = "SpatialRenderOrder";
    private static final boolean DEBUG_SPATIAL_ACTORS =
            Boolean.getBoolean("pixscape.debugSpatialActors");
    private static final int DEBUG_LINES_PER_FRAME = 20;

    private final RenderStateSOA state;
    private final DrawList drawList;

    private ComponentMapper<LayerComponent> mLayer;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<SpatialHeightComponent> mSpatialHeight;

    private EntitySubscription layersSub;

    private boolean[] spatialLayers = new boolean[0];
    private int[] actorSlots = new int[0];
    private int[] actorScratchSlots = new int[0];
    private int[] actorPositions = new int[0];
    private float[] actorFootY = new float[0];
    private float[] actorScratchFootY = new float[0];
    private int[] debugActorOriginalPositions = new int[0];
    private int[] debugActorOriginalFrame = new int[0];
    private int debugActorFrameId = 1;
    private int debugActorLinesThisFrame;

    public SpatialRenderOrderSystem(RenderStateSOA state, DrawList drawList) {
        this.state = state;
        this.drawList = drawList;
    }

    @Override
    protected void initialize() {
        layersSub = world.getAspectSubscriptionManager().get(Aspect.all(LayerComponent.class));
    }

    @Override
    protected void processSystem() {
        if (state == null || drawList == null || drawList.size <= 1) return;

        debugActorLinesThisFrame = 0;
        rebuildSpatialLayers();
        if (SpatialActorDebugState.ENABLED) {
            SpatialActorDebugState.beginFrame(state.getCapacity());
        }

        int[] data = drawList.data();
        int start = 0;
        while (start < drawList.size) {
            int layer = state.layerIndex[data[start]];
            int end = start + 1;
            while (end < drawList.size && state.layerIndex[data[end]] == layer) {
                end++;
            }

            if (isSpatialLayer(layer)) {
                if (DEBUG_SPATIAL_ACTORS) captureDebugActorOriginalPositions(data, start, end);
                sortSpatialActorsInLayerRun(data, start, end);
                if (DEBUG_SPATIAL_ACTORS) logSpatialActorFinalRun(data, start, end);
            }

            start = end;
        }
    }

    private void rebuildSpatialLayers() {
        for (int i = 0, n = spatialLayers.length; i < n; i++) {
            spatialLayers[i] = false;
        }

        if (layersSub == null) return;

        IntBag layers = layersSub.getEntities();
        int[] data = layers.getData();
        for (int i = 0, n = layers.size(); i < n; i++) {
            int entity = data[i];
            LayerComponent layer = mLayer.getSafe(entity, null);
            if (layer == null || layer.layerIndex < 0 || !layer.spatialEnabled) continue;
            if (layer.type == LayerComponent.TYPE_TILED) continue;

            ensureSpatialLayerCapacity(layer.layerIndex + 1);
            spatialLayers[layer.layerIndex] = true;
        }
    }

    private int sortSpatialActorsInLayerRun(int[] data, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            int slot = data[i];
            if (!isSpatialActorSlot(slot)) continue;

            ensureActorCapacity(count + 1);
            actorSlots[count] = slot;
            actorPositions[count] = i;
            actorFootY[count] = actorFootY(state.entityId[slot]);
            count++;
        }

        if (count <= 1) return count;

        stableSortActorsByFootY(count);

        for (int i = 0; i < count; i++) {
            data[actorPositions[i]] = actorSlots[i];
        }

        return count;
    }

    private void stableSortActorsByFootY(int count) {
        for (int width = 1; width < count; width <<= 1) {
            for (int left = 0; left < count; left += width << 1) {
                int mid = Math.min(left + width, count);
                int right = Math.min(left + (width << 1), count);
                mergeActorRuns(left, mid, right);
            }

            System.arraycopy(actorScratchSlots, 0, actorSlots, 0, count);
            System.arraycopy(actorScratchFootY, 0, actorFootY, 0, count);
        }
    }

    private void mergeActorRuns(int left, int mid, int right) {
        int a = left;
        int b = mid;
        int write = left;

        while (a < mid && b < right) {
            if (Float.compare(actorFootY[a], actorFootY[b]) >= 0) {
                actorScratchSlots[write] = actorSlots[a];
                actorScratchFootY[write] = actorFootY[a];
                a++;
            } else {
                actorScratchSlots[write] = actorSlots[b];
                actorScratchFootY[write] = actorFootY[b];
                b++;
            }
            write++;
        }

        while (a < mid) {
            actorScratchSlots[write] = actorSlots[a];
            actorScratchFootY[write] = actorFootY[a];
            a++;
            write++;
        }

        while (b < right) {
            actorScratchSlots[write] = actorSlots[b];
            actorScratchFootY[write] = actorFootY[b];
            b++;
            write++;
        }
    }

    private boolean isSpatialActorSlot(int slot) {
        if (!isRenderableSlot(slot)) return false;

        int entity = state.entityId[slot];
        if (entity < 0 || entity >= state.getCapacity()) return false;
        if (!world.getEntityManager().isActive(entity)) return false;

        EntityIndexComponent index = mEntityIndex.getSafe(entity, null);
        if (index == null) return false;
        if (index.layerIndex != state.layerIndex[slot]) return false;
        if (!isSpatialLayer(index.layerIndex)) return false;

        SpatialHeightComponent height = mSpatialHeight.getSafe(entity, null);
        if (height == null || height.height <= 0f) return false;

        return mTransform.has(entity);
    }

    private boolean isRenderableSlot(int slot) {
        return slot >= 0
                && slot < state.getCapacity()
                && state.kind[slot] == RenderStateSOA.KIND_SPRITE
                && state.enabled[slot]
                && state.visible[slot]
                && state.textureHandle[slot] != 0;
    }

    private float actorFootY(int entity) {
        TransformComponent transform = mTransform.getSafe(entity, null);
        if (transform == null) return 0f;
        return transform.y - transform.originY * transform.scaleY;
    }

    private boolean isSpatialLayer(int layerIndex) {
        return layerIndex >= 0 && layerIndex < spatialLayers.length && spatialLayers[layerIndex];
    }

    private void ensureSpatialLayerCapacity(int required) {
        if (required <= spatialLayers.length) return;

        int next = Math.max(8, spatialLayers.length);
        while (required > next) next <<= 1;

        boolean[] expanded = new boolean[next];
        System.arraycopy(spatialLayers, 0, expanded, 0, spatialLayers.length);
        spatialLayers = expanded;
    }

    private void ensureActorCapacity(int required) {
        if (required <= actorSlots.length) return;

        int next = Math.max(8, actorSlots.length);
        while (required > next) next <<= 1;

        int[] expandedSlots = new int[next];
        System.arraycopy(actorSlots, 0, expandedSlots, 0, actorSlots.length);
        actorSlots = expandedSlots;

        int[] expandedScratchSlots = new int[next];
        System.arraycopy(actorScratchSlots, 0, expandedScratchSlots, 0, actorScratchSlots.length);
        actorScratchSlots = expandedScratchSlots;

        int[] expandedPositions = new int[next];
        System.arraycopy(actorPositions, 0, expandedPositions, 0, actorPositions.length);
        actorPositions = expandedPositions;

        float[] expandedFootY = new float[next];
        System.arraycopy(actorFootY, 0, expandedFootY, 0, actorFootY.length);
        actorFootY = expandedFootY;

        float[] expandedScratchFootY = new float[next];
        System.arraycopy(actorScratchFootY, 0, expandedScratchFootY, 0, actorScratchFootY.length);
        actorScratchFootY = expandedScratchFootY;
    }

    private void captureDebugActorOriginalPositions(int[] data, int start, int end) {
        ensureDebugActorPositionCapacity(state.getCapacity());
        debugActorFrameId++;
        if (debugActorFrameId == 0) {
            for (int i = 0, n = debugActorOriginalFrame.length; i < n; i++) {
                debugActorOriginalFrame[i] = 0;
            }
            debugActorFrameId = 1;
        }

        for (int i = start; i < end; i++) {
            int slot = data[i];
            if (slot < 0 || slot >= debugActorOriginalPositions.length) continue;
            debugActorOriginalPositions[slot] = i;
            debugActorOriginalFrame[slot] = debugActorFrameId;
            if (isSpatialActorSlot(slot)) {
                SpatialActorDebugState.recordOriginal(slot, i, isSpatialLayer(state.layerIndex[slot]));
            }
        }
    }

    private void ensureDebugActorPositionCapacity(int required) {
        if (required <= debugActorOriginalPositions.length) return;

        int next = Math.max(8, debugActorOriginalPositions.length);
        while (required > next) next <<= 1;

        int[] expandedPositions = new int[next];
        System.arraycopy(debugActorOriginalPositions, 0, expandedPositions, 0, debugActorOriginalPositions.length);
        debugActorOriginalPositions = expandedPositions;

        int[] expandedFrames = new int[next];
        System.arraycopy(debugActorOriginalFrame, 0, expandedFrames, 0, debugActorOriginalFrame.length);
        debugActorOriginalFrame = expandedFrames;
    }

    private int debugOriginalPositionOf(int slot) {
        if (slot < 0 || slot >= debugActorOriginalPositions.length) return -1;
        return debugActorOriginalFrame[slot] == debugActorFrameId ? debugActorOriginalPositions[slot] : -1;
    }

    private void logSpatialActorFinalRun(int[] data, int start, int end) {
        for (int i = start; i < end; i++) {
            int slot = data[i];
            if (!isSpatialActorSlot(slot)) continue;
            if (debugActorLinesThisFrame >= DEBUG_LINES_PER_FRAME) return;

            int entity = state.entityId[slot];
            int comparedSlot = adjacentSpatialActorSlot(data, start, end, i);
            String decision = "unchanged";
            if (comparedSlot >= 0) {
                float selfFootY = actorFootY(entity);
                float otherFootY = actorFootY(state.entityId[comparedSlot]);
                int comparison = Float.compare(selfFootY, otherFootY);
                decision = comparison < 0 ? "before" : comparison > 0 ? "after" : "unchanged";
            }

            String message = "spatialActor entity=" + entity
                    + " slot=" + slot
                    + " layerIndex=" + state.layerIndex[slot]
                    + " originalDrawIndex=" + debugOriginalPositionOf(slot)
                    + " finalDrawIndex=" + i
                    + " actorFootY=" + actorFootY(entity)
                    + " spatialEnabledLayer=" + isSpatialLayer(state.layerIndex[slot])
                    + " hasSpatialHeight=" + mSpatialHeight.has(entity)
                    + " comparedActorSlot=" + comparedSlot
                    + " decision=" + decision
                    + " slotCountInRun=" + (end - start)
                    + " preservedOnce=" + appearsExactlyOnce(data, start, end, slot);
            if (Gdx.app != null) {
                Gdx.app.log(TAG, message);
            } else {
                System.out.println(TAG + ": " + message);
            }
            debugActorLinesThisFrame++;
        }
    }

    private int adjacentSpatialActorSlot(int[] data, int start, int end, int index) {
        for (int i = index - 1; i >= start; i--) {
            if (isSpatialActorSlot(data[i])) return data[i];
        }
        for (int i = index + 1; i < end; i++) {
            if (isSpatialActorSlot(data[i])) return data[i];
        }
        return -1;
    }

    private static boolean appearsExactlyOnce(int[] data, int start, int end, int slot) {
        int count = 0;
        for (int i = start; i < end; i++) {
            if (data[i] == slot) count++;
        }
        return count == 1;
    }

    int getActorWorkArrayCapacity() {
        return actorSlots.length
                + actorScratchSlots.length
                + actorPositions.length
                + actorFootY.length
                + actorScratchFootY.length;
    }
}
