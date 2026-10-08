package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.SkipWire;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.math.Vector2;
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
import games.pixscape.runtime.render.IdentityLayerDisplayOffsetResolver;
import games.pixscape.runtime.render.LayerDisplayOffsetResolver;
import games.pixscape.runtime.render.RenderSourceDomain;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.spatial.*;
import games.pixscape.runtime.tiled.TiledMapLayerData;

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
    private EntitySubscription tiledMapsSub;

    private boolean[] spatialLayers = new boolean[0];
    private int[] activeSpatialLayerIndices = new int[0];
    private int activeSpatialLayerCount;

    private int[] faceMapEntities = new int[0];
    private int faceMapCount;
    private final SpatialLayerRuntimeRegistry spatialRuntimeRegistry;
    private final SpatialFaceAnchorResolver faceAnchorResolver = new SpatialFaceAnchorResolver();
    private final SpatialActorCollector actorCollector = new SpatialActorCollector();
    private SpatialFaceRelationSolver relationSolver = new SpatialFaceRelationSolver();
    private final SpatialWallContinuationCache wallContinuations = new SpatialWallContinuationCache();
    private SpatialLayerFaceRuntime[] preparedMaps = new SpatialLayerFaceRuntime[0];
    private TiledMapLayerData[] preparedMapData = new TiledMapLayerData[0];
    private int[] preparedMapLayers = new int[0];
    private float[] preparedMapOffsetX = new float[0], preparedMapOffsetY = new float[0];
    private final SpatialVisualAnchorSelector visualSelector = new SpatialVisualAnchorSelector();
    private final LayerDisplayOffsetResolver displayOffsetResolver;
    private final Vector2 mapDisplayOffset = new Vector2();
    private final SpatialFrameSnapshotBuilder snapshotBuilder = new SpatialFrameSnapshotBuilder();
    private final SpatialOrderingKernel orderingKernel = new SpatialOrderingKernel();

    private int[] tiledRefToDrawIndex = new int[0];
    private int[] mappedTiledRefs = new int[0];
    private int mappedTiledRefCount;
    @SkipWire
    private GameObjectHierarchySystem gameObjectHierarchy;
    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private int lastVisualCandidateCount;
    private int lastFaceRelationCount;
    private boolean diagnosticsEnabled;
    private final StringBuilder diagnosticDetail = new StringBuilder(256);

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
        this(ecsState, tiledState, drawList, spatialRuntimeRegistry, null);
    }

    public SpatialRenderOrderSystem(DynamicEntityRenderState ecsState,
                                    TiledMapRenderState tiledState,
                                    DrawList drawList,
                                    SpatialLayerRuntimeRegistry spatialRuntimeRegistry,
                                    LayerDisplayOffsetResolver displayOffsetResolver) {
        this.ecsState = ecsState;
        this.tiledState = tiledState;
        this.drawList = drawList;
        this.spatialRuntimeRegistry = spatialRuntimeRegistry != null
                ? spatialRuntimeRegistry : new SpatialLayerRuntimeRegistry();
        this.displayOffsetResolver = displayOffsetResolver != null
                ? displayOffsetResolver : new IdentityLayerDisplayOffsetResolver();
    }

    @Override
    protected void initialize() {
        gameObjectHierarchy = world.getSystem(GameObjectHierarchySystem.class);
        layersSub = world.getAspectSubscriptionManager().get(
                Aspect.all(LayerComponent.class).exclude(EntityIndexComponent.class));
        tiledMapsSub = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class, TiledLayerComponent.class)
                        .exclude(LayerComponent.class));
    }

    /**
     * On-demand editor overlay of the envelope used for the latest composed frame.
     * Writes into caller-owned storage; ineligible or culled actors have no envelope.
     */
    public boolean writeActorInfluenceQuad(int entityId, float[] out) {
        if (!isEnabled() || ecsState == null) return false;
        int slot = ecsState.renderSlotForEntity(entityId);
        int actor = actorCollector.actorIndexForSlot(slot);
        if (actor < 0 || actorCollector.entityId(actor) != entityId) return false;
        actorCollector.writeInfluenceQuad(actor, ecsState.offsetX[slot], ecsState.offsetY[slot], out);
        return true;
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
        lastVisualCandidateCount = 0;
        lastFaceRelationCount = 0;
        if (diagnosticsEnabled) diagnosticDetail.setLength(0);
        orderingKernel.reset();
        actorCollector.clear();
        if (ecsState == null || drawList == null || drawList.size == 0) return;

        rebuildSpatialLayers();
        collectSpatialActors();
        if (actorCollector.actorCount() == 0 || drawList.size == 1) return;

        snapshotBuilder.build(drawList, ecsState.getRenderCapacity(), actorCollector);
        collectSpatialMaps();
        orderingKernel.begin(actorCollector, snapshotBuilder);
        if (faceMapCount == 0) {
            orderingKernel.finish(drawList, actorCollector, snapshotBuilder);
            applyComposedDrawList();
            return;
        }

        buildTiledDrawIndexMap();
        if (preparedMaps.length < faceMapCount) {
            int capacity = Math.max(faceMapCount, Math.max(8, preparedMaps.length * 2));
            preparedMaps = new SpatialLayerFaceRuntime[capacity];
            preparedMapData = new TiledMapLayerData[capacity];
            preparedMapLayers = new int[capacity];
            preparedMapOffsetX = new float[capacity];
            preparedMapOffsetY = new float[capacity];
        }
        int preparedCount = 0;
        for (int mapIndex = 0; mapIndex < faceMapCount; mapIndex++) {
            int owner = faceMapEntities[mapIndex];
            TiledLayerComponent tiled = mTiled.getSafe(owner, null);
            SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(owner, null);
            if (tiled == null || tiled.data == null || blocks == null || !blocks.hasBlocks()) continue;

            SpatialLayerFaceRuntime runtime = spatialRuntimeRegistry.forLayer(owner, tiled.data);
            runtime.compiled.ensure(blocks);
            runtime.projected.ensure(runtime.compiled, tiled.data);
            runtime.tileOrder.ensure(owner, tiled.data, blocks, runtime.compiled);
            preparedMaps[preparedCount] = runtime;
            preparedMapData[preparedCount] = tiled.data;
            EntityIndexComponent index = mEntityIndex.getSafe(owner, null);
            preparedMapLayers[preparedCount] = index != null ? index.layerIndex : 0;
            faceAnchorResolver.resolve(runtime.projected, tiledRefToDrawIndex,
                    snapshotBuilder.drawIndexToBucketBefore, snapshotBuilder.drawIndexToBucketAfter,
                    drawList.size);
            displayOffsetResolver.resolveLayer(index != null ? index.layerIndex : 0, mapDisplayOffset);
            preparedMapOffsetX[preparedCount] = mapDisplayOffset.x;
            preparedMapOffsetY[preparedCount++] = mapDisplayOffset.y;
            relationSolver = runtime.relations;
            relationSolver.setCaptureCandidates(diagnosticsEnabled);
            relationSolver.solveVisual(actorCollector, runtime.projected, visualSelector,
                    ecsState, tiledState, tiled.data, mapDisplayOffset.x, mapDisplayOffset.y);
            lastVisualCandidateCount += relationSolver.visualCandidateCount;
        }

        wallContinuations.ensure(preparedMaps, preparedMapData, preparedMapLayers, preparedCount);
        wallContinuations.capture(preparedMaps, actorCollector.actorCount, preparedMapOffsetX, preparedMapOffsetY);
        for (int mapIndex = 0; mapIndex < preparedCount; mapIndex++) {
            SpatialLayerFaceRuntime runtime = preparedMaps[mapIndex];
            relationSolver = runtime.relations;
            relationSolver.applyWallContinuations(actorCollector, runtime.projected, wallContinuations, mapIndex);
            if (diagnosticsEnabled) appendVisualDiagnostics(runtime.layerEntity, runtime.projected);
            lastFaceRelationCount += relationSolver.relationCount;
            if (relationSolver.relationCount() > 0)
                orderingKernel.addRelations(actorCollector, runtime.projected, relationSolver, runtime.layerEntity);
        }

        orderingKernel.finish(drawList, actorCollector, snapshotBuilder);
        applyComposedDrawList();
    }

    private void appendVisualDiagnostics(int mapEntity, SpatialProjectedFaceCache faces) {
        for (int actor = 0; actor < actorCollector.actorCount; actor++) {
            PixscapeIdentityComponent mapIdentity = mIdentity.getSafe(mapEntity, null);
            int actorEntity = actorCollector.entityId(actor);
            PixscapeIdentityComponent actorIdentity = mIdentity.getSafe(actorEntity, null);
            diagnosticDetail.append("\nmap=").append(mapEntity).append(" actor=").append(actor)
                    .append(" mapStableId=").append(mapIdentity != null ? mapIdentity.stableId : -1)
                    .append(" actorEntity=").append(actorEntity)
                    .append(" actorStableId=").append(actorIdentity != null ? actorIdentity.stableId : -1)
                    .append(" candidates:");
            int end = relationSolver.actorCandidateStart[actor] + relationSolver.actorCandidateCount[actor];
            for (int candidate = relationSolver.actorCandidateStart[actor]; candidate < end; candidate++) {
                int anchor = relationSolver.candidateAnchorIndex[candidate];
                diagnosticDetail.append(' ').append(faces.anchorGx[anchor]).append(',')
                        .append(faces.anchorGy[anchor]);
            }
            diagnosticDetail.append(" relations:");
            end = relationSolver.actorRelationStart[actor] + relationSolver.actorRelationCount[actor];
            for (int relation = relationSolver.actorRelationStart[actor]; relation < end; relation++) {
                int anchor = relationSolver.relationAnchorIndex[relation];
                int face = relationSolver.relationFaceIndex[relation];
                int membership = relationSolver.relationMembershipIndex[relation];
                diagnosticDetail.append(' ').append(faces.anchorGx[anchor]).append(',')
                        .append(faces.anchorGy[anchor]).append("/structure=")
                        .append(faces.faceStructureId[face]).append("/face=")
                        .append(faces.faceCompiledIndex[face])
                        .append("/membership=").append(membership)
                        .append("/ref=").append(faces.anchorTiledRef[anchor])
                        .append("/reach=[").append(faces.faceAnchorScreenMinX[membership]).append(',')
                        .append(faces.faceAnchorScreenMaxX[membership]).append(']')
                        .append("/bound=").append(relationSolver.relationType[relation] == SpatialFaceRelationSolver.ACTOR_BEHIND_FACE
                                ? faces.anchorBeforeBucket[anchor] : faces.anchorAfterBucket[anchor]).append('/')
                        .append(relationSolver.relationType[relation] == SpatialFaceRelationSolver.ACTOR_BEHIND_FACE
                                ? "behind" : "front");
            }
            diagnosticDetail.append(" rejected:");
            for (int r = 0; r < relationSolver.rejectedCount; r++) {
                if (relationSolver.rejectedActor[r] != actor) continue;
                int anchor = relationSolver.rejectedAnchor[r];
                int face = relationSolver.rejectedFace[r];
                int m = relationSolver.rejectedMembership[r];
                diagnosticDetail.append(' ').append(faces.anchorGx[anchor]).append(',')
                        .append(faces.anchorGy[anchor]).append("/structure=").append(faces.faceStructureId[face])
                        .append("/face=").append(faces.faceCompiledIndex[face]).append("/membership=").append(m)
                        .append("/ref=").append(faces.anchorTiledRef[anchor])
                        .append("/reach=[").append(faces.faceAnchorScreenMinX[m]).append(',')
                        .append(faces.faceAnchorScreenMaxX[m]).append(']')
                        .append("/front/reason=DISTANT_DIRECT_JUNCTION_FRONT/witnessFace=")
                        .append(faces.faceCompiledIndex[relationSolver.rejectedWitnessFace[r]]);
            }
        }
    }

    private void collectSpatialMaps() {
        faceMapCount = 0;
        if (tiledMapsSub == null) return;

        IntBag maps = tiledMapsSub.getEntities();
        int[] data = maps.getData();
        for (int i = 0, n = maps.size(); i < n; i++) {
            int entity = data[i];
            TiledLayerComponent tiled = mTiled.getSafe(entity, null);
            if (tiled == null) continue;
            if (tiled.data == null) continue;
            if (!isSpatialTiledMap(tiled)) continue;

            ensureFaceMapCapacity(faceMapCount + 1);
            faceMapEntities[faceMapCount] = entity;
            faceMapCount++;
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
                mIdentity,
                gameObjectHierarchy != null ? gameObjectHierarchy.worldTransforms() : null);
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
                mSpatialPhysicsFootprint,
                gameObjectHierarchy != null ? gameObjectHierarchy.worldTransforms() : null);
    }

    private void buildTiledDrawIndexMap() {
        int tiledRefCapacity = tiledState != null ? tiledState.getCapacity() : 0;
        ensureTiledRefToDrawIndexCapacity(tiledRefCapacity);
        for (int i = 0; i < mappedTiledRefCount; i++) {
            tiledRefToDrawIndex[mappedTiledRefs[i]] = -1;
        }
        mappedTiledRefCount = 0;
        ensureMappedTiledRefCapacity(drawList.size);
        int[] data = drawList.data();
        byte[] domains = drawList.domainData();
        for (int drawIndex = 0; drawIndex < drawList.size; drawIndex++) {
            int slot = data[drawIndex];
            byte domain = domains[drawIndex];
            if (domain == RenderSourceDomain.SOURCE_TILED
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

    /** Counts Tiled Map owners; the legacy helper name does not denote ordinary layers. */
    int tiledLayerEntityCount() {
        return tiledMapsSub != null ? tiledMapsSub.getEntities().size() : 0;
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

    private void ensureMappedTiledRefCapacity(int required) {
        if (required <= mappedTiledRefs.length) return;
        int next = Math.max(8, mappedTiledRefs.length);
        while (required > next) next <<= 1;
        mappedTiledRefs = Arrays.copyOf(mappedTiledRefs, next);
    }

    private void ensureFaceMapCapacity(int required) {
        if (required <= faceMapEntities.length) return;
        int next = Math.max(4, faceMapEntities.length);
        while (required > next) next <<= 1;
        int[] expandedEntities = new int[next];
        System.arraycopy(faceMapEntities, 0, expandedEntities, 0, faceMapEntities.length);
        faceMapEntities = expandedEntities;
    }

    private void ensureTiledRefToDrawIndexCapacity(int required) {
        if (required <= tiledRefToDrawIndex.length) return;
        int oldLength = tiledRefToDrawIndex.length;
        int next = Math.max(8, tiledRefToDrawIndex.length);
        while (required > next) next <<= 1;
        tiledRefToDrawIndex = grow(tiledRefToDrawIndex, next);
        Arrays.fill(tiledRefToDrawIndex, oldLength, next, -1);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    int getActorWorkArrayCapacity() {
        return faceMapEntities.length
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

    public int visualCandidateCount() { return lastVisualCandidateCount; }
    public int faceRelationCount() { return lastFaceRelationCount; }

    /** Builds a diagnostic snapshot only when explicitly requested; ordinary frames do not log. */
    public String diagnosticSummary() {
        return orderingKernel.diagnosticSummary(lastVisualCandidateCount, lastFaceRelationCount)
                + (diagnosticsEnabled ? diagnosticDetail.toString() : "");
    }

    public void setDiagnosticsEnabled(boolean enabled) { diagnosticsEnabled = enabled; }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
