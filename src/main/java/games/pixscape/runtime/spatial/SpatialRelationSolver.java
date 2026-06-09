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
    private final float[] tmpBlockRelationSegment = new float[4];
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
        if (!verticalOverlaps(currentActorBottom(actor), currentActorTop(actor),
                SpatialBlockGeometry.bottom(block), SpatialBlockGeometry.top(block))) {
            return;
        }
        if (!writeBlockTopSegment(map, block, tmpBlockRelationSegment)) {
            throw new IllegalStateException("Spatial block relation segment could not be resolved: block=" + cacheBlock);
        }

        int relation = relationKernel.relation(currentActorCenterX(actor),
                currentActorCenterY(actor),
                tmpBlockRelationSegment[0],
                tmpBlockRelationSegment[1],
                tmpBlockRelationSegment[2],
                tmpBlockRelationSegment[3]);
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

    private boolean writeBlockTopSegment(TiledMapLayerData map, SpatialBlockData block, float[] out4) {
        if (out4 == null || out4.length < 4) return false;
        if (!SpatialBlockGeometry.writeTileCellFootprint(block, map, tmpBlockFootprint)) return false;
        if (block.width >= block.depth) {
            out4[0] = tmpBlockFootprint[0];
            out4[1] = tmpBlockFootprint[1];
            out4[2] = tmpBlockFootprint[2];
            out4[3] = tmpBlockFootprint[3];
        } else {
            out4[0] = tmpBlockFootprint[0];
            out4[1] = tmpBlockFootprint[1];
            out4[2] = tmpBlockFootprint[6];
            out4[3] = tmpBlockFootprint[7];
        }
        return true;
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
