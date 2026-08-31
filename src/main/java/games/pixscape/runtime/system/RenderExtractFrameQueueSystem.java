package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;

/**
 * Copies finalized draw-list source slots into draw-ready frame queue data.
 */
public final class RenderExtractFrameQueueSystem extends BaseSystem implements ProfiledSystem {
    private final DynamicEntityRenderState ecsState;
    private final TiledMapRenderState tiledState;
    private final VfxRenderState vfxState;
    private final DrawList drawList;
    private final FrameRenderQueue frameQueue;
    private final RenderStats stats;
    private final LayerDisplayOffsetResolver displayOffsetResolver;
    private final Vector2 displayOffset = new Vector2();
    private int frameQueuePeakCapacity;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public RenderExtractFrameQueueSystem(DynamicEntityRenderState ecsState,
                                         TiledMapRenderState tiledState,
                                         DrawList drawList,
                                         FrameRenderQueue frameQueue,
                                         RenderStats stats,
                                         int ecsEndExclusive,
                                         int vfxStartInclusive,
                                         int vfxEndExclusive) {
        this(ecsState, tiledState, null, drawList, frameQueue, stats, ecsEndExclusive, vfxStartInclusive, vfxEndExclusive);
    }

    public RenderExtractFrameQueueSystem(DynamicEntityRenderState ecsState,
                                         TiledMapRenderState tiledState,
                                         VfxRenderState vfxState,
                                         DrawList drawList,
                                         FrameRenderQueue frameQueue,
                                         RenderStats stats,
                                         int ecsEndExclusive,
                                         int vfxStartInclusive,
                                         int vfxEndExclusive) {
        this.ecsState = ecsState;
        this.tiledState = tiledState;
        this.vfxState = vfxState;
        this.drawList = drawList;
        this.frameQueue = frameQueue;
        this.stats = stats;
        this.displayOffsetResolver = new IdentityLayerDisplayOffsetResolver();
    }

    /** Extracts all render domains with the owning Layer's display parallax. */
    public RenderExtractFrameQueueSystem(DynamicEntityRenderState ecsState,
                                         TiledMapRenderState tiledState,
                                         VfxRenderState vfxState,
                                         LayerStateSOA layerState,
                                         com.badlogic.gdx.graphics.OrthographicCamera camera,
                                         DrawList drawList,
                                         FrameRenderQueue frameQueue,
                                         RenderStats stats,
                                         int ecsEndExclusive,
                                         int vfxStartInclusive,
                                         int vfxEndExclusive) {
        this(ecsState, tiledState, vfxState,
                new LayerParallaxDisplayOffsetResolver(layerState, camera),
                drawList, frameQueue, stats, ecsEndExclusive, vfxStartInclusive, vfxEndExclusive);
    }

    /** Extracts all render domains using the World's display-space authority. */
    public RenderExtractFrameQueueSystem(DynamicEntityRenderState ecsState,
                                         TiledMapRenderState tiledState,
                                         VfxRenderState vfxState,
                                         LayerDisplayOffsetResolver displayOffsetResolver,
                                         DrawList drawList,
                                         FrameRenderQueue frameQueue,
                                         RenderStats stats,
                                         int ecsEndExclusive,
                                         int vfxStartInclusive,
                                         int vfxEndExclusive) {
        this.ecsState = ecsState;
        this.tiledState = tiledState;
        this.vfxState = vfxState;
        this.displayOffsetResolver = displayOffsetResolver;
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
                addEcsQuad(slot);
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

    private void addEcsQuad(int renderSlot) {
        if (ecsState == null || renderSlot < 0 || renderSlot >= ecsState.activeCount) {
            return;
        }

        float ox = ecsState.offsetX[renderSlot];
        float oy = ecsState.offsetY[renderSlot];
        int sourceEntity = ecsState.renderSlotToEntityId[renderSlot];

        frameQueue.addQuad(
                ecsState.textureHandle[renderSlot],
                ecsState.shader[renderSlot],
                ecsState.blend[renderSlot],
                ecsState.layerIndex[renderSlot],
                ecsState.paramsId[renderSlot],
                ecsState.customParamsId[renderSlot],
                ecsState.sortKey[renderSlot],
                ecsState.x1[renderSlot] + ox,
                ecsState.y1[renderSlot] + oy,
                ecsState.x2[renderSlot] + ox,
                ecsState.y2[renderSlot] + oy,
                ecsState.x3[renderSlot] + ox,
                ecsState.y3[renderSlot] + oy,
                ecsState.x4[renderSlot] + ox,
                ecsState.y4[renderSlot] + oy,
                ecsState.u1[renderSlot],
                ecsState.v1[renderSlot],
                ecsState.u2[renderSlot],
                ecsState.v2[renderSlot],
                ecsState.colorPacked[renderSlot],
                ecsState.repeatFlags[renderSlot],
                RenderSourceDomain.SOURCE_ECS,
                renderSlot,
                sourceEntity
        );
    }

    private void addTiledQuad(int tiledRenderRef) {
        if (tiledState == null || !tiledState.isRenderableRef(tiledRenderRef)) {
            return;
        }

        resolveLayerDisplayOffset(tiledState.layerIndex[tiledRenderRef]);
        frameQueue.addQuad(
                tiledState.textureHandle[tiledRenderRef],
                tiledState.shader[tiledRenderRef],
                tiledState.blend[tiledRenderRef],
                tiledState.layerIndex[tiledRenderRef],
                tiledState.paramsId[tiledRenderRef],
                tiledState.customParamsId[tiledRenderRef],
                tiledState.sortKey[tiledRenderRef],
                tiledState.x1[tiledRenderRef] + displayOffset.x,
                tiledState.y1[tiledRenderRef] + displayOffset.y,
                tiledState.x2[tiledRenderRef] + displayOffset.x,
                tiledState.y2[tiledRenderRef] + displayOffset.y,
                tiledState.x3[tiledRenderRef] + displayOffset.x,
                tiledState.y3[tiledRenderRef] + displayOffset.y,
                tiledState.x4[tiledRenderRef] + displayOffset.x,
                tiledState.y4[tiledRenderRef] + displayOffset.y,
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
        resolveLayerDisplayOffset(vfxState.layerIndex[index]);
        frameQueue.addQuad(
                vfxState.textureHandle[index],
                vfxState.shader[index],
                vfxState.blend[index],
                vfxState.layerIndex[index],
                vfxState.paramsId[index],
                vfxState.customParamsId[index],
                vfxState.sortKey[index],
                vfxState.x1[index] + displayOffset.x,
                vfxState.y1[index] + displayOffset.y,
                vfxState.x2[index] + displayOffset.x,
                vfxState.y2[index] + displayOffset.y,
                vfxState.x3[index] + displayOffset.x,
                vfxState.y3[index] + displayOffset.y,
                vfxState.x4[index] + displayOffset.x,
                vfxState.y4[index] + displayOffset.y,
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

    private void resolveLayerDisplayOffset(int layerIndex) {
        displayOffsetResolver.resolveLayer(layerIndex, displayOffset);
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
