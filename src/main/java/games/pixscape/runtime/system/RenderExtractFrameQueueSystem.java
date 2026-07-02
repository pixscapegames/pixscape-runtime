package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.render.batch.performance.RenderStats;

/**
 * Copies the finalized legacy draw-list slots into draw-ready frame queue data.
 */
public final class RenderExtractFrameQueueSystem extends BaseSystem implements ProfiledSystem {
    private final RenderStateSOA state;
    private final VfxRenderState vfxState;
    private final DrawList drawList;
    private final FrameRenderQueue frameQueue;
    private final RenderStats stats;
    private final int ecsEndExclusive;
    private final int vfxStartInclusive;
    private final int vfxEndExclusive;
    private int frameQueuePeakCapacity;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public RenderExtractFrameQueueSystem(RenderStateSOA state,
                                         DrawList drawList,
                                         FrameRenderQueue frameQueue,
                                         RenderStats stats,
                                         int ecsEndExclusive,
                                         int vfxStartInclusive,
                                         int vfxEndExclusive) {
        this(state, null, drawList, frameQueue, stats, ecsEndExclusive, vfxStartInclusive, vfxEndExclusive);
    }

    public RenderExtractFrameQueueSystem(RenderStateSOA state,
                                         VfxRenderState vfxState,
                                         DrawList drawList,
                                         FrameRenderQueue frameQueue,
                                         RenderStats stats,
                                         int ecsEndExclusive,
                                         int vfxStartInclusive,
                                         int vfxEndExclusive) {
        this.state = state;
        this.vfxState = vfxState;
        this.drawList = drawList;
        this.frameQueue = frameQueue;
        this.stats = stats;
        this.ecsEndExclusive = ecsEndExclusive;
        this.vfxStartInclusive = vfxStartInclusive;
        this.vfxEndExclusive = vfxEndExclusive;
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
        for (int i = 0; i < drawList.size; i++) {
            int slot = slots[i];
            int vfxIndex = vfxIndex(slot);
            if (vfxIndex >= 0) {
                addVfxQuad(slot, vfxIndex);
                continue;
            }

            if (slot < 0 || slot >= state.getCapacity()) {
                continue;
            }

            float ox = state.offsetX[slot];
            float oy = state.offsetY[slot];
            int sourceEntity = state.entityId[slot];

            frameQueue.addQuad(
                    state.textureHandle[slot],
                    state.shader[slot],
                    state.blend[slot],
                    state.layerIndex[slot],
                    state.paramsId[slot],
                    state.customParamsId[slot],
                    state.sortKey[slot],
                    state.x1[slot] + ox,
                    state.y1[slot] + oy,
                    state.x2[slot] + ox,
                    state.y2[slot] + oy,
                    state.x3[slot] + ox,
                    state.y3[slot] + oy,
                    state.x4[slot] + ox,
                    state.y4[slot] + oy,
                    state.u1[slot],
                    state.v1[slot],
                    state.u2[slot],
                    state.v2[slot],
                    state.colorPacked[slot],
                    state.repeatFlags[slot],
                    sourceDomain(slot, sourceEntity),
                    slot,
                    sourceEntity
            );
        }

        frameQueuePeakCapacity = Math.max(frameQueuePeakCapacity, frameQueue.getCapacity());
        if (stats != null) {
            stats.frameQueueQuads = frameQueue.size;
            stats.frameQueuePeakCapacity = frameQueuePeakCapacity;
            stats.frameQueueGrowthCount = frameQueue.getGrowthCount();
        }
    }

    private void addVfxQuad(int sourceSlot, int index) {
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
                FrameRenderQueue.SOURCE_VFX,
                sourceSlot,
                -1
        );
    }

    private byte sourceDomain(int slot, int sourceEntity) {
        if (vfxState != null && isVfxSlot(slot)) {
            return FrameRenderQueue.SOURCE_VFX;
        }
        if (slot >= 0 && slot < ecsEndExclusive) {
            return FrameRenderQueue.SOURCE_ECS;
        }
        if (sourceEntity >= 0) {
            return FrameRenderQueue.SOURCE_ECS;
        }
        return FrameRenderQueue.SOURCE_TILED;
    }

    private boolean isVfxSlot(int slot) {
        return vfxStartInclusive >= 0
                && slot >= vfxStartInclusive
                && slot < vfxEndExclusive;
    }

    private int vfxIndex(int slot) {
        if (vfxState == null || !isVfxSlot(slot)) {
            return -1;
        }

        int index = slot - vfxStartInclusive;
        return index >= 0 && index < vfxState.activeCount ? index : -1;
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
