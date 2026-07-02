package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.profiling.ProfiledSystem;

public final class RenderBuildDrawListSystem extends BaseSystem implements ProfiledSystem {
    private final RenderStateSOA state;
    private final TiledMapRenderState tiledState;
    private final VfxRenderState vfxState;
    private final LayerStateSOA layerState;
    private final DrawList drawList;
    private final RenderStats stats;

    private final int ecsEndExclusive;
    private final int vfxStartInclusive;
    private final int vfxEndExclusive;
    private int vfxPeakCapacity;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public RenderBuildDrawListSystem(RenderStateSOA state,
                                     TiledMapRenderState tiledState,
                                     LayerStateSOA layerState,
                                     DrawList drawList,
                                     RenderStats stats,
                                     int ecsEndExclusive,
                                     int vfxStartInclusive,
                                     int vfxEndExclusive) {
        this(state, tiledState, null, layerState, drawList, stats, ecsEndExclusive, vfxStartInclusive, vfxEndExclusive);
    }

    public RenderBuildDrawListSystem(RenderStateSOA state,
                                     TiledMapRenderState tiledState,
                                     VfxRenderState vfxState,
                                     LayerStateSOA layerState,
                                     DrawList drawList,
                                     RenderStats stats,
                                     int ecsEndExclusive,
                                     int vfxStartInclusive,
                                     int vfxEndExclusive) {
        this.state = state;
        this.tiledState = tiledState;
        this.vfxState = vfxState;
        this.layerState = layerState;
        this.drawList = drawList;
        this.stats = stats;
        this.ecsEndExclusive = ecsEndExclusive;
        this.vfxStartInclusive = vfxStartInclusive;
        this.vfxEndExclusive = vfxEndExclusive;
    }

    @Override
    protected void begin() {
        drawList.clear();
        stats.reset();
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.RENDER_BUILD_DRAW_LIST);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.RENDER_BUILD_DRAW_LIST, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        int maxId = state.maxEntityId();

        if (maxId >= 0) {
            final int ecsUpper = Math.min(maxId, ecsEndExclusive - 1);

            for (int slot = 0; slot <= ecsUpper; slot++) {
                boolean renderable = isRenderableSlot(slot);
                if (renderable) {
                    drawList.addEcsSlot(slot);
                }
            }

            stats.buildDrawListScannedEcsSlots = Math.max(0, ecsUpper + 1);
        } else {
            stats.buildDrawListScannedEcsSlots = 0;
        }

        int tiledVisibleRefCount = tiledState.getVisibleRefCount();
        int[] tiledVisibleRefs = tiledState.getVisibleRefs();
        for (int i = 0; i < tiledVisibleRefCount; i++) {
            int tiledRenderRef = tiledVisibleRefs[i];
            int slot = tiledState.legacySlotForRef(tiledRenderRef);

            if (slot < ecsEndExclusive) {
                continue;
            }

            if (isVfxSlot(slot)) {
                continue;
            }

            boolean renderable = isRenderableSlot(slot);
            if (renderable) {
                drawList.addTiledSlot(tiledRenderRef);
            }
        }

        if (vfxState != null && vfxStartInclusive >= 0 && vfxEndExclusive > vfxStartInclusive) {
            int count = Math.min(vfxState.activeCount, vfxEndExclusive - vfxStartInclusive);

            for (int i = 0; i < count; i++) {
                if (isRenderableVfxIndex(i)) {
                    drawList.addVfxSlot(i);
                }
            }
        }

        stats.buildDrawListScannedTiledSlots = tiledVisibleRefCount;
        stats.extractedQuads = drawList.size;
        if (vfxState != null) {
            vfxPeakCapacity = Math.max(vfxPeakCapacity, vfxState.getCapacity());
            stats.vfxActiveParticles = vfxState.activeCount;
            stats.vfxPeakCapacity = vfxPeakCapacity;
            stats.vfxGrowthCount = vfxState.getGrowthCount();
        }
    }

    private boolean isVfxSlot(int slot) {
        return vfxStartInclusive >= 0
                && slot >= vfxStartInclusive
                && slot < vfxEndExclusive;
    }

    private boolean isRenderableSlot(int slot) {
        if (slot < 0 || slot >= state.enabled.length) return false;
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

    private boolean isRenderableVfxIndex(int index) {
        if (vfxState == null || index < 0 || index >= vfxState.activeCount) return false;
        if (vfxState.textureHandle[index] == 0) return false;

        if (layerState != null) {
            int layerIdx = vfxState.layerIndex[index];

            if (layerIdx < 0 || layerIdx >= layerState.enabled.length) {
                return false;
            }

            if (!layerState.enabled[layerIdx]) {
                return false;
            }
        }

        return true;
    }

    public RenderStateSOA getRenderState() {
        return state;
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
