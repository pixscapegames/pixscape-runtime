package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;

public final class SpatialRelationSolver {
    static final int NO_RELATION = 0;
    static final int ACTOR_BEHIND_BLOCK = 1;
    static final int ACTOR_IN_FRONT_OF_BLOCK = 2;

    int relationCount;
    int[] relationActorIndex = new int[0];
    int[] relationBlockIndex = new int[0];
    int[] relationType = new int[0];

    private final SpatialRelationKernel relationKernel = new SpatialRelationKernel();
    private final float[] tmpBlockRelationSegments = new float[8];
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

        activeActors = actors;
        int enabledBlocks = validateBlocks(blocks, blockCache);
        if (enabledBlocks != blockCache.blockCount) {
            throw new IllegalStateException("Spatial block cache count does not match enabled authored blocks: expected="
                    + enabledBlocks + " actual=" + blockCache.blockCount);
        }

        for (int actor = 0; actor < actors.actorCount; actor++) {
            int cacheBlock = 0;
            for (int authoredBlock = 0, blockCount = blocks.blocks.size; authoredBlock < blockCount; authoredBlock++) {
                SpatialBlockData block = blocks.blocks.get(authoredBlock);
                if (!block.enabled) continue;
                if (block.actorOccluder && blockCache.hasResolvedBlock(cacheBlock)) {
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

    private void validateResolvedBlock(SpatialBlockData block,
                                       int authoredBlock,
                                       int cacheBlock,
                                       SpatialBlocksRuntimeCache blockCache) {
        if (!SpatialV2Rule.hasValidAuthoredTileRefs(block)) {
            throw new IllegalStateException("Spatial block V2 anchors require valid authored linked tile refs: blockIndex="
                    + authoredBlock);
        }
        if (cacheBlock < 0 || cacheBlock >= blockCache.blockCount) {
            throw new IllegalStateException("Spatial block is missing from runtime cache: blockIndex=" + authoredBlock);
        }
        if (blockCache.blockAnchorCount[cacheBlock] <= 0) {
            throw new IllegalStateException("Spatial block cache entry is unresolved: blockIndex=" + authoredBlock);
        }
    }

    private void solveActorBlock(int actor, SpatialBlockData block, int cacheBlock, TiledMapLayerData map) {
        if (!verticalOverlaps(currentActorBottom(actor), currentActorTop(actor),
                SpatialBlockGeometry.bottom(block), SpatialBlockGeometry.top(block))) {
            return;
        }
        if (!writeBlockLowerRelationSegments(map, block, tmpBlockRelationSegments)) {
            throw new IllegalStateException("Spatial block relation segments could not be resolved: block=" + cacheBlock);
        }

        for (int segment = 0; segment < 2; segment++) {
            int offset = segment * 4;
            int relation = relationKernel.relation(currentActorCenterX(actor),
                    currentActorCenterY(actor),
                    tmpBlockRelationSegments[offset],
                    tmpBlockRelationSegments[offset + 1],
                    tmpBlockRelationSegments[offset + 2],
                    tmpBlockRelationSegments[offset + 3]);
            if (relation == ACTOR_BEHIND_BLOCK || relation == ACTOR_IN_FRONT_OF_BLOCK) {
                addRelation(actor, cacheBlock, relation);
                return;
            }
        }
    }

    private void addRelation(int actor, int block, int relation) {
        ensureRelationCapacity(relationCount + 1);
        relationActorIndex[relationCount] = actor;
        relationBlockIndex[relationCount] = block;
        relationType[relationCount] = relation;
        relationCount++;
    }

    private boolean writeBlockLowerRelationSegments(TiledMapLayerData map, SpatialBlockData block, float[] out8) {
        if (out8 == null || out8.length < 8) return false;
        if (!SpatialBlockGeometry.writeTileCellFootprint(block, map, tmpBlockFootprint)) return false;

        int lowerVertex = lowerBaseVertex(tmpBlockFootprint);
        int previous = (lowerVertex + 3) & 3;
        int next = (lowerVertex + 1) & 3;
        writeSegmentLeftToRight(tmpBlockFootprint, previous, lowerVertex, out8, 0);
        writeSegmentLeftToRight(tmpBlockFootprint, lowerVertex, next, out8, 4);
        return true;
    }

    private static int lowerBaseVertex(float[] footprint) {
        int lowerVertex = 0;
        float lowerY = footprint[1];
        float lowerX = footprint[0];
        for (int vertex = 1; vertex < 4; vertex++) {
            int offset = vertex * 2;
            float y = footprint[offset + 1];
            float x = footprint[offset];
            if (y > lowerY || (y == lowerY && x > lowerX)) {
                lowerVertex = vertex;
                lowerY = y;
                lowerX = x;
            }
        }
        return lowerVertex;
    }

    private static void writeSegmentLeftToRight(float[] footprint,
                                                int a,
                                                int b,
                                                float[] out,
                                                int outOffset) {
        int ao = a * 2;
        int bo = b * 2;
        float ax = footprint[ao];
        float ay = footprint[ao + 1];
        float bx = footprint[bo];
        float by = footprint[bo + 1];
        if (ax <= bx) {
            out[outOffset] = ax;
            out[outOffset + 1] = ay;
            out[outOffset + 2] = bx;
            out[outOffset + 3] = by;
        } else {
            out[outOffset] = bx;
            out[outOffset + 1] = by;
            out[outOffset + 2] = ax;
            out[outOffset + 3] = ay;
        }
    }

    private static boolean verticalOverlaps(float actorBottom,
                                            float actorTop,
                                            float blockBottom,
                                            float blockTop) {
        return actorTop > blockBottom && blockTop > actorBottom;
    }

    private float currentActorBottom(int actor) {
        return activeActors.actorAltitude[actor];
    }

    private float currentActorTop(int actor) {
        return activeActors.actorAltitude[actor] + activeActors.actorHeight[actor];
    }

    private float currentActorCenterX(int actor) {
        return activeActors.actorCircleX[actor];
    }

    private float currentActorCenterY(int actor) {
        return activeActors.actorCircleY[actor];
    }

    private SpatialActorCollector activeActors;

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
