package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.SkipWire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.hierarchy.GameObjectTopologyState;
import games.pixscape.runtime.spatial.*;

import java.util.Arrays;

public final class SpatialRenderOrderSystem extends BaseSystem implements ProfiledSystem {
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
    private ComponentMapper<SpatialPhysicsFootprintComponent> mSpatialPhysicsFootprint;

    private EntitySubscription layersSub;
    private EntitySubscription blockLayersSub;

    private boolean[] spatialLayers = new boolean[0];
    private int[] activeSpatialLayerIndices = new int[0];
    private int activeSpatialLayerCount;

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
    private int[] mappedSlots = new int[0];
    private int mappedSlotCount;
    private int[] mappedTiledRefs = new int[0];
    private int mappedTiledRefCount;
    @SkipWire
    private GameObjectHierarchySystem gameObjectHierarchy;
    private int[] gameObjectFirstDrawIndex = new int[0];
    private int[] gameObjectLastDrawIndex = new int[0];
    private int[] gameObjectNextDrawIndex = new int[0];
    private int[] atomicSlots = new int[0];
    private byte[] atomicDomains = new byte[0];
    private final IntArray touchedGameObjectRoots = new IntArray(false, 16);
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public SpatialRenderOrderSystem(DynamicEntityRenderState ecsState, DrawList drawList) {
        this(ecsState, null, drawList);
    }

    public SpatialRenderOrderSystem(DynamicEntityRenderState ecsState, TiledMapRenderState tiledState, DrawList drawList) {
        this(ecsState, tiledState, drawList, null);
    }

    public SpatialRenderOrderSystem(DynamicEntityRenderState ecsState,
                                    TiledMapRenderState tiledState,
                                    DrawList drawList,
                                    SpatialLayerRuntimeRegistry spatialRuntimeRegistry) {
        this.ecsState = ecsState;
        this.tiledState = tiledState;
        this.drawList = drawList;
        this.spatialRuntimeRegistry = spatialRuntimeRegistry != null
                ? spatialRuntimeRegistry : new SpatialLayerRuntimeRegistry();
    }

