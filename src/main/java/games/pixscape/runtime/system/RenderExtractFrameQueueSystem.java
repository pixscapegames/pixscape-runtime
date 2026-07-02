package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;

/**
 * Copies the finalized legacy draw-list slots into draw-ready frame queue data.
 */
public final class RenderExtractFrameQueueSystem extends BaseSystem implements ProfiledSystem {
    private final RenderStateSOA state;
    private final TiledMapRenderState tiledState;
    private final VfxRenderState vfxState;
    private final DrawList drawList;
    private final FrameRenderQueue frameQueue;
    private final RenderStats stats;
    private int frameQueuePeakCapacity;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public RenderExtractFrameQueueSystem(RenderStateSOA state,
                                         TiledMapRenderState tiledState,
                                         DrawList drawList,
                                         FrameRenderQueue frameQueue,
                                         RenderStats stats,
                                         int ecsEndExclusive,
                                         int vfxStartInclusive,
                                         int vfxEndExclusive) {
        this(state, tiledState, null, drawList, frameQueue, stats, ecsEndExclusive, vfxStartInclusive, vfxEndExclusive);
    }

    public RenderExtractFrameQueueSystem(RenderStateSOA state,
                                         TiledMapRenderState tiledState,
                                         VfxRenderState vfxState,
                                         DrawList drawList,
                                         FrameRenderQueue frameQueue,
                                         RenderStats stats,
                                         int ecsEndExclusive,
                                         int vfxStartInclusive,
                                         int vfxEndExclusive) {
        this.state = state;
        this.tiledState = tiledState;
        this.vfxState = vfxState;
        this.drawList = drawList;
        this.frameQueue = frameQueue;
        this.stats = stats;
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.RENDER_EXTRACT_FRAME_QUEUE);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.RENDER_EXTRACT_FRAME_QUEUE, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        frameQueue.clear();
        frameQueue.ensureCapacity(drawList.size);

        int[] slots = drawList.data();
        byte[] domains = drawList.domainData();
        for (int i = 0; i < drawList.size; i++) {
            int slot = slots[i];
            byte domain = domains[i];
            if (domain == RenderSourceDomain.SOURCE_VFX) {
                addVfxQuad(slot);
                continue;
            }

            if (domain == RenderSourceDomain.SOURCE_ECS) {
                addStateQuad(domain, slot, slot);
                continue;
            }

            if (domain == RenderSourceDomain.SOURCE_TILED) {
                addTiledQuad(slot);
            }
        }

        frameQueuePeakCapacity = Math.max(frameQueuePeakCapacity, frameQueue.getCapacity());
        if (stats != null) {
            stats.frameQueueQuads = frameQueue.size;
            stats.frameQueuePeakCapacity = frameQueuePeakCapacity;
            stats.frameQueueGrowthCount = frameQueue.getGrowthCount();
        }
    }

    private void addStateQuad(byte sourceDomain, int sourceSlot, int stateSlot) {
        if (stateSlot < 0 || stateSlot >= state.getCapacity()) {
            return;
        }

        float ox = state.offsetX[stateSlot];
        float oy = state.offsetY[stateSlot];
        int sourceEntity = sourceDomain == RenderSourceDomain.SOURCE_ECS
                ? state.entityId[stateSlot]
                : -1;

        frameQueue.addQuad(
                state.textureHandle[stateSlot],
                state.shader[stateSlot],
                state.blend[stateSlot],
                state.layerIndex[stateSlot],
                state.paramsId[stateSlot],
                state.customParamsId[stateSlot],
                state.sortKey[stateSlot],
                state.x1[stateSlot] + ox,
                state.y1[stateSlot] + oy,
                state.x2[stateSlot] + ox,
                state.y2[stateSlot] + oy,
                state.x3[stateSlot] + ox,
                state.y3[stateSlot] + oy,
                state.x4[stateSlot] + ox,
                state.y4[stateSlot] + oy,
                state.u1[stateSlot],
                state.v1[stateSlot],
                state.u2[stateSlot],
                state.v2[stateSlot],
                state.colorPacked[stateSlot],
                state.repeatFlags[stateSlot],
                sourceDomain,
                sourceSlot,
                sourceEntity
        );
    }

    private void addTiledQuad(int tiledRenderRef) {
        if (tiledState == null || !tiledState.isRenderableRef(tiledRenderRef)) {
            return;
        }

        frameQueue.addQuad(
                tiledState.textureHandle[tiledRenderRef],
                tiledState.shader[tiledRenderRef],
                tiledState.blend[tiledRenderRef],
                tiledState.layerIndex[tiledRenderRef],
                tiledState.paramsId[tiledRenderRef],
                tiledState.customParamsId[tiledRenderRef],
                tiledState.sortKey[tiledRenderRef],
                tiledState.x1[tiledRenderRef],
                tiledState.y1[tiledRenderRef],
                tiledState.x2[tiledRenderRef],
                tiledState.y2[tiledRenderRef],
                tiledState.x3[tiledRenderRef],
                tiledState.y3[tiledRenderRef],
                tiledState.x4[tiledRenderRef],
                tiledState.y4[tiledRenderRef],
                tiledState.u1[tiledRenderRef],
                tiledState.v1[tiledRenderRef],
                tiledState.u2[tiledRenderRef],
                tiledState.v2[tiledRenderRef],
                tiledState.colorPacked[tiledRenderRef],
                tiledState.repeatFlags[tiledRenderRef],
                RenderSourceDomain.SOURCE_TILED,
                tiledRenderRef,
                -1
        );
    }

    private void addVfxQuad(int index) {
        if (vfxState == null || index < 0 || index >= vfxState.activeCount) {
            return;
        }
        frameQueue.addQuad(
                vfxState.textureHandle[index],
                vfxState.shader[index],
                vfxState.blend[index],
                vfxState.layerIndex[index],
                vfxState.paramsId[index],
                vfxState.customParamsId[index],
                vfxState.sortKey[index],
                vfxState.x1[index],
                vfxState.y1[index],
                vfxState.x2[index],
                vfxState.y2[index],
                vfxState.x3[index],
                vfxState.y3[index],
                vfxState.x4[index],
                vfxState.y4[index],
                vfxState.u1[index],
                vfxState.v1[index],
                vfxState.u2[index],
                vfxState.v2[index],
                vfxState.colorPacked[index],
                vfxState.repeatFlags[index],
                RenderSourceDomain.SOURCE_VFX,
                index,
                -1
        );
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
