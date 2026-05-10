package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.batch.performance.RenderStats;

public final class RenderBuildDrawListSystem extends BaseSystem {

    private final RenderStateSOA state;
    private final LayerStateSOA layerState;
    private final DrawList drawList;
    private final RenderStats stats;
    private final int ecsEndExclusive;

    public RenderBuildDrawListSystem(RenderStateSOA state,
                                     LayerStateSOA layerState,
                                     DrawList drawList,
                                     RenderStats stats,
                                     int ecsEndExclusive) {
        this(state, layerState, drawList, stats, ecsEndExclusive, -1, -1);
    }

    public RenderBuildDrawListSystem(RenderStateSOA state,
                                     LayerStateSOA layerState,
                                     DrawList drawList,
                                     RenderStats stats,
                                     int ecsEndExclusive,
                                     int reservedStartInclusive,
                                     int reservedEndExclusive) {
        this.state = state;
        this.layerState = layerState;
        this.drawList = drawList;
        this.stats = stats;
        this.ecsEndExclusive = ecsEndExclusive;
    }

    @Override
    protected void begin() {
        drawList.clear();
        stats.reset();
    }

    @Override
    protected void processSystem() {
        int maxId = state.maxEntityId();

        if (maxId >= 0) {
            final int ecsUpper = Math.min(maxId, ecsEndExclusive - 1);

            for (int e = 0; e <= ecsUpper; e++) {
                if (isRenderableSlot(e)) {
                    drawList.add(e);
                }
            }

            stats.buildDrawListScannedEcsSlots = Math.max(0, ecsUpper + 1);
        } else {
            stats.buildDrawListScannedEcsSlots = 0;
        }

        for (int i = 0; i < state.tiledVisibleSlotCount; i++) {
            int slot = state.tiledVisibleSlots[i];

            if (slot < ecsEndExclusive) {
                continue;
            }

            if (isRenderableSlot(slot)) {
                drawList.add(slot);
            }
        }

        stats.buildDrawListScannedTiledSlots = state.tiledVisibleSlotCount;
        stats.extractedQuads = drawList.size;
    }

    private boolean isRenderableSlot(int slot) {
        if (!state.enabled[slot]) return false;
        if (!state.visible[slot]) return false;

        if (layerState != null) {
            int layerIdx = state.layerIndex[slot];

            if (layerIdx < 0 || layerIdx >= layerState.enabled.length) {
                return false;
            }

            if (!layerState.enabled[layerIdx]) {
                return false;
            }
        }

        return state.kind[slot] == RenderStateSOA.KIND_SPRITE;
    }

    public RenderStateSOA getRenderState() {
        return state;
    }
}