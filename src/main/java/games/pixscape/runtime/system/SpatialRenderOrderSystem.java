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
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.spatial.SpatialActorCollector;
import games.pixscape.runtime.spatial.SpatialBlockAnchorResolver;
import games.pixscape.runtime.spatial.SpatialBlocksRuntimeCache;
import games.pixscape.runtime.spatial.SpatialFrameSnapshotBuilder;
import games.pixscape.runtime.spatial.SpatialOrderingKernel;
import games.pixscape.runtime.spatial.SpatialRelationSolver;
import games.pixscape.runtime.spatial.SpatialTiledSort;
import games.pixscape.runtime.tiled.TiledMapLayerData;

import java.util.Arrays;

public final class SpatialRenderOrderSystem extends BaseSystem implements ProfiledSystem {
    private static final float DEFAULT_PIXELS_PER_METER = 100f;

    private final RenderStateSOA state;
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

    private int[] blockLayerEntities = new int[0];
    private int blockLayerCount;
    private final SpatialBlockAnchorResolver blockAnchorResolver = new SpatialBlockAnchorResolver();
    private final SpatialBlocksRuntimeCache blockCache = new SpatialBlocksRuntimeCache();
    private final SpatialActorCollector actorCollector = new SpatialActorCollector();
    private final SpatialRelationSolver relationSolver = new SpatialRelationSolver();
    private final SpatialFrameSnapshotBuilder snapshotBuilder = new SpatialFrameSnapshotBuilder();
    private final SpatialOrderingKernel orderingKernel = new SpatialOrderingKernel();

    private int[] slotToDrawIndex = new int[0];
    private int[] nonActorSubsequenceAfter = new int[0];
    private byte[] nonActorDomainAfter = new byte[0];

    private float pixelsPerMeter = DEFAULT_PIXELS_PER_METER;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public SpatialRenderOrderSystem(RenderStateSOA state, DrawList drawList) {
        this(state, null, drawList);
    }

    public SpatialRenderOrderSystem(RenderStateSOA state, TiledMapRenderState tiledState, DrawList drawList) {
        this.state = state;
        this.tiledState = tiledState;
        this.drawList = drawList;
    }

    public SpatialRenderOrderSystem(RenderStateSOA state, DrawList drawList, float pixelsPerMeter) {
        this(state, null, drawList, pixelsPerMeter);
    }

    public SpatialRenderOrderSystem(RenderStateSOA state,
                                    TiledMapRenderState tiledState,
                                    DrawList drawList,
                                    float pixelsPerMeter) {
        this(state, tiledState, drawList);
        this.pixelsPerMeter = pixelsPerMeter > 0f ? pixelsPerMeter : DEFAULT_PIXELS_PER_METER;
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
        if (state == null || drawList == null || drawList.size <= 1) return;

        rebuildSpatialLayers();
        collectSpatialActors();
        if (actorCollector.actorCount() == 0) return;

        snapshotBuilder.build(drawList, state.getCapacity(), actorCollector);
        rebuildSpatialBlockLayers();
        orderingKernel.begin(actorCollector, snapshotBuilder);
        if (blockLayerCount == 0) {
            orderingKernel.finish(drawList, actorCollector, snapshotBuilder);
            applyComposedDrawList();
            assertNonActorSubsequencePreserved();
            return;
        }

        buildSlotToDrawIndex();
        for (int layer = 0; layer < blockLayerCount; layer++) {
            int owner = blockLayerEntities[layer];
            TiledLayerComponent tiled = mTiled.getSafe(owner, null);
            SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(owner, null);
            if (tiled == null || tiled.data == null || blocks == null || !blocks.hasBlocks()) continue;

            SpatialTiledSort.Context spatialSort = SpatialTiledSort.contextForLayer(owner,
                    mLayer.get(owner),
                    tiled,
                    blocks);
            blockAnchorResolver.resolve(blocks, tiled.data, slotToDrawIndex, blockCache, spatialSort);
            if (blockCache.blockCount() == 0) continue;
            convertBlockAnchorsToStableBuckets();

            relationSolver.solve(actorCollector, blockCache, blocks, tiled.data);
            SpatialTiledSort.verifyLayer(owner,
                    mLayer.get(owner),
                    tiled,
                    blocks,
                    state,
                    slotToDrawIndex,
                    tiledLayerEntityCount(),
                    spatialSort,
                    actorCollector,
                    relationSolver);
            if (relationSolver.relationCount() == 0) continue;
            orderingKernel.addRelations(actorCollector, blockCache, relationSolver);
        }

        orderingKernel.finish(drawList, actorCollector, snapshotBuilder);
        applyComposedDrawList();
        assertNonActorSubsequencePreserved();
    }

    private void rebuildSpatialBlockLayers() {
        blockLayerCount = 0;
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

            ensureBlockLayerCapacity(blockLayerCount + 1);
            blockLayerEntities[blockLayerCount] = entity;
            blockLayerCount++;
        }
    }

    private void collectSpatialActors() {
        actorCollector.collect(drawList,
                state,
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

    private void buildSlotToDrawIndex() {
        ensureSlotToDrawIndexCapacity(state.getCapacity());
        Arrays.fill(slotToDrawIndex, 0, state.getCapacity(), -1);
        int[] data = drawList.data();
        byte[] domains = drawList.domainData();
        for (int drawIndex = 0; drawIndex < drawList.size; drawIndex++) {
            int slot = data[drawIndex];
            byte domain = domains[drawIndex];
            if (domain == RenderSourceDomain.SOURCE_TILED) {
                slot = tiledState != null ? tiledState.legacySlotForRef(slot) : -1;
            }
            if ((domain == RenderSourceDomain.SOURCE_ECS || domain == RenderSourceDomain.SOURCE_TILED)
                    && slot >= 0
                    && slot < state.getCapacity()) {
                slotToDrawIndex[slot] = drawIndex;
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

    private void convertBlockAnchorsToStableBuckets() {
        blockCache.convertDrawIndexRangesToBuckets(snapshotBuilder.drawIndexToBucketBefore,
                snapshotBuilder.drawIndexToBucketAfter,
                drawList.size);
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

    private void ensureBlockLayerCapacity(int required) {
        if (required <= blockLayerEntities.length) return;
        int next = Math.max(4, blockLayerEntities.length);
        while (required > next) next <<= 1;

        int[] expandedEntities = new int[next];
        System.arraycopy(blockLayerEntities, 0, expandedEntities, 0, blockLayerEntities.length);
        blockLayerEntities = expandedEntities;
    }

    private void ensureSlotToDrawIndexCapacity(int required) {
        if (required <= slotToDrawIndex.length) return;
        int next = Math.max(8, slotToDrawIndex.length);
        while (required > next) next <<= 1;
        slotToDrawIndex = grow(slotToDrawIndex, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    int getActorWorkArrayCapacity() {
        return blockLayerEntities.length
                + slotToDrawIndex.length
                + snapshotBuilder.drawIndexToBucketBefore.length
                + snapshotBuilder.drawIndexToBucketAfter.length
                + snapshotBuilder.actorOriginalBucket.length
                + snapshotBuilder.actorSlotMask.length
                + snapshotBuilder.nonActorSlots.length
                + snapshotBuilder.nonActorDomains.length
                + nonActorDomainAfter.length
                + nonActorSubsequenceAfter.length;
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
