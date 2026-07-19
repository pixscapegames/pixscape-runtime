package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.spatial.SpatialActorCollector;
import games.pixscape.runtime.spatial.SpatialFaceAnchorResolver;
import games.pixscape.runtime.spatial.SpatialFaceRelationSolver;
import games.pixscape.runtime.spatial.SpatialFrameSnapshotBuilder;
import games.pixscape.runtime.spatial.SpatialLayerFaceRuntime;
import games.pixscape.runtime.spatial.SpatialLayerRuntimeRegistry;
import games.pixscape.runtime.spatial.SpatialOrderingKernel;
import games.pixscape.runtime.tiled.TiledMapLayerData;

import java.util.Arrays;

public final class SpatialRenderOrderSystem extends BaseSystem implements ProfiledSystem {
    private static final float DEFAULT_PIXELS_PER_METER = 100f;

    private final DynamicEntityRenderState ecsState;
    private final TiledMapRenderState tiledState;
    private final DrawList drawList;

    private ComponentMapper<LayerComponent> mLayer;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<PixscapeIdentityComponent> mIdentity;
    private ComponentMapper<SpatialHeightComponent> mSpatialHeight;
    private ComponentMapper<TiledLayerComponent> mTiled;
    private ComponentMapper<SpatialBlocksComponent> mSpatialBlocks;
    private ComponentMapper<PhysicsBodyComponent> mPhysicsBody;
    private ComponentMapper<PhysicsFixturesComponent> mPhysicsFixtures;

    private EntitySubscription layersSub;
    private EntitySubscription blockLayersSub;

    private boolean[] spatialLayers = new boolean[0];

    private int[] faceLayerEntities = new int[0];
    private int faceLayerCount;
    private final SpatialLayerRuntimeRegistry spatialRuntimeRegistry;
    private final SpatialFaceAnchorResolver faceAnchorResolver = new SpatialFaceAnchorResolver();
    private final SpatialActorCollector actorCollector = new SpatialActorCollector();
    private final SpatialFaceRelationSolver relationSolver = new SpatialFaceRelationSolver();
    private final SpatialFrameSnapshotBuilder snapshotBuilder = new SpatialFrameSnapshotBuilder();
    private final SpatialOrderingKernel orderingKernel = new SpatialOrderingKernel();

    private int[] slotToDrawIndex = new int[0];
    private int[] tiledRefToDrawIndex = new int[0];
    private int[] nonActorSubsequenceAfter = new int[0];
    private byte[] nonActorDomainAfter = new byte[0];

    private float pixelsPerMeter = DEFAULT_PIXELS_PER_METER;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public SpatialRenderOrderSystem(DynamicEntityRenderState ecsState, DrawList drawList) {
        this(ecsState, null, drawList);
    }

    public SpatialRenderOrderSystem(DynamicEntityRenderState ecsState, TiledMapRenderState tiledState, DrawList drawList) {
        this(ecsState, tiledState, drawList, DEFAULT_PIXELS_PER_METER, null);
    }

    public SpatialRenderOrderSystem(DynamicEntityRenderState ecsState, DrawList drawList, float pixelsPerMeter) {
        this(ecsState, null, drawList, pixelsPerMeter);
    }

    public SpatialRenderOrderSystem(DynamicEntityRenderState ecsState,
                                    TiledMapRenderState tiledState,
                                    DrawList drawList,
                                    float pixelsPerMeter) {
        this(ecsState, tiledState, drawList, pixelsPerMeter, null);
    }

    public SpatialRenderOrderSystem(DynamicEntityRenderState ecsState,
                                    TiledMapRenderState tiledState,
                                    DrawList drawList,
                                    float pixelsPerMeter,
                                    SpatialLayerRuntimeRegistry spatialRuntimeRegistry) {
        this.ecsState = ecsState;
        this.tiledState = tiledState;
        this.drawList = drawList;
        this.pixelsPerMeter = pixelsPerMeter > 0f ? pixelsPerMeter : DEFAULT_PIXELS_PER_METER;
        this.spatialRuntimeRegistry = spatialRuntimeRegistry != null
                ? spatialRuntimeRegistry : new SpatialLayerRuntimeRegistry();
    }

    @Override
    protected void initialize() {
        layersSub = world.getAspectSubscriptionManager().get(Aspect.all(LayerComponent.class));
        blockLayersSub = world.getAspectSubscriptionManager()
                .get(Aspect.all(LayerComponent.class, TiledLayerComponent.class));
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.SPATIAL_RENDER_ORDER);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.SPATIAL_RENDER_ORDER, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        orderingKernel.reset();
        if (ecsState == null || drawList == null || drawList.size <= 1) return;

