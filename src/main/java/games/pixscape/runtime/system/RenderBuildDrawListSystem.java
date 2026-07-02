package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderKind;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.profiling.ProfiledSystem;

public final class RenderBuildDrawListSystem extends BaseSystem implements ProfiledSystem {
    private final DynamicEntityRenderState ecsState;
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

    public RenderBuildDrawListSystem(DynamicEntityRenderState ecsState,
                                     TiledMapRenderState tiledState,
                                     LayerStateSOA layerState,
                                     DrawList drawList,
                                     RenderStats stats,
                                     int ecsEndExclusive,
                                     int vfxStartInclusive,
                                     int vfxEndExclusive) {
        this(ecsState, tiledState, null, layerState, drawList, stats, ecsEndExclusive, vfxStartInclusive, vfxEndExclusive);
    }

    public RenderBuildDrawListSystem(DynamicEntityRenderState ecsState,
                                     TiledMapRenderState tiledState,
                                     VfxRenderState vfxState,
                                     LayerStateSOA layerState,
                                     DrawList drawList,
                                     RenderStats stats,
                                     int ecsEndExclusive,
                                     int vfxStartInclusive,
                                     int vfxEndExclusive) {
        this.ecsState = ecsState;
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
        int activeEcsSlots = ecsState != null ? ecsState.activeCount : 0;
        for (int slot = 0; slot < activeEcsSlots; slot++) {
            boolean renderable = isRenderableSlot(slot);
            if (renderable) {
                drawList.addEcsSlot(slot);
            }
        }
        stats.buildDrawListScannedEcsSlots = activeEcsSlots;
        if (ecsState != null) {
            stats.ecsActiveRenderSlots = ecsState.activeCount;
            stats.ecsRenderCapacity = ecsState.getRenderCapacity();
            stats.ecsEntityMappingCapacity = ecsState.getEntityMappingCapacity();
        }

        int tiledVisibleRefCount = tiledState.getVisibleRefCount();
        int[] tiledVisibleRefs = tiledState.getVisibleRefs();
        for (int i = 0; i < tiledVisibleRefCount; i++) {
            int tiledRenderRef = tiledVisibleRefs[i];

            boolean renderable = isRenderableTiledRef(tiledRenderRef);
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

    private boolean isRenderableSlot(int slot) {
        if (ecsState == null || slot < 0 || slot >= ecsState.activeCount) return false;
        if (!ecsState.enabled[slot]) return false;
        if (!ecsState.visible[slot]) return false;

        if (layerState != null) {
            int layerIdx = ecsState.layerIndex[slot];

            if (layerIdx < 0 || layerIdx >= layerState.enabled.length) {
                return false;
            }

            if (!layerState.enabled[layerIdx]) {
                return false;
            }
        }

        return ecsState.kind[slot] == RenderKind.SPRITE;
    }

    private boolean isRenderableTiledRef(int tiledRenderRef) {
        if (!tiledState.isRenderableRef(tiledRenderRef)) return false;

        if (layerState != null) {
            int layerIdx = tiledState.layerIndex[tiledRenderRef];

            if (layerIdx < 0 || layerIdx >= layerState.enabled.length) {
                return false;
            }

            if (!layerState.enabled[layerIdx]) {
                return false;
            }
        }

        return true;
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

    public DynamicEntityRenderState getDynamicEntityRenderState() {
        return ecsState;
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
