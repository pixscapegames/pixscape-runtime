package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;

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
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<OrientedBoundsComponent> mBounds;
    private ComponentMapper<RenderMaterialComponent> mMaterial;
    private ComponentMapper<TextureRegionComponent> mTextureRegion;
    private ComponentMapper<VisibilityComponent> mVisibility;
    private ComponentMapper<PhysicsBodyComponent> mBody;
    private ComponentMapper<PhysicsShapesComponent> mShapes;
    private ComponentMapper<SpatialHeightComponent> mSpatialHeight;
    private EntitySubscription allEntities;
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
    protected void initialize() {
        allEntities = world.getAspectSubscriptionManager().get(Aspect.all());
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
        RenderCompositionList composition = drawList.composition();
        int extractedQuads = 0;
        int activeEcsSlots = ecsState != null ? ecsState.activeCount : 0;
        for (int slot = 0; slot < activeEcsSlots; slot++) {
            boolean renderable = isRenderableSlot(slot);
            if (renderable) {
                composition.add(RenderSourceDomain.SOURCE_ECS, slot, ecsState.sortKey[slot]);
                stats.ecsEmittedRenderSlots++;
                extractedQuads++;
            }
        }
        stats.buildDrawListScannedEcsSlots = activeEcsSlots;
        if (ecsState != null) {
            stats.ecsActiveRenderSlots = ecsState.activeCount;
            stats.ecsRenderCapacity = ecsState.getRenderCapacity();
            stats.ecsEntityMappingCapacity = ecsState.getEntityMappingCapacity();
        }

        int tiledVisibleRefCount = tiledState.getVisibleRefCount();
        int visibleMapCount = tiledState.getVisibleMapCount();
        for (int group = 0; group < visibleMapCount; group++) {
            if (!isRenderableTiledGroup(group)) continue;
            composition.add(
                    RenderSourceDomain.SOURCE_TILED,
                    group,
                    tiledState.visibleMapCompositionKey(group)
            );
            extractedQuads += tiledState.visibleMapRefCount(group);
        }

        if (vfxState != null && vfxStartInclusive >= 0 && vfxEndExclusive > vfxStartInclusive) {
            int count = Math.min(vfxState.activeCount, vfxEndExclusive - vfxStartInclusive);

            for (int i = 0; i < count; i++) {
                if (isRenderableVfxIndex(i)) {
                    composition.add(RenderSourceDomain.SOURCE_VFX, i, vfxState.sortKey[i]);
                    extractedQuads++;
                }
            }
        }

        stats.buildDrawListScannedTiledSlots = tiledVisibleRefCount;
        stats.tiledChunksTested = tiledState.cullingChunksTested;
        stats.tiledChunksOutside = tiledState.cullingChunksOutside;
        stats.tiledChunksFullyInside = tiledState.cullingChunksFullyInside;
        stats.tiledChunksPartial = tiledState.cullingChunksPartial;
        stats.tiledRenderableRefsConsidered = tiledState.cullingRenderableRefsConsidered;
        stats.tiledRenderableRefsVisible = tiledState.cullingRenderableRefsVisible;
        stats.tiledRenderableRefsCulled = tiledState.cullingRenderableRefsCulled;
        stats.extractedQuads = extractedQuads;
        if (vfxState != null) {
            vfxPeakCapacity = Math.max(vfxPeakCapacity, vfxState.getCapacity());
            stats.vfxActiveParticles = vfxState.activeCount;
            stats.vfxPeakCapacity = vfxPeakCapacity;
            stats.vfxGrowthCount = vfxState.getGrowthCount();
        }
    }

    private boolean isRenderableSlot(int slot) {
        if (ecsState == null || slot < 0 || slot >= ecsState.activeCount) return false;
        if (!ecsState.enabled[slot]) {
            stats.ecsSkippedDisabledSlots++;
            recordFirstEcsSkip(slot, RenderStats.ECS_SKIP_DISABLED);
            return false;
        }
        if (!ecsState.visible[slot]) {
            stats.ecsSkippedNotVisibleSlots++;
            recordFirstEcsSkip(slot, RenderStats.ECS_SKIP_NOT_VISIBLE);
            return false;
        }

        if (layerState != null) {
            int layerIdx = ecsState.layerIndex[slot];

            if (layerIdx < 0 || layerIdx >= layerState.enabled.length) {
                stats.ecsSkippedInvalidLayerSlots++;
                recordFirstEcsSkip(slot, RenderStats.ECS_SKIP_INVALID_LAYER);
                return false;
            }

            if (!layerState.enabled[layerIdx]) {
                stats.ecsSkippedDisabledLayerSlots++;
                recordFirstEcsSkip(slot, RenderStats.ECS_SKIP_DISABLED_LAYER);
                return false;
            }
        }

        if (ecsState.kind[slot] != RenderKind.SPRITE) {
            stats.ecsSkippedNonSpriteSlots++;
            recordFirstEcsSkip(slot, RenderStats.ECS_SKIP_NOT_SPRITE);
            return false;
        }
        return true;
    }

    private void recordFirstEcsSkip(int slot, int reason) {
        if (stats.ecsFirstSkippedReason != RenderStats.ECS_SKIP_NONE) return;
        int entity = ecsState.entityIdForSlot(slot);
        int flags = 0;
        boolean active = entity >= 0 && allEntities.getActiveEntityIds().get(entity);
        if (active) {
            flags |= RenderStats.ECS_COMPONENT_ACTIVE;
            if (mTransform.has(entity)) flags |= RenderStats.ECS_COMPONENT_TRANSFORM;
            if (mBounds.has(entity)) flags |= RenderStats.ECS_COMPONENT_BOUNDS;
            if (mMaterial.has(entity)) flags |= RenderStats.ECS_COMPONENT_MATERIAL;
            if (mTextureRegion.has(entity)) flags |= RenderStats.ECS_COMPONENT_TEXTURE_REGION;
            if (mVisibility.has(entity)) flags |= RenderStats.ECS_COMPONENT_VISIBILITY;
            if (mBody.has(entity)) flags |= RenderStats.ECS_COMPONENT_BODY;
            if (mShapes.has(entity)) flags |= RenderStats.ECS_COMPONENT_FIXTURES;
            if (mSpatialHeight.has(entity)) flags |= RenderStats.ECS_COMPONENT_SPATIAL_HEIGHT;
        }

        stats.ecsFirstSkippedReason = reason;
        stats.ecsFirstSkippedEntityId = entity;
        stats.ecsFirstSkippedRenderSlot = slot;
        stats.ecsFirstSkippedMappedSlot = entity >= 0
                ? ecsState.renderSlotForEntity(entity) : DynamicEntityRenderState.NO_SLOT;
        stats.ecsFirstSkippedKind = ecsState.kind[slot];
        stats.ecsFirstSkippedLayer = ecsState.layerIndex[slot];
        stats.ecsFirstSkippedComponentFlags = flags;
    }

    private boolean isRenderableTiledGroup(int groupIndex) {
        int refCount = tiledState.visibleMapRefCount(groupIndex);
        if (refCount <= 0) return false;

        if (layerState != null) {
            int layerIdx = tiledState.visibleMapLayerIndex(groupIndex);
            if (layerIdx < 0 || layerIdx >= layerState.enabled.length || !layerState.enabled[layerIdx]) {
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