        rebuildSpatialLayers();
        collectSpatialActors();
        if (actorCollector.actorCount() == 0) return;

        snapshotBuilder.build(drawList, ecsState.getRenderCapacity(), actorCollector);
        rebuildSpatialBlockLayers();
        orderingKernel.begin(actorCollector, snapshotBuilder);
        if (faceLayerCount == 0) {
            orderingKernel.finish(drawList, actorCollector, snapshotBuilder);
            applyComposedDrawList();
            assertNonActorSubsequencePreserved();
            return;
        }

        buildDrawIndexMaps();
        for (int layer = 0; layer < faceLayerCount; layer++) {
            int owner = faceLayerEntities[layer];
            TiledLayerComponent tiled = mTiled.getSafe(owner, null);
            SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(owner, null);
            if (tiled == null || tiled.data == null || blocks == null || !blocks.hasBlocks()) continue;

            SpatialLayerFaceRuntime runtime = spatialRuntimeRegistry.forLayer(owner, tiled.data);
            runtime.compiled.ensure(blocks);
            runtime.projected.ensure(runtime.compiled, tiled.data);
            runtime.tileOrder.ensure(owner, tiled.data, blocks, runtime.compiled);
            if (runtime.projected.faceCount == 0) continue;
            faceAnchorResolver.resolve(runtime.projected, tiledRefToDrawIndex,
                    snapshotBuilder.drawIndexToBucketBefore, snapshotBuilder.drawIndexToBucketAfter,
                    drawList.size);
            relationSolver.solve(actorCollector, runtime.projected);
            if (relationSolver.relationCount() == 0) continue;
            orderingKernel.addRelations(actorCollector, runtime.projected, relationSolver);
        }

