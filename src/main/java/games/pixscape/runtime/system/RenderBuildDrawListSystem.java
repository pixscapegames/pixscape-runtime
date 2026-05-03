package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.batch.performance.RenderStats;

/**
 * Builds the flat draw list from RenderStateSOA.
 *
 * IMPORTANT:
 * - This system is SOA-only: no ECS reads (ComponentMapper) here.
 * - "Per-entity" visibility (visible + culling) must be computed upstream
 *   and stored in RenderStateSOA.visible[e].
 * - Here we additionally apply layer filtering via LayerStateSOA (if present).
 */
public final class RenderBuildDrawListSystem extends BaseSystem {

    private static final boolean DEBUG_PREFAB = Boolean.getBoolean("pixscape.debug.prefabSpawn");

    private final RenderStateSOA state;
    private final LayerStateSOA  layerState;
    private final DrawList       drawList;
    private final RenderStats    stats;
    private final int            ecsEndExclusive;
    private final int reservedStartInclusive;
    private final int reservedEndExclusive;

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
        this.reservedStartInclusive = reservedStartInclusive;
        this.reservedEndExclusive = reservedEndExclusive;
    }

    @Override
    protected void begin() {
        drawList.clear();
        stats.reset();
    }

    @Override
    protected void processSystem() {
        int maxId = state.maxEntityId();
        if (maxId < 0) return;

        final int ecsUpper = Math.min(maxId, ecsEndExclusive - 1);

        if (DEBUG_PREFAB && maxId >= ecsEndExclusive) {
            System.out.println("[pixscape.debug.prefabSpawn] drawlist ecs scan clamped: maxId=" + maxId
                    + " ecsEndExclusive=" + ecsEndExclusive + " skippedHighEntitySlots=" + (maxId - ecsUpper));
        }

        for (int e = 0; e <= ecsUpper; e++) {
            if (isRenderableSlot(e)) {
                drawList.add(e);
            }
        }

        for (int i = 0; i < state.tiledVisibleSlotCount; i++) {
            int slot = state.tiledVisibleSlots[i];
            if (slot < ecsEndExclusive) continue;
            if (isRenderableSlot(slot)) {
                drawList.add(slot);
            }
        }

        if (reservedStartInclusive >= 0 && reservedEndExclusive > reservedStartInclusive) {
            final int reservedStart = Math.max(ecsEndExclusive, reservedStartInclusive);
            final int reservedEnd = Math.min(maxId + 1, reservedEndExclusive);
            for (int slot = reservedStart; slot < reservedEnd; slot++) {
                if (isRenderableSlot(slot)) {
                    drawList.add(slot);
                }
            }
        }

        stats.buildDrawListScannedEcsSlots = Math.max(0, ecsUpper + 1);
        stats.buildDrawListScannedTiledSlots = state.tiledVisibleSlotCount;
        stats.extractedQuads = drawList.size;
    }

    private boolean isRenderableSlot(int slot) {
        if (!state.enabled[slot]) {
            if (DEBUG_PREFAB) System.out.println("[pixscape.debug.prefabSpawn] drawlist skip slot=" + slot + " reason=disabled");
            return false;
        }

        // Entity visibility already computed upstream (SOA cache).
        if (!state.visible[slot]) {
            if (DEBUG_PREFAB) System.out.println("[pixscape.debug.prefabSpawn] drawlist skip slot=" + slot + " reason=notVisible");
            return false;
        }

        // Layer filter (final authority on render side)
        if (layerState != null) {
            int layerIdx = state.layerIndex[slot];
            if (layerIdx >= 0 && layerIdx < layerState.enabled.length && !layerState.enabled[layerIdx]) {
                if (DEBUG_PREFAB) System.out.println("[pixscape.debug.prefabSpawn] drawlist skip slot=" + slot + " reason=layerDisabled layer=" + layerIdx);
                return false;
            }
        }

        boolean sprite = state.kind[slot] == RenderStateSOA.KIND_SPRITE;
        if (DEBUG_PREFAB && !sprite) System.out.println("[pixscape.debug.prefabSpawn] drawlist skip slot=" + slot + " reason=kind kind=" + state.kind[slot]);
        return sprite;
    }

    public RenderStateSOA getRenderState() {
        return state;
    }
}
