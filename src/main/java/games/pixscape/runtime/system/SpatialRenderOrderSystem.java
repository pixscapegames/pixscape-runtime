package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.spatial.SpatialActorGeometry;
import games.pixscape.runtime.spatial.SpatialBlockGeometry;
import games.pixscape.runtime.spatial.SpatialBlockIndex;
import games.pixscape.runtime.spatial.SpatialBlockLinkedTiles;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialRenderOrderSystem extends BaseSystem {
    private static final float BLOCK_BOUNDARY_EPSILON = 0.0001f;
    private static final int BLOCK_RELATION_NOT_APPLICABLE = 0;
    private static final int BLOCK_RELATION_ACTOR_BEHIND_BLOCK = 1;
    private static final int BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK = 2;
    private static final float DEFAULT_PIXELS_PER_METER = 100f;

    private static final int ITEM_ACTOR = 1;
    private static final int ITEM_BLOCK = 2;
    private static final int ITEM_OTHER_SPATIAL = 3;

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

    private SpatialBlockIndex[] blockIndices = new SpatialBlockIndex[0];
    private int[] blockLayerEntities = new int[0];
    private int blockLayerCount;

    private int[] itemType = new int[0];
    private int[] itemEntryStart = new int[0];
    private int[] itemEntryCount = new int[0];
    private int[] itemOriginalDrawIndex = new int[0];
    private int[] itemLayerOrder = new int[0];
    private int[] itemStableId = new int[0];
    private int[] itemActorSlot = new int[0];
    private int[] itemActorEntity = new int[0];
    private int[] itemBlockOwner = new int[0];
    private int[] itemBlockIndex = new int[0];
    private int[] itemBlockId = new int[0];
    private float[] itemDepthKey = new float[0];
    private int itemCount;

    private int[] itemEntries = new int[0];
    private int itemEntryCountTotal;

    private int[] edgeFrom = new int[0];
    private int[] edgeTo = new int[0];
    private int[] itemIndegree = new int[0];
    private boolean[] itemEmitted = new boolean[0];
    private int[] sortedItems = new int[0];
    private int edgeCount;

    private int[] rewriteSlots = new int[0];

    private final SpatialActorGeometry.Footprint tmpActorFootprint = new SpatialActorGeometry.Footprint();
    private final SpatialActorGeometry.Footprint tmpActorSortFootprint = new SpatialActorGeometry.Footprint();
    private final float[] tmpActorBaseSegment = new float[4];
    private final float[] tmpBlockBottomSegment = new float[4];
    private final float[] tmpActorReferencePoint = new float[2];
    private final float[] tmpBlockFootprint = new float[8];
    private final SpatialBlockLinkedTiles.Refs tmpLinkedTileRefs = new SpatialBlockLinkedTiles.Refs();
    private final SpatialBlockGeometry.CellRange tmpActorCellRange = new SpatialBlockGeometry.CellRange();
    private final IntArray tmpCandidateBlocks = new IntArray(false, 16);

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
        rebuildSpatialBlockIndices();

        int[] data = drawList.data();
        int start = 0;
        while (start < drawList.size) {
            while (start < drawList.size && !isSpatialRunEntry(data[start])) {
                start++;
            }
            if (start >= drawList.size) break;

            int end = start + 1;
            while (end < drawList.size && isSpatialRunEntry(data[end])) {
                end++;
            }

            processSpatialRun(data, start, end);
            start = end;
        }
    }

    private void processSpatialRun(int[] data, int start, int end) {
        itemCount = 0;
        itemEntryCountTotal = 0;
        edgeCount = 0;

        buildSpatialItemsForRun(data, start, end);
        if (itemCount <= 1) return;

        computeSpatialRelationsForRun();
        int sortedCount = sortSpatialItemsDeterministically();
        rewriteRun(data, start, end, sortedCount);
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

    private void rebuildSpatialBlockIndices() {
        blockLayerCount = 0;
        if (blockLayersSub == null) return;

        IntBag layers = blockLayersSub.getEntities();
        int[] data = layers.getData();
        for (int i = 0, n = layers.size(); i < n; i++) {
            int entity = data[i];
            LayerComponent layer = mLayer.getSafe(entity, null);
            TiledLayerComponent tiled = mTiled.getSafe(entity, null);
            SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(entity, null);
            if (layer == null || tiled == null) continue;
            if (layer.type != LayerComponent.TYPE_TILED) continue;
            if (tiled.data == null) continue;
            if (!isSpatialTiledLayer(layer, tiled)) continue;

            ensureBlockLayerCapacity(blockLayerCount + 1);
            SpatialBlockIndex index = blockIndices[blockLayerCount];
            if (index == null) {
                index = new SpatialBlockIndex();
                blockIndices[blockLayerCount] = index;
            }
            if (blocks != null && blocks.hasBlocks()) {
                index.rebuild(entity, blocks);
            } else {
                index.clear();
            }
            blockLayerEntities[blockLayerCount] = entity;
            blockLayerCount++;
        }
    }

    private void buildSpatialItemsForRun(int[] data, int start, int end) {
        for (int drawIndex = start; drawIndex < end; drawIndex++) {
            int slot = data[drawIndex];
            if (isSlotAlreadyGrouped(slot)) continue;

            if (isSpatialActorSlot(slot)) {
                addActorItem(slot, drawIndex);
                continue;
            }

            int blockOwner = findLinkedBlockOwnerForTileSlot(slot);
            if (blockOwner >= 0) {
                int blockIndex = tmpFoundBlockIndex;
                if (findBlockItem(blockOwner, blockIndex) < 0) {
                    addBlockItem(data, start, end, blockOwner, blockIndex, drawIndex);
                }
                continue;
            }

            addOtherSpatialItem(slot, drawIndex);
        }
    }

    private int tmpFoundBlockIndex = -1;

    private int findLinkedBlockOwnerForTileSlot(int slot) {
        tmpFoundBlockIndex = -1;
        if (!isRenderableSlot(slot)) return -1;
        for (int i = 0; i < blockLayerCount; i++) {
            int owner = blockLayerEntities[i];
            TiledLayerComponent tiled = mTiled.getSafe(owner, null);
            SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(owner, null);
            if (tiled == null || tiled.data == null || blocks == null || blocks.blocks == null) continue;
            if (slot < tiled.data.layerTiledStart || slot >= tiled.data.layerTiledEnd) continue;

            for (int blockIndex = 0, n = blocks.blocks.size; blockIndex < n; blockIndex++) {
                SpatialBlockData block = blocks.blocks.get(blockIndex);
                if (!SpatialBlockGeometry.isIndexableActorOccluder(block)) continue;
                SpatialBlockLinkedTiles.compute(block, tiled.data, tmpLinkedTileRefs);
                for (int ref = 0; ref < tmpLinkedTileRefs.count; ref++) {
                    if (tmpLinkedTileRefs.slot(ref) == slot) {
                        tmpFoundBlockIndex = blockIndex;
                        return owner;
                    }
                }
            }
        }
        return -1;
    }

    private void addActorItem(int slot, int drawIndex) {
        int entity = state.entityId[slot];
        int item = addItem(ITEM_ACTOR, drawIndex, state.layerIndex[slot], actorFootY(entity), stableActorId(slot));
        itemActorSlot[item] = slot;
        itemActorEntity[item] = entity;
        appendItemEntry(slot);
        itemEntryCount[item] = 1;
    }

    private void addBlockItem(int[] data, int start, int end, int owner, int blockIndex, int firstDrawIndex) {
        SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(owner, null);
        TiledLayerComponent tiled = mTiled.getSafe(owner, null);
        if (blocks == null || tiled == null || tiled.data == null) return;

        SpatialBlockData block = blocks.blocks.get(blockIndex);
        if (!SpatialBlockGeometry.isIndexableActorOccluder(block)) return;

        int item = addItem(ITEM_BLOCK, firstDrawIndex, layerOrderOf(owner), blockDepthKey(tiled.data, block),
                stableBlockId(owner, block, blockIndex));
        itemBlockOwner[item] = owner;
        itemBlockIndex[item] = blockIndex;
        itemBlockId[item] = block.id;

        SpatialBlockLinkedTiles.compute(block, tiled.data, tmpLinkedTileRefs);
        int count = 0;
        for (int linked = 0; linked < tmpLinkedTileRefs.count; linked++) {
            int slot = tmpLinkedTileRefs.slot(linked);
            if (!isRenderableSlot(slot)) continue;
            if (findDrawIndexInRun(data, start, end, slot) < 0) continue;
            appendItemEntry(slot);
            count++;
        }
        itemEntryCount[item] = count;
        if (count == 0) {
            itemCount--;
            itemEntryCountTotal = itemEntryStart[item];
        }
    }

    private void addOtherSpatialItem(int slot, int drawIndex) {
        int item = addItem(ITEM_OTHER_SPATIAL, drawIndex, state.layerIndex[slot], state.runtimeOrder[slot], slot);
        itemActorSlot[item] = slot;
        appendItemEntry(slot);
        itemEntryCount[item] = 1;
    }

    private int addItem(int type, int originalDrawIndex, int layerOrder, float depthKey, int stableId) {
        ensureItemCapacity(itemCount + 1);
        int item = itemCount++;
        itemType[item] = type;
        itemEntryStart[item] = itemEntryCountTotal;
        itemEntryCount[item] = 0;
        itemOriginalDrawIndex[item] = originalDrawIndex;
        itemLayerOrder[item] = layerOrder;
        itemDepthKey[item] = depthKey;
        itemStableId[item] = stableId;
        itemActorSlot[item] = -1;
        itemActorEntity[item] = -1;
        itemBlockOwner[item] = -1;
        itemBlockIndex[item] = -1;
        itemBlockId[item] = 0;
        return item;
    }

    private void appendItemEntry(int slot) {
        ensureItemEntryCapacity(itemEntryCountTotal + 1);
        itemEntries[itemEntryCountTotal++] = slot;
    }

    private void computeSpatialRelationsForRun() {
        ensureSortCapacity(itemCount);
        for (int i = 0; i < itemCount; i++) {
            itemIndegree[i] = 0;
            itemEmitted[i] = false;
        }

        for (int item = 0; item < itemCount; item++) {
            if (itemType[item] != ITEM_ACTOR) continue;
            int entity = itemActorEntity[item];
            SpatialHeightComponent height = mSpatialHeight.getSafe(entity, null);
            TransformComponent transform = mTransform.getSafe(entity, null);
            if (!writeActorPhysicsCircleFootprint(entity, transform, height, tmpActorFootprint)) continue;
            writeActorBaseSegment(tmpActorFootprint, tmpActorBaseSegment);

            for (int layer = 0; layer < blockLayerCount; layer++) {
                int owner = blockLayerEntities[layer];
                TiledLayerComponent tiled = mTiled.getSafe(owner, null);
                if (tiled == null || tiled.data == null) continue;
                if (!writeActorCandidateCellRange(tiled.data, tmpActorFootprint, tmpActorCellRange)) continue;

                SpatialBlockIndex index = blockIndices[layer];
                if (index == null) continue;
                index.queryRange(tmpActorCellRange.minGx, tmpActorCellRange.maxGxExclusive,
                        tmpActorCellRange.minGy, tmpActorCellRange.maxGyExclusive, tmpCandidateBlocks);
                sortCandidateBlockRefs(index, tmpCandidateBlocks);

                for (int c = 0; c < tmpCandidateBlocks.size; c++) {
                    int ref = tmpCandidateBlocks.get(c);
                    int blockItem = findBlockItem(owner, index.getRefBlockIndex(ref));
                    if (blockItem < 0) continue;
                    addActorBlockRelation(item, blockItem, owner, index.getRefBlockIndex(ref), tiled.data);
                }
            }
        }
    }

    private void addActorBlockRelation(int actorItem, int blockItem, int owner, int blockIndex, TiledMapLayerData map) {
        SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(owner, null);
        if (blocks == null || blocks.blocks == null || blockIndex < 0 || blockIndex >= blocks.blocks.size) return;
        SpatialBlockData block = blocks.blocks.get(blockIndex);
        if (!verticalOverlaps(tmpActorFootprint.bottom, tmpActorFootprint.top,
                SpatialBlockGeometry.bottom(block), SpatialBlockGeometry.top(block))) {
            return;
        }
        if (!writeBlockBottomSegment(map, block, tmpBlockBottomSegment)) return;
        if (!isActorInAuthoredBlockInfluence(tmpActorBaseSegment, tmpBlockBottomSegment,
                tmpActorFootprint.bottom, block)) {
            return;
        }

        int relation = actorBlockBottomSegmentRelation(map, tmpActorFootprint, block, tmpBlockBottomSegment);
        if (relation == BLOCK_RELATION_ACTOR_BEHIND_BLOCK) {
            addEdge(actorItem, blockItem);
        } else if (relation == BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK) {
            addEdge(blockItem, actorItem);
        }
    }

    private int sortSpatialItemsDeterministically() {
        int out = 0;
        while (out < itemCount) {
            int next = -1;
            for (int i = 0; i < itemCount; i++) {
                if (itemEmitted[i]) continue;
                if (itemIndegree[i] != 0) continue;
                if (next < 0 || compareItemStable(i, next) < 0) next = i;
            }
            if (next < 0) {
                for (int i = 0; i < itemCount; i++) {
                    if (itemEmitted[i]) continue;
                    if (next < 0 || compareItemStable(i, next) < 0) next = i;
                }
            }

            itemEmitted[next] = true;
            sortedItems[out++] = next;
            for (int edge = 0; edge < edgeCount; edge++) {
                if (edgeFrom[edge] == next && !itemEmitted[edgeTo[edge]]) {
                    itemIndegree[edgeTo[edge]]--;
                }
            }
        }
        return out;
    }

    private void rewriteRun(int[] data, int start, int end, int sortedCount) {
        int runSize = end - start;
        ensureRewriteCapacity(runSize);
        int write = 0;
        for (int i = 0; i < sortedCount; i++) {
            int item = sortedItems[i];
            int entryStart = itemEntryStart[item];
            int entryCount = itemEntryCount[item];
            for (int entry = 0; entry < entryCount; entry++) {
                rewriteSlots[write++] = itemEntries[entryStart + entry];
            }
        }
        if (write != runSize) return;
        System.arraycopy(rewriteSlots, 0, data, start, runSize);
    }

    private void addEdge(int from, int to) {
        if (from == to) return;
        for (int i = 0; i < edgeCount; i++) {
            if (edgeFrom[i] == from && edgeTo[i] == to) return;
        }
        ensureEdgeCapacity(edgeCount + 1);
        edgeFrom[edgeCount] = from;
        edgeTo[edgeCount] = to;
        edgeCount++;
        itemIndegree[to]++;
    }

    private int compareItemStable(int left, int right) {
        if (itemType[left] == ITEM_ACTOR && itemType[right] == ITEM_ACTOR) {
            int depth = compareFloatDescending(itemDepthKey[left], itemDepthKey[right]);
            if (depth != 0) return depth;
        }
        if (itemOriginalDrawIndex[left] != itemOriginalDrawIndex[right]) {
            return itemOriginalDrawIndex[left] < itemOriginalDrawIndex[right] ? -1 : 1;
        }
        if (itemLayerOrder[left] != itemLayerOrder[right]) {
            return itemLayerOrder[left] < itemLayerOrder[right] ? -1 : 1;
        }
        if (itemStableId[left] != itemStableId[right]) {
            return itemStableId[left] < itemStableId[right] ? -1 : 1;
        }
        return Integer.compare(left, right);
    }

    private static int compareFloatDescending(float left, float right) {
        int cmp = Float.compare(right, left);
        return cmp < 0 ? -1 : (cmp > 0 ? 1 : 0);
    }

    private boolean isSpatialRunEntry(int slot) {
        return isSpatialActorSlot(slot) || isSpatialTiledSlot(slot);
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

        int entity = state.entityId[slot];
        if (entity < 0 || entity >= state.getCapacity()) return false;
        if (!world.getEntityManager().isActive(entity)) return false;

        EntityIndexComponent index = mEntityIndex.getSafe(entity, null);
        if (index == null) return false;
        if (index.layerIndex != state.layerIndex[slot]) return false;
        if (!isSpatialLayer(index.layerIndex)) return false;

        SpatialHeightComponent height = mSpatialHeight.getSafe(entity, null);
        if (height == null || height.height <= 0f) return false;

        TransformComponent transform = mTransform.getSafe(entity, null);
        return writeActorPhysicsCircleFootprint(entity, transform, height, tmpActorSortFootprint);
    }

    private boolean isRenderableSlot(int slot) {
        return slot >= 0
                && slot < state.getCapacity()
                && state.kind[slot] == RenderStateSOA.KIND_SPRITE
                && state.enabled[slot]
                && state.visible[slot]
                && state.textureHandle[slot] != 0;
    }

    private float actorFootY(int entity) {
        TransformComponent transform = mTransform.getSafe(entity, null);
        if (transform == null) return 0f;
        SpatialHeightComponent height = mSpatialHeight.getSafe(entity, null);
        if (height != null && writeActorPhysicsCircleFootprint(entity, transform, height, tmpActorSortFootprint)) {
            return tmpActorSortFootprint.footY;
        }
        return transform.y;
    }

    private boolean writeActorPhysicsCircleFootprint(int entity,
                                                     TransformComponent transform,
                                                     SpatialHeightComponent height,
                                                     SpatialActorGeometry.Footprint out) {
        if (transform == null || height == null || out == null) return false;
        PhysicsBodyComponent body = mPhysicsBody.getSafe(entity, null);
        if (body == null || !body.enabled) return false;
        PhysicsFixturesComponent fixtures = mPhysicsFixtures.getSafe(entity, null);
        if (fixtures == null || fixtures.fixtures == null || fixtures.fixtures.size == 0) return false;

        for (int i = 0, n = fixtures.fixtures.size; i < n; i++) {
            FixtureDefData fixture = fixtures.fixtures.get(i);
            if (fixture == null || fixture.shapeType != FixtureDefData.SHAPE_CIRCLE) continue;
            if (fixture.radius <= 0f) continue;

            float localX = fixture.offsetX * pixelsPerMeter;
            float localY = fixture.offsetY * pixelsPerMeter;
            float cos = (float) Math.cos(transform.rotationRad);
            float sin = (float) Math.sin(transform.rotationRad);
            float cx = transform.x + localX * cos - localY * sin;
            float cy = transform.y + localX * sin + localY * cos;
            float radius = fixture.radius * pixelsPerMeter;
            if (!Float.isFinite(cx) || !Float.isFinite(cy) || !Float.isFinite(radius) || radius <= 0f) {
                continue;
            }

            out.footX = cx;
            out.footY = cy;
            out.minX = cx - radius;
            out.maxX = cx + radius;
            out.minY = cy - radius;
            out.maxY = cy + radius;
            out.bottom = height.altitude;
            out.top = height.altitude + height.height;
            out.pointOnly = false;
            return true;
        }
        return false;
    }

    private boolean writeActorCandidateCellRange(TiledMapLayerData map,
                                                 SpatialActorGeometry.Footprint footprint,
                                                 SpatialBlockGeometry.CellRange out) {
        if (map == null || footprint == null || out == null) return false;
        float gx0 = map.projectWorldToTileX(footprint.minX, footprint.minY);
        float gy0 = map.projectWorldToTileY(footprint.minX, footprint.minY);
        float gx1 = map.projectWorldToTileX(footprint.maxX, footprint.minY);
        float gy1 = map.projectWorldToTileY(footprint.maxX, footprint.minY);
        float gx2 = map.projectWorldToTileX(footprint.minX, footprint.maxY);
        float gy2 = map.projectWorldToTileY(footprint.minX, footprint.maxY);
        float gx3 = map.projectWorldToTileX(footprint.maxX, footprint.maxY);
        float gy3 = map.projectWorldToTileY(footprint.maxX, footprint.maxY);

        int minGx = (int) Math.floor(Math.min(Math.min(gx0, gx1), Math.min(gx2, gx3))) - 1;
        int maxGx = (int) Math.ceil(Math.max(Math.max(gx0, gx1), Math.max(gx2, gx3))) + 2;
        int minGy = (int) Math.floor(Math.min(Math.min(gy0, gy1), Math.min(gy2, gy3))) - 1;
        int maxGy = (int) Math.ceil(Math.max(Math.max(gy0, gy1), Math.max(gy2, gy3))) + 2;
        out.set(minGx, maxGx, minGy, maxGy);
        return true;
    }

    private void sortCandidateBlockRefs(SpatialBlockIndex index, IntArray refs) {
        for (int i = 1; i < refs.size; i++) {
            int ref = refs.get(i);
            int j = i - 1;
            while (j >= 0 && compareBlockRefs(index, ref, refs.get(j)) < 0) {
                refs.set(j + 1, refs.get(j));
                j--;
            }
            refs.set(j + 1, ref);
        }
    }

    private int compareBlockRefs(SpatialBlockIndex index, int left, int right) {
        float leftAltitude = index.getRefAltitude(left);
        float rightAltitude = index.getRefAltitude(right);
        if (Math.abs(leftAltitude - rightAltitude) > BLOCK_BOUNDARY_EPSILON) {
            return leftAltitude > rightAltitude ? -1 : 1;
        }
        int leftId = index.getRefBlockId(left);
        int rightId = index.getRefBlockId(right);
        if (leftId != rightId) return leftId < rightId ? -1 : 1;
        int leftBlock = index.getRefBlockIndex(left);
        int rightBlock = index.getRefBlockIndex(right);
        if (leftBlock != rightBlock) return leftBlock < rightBlock ? -1 : 1;
        return 0;
    }

    private boolean isSlotAlreadyGrouped(int slot) {
        for (int item = 0; item < itemCount; item++) {
            int start = itemEntryStart[item];
            int count = itemEntryCount[item];
            for (int i = 0; i < count; i++) {
                if (itemEntries[start + i] == slot) return true;
            }
        }
        return false;
    }

    private int findBlockItem(int owner, int blockIndex) {
        for (int i = 0; i < itemCount; i++) {
            if (itemType[i] == ITEM_BLOCK
                    && itemBlockOwner[i] == owner
                    && itemBlockIndex[i] == blockIndex) {
                return i;
            }
        }
        return -1;
    }

    private static int findDrawIndexInRun(int[] data, int start, int end, int slot) {
        if (data == null) return -1;
        for (int i = start; i < end; i++) {
            if (data[i] == slot) return i;
        }
        return -1;
    }

    private int layerOrderOf(int owner) {
        LayerComponent layer = mLayer.getSafe(owner, null);
        return layer != null ? layer.layerIndex : 0;
    }

    private int stableActorId(int slot) {
        int entity = state.entityId[slot];
        return entity >= 0 ? entity : slot;
    }

    private int stableBlockId(int owner, SpatialBlockData block, int blockIndex) {
        int id = block != null ? block.id : 0;
        int low = id != 0 ? id : blockIndex + 1;
        return (owner << 12) ^ low;
    }

    private float blockDepthKey(TiledMapLayerData map, SpatialBlockData block) {
        if (!writeBlockBottomSegment(map, block, tmpBlockBottomSegment)) return 0f;
        return (tmpBlockBottomSegment[1] + tmpBlockBottomSegment[3]) * 0.5f;
    }

    private boolean isSpatialTiledLayer(LayerComponent layer, TiledLayerComponent tiled) {
        return (layer != null && layer.spatialEnabled)
                || (tiled != null && tiled.spatialEnabled)
                || (tiled != null && tiled.data != null && tiled.data.spatialEnabled);
    }

    private static boolean verticalOverlaps(float actorBottom,
                                            float actorTop,
                                            float blockBottom,
                                            float blockTop) {
        return actorTop > blockBottom && blockTop > actorBottom;
    }

    private static boolean isActorOnBlockAltitude(float actorBaseAltitude, SpatialBlockData block) {
        return block != null
                && block.altitude <= actorBaseAltitude + BLOCK_BOUNDARY_EPSILON;
    }

    private boolean isActorInAuthoredBlockInfluence(float[] actorBottomSegment,
                                                    float[] blockBottomSegment,
                                                    float actorBaseAltitude,
                                                    SpatialBlockData block) {
        if (actorBottomSegment == null || actorBottomSegment.length < 4) return false;
        if (blockBottomSegment == null || blockBottomSegment.length < 4) return false;
        if (!isActorOnBlockAltitude(actorBaseAltitude, block)) return false;
        writeActorReferencePointForBlockBottom(blockBottomSegment, actorBottomSegment, tmpActorReferencePoint);

        float blockDx = blockBottomSegment[2] - blockBottomSegment[0];
        float blockDy = blockBottomSegment[3] - blockBottomSegment[1];
        float blockLength2 = blockDx * blockDx + blockDy * blockDy;
        if (blockLength2 <= BLOCK_BOUNDARY_EPSILON * BLOCK_BOUNDARY_EPSILON) return false;

        float projectionLeft = projectionT(blockBottomSegment, actorBottomSegment[0], actorBottomSegment[1]);
        float projectionRight = projectionT(blockBottomSegment, actorBottomSegment[2], actorBottomSegment[3]);
        float actorDx = actorBottomSegment[2] - actorBottomSegment[0];
        float actorDy = actorBottomSegment[3] - actorBottomSegment[1];
        float actorLength = (float) Math.sqrt(actorDx * actorDx + actorDy * actorDy);
        float blockLength = (float) Math.sqrt(blockLength2);
        float margin = blockLength > BLOCK_BOUNDARY_EPSILON
                ? Math.min(0.25f, actorLength / blockLength * 0.5f + BLOCK_BOUNDARY_EPSILON)
                : BLOCK_BOUNDARY_EPSILON;

        float minT = Math.min(projectionLeft, projectionRight);
        float maxT = Math.max(projectionLeft, projectionRight);
        return maxT >= -margin && minT <= 1f + margin;
    }

    private static float projectionT(float[] segment, float pointX, float pointY) {
        float dx = segment[2] - segment[0];
        float dy = segment[3] - segment[1];
        float length2 = dx * dx + dy * dy;
        if (length2 <= BLOCK_BOUNDARY_EPSILON * BLOCK_BOUNDARY_EPSILON) return Float.NaN;
        return ((pointX - segment[0]) * dx + (pointY - segment[1]) * dy) / length2;
    }

    private static void writeActorBaseSegment(SpatialActorGeometry.Footprint footprint, float[] out4) {
        out4[0] = footprint.minX;
        out4[1] = footprint.maxY;
        out4[2] = footprint.maxX;
        out4[3] = footprint.maxY;
    }

    private int actorBlockBottomSegmentRelation(TiledMapLayerData map,
                                                SpatialActorGeometry.Footprint footprint,
                                                SpatialBlockData block,
                                                float[] outBlockBottomSegment) {
        if (map == null || footprint == null || block == null) return BLOCK_RELATION_NOT_APPLICABLE;
        if (!writeBlockBottomSegment(map, block, outBlockBottomSegment)) return BLOCK_RELATION_NOT_APPLICABLE;
        return actorCircleRelationByWallLine(footprint, outBlockBottomSegment);
    }

    private static int actorCircleRelationByWallLine(SpatialActorGeometry.Footprint footprint,
                                                     float[] blockBottomSegment) {
        if (footprint == null) return BLOCK_RELATION_NOT_APPLICABLE;
        float cx = actorFootCenterX(footprint);
        float cy = actorFootCenterY(footprint);
        float radius = actorFootRadius(footprint);
        float lineY = lineYAt(blockBottomSegment, cx);
        if (Float.isNaN(lineY)) return BLOCK_RELATION_NOT_APPLICABLE;
        float centerT = projectionT(blockBottomSegment, cx, cy);
        if (centerT < -BLOCK_BOUNDARY_EPSILON || centerT > 1f + BLOCK_BOUNDARY_EPSILON) {
            return pointRelationByLineEquation(blockBottomSegment, cx, cy);
        }
        float d = lineY - cy;
        if (d > radius + BLOCK_BOUNDARY_EPSILON) return BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK;
        if (d < -radius - BLOCK_BOUNDARY_EPSILON) return BLOCK_RELATION_ACTOR_BEHIND_BLOCK;
        return BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK;
    }

    private static float actorFootCenterX(SpatialActorGeometry.Footprint footprint) {
        return (footprint.minX + footprint.maxX) * 0.5f;
    }

    private static float actorFootCenterY(SpatialActorGeometry.Footprint footprint) {
        return (footprint.minY + footprint.maxY) * 0.5f;
    }

    private static float actorFootRadius(SpatialActorGeometry.Footprint footprint) {
        float width = footprint.maxX - footprint.minX;
        float depth = footprint.maxY - footprint.minY;
        return Math.max(width, depth) * 0.5f;
    }

    private static int actorBottomSegmentRelation(float[] blockBottomSegment, float[] actorBottomSegment) {
        if (blockBottomSegment == null || blockBottomSegment.length < 4) return BLOCK_RELATION_NOT_APPLICABLE;
        if (actorBottomSegment == null || actorBottomSegment.length < 4) return BLOCK_RELATION_NOT_APPLICABLE;
        boolean ascending = blockBottomAscending(blockBottomSegment);
        float sideLeft = crossSide(blockBottomSegment[0], blockBottomSegment[1],
                blockBottomSegment[2], blockBottomSegment[3],
                actorBottomSegment[0], actorBottomSegment[1]);
        float sideRight = crossSide(blockBottomSegment[0], blockBottomSegment[1],
                blockBottomSegment[2], blockBottomSegment[3],
                actorBottomSegment[2], actorBottomSegment[3]);
        boolean leftBehind = crossSideIsBehind(sideLeft, ascending);
        boolean rightBehind = crossSideIsBehind(sideRight, ascending);
        if (leftBehind && rightBehind) return BLOCK_RELATION_ACTOR_BEHIND_BLOCK;
        if (!leftBehind && !rightBehind) return BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK;
        return BLOCK_RELATION_NOT_APPLICABLE;
    }

    private static int pointRelationByLineEquation(float[] blockBottomSegment, float pointX, float pointY) {
        float lineY = lineYAt(blockBottomSegment, pointX);
        if (Float.isNaN(lineY)) return BLOCK_RELATION_NOT_APPLICABLE;
        float d = pointY - lineY;
        if (d > BLOCK_BOUNDARY_EPSILON) return BLOCK_RELATION_ACTOR_BEHIND_BLOCK;
        if (d < -BLOCK_BOUNDARY_EPSILON) return BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK;
        return BLOCK_RELATION_NOT_APPLICABLE;
    }

    private static float lineYAt(float[] segment, float x) {
        if (segment == null || segment.length < 4) return Float.NaN;
        float dx = segment[2] - segment[0];
        if (Math.abs(dx) <= BLOCK_BOUNDARY_EPSILON) return Float.NaN;
        float slope = (segment[3] - segment[1]) / dx;
        return segment[1] + slope * (x - segment[0]);
    }

    private static void writeActorReferencePointForBlockBottom(float[] blockBottomSegment,
                                                               float[] actorBottomSegment,
                                                               float[] out2) {
        if (blockBottomAscending(blockBottomSegment)) {
            out2[0] = actorBottomSegment[2];
            out2[1] = actorBottomSegment[3];
        } else {
            out2[0] = actorBottomSegment[0];
            out2[1] = actorBottomSegment[1];
        }
    }

    private static boolean blockBottomAscending(float[] blockBottomSegment) {
        return blockBottomSegment != null
                && blockBottomSegment.length >= 4
                && blockBottomSegment[3] < blockBottomSegment[1] - BLOCK_BOUNDARY_EPSILON;
    }

    private static boolean crossSideIsBehind(float crossSide, boolean ascending) {
        return (!ascending && crossSide < -BLOCK_BOUNDARY_EPSILON)
                || (ascending && crossSide > BLOCK_BOUNDARY_EPSILON);
    }

    static int bottomSegmentRelationForTest(float blockBottomStartX,
                                            float blockBottomStartY,
                                            float blockBottomEndX,
                                            float blockBottomEndY,
                                            float actorReferenceX,
                                            float actorReferenceY) {
        float side = crossSide(blockBottomStartX, blockBottomStartY,
                blockBottomEndX, blockBottomEndY,
                actorReferenceX, actorReferenceY);
        boolean ascending = blockBottomEndY < blockBottomStartY - BLOCK_BOUNDARY_EPSILON;
        return crossSideIsBehind(side, ascending)
                ? BLOCK_RELATION_ACTOR_BEHIND_BLOCK
                : BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK;
    }

    static int actorBottomSegmentRelationForTest(float blockBottomStartX,
                                                 float blockBottomStartY,
                                                 float blockBottomEndX,
                                                 float blockBottomEndY,
                                                 float actorBottomLeftX,
                                                 float actorBottomLeftY,
                                                 float actorBottomRightX,
                                                 float actorBottomRightY) {
        float[] blockBottomSegment = {
                blockBottomStartX, blockBottomStartY,
                blockBottomEndX, blockBottomEndY
        };
        float[] actorBottomSegment = {
                actorBottomLeftX, actorBottomLeftY,
                actorBottomRightX, actorBottomRightY
        };
        return actorBottomSegmentRelation(blockBottomSegment, actorBottomSegment);
    }

    private static float crossSide(float lineStartX,
                                   float lineStartY,
                                   float lineEndX,
                                   float lineEndY,
                                   float pointX,
                                   float pointY) {
        return (lineEndX - lineStartX) * (pointY - lineStartY)
                - (lineEndY - lineStartY) * (pointX - lineStartX);
    }

    private boolean writeBlockBottomSegment(TiledMapLayerData map, SpatialBlockData block, float[] out4) {
        if (out4 == null || out4.length < 4) return false;
        if (!SpatialBlockGeometry.writeTileCellFootprint(block, map, tmpBlockFootprint)) return false;
        out4[0] = tmpBlockFootprint[6];
        out4[1] = tmpBlockFootprint[7];
        out4[2] = tmpBlockFootprint[4];
        out4[3] = tmpBlockFootprint[5];
        return true;
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

        SpatialBlockIndex[] expandedIndices = new SpatialBlockIndex[next];
        System.arraycopy(blockIndices, 0, expandedIndices, 0, blockIndices.length);
        blockIndices = expandedIndices;

        int[] expandedEntities = new int[next];
        System.arraycopy(blockLayerEntities, 0, expandedEntities, 0, blockLayerEntities.length);
        blockLayerEntities = expandedEntities;
    }

    private void ensureItemCapacity(int required) {
        if (required <= itemType.length) return;
        int next = Math.max(8, itemType.length);
        while (required > next) next <<= 1;

        itemType = grow(itemType, next);
        itemEntryStart = grow(itemEntryStart, next);
        itemEntryCount = grow(itemEntryCount, next);
        itemOriginalDrawIndex = grow(itemOriginalDrawIndex, next);
        itemLayerOrder = grow(itemLayerOrder, next);
        itemStableId = grow(itemStableId, next);
        itemActorSlot = grow(itemActorSlot, next);
        itemActorEntity = grow(itemActorEntity, next);
        itemBlockOwner = grow(itemBlockOwner, next);
        itemBlockIndex = grow(itemBlockIndex, next);
        itemBlockId = grow(itemBlockId, next);
        itemDepthKey = grow(itemDepthKey, next);
    }

    private void ensureItemEntryCapacity(int required) {
        if (required <= itemEntries.length) return;
        int next = Math.max(8, itemEntries.length);
        while (required > next) next <<= 1;
        itemEntries = grow(itemEntries, next);
    }

    private void ensureSortCapacity(int required) {
        if (required <= itemIndegree.length) return;
        int next = Math.max(8, itemIndegree.length);
        while (required > next) next <<= 1;
        itemIndegree = grow(itemIndegree, next);
        sortedItems = grow(sortedItems, next);

        boolean[] expandedEmitted = new boolean[next];
        System.arraycopy(itemEmitted, 0, expandedEmitted, 0, itemEmitted.length);
        itemEmitted = expandedEmitted;
    }

    private void ensureEdgeCapacity(int required) {
        if (required <= edgeFrom.length) return;
        int next = Math.max(8, edgeFrom.length);
        while (required > next) next <<= 1;
        edgeFrom = grow(edgeFrom, next);
        edgeTo = grow(edgeTo, next);
    }

    private void ensureRewriteCapacity(int required) {
        if (required <= rewriteSlots.length) return;
        int next = Math.max(8, rewriteSlots.length);
        while (required > next) next <<= 1;
        rewriteSlots = grow(rewriteSlots, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    private static float[] grow(float[] source, int next) {
        float[] expanded = new float[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }

    static String relationName(int relation) {
        if (relation == BLOCK_RELATION_ACTOR_BEHIND_BLOCK) return "ACTOR_BEHIND_BLOCK";
        if (relation == BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK) return "ACTOR_IN_FRONT_OF_BLOCK";
        return "NOT_APPLICABLE";
    }

    int getActorWorkArrayCapacity() {
        return itemType.length
                + itemEntryStart.length
                + itemEntryCount.length
                + itemOriginalDrawIndex.length
                + itemLayerOrder.length
                + itemStableId.length
                + itemActorSlot.length
                + itemActorEntity.length
                + itemBlockOwner.length
                + itemBlockIndex.length
                + itemBlockId.length
                + itemDepthKey.length
                + itemEntries.length
                + edgeFrom.length
                + edgeTo.length
                + itemIndegree.length
                + sortedItems.length
                + rewriteSlots.length;
    }
}