        orderingKernel.finish(drawList, actorCollector, snapshotBuilder);
        applyComposedDrawList();
        assertNonActorSubsequencePreserved();
    }

    private void rebuildSpatialBlockLayers() {
        faceLayerCount = 0;
        if (blockLayersSub == null) return;

        IntBag layers = blockLayersSub.getEntities();
        int[] data = layers.getData();
        for (int i = 0, n = layers.size(); i < n; i++) {
            int entity = data[i];
            LayerComponent layer = mLayer.getSafe(entity, null);
            TiledLayerComponent tiled = mTiled.getSafe(entity, null);
            if (layer == null || tiled == null) continue;
            if (layer.type != LayerComponent.TYPE_TILED) continue;
            if (tiled.data == null) continue;
            if (!isSpatialTiledLayer(layer, tiled)) continue;

            ensureFaceLayerCapacity(faceLayerCount + 1);
            faceLayerEntities[faceLayerCount] = entity;
            faceLayerCount++;
        }
    }

    private void collectSpatialActors() {
        actorCollector.collect(drawList,
                ecsState,
                spatialLayers,
                world.getEntityManager(),
                mEntityIndex,
                mTransform,
                mSpatialHeight,
                mPhysicsBody,
                mPhysicsFixtures,
                mIdentity,
                pixelsPerMeter);
    }

    private void buildDrawIndexMaps() {
        int ecsRenderCapacity = ecsState.getRenderCapacity();
        ensureSlotToDrawIndexCapacity(ecsRenderCapacity);
        Arrays.fill(slotToDrawIndex, 0, ecsRenderCapacity, -1);
        int tiledRefCapacity = tiledState != null ? tiledState.getCapacity() : 0;
        ensureTiledRefToDrawIndexCapacity(tiledRefCapacity);
        if (tiledRefCapacity > 0) {
            Arrays.fill(tiledRefToDrawIndex, 0, tiledRefCapacity, -1);
        }
        int[] data = drawList.data();
        byte[] domains = drawList.domainData();
        for (int drawIndex = 0; drawIndex < drawList.size; drawIndex++) {
            int slot = data[drawIndex];
            byte domain = domains[drawIndex];
            if (domain == RenderSourceDomain.SOURCE_ECS && slot >= 0 && slot < ecsRenderCapacity) {
                slotToDrawIndex[slot] = drawIndex;
            } else if (domain == RenderSourceDomain.SOURCE_TILED
                    && slot >= 0
                    && slot < tiledRefToDrawIndex.length) {
                tiledRefToDrawIndex[slot] = drawIndex;
            }
        }
    }

    private void applyComposedDrawList() {
        if (orderingKernel.orderedSize() != drawList.size) {
            throw new IllegalStateException("Spatial bucket composer changed draw-list size.");
        }
        System.arraycopy(orderingKernel.orderedSlots(), 0, drawList.data(), 0, drawList.size);
        System.arraycopy(orderingKernel.orderedDomains(), 0, drawList.domainData(), 0, drawList.size);
    }

    private void assertNonActorSubsequencePreserved() {
        int count = 0;
        int[] data = drawList.data();
        byte[] domains = drawList.domainData();
        for (int i = 0; i < drawList.size; i++) {
            if (!snapshotBuilder.isActorEntry(domains[i], data[i])) count++;
        }
        if (nonActorSubsequenceAfter.length < count) {
            nonActorSubsequenceAfter = new int[count];
        }
        if (nonActorDomainAfter.length < count) {
            nonActorDomainAfter = new byte[count];
        }
        int out = 0;
        for (int i = 0; i < drawList.size; i++) {
            int slot = data[i];
            byte domain = domains[i];
            if (!snapshotBuilder.isActorEntry(domain, slot)) {
                nonActorDomainAfter[out] = domain;
                nonActorSubsequenceAfter[out++] = slot;
            }
        }
        if (count != snapshotBuilder.nonActorCount) {
            throw new IllegalStateException("Spatial non-actor subsequence changed length.");
        }
        for (int i = 0; i < count; i++) {
            if (snapshotBuilder.nonActorDomains[i] != nonActorDomainAfter[i]
                    || snapshotBuilder.nonActorSlots[i] != nonActorSubsequenceAfter[i]) {
                throw new IllegalStateException("Spatial non-actor subsequence changed during bucket composition.");
            }
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

    private boolean isSpatialTiledLayer(LayerComponent layer, TiledLayerComponent tiled) {
        return (layer != null && layer.spatialEnabled)
                || (tiled != null && tiled.spatialEnabled)
                || (tiled != null && tiled.data != null && tiled.data.spatialEnabled);
    }

    private int tiledLayerEntityCount() {
        return blockLayersSub != null ? blockLayersSub.getEntities().size() : 0;
    }

    private void ensureSpatialLayerCapacity(int required) {
        if (required <= spatialLayers.length) return;
        int next = Math.max(8, spatialLayers.length);
        while (required > next) next <<= 1;
        boolean[] expanded = new boolean[next];
        System.arraycopy(spatialLayers, 0, expanded, 0, spatialLayers.length);
        spatialLayers = expanded;
    }

    private void ensureFaceLayerCapacity(int required) {
        if (required <= faceLayerEntities.length) return;
        int next = Math.max(4, faceLayerEntities.length);
        while (required > next) next <<= 1;
        int[] expandedEntities = new int[next];
        System.arraycopy(faceLayerEntities, 0, expandedEntities, 0, faceLayerEntities.length);
        faceLayerEntities = expandedEntities;
    }

    private void ensureSlotToDrawIndexCapacity(int required) {
        if (required <= slotToDrawIndex.length) return;
        int next = Math.max(8, slotToDrawIndex.length);
        while (required > next) next <<= 1;
        slotToDrawIndex = grow(slotToDrawIndex, next);
    }

    private void ensureTiledRefToDrawIndexCapacity(int required) {
        if (required <= tiledRefToDrawIndex.length) return;
        int next = Math.max(8, tiledRefToDrawIndex.length);
        while (required > next) next <<= 1;
        tiledRefToDrawIndex = grow(tiledRefToDrawIndex, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    int getActorWorkArrayCapacity() {
        return faceLayerEntities.length
                + slotToDrawIndex.length
                + tiledRefToDrawIndex.length
                + snapshotBuilder.drawIndexToBucketBefore.length
                + snapshotBuilder.drawIndexToBucketAfter.length
                + snapshotBuilder.actorOriginalBucket.length
                + snapshotBuilder.actorSlotMask.length
                + snapshotBuilder.nonActorSlots.length
                + snapshotBuilder.nonActorDomains.length
                + nonActorDomainAfter.length
                + nonActorSubsequenceAfter.length;
    }

    int tiledDrawIndexForRef(int tiledRenderRef) {
        return tiledRenderRef >= 0 && tiledRenderRef < tiledRefToDrawIndex.length
                ? tiledRefToDrawIndex[tiledRenderRef]
                : -1;
    }

    /** Number of actors whose exact-anchor interval was contradictory in the latest ordering pass. */
    public int unresolvedConstraintCount() {
        return orderingKernel.unresolvedConstraintCount();
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
