package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.utils.IntBag;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.SpatialHeightComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
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
    private int[] actorSlots = new int[0];
    private int[] actorScratchSlots = new int[0];
    private int[] actorPositions = new int[0];
    private float[] actorFootY = new float[0];
    private float[] actorScratchFootY = new float[0];

    private SpatialBlockIndex[] blockIndices = new SpatialBlockIndex[0];
    private int[] blockLayerEntities = new int[0];
    private int blockLayerCount;

    private int[] snapshot = new int[0];
    private int[] slotDrawIndex = new int[0];
    private int[] slotDrawIndexFrame = new int[0];
    private int slotDrawIndexFrameId = 1;

    private int[] intentActorSlot = new int[0];
    private int[] intentFirstLinkedTileSlot = new int[0];
    private int[] intentLastLinkedTileSlot = new int[0];
    private int[] intentSpatialBlockId = new int[0];
    private int[] intentOriginalActorDrawIndex = new int[0];
    private int[] intentFirstLinkedDrawIndex = new int[0];
    private int[] intentLastLinkedDrawIndex = new int[0];
    private int[] intentTargetDrawIndex = new int[0];
    private int[] intentBlockRelation = new int[0];
    private int[] intentApplyOrder = new int[0];
    private boolean[] intentAfterLinkedTile = new boolean[0];
    private boolean[] intentLinkedRefsAuthoredSource = new boolean[0];
    private int intentCount;

    private boolean[] movedActorSlots = new boolean[0];
    private int[] touchedMovedSlots = new int[0];
    private int touchedMovedCount;
    private final SpatialActorGeometry.Footprint tmpActorFootprint = new SpatialActorGeometry.Footprint();
    private final SpatialActorGeometry.Footprint tmpActorSortFootprint = new SpatialActorGeometry.Footprint();
    private final float[] tmpActorBaseSegment = new float[4];
    private final float[] tmpBlockBottomSegment = new float[4];
    private final float[] tmpActorReferencePoint = new float[2];
    private final float[] tmpBlockFootprint = new float[8];
    private final SpatialBlockLinkedTiles.Refs tmpLinkedTileRefs = new SpatialBlockLinkedTiles.Refs();

    private float tmpProjectionTLeft;
    private float tmpProjectionTRight;
    private boolean tmpInfluencePassed;
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
            int layer = state.layerIndex[data[start]];
            int end = start + 1;
            while (end < drawList.size && state.layerIndex[data[end]] == layer) {
                end++;
            }

            if (isSpatialLayer(layer)) {
                sortSpatialActorsInLayerRun(data, start, end);
            }

            start = end;
        }

        applySpatialBlockOrdering();
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

    private int sortSpatialActorsInLayerRun(int[] data, int start, int end) {
        int count = 0;
        for (int i = start; i < end; i++) {
            int slot = data[i];
            if (!isSpatialActorSlot(slot)) continue;

            ensureActorCapacity(count + 1);
            actorSlots[count] = slot;
            actorPositions[count] = i;
            actorFootY[count] = actorFootY(state.entityId[slot]);
            count++;
        }

        if (count <= 1) return count;

        stableSortActorsByFootY(count);

        for (int i = 0; i < count; i++) {
            data[actorPositions[i]] = actorSlots[i];
        }

        return count;
    }

    private void stableSortActorsByFootY(int count) {
        for (int width = 1; width < count; width <<= 1) {
            for (int left = 0; left < count; left += width << 1) {
                int mid = Math.min(left + width, count);
                int right = Math.min(left + (width << 1), count);
                mergeActorRuns(left, mid, right);
            }

            System.arraycopy(actorScratchSlots, 0, actorSlots, 0, count);
            System.arraycopy(actorScratchFootY, 0, actorFootY, 0, count);
        }
    }

    private void mergeActorRuns(int left, int mid, int right) {
        int a = left;
        int b = mid;
        int write = left;

        while (a < mid && b < right) {
            if (Float.compare(actorFootY[a], actorFootY[b]) >= 0) {
                actorScratchSlots[write] = actorSlots[a];
                actorScratchFootY[write] = actorFootY[a];
                a++;
            } else {
                actorScratchSlots[write] = actorSlots[b];
                actorScratchFootY[write] = actorFootY[b];
                b++;
            }
            write++;
        }

        while (a < mid) {
            actorScratchSlots[write] = actorSlots[a];
            actorScratchFootY[write] = actorFootY[a];
            a++;
            write++;
        }

        while (b < right) {
            actorScratchSlots[write] = actorSlots[b];
            actorScratchFootY[write] = actorFootY[b];
            b++;
            write++;
        }
    }

    private boolean isSpatialActorSlot(int slot) {
        if (!isRenderableSlot(slot)) {
            return false;
        }

        int entity = state.entityId[slot];
        if (entity < 0 || entity >= state.getCapacity()) {
            return false;
        }
        if (!world.getEntityManager().isActive(entity)) {
            return false;
        }

        EntityIndexComponent index = mEntityIndex.getSafe(entity, null);
        if (index == null) {
            return false;
        }
        if (index.layerIndex != state.layerIndex[slot]) {
            return false;
        }
        if (!isSpatialLayer(index.layerIndex)) {
            return false;
        }

        SpatialHeightComponent height = mSpatialHeight.getSafe(entity, null);
        if (height == null) {
            return false;
        }
        if (height.height <= 0f) {
            return false;
        }

        if (!mTransform.has(entity)) {
            return false;
        }
        TransformComponent transform = mTransform.get(entity);
        if (!writeActorPhysicsCircleFootprint(entity, transform, height, tmpActorSortFootprint)) {
            return false;
        }

        return true;
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

    private boolean isSpatialTiledLayer(LayerComponent layer, TiledLayerComponent tiled) {
        return (layer != null && layer.spatialEnabled)
                || (tiled != null && tiled.spatialEnabled)
                || (tiled != null && tiled.data != null && tiled.data.spatialEnabled);
    }

    private void applySpatialBlockOrdering() {
        if (blockLayerCount <= 0 || drawList.size <= 1) return;

        int size = drawList.size;
        ensureSnapshotCapacity(size);
        int[] data = drawList.data();
        System.arraycopy(data, 0, snapshot, 0, size);
        captureSlotDrawIndices(snapshot, size);

        intentCount = 0;
        for (int i = 0; i < size; i++) {
            int slot = snapshot[i];
            if (!isSpatialActorSlot(slot)) continue;
            computeAuthoredSpatialBlockIntent(slot, i);
        }

        if (intentCount <= 0) {
            return;
        }
        rebuildDrawListFromBlockIntents(data, size);
    }

    private void computeAuthoredSpatialBlockIntent(int actorSlot, int stableActorOrderIndex) {
        int actorEntity = state.entityId[actorSlot];
        SpatialHeightComponent actorHeight = mSpatialHeight.getSafe(actorEntity, null);
        if (actorHeight == null || actorHeight.height <= 0f) return;

        TransformComponent actorTransform = mTransform.getSafe(actorEntity, null);
        boolean hasFootprint = writeActorPhysicsCircleFootprint(actorEntity, actorTransform, actorHeight, tmpActorFootprint);
        if (!hasFootprint) {
            return;
        }
        float actorBottom = tmpActorFootprint.bottom;

        boolean hasSelected = false;
        SpatialBlockData selectedBlock = null;
        int selectedOwner = -1;
        int selectedBlockIndex = -1;
        int selectedBlockId = 0;
        int selectedTileId = 0;
        int selectedLinkedTileSlot = -1;
        int selectedLinkedTileDrawIndex = -1;
        int selectedLinkedTileGx = 0;
        int selectedLinkedTileGy = 0;
        int selectedMinLinkedTileSlot = -1;
        int selectedMaxLinkedTileSlot = -1;
        int selectedMinLinkedDrawIndex = -1;
        int selectedMaxLinkedDrawIndex = -1;
        int selectedAuthoredLinkedRefsCount = 0;
        int selectedResolvedLinkedRefsCount = 0;
        int selectedTargetDrawIndex = -1;
        int selectedRelation = BLOCK_RELATION_NOT_APPLICABLE;
        float selectedBlockAltitude = -Float.MAX_VALUE;
        float selectedActorReferenceX = 0f;
        float selectedActorReferenceY = 0f;
        float selectedBlockBottomStartX = 0f;
        float selectedBlockBottomStartY = 0f;
        float selectedBlockBottomEndX = 0f;
        float selectedBlockBottomEndY = 0f;
        float selectedCrossSide = 0f;
        boolean selectedAfterLinkedTile = false;

        writeActorBaseSegment(tmpActorFootprint, tmpActorBaseSegment);
        for (int i = 0; i < blockLayerCount; i++) {
            int owner = blockLayerEntities[i];
            TiledLayerComponent tiled = mTiled.getSafe(owner, null);
            SpatialBlocksComponent blocks = mSpatialBlocks.getSafe(owner, null);
            if (tiled == null || tiled.data == null) continue;
            if (blocks == null || blocks.blocks == null || blocks.blocks.size == 0) continue;

            TiledMapLayerData map = tiled.data;
            for (int blockIndex = 0, n = blocks.blocks.size; blockIndex < n; blockIndex++) {
                SpatialBlockData block = blocks.blocks.get(blockIndex);
                if (block == null || !block.hasAuthoredLinkedTileRefs()) continue;

                resetAuthoredInfluenceDebug();
                if (!SpatialBlockGeometry.isIndexableActorOccluder(block)) {
                    continue;
                }
                if (!writeBlockBottomSegment(map, block, tmpBlockBottomSegment)) {
                    continue;
                }

                SpatialBlockLinkedTiles.compute(block, map, tmpLinkedTileRefs);
                if (tmpLinkedTileRefs.count == 0) {
                    continue;
                }

                boolean hasRenderableLinkedTile = false;
                int minLinkedTileSlot = -1;
                int minLinkedTileDrawIndex = Integer.MAX_VALUE;
                int minLinkedTileGx = 0;
                int minLinkedTileGy = 0;
                int minLinkedTileId = 0;
                int maxLinkedTileSlot = -1;
                int maxLinkedTileDrawIndex = Integer.MIN_VALUE;
                int maxLinkedTileGx = 0;
                int maxLinkedTileGy = 0;
                int maxLinkedTileId = 0;
                for (int linked = 0; linked < tmpLinkedTileRefs.count; linked++) {
                    int tileSlot = tmpLinkedTileRefs.slot(linked);
                    int tileDrawIndex = drawIndexOfSlot(tileSlot);
                    if (tileSlot < 0 || tileDrawIndex < 0 || !isRenderableSlot(tileSlot)) {
                        continue;
                    }
                    hasRenderableLinkedTile = true;
                    int tileId = tmpLinkedTileRefs.tileId(linked);
                    if (tileDrawIndex < minLinkedTileDrawIndex) {
                        minLinkedTileSlot = tileSlot;
                        minLinkedTileDrawIndex = tileDrawIndex;
                        minLinkedTileGx = tmpLinkedTileRefs.gx(linked);
                        minLinkedTileGy = tmpLinkedTileRefs.gy(linked);
                        minLinkedTileId = tileId;
                    }
                    if (tileDrawIndex > maxLinkedTileDrawIndex) {
                        maxLinkedTileSlot = tileSlot;
                        maxLinkedTileDrawIndex = tileDrawIndex;
                        maxLinkedTileGx = tmpLinkedTileRefs.gx(linked);
                        maxLinkedTileGy = tmpLinkedTileRefs.gy(linked);
                        maxLinkedTileId = tileId;
                    }
                }
                if (!hasRenderableLinkedTile) {
                    continue;
                }

                if (!isActorInAuthoredBlockInfluence(tmpActorBaseSegment, tmpBlockBottomSegment, actorBottom, block)) {
                    continue;
                }

                int blockRelation = actorBlockBottomSegmentRelation(map, tmpActorFootprint, block, tmpBlockBottomSegment);
                if (blockRelation == BLOCK_RELATION_NOT_APPLICABLE) {
                    continue;
                }

                boolean afterLinkedTile = blockRelation == BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK;
                int targetDrawIndex = afterLinkedTile
                        ? maxLinkedTileDrawIndex + 1
                        : minLinkedTileDrawIndex - 1;
                int linkedTileSlot = afterLinkedTile ? maxLinkedTileSlot : minLinkedTileSlot;
                int linkedTileDrawIndex = afterLinkedTile ? maxLinkedTileDrawIndex : minLinkedTileDrawIndex;
                int linkedTileGx = afterLinkedTile ? maxLinkedTileGx : minLinkedTileGx;
                int linkedTileGy = afterLinkedTile ? maxLinkedTileGy : minLinkedTileGy;
                int linkedTileId = afterLinkedTile ? maxLinkedTileId : minLinkedTileId;

                int tieBreak = hasSelected
                        ? compareAuthoredSelection(block.altitude, block.id, blockIndex,
                        selectedBlockAltitude, selectedBlockId, selectedBlockIndex)
                        : -1;
                boolean selectedByTieBreak = !hasSelected
                        || tieBreak < 0;
                if (!selectedByTieBreak) {
                    continue;
                }

                hasSelected = true;
                selectedBlock = block;
                selectedOwner = owner;
                selectedBlockIndex = blockIndex;
                selectedBlockId = block.id;
                selectedTileId = linkedTileId;
                selectedLinkedTileSlot = linkedTileSlot;
                selectedLinkedTileDrawIndex = linkedTileDrawIndex;
                selectedLinkedTileGx = linkedTileGx;
                selectedLinkedTileGy = linkedTileGy;
                selectedMinLinkedTileSlot = minLinkedTileSlot;
                selectedMaxLinkedTileSlot = maxLinkedTileSlot;
                selectedMinLinkedDrawIndex = minLinkedTileDrawIndex;
                selectedMaxLinkedDrawIndex = maxLinkedTileDrawIndex;
                selectedAuthoredLinkedRefsCount = tmpLinkedTileRefs.authoredRefCount;
                selectedResolvedLinkedRefsCount = tmpLinkedTileRefs.count;
                selectedTargetDrawIndex = targetDrawIndex;
                selectedRelation = blockRelation;
                selectedBlockAltitude = block.altitude;
                selectedActorReferenceX = tmpActorReferencePoint[0];
                selectedActorReferenceY = tmpActorReferencePoint[1];
                selectedBlockBottomStartX = tmpBlockBottomSegment[0];
                selectedBlockBottomStartY = tmpBlockBottomSegment[1];
                selectedBlockBottomEndX = tmpBlockBottomSegment[2];
                selectedBlockBottomEndY = tmpBlockBottomSegment[3];
                selectedCrossSide = crossSide(tmpBlockBottomSegment[0], tmpBlockBottomSegment[1],
                        tmpBlockBottomSegment[2], tmpBlockBottomSegment[3],
                        tmpActorReferencePoint[0], tmpActorReferencePoint[1]);
                selectedAfterLinkedTile = afterLinkedTile;
            }
        }

        if (hasSelected) {
            addIntent(actorSlot, selectedBlockId, selectedAfterLinkedTile, true, stableActorOrderIndex,
                    selectedMinLinkedTileSlot, selectedMaxLinkedTileSlot,
                    selectedMinLinkedDrawIndex, selectedMaxLinkedDrawIndex,
                    selectedTargetDrawIndex, selectedRelation);
            return;
        }
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

    private void resetAuthoredInfluenceDebug() {
        tmpProjectionTLeft = Float.NaN;
        tmpProjectionTRight = Float.NaN;
        tmpInfluencePassed = false;
    }

    private boolean isActorInAuthoredBlockInfluence(float[] actorBottomSegment,
                                                    float[] blockBottomSegment,
                                                    float actorBaseAltitude,
                                                    SpatialBlockData block) {
        tmpProjectionTLeft = Float.NaN;
        tmpProjectionTRight = Float.NaN;
        tmpInfluencePassed = false;
        if (actorBottomSegment == null || actorBottomSegment.length < 4) return false;
        if (blockBottomSegment == null || blockBottomSegment.length < 4) return false;
        if (!isActorOnBlockAltitude(actorBaseAltitude, block)) return false;
        writeActorReferencePointForBlockBottom(blockBottomSegment, actorBottomSegment, tmpActorReferencePoint);

        float blockDx = blockBottomSegment[2] - blockBottomSegment[0];
        float blockDy = blockBottomSegment[3] - blockBottomSegment[1];
        float blockLength2 = blockDx * blockDx + blockDy * blockDy;
        if (blockLength2 <= BLOCK_BOUNDARY_EPSILON * BLOCK_BOUNDARY_EPSILON) return false;

        tmpProjectionTLeft = projectionT(blockBottomSegment, actorBottomSegment[0], actorBottomSegment[1]);
        tmpProjectionTRight = projectionT(blockBottomSegment, actorBottomSegment[2], actorBottomSegment[3]);

        float actorDx = actorBottomSegment[2] - actorBottomSegment[0];
        float actorDy = actorBottomSegment[3] - actorBottomSegment[1];
        float actorLength = (float) Math.sqrt(actorDx * actorDx + actorDy * actorDy);
        float blockLength = (float) Math.sqrt(blockLength2);
        float margin = blockLength > BLOCK_BOUNDARY_EPSILON
                ? Math.min(0.25f, actorLength / blockLength * 0.5f + BLOCK_BOUNDARY_EPSILON)
                : BLOCK_BOUNDARY_EPSILON;

        float minT = Math.min(tmpProjectionTLeft, tmpProjectionTRight);
        float maxT = Math.max(tmpProjectionTLeft, tmpProjectionTRight);
        tmpInfluencePassed = maxT >= -margin && minT <= 1f + margin;
        return tmpInfluencePassed;
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

    private static int segmentRelationByLineEquation(float[] blockBottomSegment, float[] actorBottomSegment) {
        if (blockBottomSegment == null || blockBottomSegment.length < 4) return BLOCK_RELATION_NOT_APPLICABLE;
        if (actorBottomSegment == null || actorBottomSegment.length < 4) return BLOCK_RELATION_NOT_APPLICABLE;
        float fLeft = lineYAt(blockBottomSegment, actorBottomSegment[0]);
        float fRight = lineYAt(blockBottomSegment, actorBottomSegment[2]);
        if (Float.isNaN(fLeft) || Float.isNaN(fRight)) return BLOCK_RELATION_NOT_APPLICABLE;
        float dLeft = actorBottomSegment[1] - fLeft;
        float dRight = actorBottomSegment[3] - fRight;
        if (dLeft > BLOCK_BOUNDARY_EPSILON && dRight > BLOCK_BOUNDARY_EPSILON) {
            return BLOCK_RELATION_ACTOR_BEHIND_BLOCK;
        }
        if (dLeft < -BLOCK_BOUNDARY_EPSILON && dRight < -BLOCK_BOUNDARY_EPSILON) {
            return BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK;
        }
        return BLOCK_RELATION_NOT_APPLICABLE;
    }

    private static int targetDrawIndexForRelation(int relation, int minLinkedDrawIndex, int maxLinkedDrawIndex) {
        if (relation == BLOCK_RELATION_ACTOR_BEHIND_BLOCK) return minLinkedDrawIndex - 1;
        if (relation == BLOCK_RELATION_ACTOR_IN_FRONT_OF_BLOCK) return maxLinkedDrawIndex + 1;
        return -1;
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

    private static int compareAuthoredSelection(float blockAltitude,
                                                int blockId,
                                                int blockIndex,
                                                float selectedBlockAltitude,
                                                int selectedBlockId,
                                                int selectedBlockIndex) {
        if (Math.abs(blockAltitude - selectedBlockAltitude) > BLOCK_BOUNDARY_EPSILON) {
            return blockAltitude > selectedBlockAltitude ? -1 : 1;
        }
        if (blockId != selectedBlockId) return blockId < selectedBlockId ? -1 : 1;
        if (blockIndex == selectedBlockIndex) return 0;
        return blockIndex < selectedBlockIndex ? -1 : 1;
    }

    int getLastIntentSpatialBlockIdForTest() {
        return intentCount > 0 ? intentSpatialBlockId[intentCount - 1] : 0;
    }

    boolean isLastIntentAuthoredLinkedRefsForTest() {
        return intentCount > 0 && intentLinkedRefsAuthoredSource[intentCount - 1];
    }

    boolean isLastIntentAfterLinkedTileForTest() {
        return intentCount > 0 && intentAfterLinkedTile[intentCount - 1];
    }

    int getLastIntentTargetDrawIndexForTest() {
        return intentCount > 0 ? intentTargetDrawIndex[intentCount - 1] : -1;
    }

    int getLastIntentMinLinkedDrawIndexForTest() {
        return intentCount > 0 ? intentFirstLinkedDrawIndex[intentCount - 1] : -1;
    }

    int getLastIntentMaxLinkedDrawIndexForTest() {
        return intentCount > 0 ? intentLastLinkedDrawIndex[intentCount - 1] : -1;
    }

    private void addIntent(int actorSlot,
                           int spatialBlockId,
                           boolean afterLinkedTile,
                           boolean linkedRefsAuthoredSource,
                           int stableActorOrderIndex,
                           int firstLinkedTileSlot,
                           int lastLinkedTileSlot,
                           int firstLinkedDrawIndex,
                           int lastLinkedDrawIndex,
                           int targetDrawIndex,
                           int blockRelation) {
        ensureIntentCapacity(intentCount + 1);
        int intent = intentCount++;
        intentActorSlot[intent] = actorSlot;
        intentSpatialBlockId[intent] = spatialBlockId;
        intentOriginalActorDrawIndex[intent] = stableActorOrderIndex;
        intentFirstLinkedTileSlot[intent] = firstLinkedTileSlot;
        intentLastLinkedTileSlot[intent] = lastLinkedTileSlot;
        intentLinkedRefsAuthoredSource[intent] = linkedRefsAuthoredSource;
        intentFirstLinkedDrawIndex[intent] = firstLinkedDrawIndex;
        intentLastLinkedDrawIndex[intent] = lastLinkedDrawIndex;
        intentTargetDrawIndex[intent] = targetDrawIndex;
        intentAfterLinkedTile[intent] = afterLinkedTile;
        intentBlockRelation[intent] = blockRelation;
        if (stableActorOrderIndex < 0) {
            // Stable actor order is implicit: intents are collected from the snapshot in order.
        }
    }

    private void rebuildDrawListFromBlockIntents(int[] data, int size) {
        ensureApplyCapacity(state.getCapacity());

        touchedMovedCount = 0;
        int applyCount = buildIntentApplyOrder(size);
        sortIntentApplyOrderDescending(applyCount);
        for (int i = 0; i < applyCount; i++) {
            applyIntentMove(data, size, intentApplyOrder[i]);
        }

        clearMovedActors();
    }

    private int buildIntentApplyOrder(int size) {
        int applyCount = 0;
        ensureIntentApplyOrderCapacity(intentCount);
        for (int intent = 0; intent < intentCount; intent++) {
            int actorSlot = intentActorSlot[intent];
            if (actorSlot < 0 || actorSlot >= movedActorSlots.length) continue;
            if (findDrawIndex(snapshot, size, actorSlot) < 0) continue;
            if (intentTargetDrawIndex[intent] < -1 || intentTargetDrawIndex[intent] > size) continue;

            intentApplyOrder[applyCount++] = intent;
            if (!movedActorSlots[actorSlot]) {
                movedActorSlots[actorSlot] = true;
                ensureTouchedMovedCapacity(touchedMovedCount + 1);
                touchedMovedSlots[touchedMovedCount++] = actorSlot;
            }
        }
        return applyCount;
    }

    private void sortIntentApplyOrderDescending(int count) {
        for (int i = 1; i < count; i++) {
            int intent = intentApplyOrder[i];
            int j = i - 1;
            while (j >= 0 && compareIntentApplyOrder(intent, intentApplyOrder[j]) < 0) {
                intentApplyOrder[j + 1] = intentApplyOrder[j];
                j--;
            }
            intentApplyOrder[j + 1] = intent;
        }
    }

    private int compareIntentApplyOrder(int left, int right) {
        int leftTarget = intentTargetDrawIndex[left];
        int rightTarget = intentTargetDrawIndex[right];
        if (leftTarget != rightTarget) return leftTarget > rightTarget ? -1 : 1;
        int leftOriginal = intentOriginalActorDrawIndex[left];
        int rightOriginal = intentOriginalActorDrawIndex[right];
        if (leftOriginal != rightOriginal) return leftOriginal > rightOriginal ? -1 : 1;
        if (left == right) return 0;
        return left < right ? -1 : 1;
    }

    private void applyIntentMove(int[] data, int size, int intent) {
        int actorSlot = intentActorSlot[intent];
        int currentIndex = findDrawIndex(data, size, actorSlot);
        if (currentIndex < 0) return;

        int targetIndex = currentLinkedRefInsertionIndex(data, size, intent);
        if (targetIndex < 0) return;
        if (currentIndex < targetIndex) {
            targetIndex--;
        }
        if (targetIndex < 0) targetIndex = 0;
        if (targetIndex >= size) targetIndex = size - 1;

        if (currentIndex < targetIndex) {
            for (int i = currentIndex; i < targetIndex; i++) {
                data[i] = data[i + 1];
            }
            data[targetIndex] = actorSlot;
        } else if (currentIndex > targetIndex) {
            for (int i = currentIndex; i > targetIndex; i--) {
                data[i] = data[i - 1];
            }
            data[targetIndex] = actorSlot;
        }
    }

    private int currentLinkedRefInsertionIndex(int[] data, int size, int intent) {
        if (intentBlockRelation[intent] == BLOCK_RELATION_ACTOR_BEHIND_BLOCK) {
            return findDrawIndex(data, size, intentFirstLinkedTileSlot[intent]);
        }
        int lastLinkedPosition = findDrawIndex(data, size, intentLastLinkedTileSlot[intent]);
        return lastLinkedPosition >= 0 ? lastLinkedPosition + 1 : -1;
    }

    private void captureSlotDrawIndices(int[] slots, int size) {
        ensureSlotDrawIndexCapacity(state.getCapacity());
        slotDrawIndexFrameId++;
        if (slotDrawIndexFrameId == 0) {
            for (int i = 0, n = slotDrawIndexFrame.length; i < n; i++) {
                slotDrawIndexFrame[i] = 0;
            }
            slotDrawIndexFrameId = 1;
        }

        for (int i = 0; i < size; i++) {
            int slot = slots[i];
            if (slot < 0 || slot >= slotDrawIndex.length) continue;
            slotDrawIndex[slot] = i;
            slotDrawIndexFrame[slot] = slotDrawIndexFrameId;
        }
    }

    private int drawIndexOfSlot(int slot) {
        if (slot < 0 || slot >= slotDrawIndex.length) return -1;
        return slotDrawIndexFrame[slot] == slotDrawIndexFrameId ? slotDrawIndex[slot] : -1;
    }

    private static int findDrawIndex(int[] data, int size, int slot) {
        if (data == null) return -1;
        for (int i = 0; i < size; i++) {
            if (data[i] == slot) return i;
        }
        return -1;
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

    private void ensureActorCapacity(int required) {
        if (required <= actorSlots.length) return;

        int next = Math.max(8, actorSlots.length);
        while (required > next) next <<= 1;

        int[] expandedSlots = new int[next];
        System.arraycopy(actorSlots, 0, expandedSlots, 0, actorSlots.length);
        actorSlots = expandedSlots;

        int[] expandedScratchSlots = new int[next];
        System.arraycopy(actorScratchSlots, 0, expandedScratchSlots, 0, actorScratchSlots.length);
        actorScratchSlots = expandedScratchSlots;

        int[] expandedPositions = new int[next];
        System.arraycopy(actorPositions, 0, expandedPositions, 0, actorPositions.length);
        actorPositions = expandedPositions;

        float[] expandedFootY = new float[next];
        System.arraycopy(actorFootY, 0, expandedFootY, 0, actorFootY.length);
        actorFootY = expandedFootY;

        float[] expandedScratchFootY = new float[next];
        System.arraycopy(actorScratchFootY, 0, expandedScratchFootY, 0, actorScratchFootY.length);
        actorScratchFootY = expandedScratchFootY;
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

    private void ensureSnapshotCapacity(int required) {
        if (required <= snapshot.length) return;

        int next = Math.max(8, snapshot.length);
        while (required > next) next <<= 1;

        int[] expanded = new int[next];
        System.arraycopy(snapshot, 0, expanded, 0, snapshot.length);
        snapshot = expanded;
    }

    private void ensureSlotDrawIndexCapacity(int required) {
        if (required <= slotDrawIndex.length) return;

        int next = Math.max(8, slotDrawIndex.length);
        while (required > next) next <<= 1;

        int[] expandedIndex = new int[next];
        System.arraycopy(slotDrawIndex, 0, expandedIndex, 0, slotDrawIndex.length);
        slotDrawIndex = expandedIndex;

        int[] expandedFrame = new int[next];
        System.arraycopy(slotDrawIndexFrame, 0, expandedFrame, 0, slotDrawIndexFrame.length);
        slotDrawIndexFrame = expandedFrame;
    }

    private void ensureIntentCapacity(int required) {
        if (required <= intentActorSlot.length) return;

        int next = Math.max(8, intentActorSlot.length);
        while (required > next) next <<= 1;

        intentActorSlot = grow(intentActorSlot, next);
        intentFirstLinkedTileSlot = grow(intentFirstLinkedTileSlot, next);
        intentLastLinkedTileSlot = grow(intentLastLinkedTileSlot, next);
        intentSpatialBlockId = grow(intentSpatialBlockId, next);
        intentOriginalActorDrawIndex = grow(intentOriginalActorDrawIndex, next);
        intentFirstLinkedDrawIndex = grow(intentFirstLinkedDrawIndex, next);
        intentLastLinkedDrawIndex = grow(intentLastLinkedDrawIndex, next);
        intentTargetDrawIndex = grow(intentTargetDrawIndex, next);
        intentBlockRelation = grow(intentBlockRelation, next);

        boolean[] expandedAfter = new boolean[next];
        System.arraycopy(intentAfterLinkedTile, 0, expandedAfter, 0, intentAfterLinkedTile.length);
        intentAfterLinkedTile = expandedAfter;

        boolean[] expandedAuthored = new boolean[next];
        System.arraycopy(intentLinkedRefsAuthoredSource, 0, expandedAuthored, 0, intentLinkedRefsAuthoredSource.length);
        intentLinkedRefsAuthoredSource = expandedAuthored;
    }

    private void ensureIntentApplyOrderCapacity(int required) {
        if (required <= intentApplyOrder.length) return;

        int next = Math.max(8, intentApplyOrder.length);
        while (required > next) next <<= 1;
        intentApplyOrder = grow(intentApplyOrder, next);
    }

    private void ensureApplyCapacity(int required) {
        if (required <= movedActorSlots.length) return;

        int old = movedActorSlots.length;
        int next = Math.max(8, old);
        while (required > next) next <<= 1;

        boolean[] expandedMoved = new boolean[next];
        System.arraycopy(movedActorSlots, 0, expandedMoved, 0, movedActorSlots.length);
        movedActorSlots = expandedMoved;
    }

    private void ensureTouchedMovedCapacity(int required) {
        if (required <= touchedMovedSlots.length) return;

        int next = Math.max(8, touchedMovedSlots.length);
        while (required > next) next <<= 1;
        touchedMovedSlots = grow(touchedMovedSlots, next);
    }

    private void clearMovedActors() {
        for (int i = 0; i < touchedMovedCount; i++) {
            movedActorSlots[touchedMovedSlots[i]] = false;
        }
        touchedMovedCount = 0;
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
        return actorSlots.length
                + actorScratchSlots.length
                + actorPositions.length
                + actorFootY.length
                + actorScratchFootY.length;
    }
}
