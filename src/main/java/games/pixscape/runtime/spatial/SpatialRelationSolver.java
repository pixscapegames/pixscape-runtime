package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialRelationSolver {
    static final int NO_RELATION = 0;
    static final int ACTOR_BEHIND_BLOCK = 1;
    static final int ACTOR_IN_FRONT_OF_BLOCK = 2;

    private static final float BLOCK_BOUNDARY_EPSILON = 0.0001f;

    int relationCount;
    int[] relationActorIndex = new int[0];
    int[] relationBlockIndex = new int[0];
    int[] relationType = new int[0];

    private final SpatialActorGeometry.Footprint tmpActorFootprint = new SpatialActorGeometry.Footprint();
    private final float[] tmpActorBaseSegment = new float[4];
    private final float[] tmpBlockBottomSegment = new float[4];
    private final float[] tmpActorReferencePoint = new float[2];
    private final float[] tmpBlockFootprint = new float[8];

    public void clear() {
        relationCount = 0;
    }

    public void solve(SpatialActorCollector actors,
                      SpatialBlocksRuntimeCache blockCache,
                      SpatialBlocksComponent blocks,
                      TiledMapLayerData map) {
        clear();
        if (actors == null || actors.actorCount == 0) return;
        if (blockCache == null) {
            throw new IllegalArgumentException("Spatial block runtime cache is required.");
        }
        if (blocks == null || blocks.blocks == null || blocks.blocks.size == 0) return;
        if (map == null) {
            throw new IllegalArgumentException("Owning tiled layer runtime data is required.");
        }

        int enabledBlocks = validateBlocks(blocks, blockCache);
        if (enabledBlocks != blockCache.blockCount) {
            throw new IllegalStateException("Spatial block cache count does not match enabled authored blocks: expected="
                    + enabledBlocks + " actual=" + blockCache.blockCount);
        }

        for (int actor = 0; actor < actors.actorCount; actor++) {
            writeActorFootprint(actors, actor, tmpActorFootprint);
            int cacheBlock = 0;
            for (int authoredBlock = 0, blockCount = blocks.blocks.size; authoredBlock < blockCount; authoredBlock++) {
                SpatialBlockData block = blocks.blocks.get(authoredBlock);
                if (!block.enabled) continue;
                if (block.actorOccluder) {
                    solveActorBlock(actor, block, cacheBlock, map);
                }
                cacheBlock++;
            }
        }
    }

    private int validateBlocks(SpatialBlocksComponent blocks, SpatialBlocksRuntimeCache blockCache) {
        int cacheBlock = 0;
        for (int authoredBlock = 0, blockCount = blocks.blocks.size; authoredBlock < blockCount; authoredBlock++) {
            SpatialBlockData block = blocks.blocks.get(authoredBlock);
            if (block == null) {
                throw new IllegalStateException("Spatial block is null at index " + authoredBlock);
            }
            if (!block.enabled) continue;
            validateResolvedBlock(block, authoredBlock, cacheBlock, blockCache);

            if (block.actorOccluder) {
                if (!SpatialBlockGeometry.isIndexableActorOccluder(block)) {
                    throw new IllegalStateException("Spatial actor-occluder block is not valid for relation solving: blockIndex="
                            + authoredBlock);
                }
            }
            cacheBlock++;
        }
        return cacheBlock;
    }

    public int relationCount() {
        return relationCount;
    }

    int relationForActorAndBlock(int actorIndex, int blockIndex) {
        for (int i = 0; i < relationCount; i++) {
            if (relationActorIndex[i] == actorIndex && relationBlockIndex[i] == blockIndex) {
                return relationType[i];
            }
        }
        return NO_RELATION;
    }

    private void validateResolvedBlock(SpatialBlockData block,
                                       int authoredBlock,
                                       int cacheBlock,
                                       SpatialBlocksRuntimeCache blockCache) {
        if (!SpatialBlockV1Rules.hasStraightContinuousAuthoredTileRefs(block)) {
            throw new IllegalStateException("Spatial block V1 anchors must be a straight continuous segment: blockIndex="
                    + authoredBlock);
        }
        if (cacheBlock < 0 || cacheBlock >= blockCache.blockCount) {
            throw new IllegalStateException("Spatial block is missing from runtime cache: blockIndex=" + authoredBlock);
        }
        if (blockCache.blockAnchorCount[cacheBlock] <= 0
                || blockCache.blockAnchorStartDrawIndex[cacheBlock] < 0
                || blockCache.blockAnchorEndDrawIndex[cacheBlock] < 0) {
            throw new IllegalStateException("Spatial block cache entry is unresolved: blockIndex=" + authoredBlock);
        }
    }

    private void solveActorBlock(int actor, SpatialBlockData block, int cacheBlock, TiledMapLayerData map) {
        if (!verticalOverlaps(tmpActorFootprint.bottom, tmpActorFootprint.top,
                SpatialBlockGeometry.bottom(block), SpatialBlockGeometry.top(block))) {
            return;
        }
        if (!writeBlockBottomSegment(map, block, tmpBlockBottomSegment)) {
            throw new IllegalStateException("Spatial block bottom segment could not be resolved: block=" + cacheBlock);
        }
        writeActorBaseSegment(tmpActorFootprint, tmpActorBaseSegment);
        if (!isActorInAuthoredBlockInfluence(tmpActorBaseSegment, tmpBlockBottomSegment,
                tmpActorFootprint.bottom, block)) {
            return;
        }

        int relation = actorCircleRelationByWallLine(tmpActorFootprint, tmpBlockBottomSegment);
        if (relation == ACTOR_BEHIND_BLOCK || relation == ACTOR_IN_FRONT_OF_BLOCK) {
            addRelation(actor, cacheBlock, relation);
        }
    }

    private void addRelation(int actor, int block, int relation) {
        ensureRelationCapacity(relationCount + 1);
        relationActorIndex[relationCount] = actor;
        relationBlockIndex[relationCount] = block;
        relationType[relationCount] = relation;
        relationCount++;
    }

    private static void writeActorFootprint(SpatialActorCollector actors,
                                            int actor,
                                            SpatialActorGeometry.Footprint out) {
        float radius = actors.actorCircleRadius[actor];
        float cx = actors.actorCircleX[actor];
        float cy = actors.actorCircleY[actor];
        out.footX = actors.actorFootX[actor];
        out.footY = actors.actorFootY[actor];
        out.minX = cx - radius;
        out.maxX = cx + radius;
        out.minY = cy - radius;
        out.maxY = cy + radius;
        out.bottom = actors.actorAltitude[actor];
        out.top = actors.actorAltitude[actor] + actors.actorHeight[actor];
        out.pointOnly = false;
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

    private static void writeActorBaseSegment(SpatialActorGeometry.Footprint footprint, float[] out4) {
        out4[0] = footprint.minX;
        out4[1] = footprint.maxY;
        out4[2] = footprint.maxX;
        out4[3] = footprint.maxY;
    }

    private static int actorCircleRelationByWallLine(SpatialActorGeometry.Footprint footprint,
                                                     float[] blockBottomSegment) {
        if (footprint == null) return NO_RELATION;
        float cx = actorFootCenterX(footprint);
        float cy = actorFootCenterY(footprint);
        float radius = actorFootRadius(footprint);
        float lineY = lineYAt(blockBottomSegment, cx);
        if (Float.isNaN(lineY)) return NO_RELATION;
        float centerT = projectionT(blockBottomSegment, cx, cy);
        if (centerT < -BLOCK_BOUNDARY_EPSILON || centerT > 1f + BLOCK_BOUNDARY_EPSILON) {
            return pointRelationByLineEquation(blockBottomSegment, cx, cy);
        }
        float d = lineY - cy;
        if (d > radius + BLOCK_BOUNDARY_EPSILON) return ACTOR_IN_FRONT_OF_BLOCK;
        if (d < -radius - BLOCK_BOUNDARY_EPSILON) return ACTOR_BEHIND_BLOCK;
        return ACTOR_IN_FRONT_OF_BLOCK;
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

    private static int pointRelationByLineEquation(float[] blockBottomSegment, float pointX, float pointY) {
        float lineY = lineYAt(blockBottomSegment, pointX);
        if (Float.isNaN(lineY)) return NO_RELATION;
        float d = pointY - lineY;
        if (d > BLOCK_BOUNDARY_EPSILON) return ACTOR_BEHIND_BLOCK;
        if (d < -BLOCK_BOUNDARY_EPSILON) return ACTOR_IN_FRONT_OF_BLOCK;
        return NO_RELATION;
    }

    private static float lineYAt(float[] segment, float x) {
        if (segment == null || segment.length < 4) return Float.NaN;
        float dx = segment[2] - segment[0];
        if (Math.abs(dx) <= BLOCK_BOUNDARY_EPSILON) return Float.NaN;
        float slope = (segment[3] - segment[1]) / dx;
        return segment[1] + slope * (x - segment[0]);
    }

    private static float projectionT(float[] segment, float pointX, float pointY) {
        float dx = segment[2] - segment[0];
        float dy = segment[3] - segment[1];
        float length2 = dx * dx + dy * dy;
        if (length2 <= BLOCK_BOUNDARY_EPSILON * BLOCK_BOUNDARY_EPSILON) return Float.NaN;
        return ((pointX - segment[0]) * dx + (pointY - segment[1]) * dy) / length2;
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

    private void ensureRelationCapacity(int required) {
        if (required <= relationActorIndex.length) return;
        int next = Math.max(8, relationActorIndex.length);
        while (required > next) next <<= 1;
        relationActorIndex = grow(relationActorIndex, next);
        relationBlockIndex = grow(relationBlockIndex, next);
        relationType = grow(relationType, next);
    }

    private static int[] grow(int[] source, int next) {
        int[] expanded = new int[next];
        System.arraycopy(source, 0, expanded, 0, source.length);
        return expanded;
    }
}
