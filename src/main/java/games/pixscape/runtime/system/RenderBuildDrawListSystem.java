package games.pixscape.runtime.system;

import com.artemis.BaseSystem;
import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import games.pixscape.runtime.component.OrientedBoundsComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
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
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<OrientedBoundsComponent> mBounds;
    private ComponentMapper<RenderMaterialComponent> mMaterial;
    private ComponentMapper<TextureRegionComponent> mTextureRegion;
    private ComponentMapper<VisibilityComponent> mVisibility;
    private ComponentMapper<PhysicsBodyComponent> mBody;
    private ComponentMapper<PhysicsFixturesComponent> mFixtures;
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
        int activeEcsSlots = ecsState != null ? ecsState.activeCount : 0;
        for (int slot = 0; slot < activeEcsSlots; slot++) {
            boolean renderable = isRenderableSlot(slot);
            if (renderable) {
                drawList.addEcsSlot(slot);
                stats.ecsEmittedRenderSlots++;
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
        stats.tiledChunksTested = tiledState.cullingChunksTested;
        stats.tiledChunksOutside = tiledState.cullingChunksOutside;
        stats.tiledChunksFullyInside = tiledState.cullingChunksFullyInside;
        stats.tiledChunksPartial = tiledState.cullingChunksPartial;
        stats.tiledRenderableRefsConsidered = tiledState.cullingRenderableRefsConsidered;
        stats.tiledRenderableRefsVisible = tiledState.cullingRenderableRefsVisible;
        stats.tiledRenderableRefsCulled = tiledState.cullingRenderableRefsCulled;
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
            if (mFixtures.has(entity)) flags |= RenderStats.ECS_COMPONENT_FIXTURES;
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
