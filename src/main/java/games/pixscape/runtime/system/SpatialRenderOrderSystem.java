package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.spatial.SpatialActorCollector;
import games.pixscape.runtime.spatial.SpatialBlockAnchorResolver;
import games.pixscape.runtime.spatial.SpatialBlocksRuntimeCache;
import games.pixscape.runtime.spatial.SpatialDrawListComposer;
import games.pixscape.runtime.spatial.SpatialInsertionPlanner;
import games.pixscape.runtime.spatial.SpatialRelationSolver;
import games.pixscape.runtime.tiled.TiledMapLayerData;

import java.util.Arrays;

public final class SpatialRenderOrderSystem extends BaseSystem {
    private static final float DEFAULT_PIXELS_PER_METER = 100f;

    private final RenderStateSOA state;
    private final DrawList drawList;

    private ComponentMapper<LayerComponent> mLayer;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
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
    private final SpatialInsertionPlanner insertionPlanner = new SpatialInsertionPlanner();
    private final SpatialDrawListComposer drawListComposer = new SpatialDrawListComposer();

    private int[] slotToDrawIndex = new int[0];
    private int[] tiledSubsequenceBefore = new int[0];
    private int[] tiledSubsequenceAfter = new int[0];

    private float pixelsPerMeter = DEFAULT_PIXELS_PER_METER;

    public SpatialRenderOrderSystem(RenderStateSOA state, DrawList drawList) {
        this.state = state;
        this.drawList = drawList;
    }

    public SpatialRenderOrderSystem(RenderStateSOA state, DrawList drawList, float pixelsPerMeter) {
        this(state, drawList);
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
        if (state == null || drawList == null || drawList.size <= 1) return;

        rebuildSpatialLayers();
        rebuildSpatialBlockLayers();
        if (blockLayerCount == 0) return;

        collectSpatialActors();
        if (actorCollector.actorCount() == 0) return;

        captureTiledSubsequence(tiledSubsequenceBefore);
        for (int layer = 0; layer < blockLayerCount; layer++) {
            int owner = blockLayerEntities[layer];
            TiledLayerComponent tiled = mTiled.getSafe(owner, null);
            SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(owner, null);
            if (tiled == null || tiled.data == null || blocks == null || !blocks.hasBlocks()) continue;

            buildSlotToDrawIndex();
            blockAnchorResolver.resolve(blocks, tiled.data, slotToDrawIndex, blockCache);
            if (blockCache.blockCount() == 0) continue;

            relationSolver.solve(actorCollector, blockCache, blocks, tiled.data);
            if (relationSolver.relationCount() == 0) continue;

            insertionPlanner.plan(actorCollector, blockCache, relationSolver);
            if (insertionPlanner.planCount() == 0) continue;

            drawListComposer.compose(drawList.data(), drawList.size, insertionPlanner, this::isSpatialActorSlot);
            applyComposedDrawList();
            collectSpatialActors();
        }
        assertTiledSubsequencePreserved();
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
                pixelsPerMeter);
    }

    private void buildSlotToDrawIndex() {
        ensureSlotToDrawIndexCapacity(state.getCapacity());
        Arrays.fill(slotToDrawIndex, 0, state.getCapacity(), -1);
        int[] data = drawList.data();
        for (int drawIndex = 0; drawIndex < drawList.size; drawIndex++) {
            int slot = data[drawIndex];
            if (slot >= 0 && slot < state.getCapacity()) {
                slotToDrawIndex[slot] = drawIndex;
            }
        }
    }

    private void applyComposedDrawList() {
        if (drawListComposer.composedSize != drawList.size) {
            throw new IllegalStateException("Spatial draw-list composer changed draw-list size.");
        }
        System.arraycopy(drawListComposer.composedSlots, 0, drawList.data(), 0, drawList.size);
    }

    private void captureTiledSubsequence(int[] ignored) {
        int count = 0;
        int[] data = drawList.data();
        for (int i = 0; i < drawList.size; i++) {
            if (isSpatialTiledSlot(data[i])) count++;
        }
        if (tiledSubsequenceBefore.length < count) {
            tiledSubsequenceBefore = new int[count];
        }
        int out = 0;
        for (int i = 0; i < drawList.size; i++) {
            int slot = data[i];
            if (isSpatialTiledSlot(slot)) tiledSubsequenceBefore[out++] = slot;
        }
    }

    private void assertTiledSubsequencePreserved() {
        int count = 0;
        int[] data = drawList.data();
        for (int i = 0; i < drawList.size; i++) {
            if (isSpatialTiledSlot(data[i])) count++;
        }
        if (tiledSubsequenceAfter.length < count) {
            tiledSubsequenceAfter = new int[count];
        }
        int out = 0;
        for (int i = 0; i < drawList.size; i++) {
            int slot = data[i];
            if (isSpatialTiledSlot(slot)) tiledSubsequenceAfter[out++] = slot;
        }
        if (count > tiledSubsequenceBefore.length) {
            throw new IllegalStateException("Spatial tiled subsequence changed length.");
        }
        for (int i = 0; i < count; i++) {
            if (tiledSubsequenceBefore[i] != tiledSubsequenceAfter[i]) {
                throw new IllegalStateException("Spatial tiled subsequence changed during actor-only composition.");
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

    private boolean isSpatialTiledSlot(int slot) {
        if (!isRenderableSlot(slot)) return false;
        for (int i = 0; i < blockLayerCount; i++) {
            int owner = blockLayerEntities[i];
            TiledLayerComponent tiled = mTiled.getSafe(owner, null);
            if (tiled == null || tiled.data == null) continue;
            if (slot >= tiled.data.layerTiledStart && slot < tiled.data.layerTiledEnd) return true;
        }
        return false;
    }

    private boolean isSpatialActorSlot(int slot) {
        if (!isRenderableSlot(slot)) return false;

        return actorCollector.isEligibleActorSlot(slot,
                state,
                spatialLayers,
                world.getEntityManager(),
                mEntityIndex,
                mTransform,
                mSpatialHeight,
                mPhysicsBody,
                mPhysicsFixtures,
                pixelsPerMeter);
    }

    private boolean isRenderableSlot(int slot) {
        return slot >= 0
                && slot < state.getCapacity()
                && state.kind[slot] == RenderStateSOA.KIND_SPRITE
                && state.enabled[slot]
                && state.visible[slot]
                && state.textureHandle[slot] != 0;
    }

    private boolean isSpatialTiledLayer(LayerComponent layer, TiledLayerComponent tiled) {
        return (layer != null && layer.spatialEnabled)
                || (tiled != null && tiled.spatialEnabled)
                || (tiled != null && tiled.data != null && tiled.data.spatialEnabled);
    }

    private boolean isSpatialLayer(int layerIndex) {
        return layerIndex >= 0 && layerIndex < spatialLayers.length && spatialLayers[layerIndex];
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
                + tiledSubsequenceBefore.length
                + tiledSubsequenceAfter.length;
    }
}