    @Override
    protected void initialize() {
        gameObjectHierarchy = world.getSystem(GameObjectHierarchySystem.class);
        layersSub = world.getAspectSubscriptionManager().get(
                Aspect.all(LayerComponent.class).exclude(EntityIndexComponent.class));
        blockLayersSub = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TiledLayerComponent.class)
                        .exclude(LayerComponent.class));
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
    }

    private void rebuildSpatialBlockLayers() {
        faceLayerCount = 0;
        if (blockLayersSub == null) return;

        IntBag layers = blockLayersSub.getEntities();
        int[] data = layers.getData();
        for (int i = 0, n = layers.size(); i < n; i++) {
            int entity = data[i];
            TiledLayerComponent tiled = mTiled.getSafe(entity, null);
            if (tiled == null) continue;
            if (tiled.data == null) continue;
            if (!isSpatialTiledMap(tiled)) continue;

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
                mSpatialPhysicsFootprint,
                mIdentity);
    }

    /**
     * Returns whether the current derived render state makes {@code entityId}
     * eligible for Spatial actor ordering.
     */
    public boolean participatesInRenderOrder(int entityId, boolean spatialLayerEnabled) {
        if (ecsState == null || entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return false;
        }
        int slot = ecsState.renderSlotForEntity(entityId);
        return actorCollector.isEligibleActorSlotOnSpatialLayer(
                slot,
                ecsState,
                spatialLayerEnabled,
                world.getEntityManager(),
                mEntityIndex,
                mTransform,
                mSpatialHeight,
                mSpatialPhysicsFootprint);
    }

    private void buildDrawIndexMaps() {
        int ecsRenderCapacity = ecsState.getRenderCapacity();
        ensureSlotToDrawIndexCapacity(ecsRenderCapacity);
        for (int i = 0; i < mappedSlotCount; i++) {
            slotToDrawIndex[mappedSlots[i]] = -1;
        }
        mappedSlotCount = 0;
        int tiledRefCapacity = tiledState != null ? tiledState.getCapacity() : 0;
        ensureTiledRefToDrawIndexCapacity(tiledRefCapacity);
        for (int i = 0; i < mappedTiledRefCount; i++) {
            tiledRefToDrawIndex[mappedTiledRefs[i]] = -1;
        }
        mappedTiledRefCount = 0;
        ensureMappedEntryCapacity(drawList.size);
        int[] data = drawList.data();
        byte[] domains = drawList.domainData();
        for (int drawIndex = 0; drawIndex < drawList.size; drawIndex++) {
            int slot = data[drawIndex];
            byte domain = domains[drawIndex];
            if (domain == RenderSourceDomain.SOURCE_ECS && slot >= 0 && slot < ecsRenderCapacity) {
                slotToDrawIndex[slot] = drawIndex;
                mappedSlots[mappedSlotCount++] = slot;
            } else if (domain == RenderSourceDomain.SOURCE_TILED
                    && slot >= 0
                    && slot < tiledRefToDrawIndex.length) {
                tiledRefToDrawIndex[slot] = drawIndex;
                mappedTiledRefs[mappedTiledRefCount++] = slot;
            }
        }
    }

    private void applyComposedDrawList() {
        if (orderingKernel.orderedSize() != drawList.size) {
            throw new IllegalStateException("Spatial bucket composer changed draw-list size.");
        }
        System.arraycopy(orderingKernel.orderedSlots(), 0, drawList.data(), 0, drawList.size);
        System.arraycopy(orderingKernel.orderedDomains(), 0, drawList.domainData(), 0, drawList.size);
        restoreGameObjectAtomicity();
    }

    /**
     * Spatial ordering is deliberately hierarchy-agnostic in this Runtime stage. If it moves
     * unrelated actors through a flattened Game Object block, compact the block again while
     * preserving the member order produced by {@link RenderSortSystem}.
     */
    void restoreGameObjectAtomicity() {
        if (gameObjectHierarchy == null || drawList.size <= 1) return;
        GameObjectTopologyState topology = gameObjectHierarchy.topology();
        ensureAtomicEntityCapacity(topology.getEntityCapacity());
        ensureAtomicDrawCapacity(drawList.size);
        for (int i = 0; i < touchedGameObjectRoots.size; i++) {
            int root = touchedGameObjectRoots.get(i);
            gameObjectFirstDrawIndex[root] = -1;
            gameObjectLastDrawIndex[root] = -1;
        }
        touchedGameObjectRoots.clear();
        Arrays.fill(gameObjectNextDrawIndex, 0, drawList.size, -1);

        int[] slots = drawList.data();
        byte[] domains = drawList.domainData();
        for (int drawIndex = 0; drawIndex < drawList.size; drawIndex++) {
            int root = gameObjectRoot(domains[drawIndex], slots[drawIndex], topology);
            if (root < 0) continue;
            if (gameObjectFirstDrawIndex[root] < 0) {
                gameObjectFirstDrawIndex[root] = drawIndex;
                touchedGameObjectRoots.add(root);
            } else {
                gameObjectNextDrawIndex[gameObjectLastDrawIndex[root]] = drawIndex;
            }
            gameObjectLastDrawIndex[root] = drawIndex;
        }
        if (touchedGameObjectRoots.size == 0) return;

        int output = 0;
        for (int drawIndex = 0; drawIndex < drawList.size; drawIndex++) {
            int root = gameObjectRoot(domains[drawIndex], slots[drawIndex], topology);
            if (root < 0) {
                atomicDomains[output] = domains[drawIndex];
                atomicSlots[output++] = slots[drawIndex];
                continue;
            }
            if (gameObjectFirstDrawIndex[root] != drawIndex) continue;
            for (int memberDrawIndex = drawIndex; memberDrawIndex >= 0;
                 memberDrawIndex = gameObjectNextDrawIndex[memberDrawIndex]) {
                atomicDomains[output] = domains[memberDrawIndex];
                atomicSlots[output++] = slots[memberDrawIndex];
            }
        }
        if (output != drawList.size) {
            throw new IllegalStateException("Game Object atomic compaction changed draw-list size.");
        }
        System.arraycopy(atomicSlots, 0, slots, 0, output);
        System.arraycopy(atomicDomains, 0, domains, 0, output);
    }

    private int gameObjectRoot(byte domain, int slot, GameObjectTopologyState topology) {
        if (domain != RenderSourceDomain.SOURCE_ECS || ecsState == null) return -1;
        int entityId = ecsState.entityIdForSlot(slot);
        if (entityId < 0 || entityId >= topology.getEntityCapacity()
                || !topology.parented[entityId]) {
            return -1;
        }
        return topology.rootEntityId[entityId];
    }

    private void rebuildSpatialLayers() {
        for (int i = 0; i < activeSpatialLayerCount; i++) {
            spatialLayers[activeSpatialLayerIndices[i]] = false;
        }
        activeSpatialLayerCount = 0;

        if (layersSub == null) return;

        IntBag layers = layersSub.getEntities();
        int[] data = layers.getData();
        for (int i = 0, n = layers.size(); i < n; i++) {
            int entity = data[i];
            LayerComponent layer = mLayer.getSafe(entity, null);
            if (layer == null
                    || layer.layerIndex < 0
                    || !layer.spatialEnabled) {
                continue;
            }

            ensureSpatialLayerCapacity(layer.layerIndex + 1);
            if (!spatialLayers[layer.layerIndex]) {
                ensureActiveSpatialLayerCapacity(activeSpatialLayerCount + 1);
                spatialLayers[layer.layerIndex] = true;
                activeSpatialLayerIndices[activeSpatialLayerCount++] = layer.layerIndex;
            }
        }
    }

    private boolean isSpatialTiledMap(TiledLayerComponent tiled) {
        return (tiled != null && tiled.spatialEnabled)
                || (tiled != null && tiled.data != null && tiled.data.spatialEnabled);
    }

    int tiledLayerEntityCount() {
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

    private void ensureActiveSpatialLayerCapacity(int required) {
        if (required <= activeSpatialLayerIndices.length) return;
        int next = Math.max(8, activeSpatialLayerIndices.length);
        while (required > next) next <<= 1;
        activeSpatialLayerIndices = Arrays.copyOf(activeSpatialLayerIndices, next);
    }

    private void ensureMappedEntryCapacity(int required) {
        if (required <= mappedSlots.length) return;
        int next = Math.max(8, mappedSlots.length);
        while (required > next) next <<= 1;
        mappedSlots = Arrays.copyOf(mappedSlots, next);
        mappedTiledRefs = Arrays.copyOf(mappedTiledRefs, next);
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
        int oldLength = slotToDrawIndex.length;
        int next = Math.max(8, slotToDrawIndex.length);
        while (required > next) next <<= 1;
        slotToDrawIndex = grow(slotToDrawIndex, next);
        Arrays.fill(slotToDrawIndex, oldLength, next, -1);
    }

    private void ensureTiledRefToDrawIndexCapacity(int required) {
        if (required <= tiledRefToDrawIndex.length) return;
        int oldLength = tiledRefToDrawIndex.length;
        int next = Math.max(8, tiledRefToDrawIndex.length);
        while (required > next) next <<= 1;
        tiledRefToDrawIndex = grow(tiledRefToDrawIndex, next);
        Arrays.fill(tiledRefToDrawIndex, oldLength, next, -1);
    }

    private void ensureAtomicEntityCapacity(int required) {
        if (required <= gameObjectFirstDrawIndex.length) return;
        int oldLength = gameObjectFirstDrawIndex.length;
        int next = Math.max(16, oldLength);
        while (required > next) next <<= 1;
        gameObjectFirstDrawIndex = Arrays.copyOf(gameObjectFirstDrawIndex, next);
        gameObjectLastDrawIndex = Arrays.copyOf(gameObjectLastDrawIndex, next);
        Arrays.fill(gameObjectFirstDrawIndex, oldLength, next, -1);
        Arrays.fill(gameObjectLastDrawIndex, oldLength, next, -1);
    }

    private void ensureAtomicDrawCapacity(int required) {
        if (required <= gameObjectNextDrawIndex.length) return;
        int next = Math.max(16, gameObjectNextDrawIndex.length);
        while (required > next) next <<= 1;
        gameObjectNextDrawIndex = Arrays.copyOf(gameObjectNextDrawIndex, next);
        atomicSlots = Arrays.copyOf(atomicSlots, next);
        atomicDomains = Arrays.copyOf(atomicDomains, next);
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
                + snapshotBuilder.nonActorDomains.length;
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

    /** Number of candidate actor plans rejected in the latest ordering pass. */
    public int actorOrderingFallbackCount() {
        return orderingKernel.actorOrderingFallbackCount();
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
