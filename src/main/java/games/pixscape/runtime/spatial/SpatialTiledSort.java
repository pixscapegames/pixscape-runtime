package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.TiledMapRenderState;

import java.util.Arrays;
import java.util.HashSet;

/**
 * Default tiled ordering adjustment for spatial-enabled ISO tiled layers.
 *
 * <p>SpatialTiledSort preserves ISO {@code sortZ = -(gx + gy)} as the primary
 * order and adjusts only tie ordering inside an ISO diagonal so exclusive anchors
 * of the same spatial block remain compatible with the spatial actor/block
 * interval planner.</p>
 *
 * <p>Shared/junction anchors are allowed and treated as neutral anchors. They do
 * not disable the system and do not contribute to block interval bounds.</p>
 *
 * <p>This class does not solve corner/cube geometry rules. It only keeps tiled
 * anchor ordering compatible with the current spatial actor ordering system.</p>
 */
public final class SpatialTiledSort {
    public static final String PROPERTY = "pixscape.tiled.spatialSort";
    public static final String VERIFY_PROPERTY = "pixscape.tiled.spatialSortVerify";

    private static final boolean ENABLED = !"false".equalsIgnoreCase(System.getProperty(PROPERTY));
    private static final boolean VERIFY = "true".equalsIgnoreCase(System.getProperty(VERIFY_PROPERTY));

    private static final HashSet<String> refusalLogs = new HashSet<>();
    private static boolean verifyLayerLogged;
    private static boolean verifySceneLogged;
    private static boolean verifyBlocksLogged;
    private static boolean verifyTotoLogged;
    private static boolean verifySignoffLogged;
    private static boolean verifyNotTestedLogged;

    private static int signoffLayer = -1;
    private static int signoffLayerIndex = -1;
    private static boolean signoffAppliedToLayer;
    private static DisabledReason signoffDisabledReason = DisabledReason.PROPERTY_DISABLED;
    private static VerifyBlock[] signoffBlocks = new VerifyBlock[0];
    private static int signoffBlockCount;

    private SpatialTiledSort() {
    }

    public static boolean isEnabled() {
        return ENABLED;
    }

    public static Context contextForLayer(int layerEntity,
                                          LayerComponent layer,
                                          TiledLayerComponent tiled,
                                          SpatialBlocksComponent blocks) {
        return contextForLayer(layerEntity, layer, tiled, blocks, ENABLED);
    }

    static Context contextForLayer(int layerEntity,
                                   LayerComponent layer,
                                   TiledLayerComponent tiled,
                                   SpatialBlocksComponent blocks,
                                   boolean spatialSortEnabled) {
        if (!spatialSortEnabled) {
            return disabled(layerEntity, DisabledReason.PROPERTY_DISABLED);
        }
        if (!isSpatialTiledLayer(layer, tiled)) {
            return disabled(layerEntity, DisabledReason.NOT_SPATIAL_LAYER);
        }
        if (tiled == null || tiled.data == null
                || tiled.data.projection != SceneMetaRuntime.TiledProjection.ISO) {
            return disabled(layerEntity, DisabledReason.NOT_ISO);
        }
        if (blocks == null || blocks.blocks == null || blocks.blocks.size == 0) {
            return disabled(layerEntity, DisabledReason.NO_EXCLUSIVE_ANCHOR);
        }

        Context context = new Context(true);
        context.layerEntity = layerEntity;
        context.disabledReason = DisabledReason.NONE;
        context.tieRange = tiled.data.mapWidth;
        if (context.tieRange <= 0) {
            refuse(context, DisabledReason.TIE_OVERFLOW, "invalidMapWidth", layer, tiled, null);
            return context;
        }

        for (int blockIndex = 0; blockIndex < blocks.blocks.size; blockIndex++) {
            SpatialBlockData block = blocks.blocks.get(blockIndex);
            if (block == null || !block.enabled || block.linkedTileRefs == null
                    || block.linkedTileRefs.size == 0) {
                continue;
            }
            for (int refIndex = 0; refIndex < block.linkedTileRefs.size; refIndex++) {
                SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(refIndex);
                if (ref == null) continue;
                if (ref.gx < 0 || ref.gx >= context.tieRange) {
                    refuse(context, DisabledReason.TIE_OVERFLOW, "linkedRefOutsideTieRange", layer, tiled, block);
                    return context;
                }
                context.addTileOwner(ref.gx, ref.gy, blockIndex, block.id, block.name);
            }
        }

        context.finalizeSharedJunctions();

        for (int blockIndex = 0; blockIndex < blocks.blocks.size; blockIndex++) {
            SpatialBlockData block = blocks.blocks.get(blockIndex);
            if (block == null || !block.enabled || block.linkedTileRefs == null
                    || block.linkedTileRefs.size == 0) {
                continue;
            }
            BlockOrder order = new BlockOrder();
            order.blockIndex = blockIndex;
            order.blockId = block.id;
            order.blockName = block.name;
            order.minSortZ = Integer.MAX_VALUE;
            order.minTie = Integer.MAX_VALUE;
            for (int refIndex = 0; refIndex < block.linkedTileRefs.size; refIndex++) {
                SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(refIndex);
                if (ref == null || !context.isExclusiveOwner(ref.gx, ref.gy, blockIndex)) continue;
                int sortZ = -(ref.gx + ref.gy);
                int tie = ref.gx;
                if (sortZ < order.minSortZ || (sortZ == order.minSortZ && tie < order.minTie)) {
                    order.minSortZ = sortZ;
                    order.minTie = tie;
                }
            }
            if (order.minSortZ != Integer.MAX_VALUE) {
                context.addBlock(order);
            }
        }

        context.assignBlockRanks();
        long maxEncodedTie = ((long) context.blockCount + 1L) * (long) context.tieRange - 1L;
        if (maxEncodedTie > SortKey64.MAX_TIE) {
            context.tieOverflow = true;
            refuse(context, DisabledReason.TIE_OVERFLOW,
                    "tieCapacityExceeded maxEncodedTie=" + maxEncodedTie, layer, tiled, null);
            return context;
        }

        context.valid = true;
        return context;
    }

    public static int encodeTie(Context context, int gx, int gy, int originalTie) {
        if (context == null || !context.active || !context.valid) return originalTie;
        Owner owner = context.exclusiveOwner(gx, gy);
        if (owner == null) return originalTie;
        int blockRank = context.blockRank(owner.blockIndex);
        if (blockRank < 0) return originalTie;
        return (blockRank + 1) * context.tieRange + originalTie;
    }

    public static void verifyLayer(int layerEntity,
                                   LayerComponent layer,
                                   TiledLayerComponent tiled,
                                   SpatialBlocksComponent blocks,
                                   TiledMapRenderState tiledState,
                                   int[] slotToDrawIndex,
                                   int tiledLayerCount,
                                   Context context,
                                   SpatialActorCollector actors,
                                   SpatialRelationSolver relations) {
        if (!VERIFY || layer == null || tiled == null || tiled.data == null) return;

        if (context == null) {
            context = contextForLayer(layerEntity, layer, tiled, blocks, ENABLED);
        }
        boolean[] relatedBlocks = relatedBlocksForToto3(blocks != null && blocks.blocks != null
                ? blocks.blocks.size
                : 0, actors, relations);
        boolean hasToto3Relations = hasAnyIncludedBlock(relatedBlocks);
        if (!hasToto3Relations && context.applies()) return;

        if (!verifyLayerLogged) {
            boolean spatialEnabled = isSpatialTiledLayer(layer, tiled);
            rememberSignoffLayer(layerEntity, layer, context, tiled, blocks);
            if (!verifySceneLogged) {
                System.out.println("SPATIAL_TILED_SORT_SCENE_INFO"
                        + " mapWidth=" + tiled.data.mapWidth
                        + " mapHeight=" + tiled.data.mapHeight
                        + " tiledLayerCount=" + tiledLayerCount
                        + " spatialBlockCountForLayer=" + (blocks != null && blocks.blocks != null
                        ? blocks.blocks.size
                        : 0)
                        + " actorCount=" + (actors != null ? actors.actorCount : 0));
                verifySceneLogged = true;
            }
            System.out.println("SPATIAL_TILED_SORT_VERIFY"
                    + " flagEnabled=" + ENABLED
                    + " layer=" + layerEntity
                    + " layerIndex=" + layer.layerIndex
                    + " projection=" + tiled.data.projection
                    + " spatialEnabled=" + spatialEnabled
                    + " spatialSortApplied=" + context.applies()
                    + " disabledReason=" + disabledReasonText(context.disabledReason)
                    + " sharedJunctionCount=" + context.sharedJunctionCount
                    + " tieOverflow=" + context.tieOverflow);
            logSharedJunctions(layerEntity, context);
            logNoExclusiveBlocks(layerEntity, context, blocks);
            verifyLayerLogged = true;
        }

        if (!verifyBlocksLogged && logVerifyBlocks(layerEntity, context, tiled, blocks, tiledState, slotToDrawIndex,
                relatedBlocks)) {
            verifyBlocksLogged = true;
        }
    }

    static void verifyToto3(SpatialActorCollector actors, SpatialBucketPlanner planner) {
        if (!VERIFY || verifyTotoLogged || actors == null || planner == null) return;
        int actor = -1;
        for (int i = 0; i < actors.actorCount; i++) {
            String name = actors.actorName != null && i < actors.actorName.length ? actors.actorName[i] : null;
            if ("Toto3".equals(name)) {
                actor = i;
                break;
            }
        }

        if (actor < 0) {
            return;
        } else {
            int lower = planner.actorLowerBound[actor];
            int upper = planner.actorUpperBound[actor];
            int resolvedLower = lower == Integer.MIN_VALUE ? 0 : lower;
            int resolvedUpper = upper == Integer.MAX_VALUE ? planner.bucketCount - 1 : upper;
            System.out.println("SPATIAL_TILED_SORT_VERIFY_TOTO3"
                    + " found=true"
                    + " lowerBound=" + boundName(lower, true)
                    + " lowerSourceBlockId=" + planner.actorLowerSourceBlockId[actor]
                    + " upperBound=" + boundName(upper, false)
                    + " upperSourceBlockId=" + planner.actorUpperSourceBlockId[actor]
                    + " lowerGreaterThanUpper=" + (resolvedLower > resolvedUpper));
        }
        verifyTotoLogged = true;
    }

    static void verifyToto3Signoff(SpatialActorCollector actors,
                                   SpatialBucketPlanner planner,
                                   int[] composedSlots,
                                   int composedSize) {
        if (!VERIFY || verifySignoffLogged || actors == null || planner == null) return;
        if (signoffLayer < 0) return;
        int actor = findActor(actors, "Toto3");
        if (actor < 0) {
            return;
        }

        int lower = planner.actorLowerBound[actor];
        int upper = planner.actorUpperBound[actor];
        int resolvedLower = lower == Integer.MIN_VALUE ? 0 : lower;
        int resolvedUpper = upper == Integer.MAX_VALUE ? planner.bucketCount - 1 : upper;
        int actorFinalIndex = planner.finalActorDrawIndex[actor];
        int expectedInFrontBlockId = planner.actorLowerSourceBlockId[actor];
        int expectedBehindBlockId = planner.actorUpperSourceBlockId[actor];
        int[] inFrontRange = finalRangeForBlock(expectedInFrontBlockId, composedSlots, composedSize);
        int[] behindRange = finalRangeForBlock(expectedBehindBlockId, composedSlots, composedSize);
        boolean actorAfterExpectedInFrontBlock = actorFinalIndex >= 0
                && inFrontRange[1] >= 0
                && actorFinalIndex > inFrontRange[1];
        boolean actorBeforeExpectedBehindBlock = actorFinalIndex >= 0
                && behindRange[0] >= 0
                && actorFinalIndex < behindRange[0];

        System.out.println("SPATIAL_TILED_SORT_TOTO3_SIGNOFF"
                + " propertyEnabled=" + ENABLED
                + " appliedToLayer=" + signoffAppliedToLayer
                + " layer=" + signoffLayer
                + " layerIndex=" + signoffLayerIndex
                + " disabledReason=" + disabledReasonText(signoffDisabledReason)
                + " toto3Found=true"
                + " lowerGreaterThanUpper=" + (resolvedLower > resolvedUpper)
                + " actorFinalIndex=" + actorFinalIndex
                + " expectedBehindBlockId=" + expectedBehindBlockId
                + " expectedBehindBlockMinIndex=" + behindRange[0]
                + " expectedBehindBlockMaxIndex=" + behindRange[1]
                + " actorBeforeExpectedBehindBlock=" + actorBeforeExpectedBehindBlock
                + " expectedInFrontOfBlockId=" + expectedInFrontBlockId
                + " expectedInFrontBlockMinIndex=" + inFrontRange[0]
                + " expectedInFrontBlockMaxIndex=" + inFrontRange[1]
                + " actorAfterExpectedInFrontBlock=" + actorAfterExpectedInFrontBlock);
        if (!signoffAppliedToLayer) {
            logNotTested();
        }
        verifySignoffLogged = true;
    }

    private static boolean isSpatialTiledLayer(LayerComponent layer, TiledLayerComponent tiled) {
        return (layer != null && layer.spatialEnabled)
                || (tiled != null && tiled.spatialEnabled)
                || (tiled != null && tiled.data != null && tiled.data.spatialEnabled);
    }

    private static Context disabled(int layerEntity, DisabledReason reason) {
        Context context = new Context(false);
        context.layerEntity = layerEntity;
        context.disabledReason = reason;
        return context;
    }

    private static void refuse(Context context,
                               DisabledReason disabledReason,
                               String reason,
                               LayerComponent layer,
                               TiledLayerComponent tiled,
                               SpatialBlockData block) {
        context.valid = false;
        context.disabledReason = disabledReason;
        String key = "disabled:" + context.layerEntity + ":" + reason;
        if (!refusalLogs.add(key)) return;
        System.out.println("SPATIAL_TILED_SORT_DISABLED"
                + " layer=" + context.layerEntity
                + " layerIndex=" + (layer != null ? layer.layerIndex : -1)
                + " reason=" + reason
                + " mapWidth=" + (tiled != null && tiled.data != null ? tiled.data.mapWidth : -1)
                    + " blockId=" + (block != null ? block.id : 0));
    }

    private static void rememberSignoffLayer(int layerEntity,
                                             LayerComponent layer,
                                             Context context,
                                             TiledLayerComponent tiled,
                                             SpatialBlocksComponent blocks) {
        signoffLayer = layerEntity;
        signoffLayerIndex = layer != null ? layer.layerIndex : -1;
        signoffAppliedToLayer = context != null && context.applies();
        signoffDisabledReason = context != null ? context.disabledReason : DisabledReason.PROPERTY_DISABLED;
        signoffBlockCount = 0;
        if (blocks == null || blocks.blocks == null || tiled == null || tiled.data == null) return;
        ensureVerifyBlockCapacity(blocks.blocks.size);
        for (int blockIndex = 0; blockIndex < blocks.blocks.size; blockIndex++) {
            SpatialBlockData block = blocks.blocks.get(blockIndex);
            if (block == null || block.linkedTileRefs == null) continue;
            VerifyBlock snapshot = signoffBlocks[signoffBlockCount];
            if (snapshot == null) {
                snapshot = new VerifyBlock();
                signoffBlocks[signoffBlockCount] = snapshot;
            }
            snapshot.blockId = block.id;
            snapshot.blockIndex = blockIndex;
            snapshot.anchorCount = 0;
            snapshot.ensureCapacity(block.linkedTileRefs.size);
            for (int refIndex = 0; refIndex < block.linkedTileRefs.size; refIndex++) {
                SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(refIndex);
                if (ref == null) continue;
                if (context != null && context.isShared(ref.gx, ref.gy)) continue;
                snapshot.anchorSlots[snapshot.anchorCount++] = tiled.data.slotForTile(ref.gx, ref.gy);
            }
            signoffBlockCount++;
        }
    }

    private static void ensureVerifyBlockCapacity(int required) {
        if (required <= signoffBlocks.length) return;
        int next = Math.max(4, signoffBlocks.length);
        while (required > next) next <<= 1;
        signoffBlocks = Arrays.copyOf(signoffBlocks, next);
    }

    private static void logSharedJunctions(int layerEntity, Context context) {
        if (context == null || context.sharedJunctionCount == 0) return;
        for (int i = 0; i < context.ownershipCount; i++) {
            TileOwnership tile = context.ownerships[i];
            if (tile == null || tile.ownerCount <= 1) continue;
            String key = "sharedJunction:" + layerEntity + ":" + tile.gx + ":" + tile.gy;
            if (!VERIFY && !refusalLogs.add(key)) continue;
            System.out.println("SPATIAL_TILED_SORT_SHARED_JUNCTION"
                    + " layer=" + layerEntity
                    + " gx=" + tile.gx
                    + " gy=" + tile.gy
                    + " owners=" + ownersText(tile)
                    + " action=shared_anchor");
        }
    }

    private static void logBlockNoExclusiveAnchor(int layerEntity,
                                                  int blockIndex,
                                                  int blockId,
                                                  StringBuilder sharedAnchors) {
        String key = "blockNoExclusive:" + layerEntity + ":" + blockIndex + ":" + blockId;
        if (!refusalLogs.add(key)) return;
        System.out.println("SPATIAL_TILED_SORT_BLOCK_NO_EXCLUSIVE_ANCHOR"
                + " layer=" + layerEntity
                + " blockIndex=" + blockIndex
                + " blockId=" + blockId
                + " sharedAnchors=" + sharedAnchors
                + " action=block_interval_disabled");
    }

    private static void logNoExclusiveBlocks(int layerEntity,
                                             Context context,
                                             SpatialBlocksComponent blocks) {
        if (context == null || blocks == null || blocks.blocks == null) return;
        for (int blockIndex = 0; blockIndex < blocks.blocks.size; blockIndex++) {
            SpatialBlockData block = blocks.blocks.get(blockIndex);
            if (block == null || !block.enabled || block.linkedTileRefs == null
                    || block.linkedTileRefs.size == 0) {
                continue;
            }
            int exclusiveCount = 0;
            StringBuilder sharedAnchors = new StringBuilder("[");
            int sharedCount = 0;
            for (int refIndex = 0; refIndex < block.linkedTileRefs.size; refIndex++) {
                SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(refIndex);
                if (ref == null) continue;
                if (context.isExclusiveOwner(ref.gx, ref.gy, blockIndex)) {
                    exclusiveCount++;
                } else if (context.isShared(ref.gx, ref.gy)) {
                    if (sharedCount++ > 0) sharedAnchors.append(';');
                    TileOwnership ownership = context.ownership(ref.gx, ref.gy);
                    sharedAnchors.append(ref.gx).append(',')
                            .append(ref.gy).append(',')
                            .append(-1).append(',')
                            .append(ownersText(ownership));
                }
            }
            sharedAnchors.append(']');
            if (exclusiveCount == 0 && sharedCount > 0) {
                logBlockNoExclusiveAnchor(layerEntity, blockIndex, block.id, sharedAnchors);
            }
        }
    }

    private static void logNotTested() {
        if (verifyNotTestedLogged) return;
        System.out.println("SPATIAL_TILED_SORT_NOT_TESTED"
                + " reason=\"SpatialTiledSort did not apply to Toto3's layer; visual result is old ISO order\"");
        verifyNotTestedLogged = true;
    }

    private static boolean logVerifyBlocks(int layerEntity,
                                           Context context,
                                           TiledLayerComponent tiled,
                                           SpatialBlocksComponent blocks,
                                           TiledMapRenderState tiledState,
                                           int[] slotToDrawIndex,
                                           boolean[] included) {
        if (blocks == null || blocks.blocks == null || tiledState == null || slotToDrawIndex == null) return false;
        boolean wrote = false;
        for (int blockIndex = 0; blockIndex < blocks.blocks.size; blockIndex++) {
            SpatialBlockData block = blocks.blocks.get(blockIndex);
            if (block == null || block.linkedTileRefs == null) continue;
            if (hasAnyIncludedBlock(included) && !included[blockIndex]) continue;
            if (!hasAnyIncludedBlock(included) && block.id != 3 && block.id != 4) continue;
            logVerifyBlock(layerEntity, context, tiled, block, blockIndex, tiledState, slotToDrawIndex);
            wrote = true;
        }
        return wrote;
    }

    private static void logVerifyBlock(int layerEntity,
                                       Context context,
                                       TiledLayerComponent tiled,
                                       SpatialBlockData block,
                                       int blockIndex,
                                       TiledMapRenderState tiledState,
                                       int[] slotToDrawIndex) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        StringBuilder exclusiveAnchors = new StringBuilder("[");
        StringBuilder sharedAnchors = new StringBuilder("[");
        int exclusiveCount = 0;
        int sharedCount = 0;
        for (int refIndex = 0; refIndex < block.linkedTileRefs.size; refIndex++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(refIndex);
            if (ref == null) continue;
            int slot = tiled.data.slotForTile(ref.gx, ref.gy);
            int drawIndex = slot >= 0 && slot < slotToDrawIndex.length ? slotToDrawIndex[slot] : -1;
            int tiledRenderRef = tiled.data.tiledRenderRefForTile(ref.gx, ref.gy);
            long key = tiledRenderRef >= 0 && tiledRenderRef < tiledState.getRefCount()
                    ? tiledState.sortKey[tiledRenderRef]
                    : 0L;
            if (context != null && context.isShared(ref.gx, ref.gy)) {
                if (sharedCount++ > 0) sharedAnchors.append(';');
                TileOwnership ownership = context.ownership(ref.gx, ref.gy);
                sharedAnchors.append(ref.gx).append(',')
                        .append(ref.gy).append(',')
                        .append(slot).append(',')
                        .append(ownersText(ownership));
                continue;
            }
            if (exclusiveCount++ > 0) exclusiveAnchors.append(';');
            exclusiveAnchors.append(ref.gx).append(',')
                    .append(ref.gy).append(',')
                    .append(slot).append(',')
                    .append(drawIndex).append(',')
                    .append(SortKey64.unpackZOrdered(key)).append(',')
                    .append(ref.gx).append(',')
                    .append(SortKey64.unpackTieOrdered(key));
            if (drawIndex >= 0) {
                min = Math.min(min, drawIndex);
                max = Math.max(max, drawIndex);
            }
        }
        exclusiveAnchors.append(']');
        sharedAnchors.append(']');
        if (min == Integer.MAX_VALUE) {
            min = -1;
            max = -1;
        }
        if (exclusiveCount == 0 && sharedCount > 0) {
            logBlockNoExclusiveAnchor(layerEntity, blockIndex, block.id, sharedAnchors);
        }
        System.out.println("SPATIAL_TILED_SORT_VERIFY_BLOCK"
                + " blockId=" + block.id
                + " blockIndex=" + blockIndex
                + " exclusiveAnchors=" + exclusiveAnchors
                + " sharedAnchors=" + sharedAnchors
                + " minDrawIndex=" + min
                + " maxDrawIndex=" + max);
    }

    private static boolean[] relatedBlocksForToto3(int blockCount,
                                                   SpatialActorCollector actors,
                                                   SpatialRelationSolver relations) {
        boolean[] included = new boolean[Math.max(0, blockCount)];
        if (relations == null || actors == null) return included;
        for (int relation = 0; relation < relations.relationCount; relation++) {
            int actor = relations.relationActorIndex[relation];
            String name = actors.actorName != null && actor >= 0 && actor < actors.actorName.length
                    ? actors.actorName[actor]
                    : null;
            if (!"Toto3".equals(name)) continue;
            int blockIndex = relations.relationAuthoredBlockIndex != null
                    && relation < relations.relationAuthoredBlockIndex.length
                    ? relations.relationAuthoredBlockIndex[relation]
                    : -1;
            if (blockIndex >= 0 && blockIndex < included.length) included[blockIndex] = true;
        }
        return included;
    }

    private static boolean hasAnyIncludedBlock(boolean[] included) {
        if (included == null) return false;
        for (int i = 0; i < included.length; i++) {
            if (included[i]) return true;
        }
        return false;
    }

    private static int findActor(SpatialActorCollector actors, String actorName) {
        if (actors == null || actorName == null) return -1;
        for (int i = 0; i < actors.actorCount; i++) {
            String name = actors.actorName != null && i < actors.actorName.length ? actors.actorName[i] : null;
            if (actorName.equals(name)) return i;
        }
        return -1;
    }

    private static int[] finalRangeForBlock(int blockId, int[] composedSlots, int composedSize) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        if (blockId != 0 && composedSlots != null) {
            for (int block = 0; block < signoffBlockCount; block++) {
                VerifyBlock snapshot = signoffBlocks[block];
                if (snapshot == null || snapshot.blockId != blockId) continue;
                for (int anchor = 0; anchor < snapshot.anchorCount; anchor++) {
                    int slot = snapshot.anchorSlots[anchor];
                    for (int drawIndex = 0; drawIndex < composedSize && drawIndex < composedSlots.length; drawIndex++) {
                        if (composedSlots[drawIndex] == slot) {
                            min = Math.min(min, drawIndex);
                            max = Math.max(max, drawIndex);
                            break;
                        }
                    }
                }
            }
        }
        if (min == Integer.MAX_VALUE) {
            return new int[]{-1, -1};
        }
        return new int[]{min, max};
    }

    private static String disabledReasonText(DisabledReason reason) {
        if (reason == null) return "PROPERTY_DISABLED";
        if (reason == DisabledReason.NOT_SPATIAL_LAYER) return "NOT_SPATIAL";
        return reason.name();
    }

    private static String ownersText(TileOwnership ownership) {
        if (ownership == null || ownership.ownerCount == 0) return "[]";
        StringBuilder out = new StringBuilder("[");
        for (int i = 0; i < ownership.ownerCount; i++) {
            Owner owner = ownership.owners[i];
            if (i > 0) out.append("; ");
            out.append("blockIndex=").append(owner.blockIndex)
                    .append(",blockId=").append(owner.blockId);
        }
        out.append(']');
        return out.toString();
    }

    private static String boundName(int value, boolean lower) {
        if (lower && value == Integer.MIN_VALUE) return "NONE";
        if (!lower && value == Integer.MAX_VALUE) return "NONE";
        return Integer.toString(value);
    }

    public static final class Context {
        public final boolean active;
        public boolean valid;
        public DisabledReason disabledReason = DisabledReason.PROPERTY_DISABLED;
        int layerEntity = -1;
        int tieRange;
        int sharedJunctionCount;
        boolean tieOverflow;
        private TileOwnership[] ownerships = new TileOwnership[8];
        private int ownershipCount;
        private BlockOrder[] blocks = new BlockOrder[4];
        int blockCount;

        private Context(boolean active) {
            this.active = active;
        }

        public boolean applies() {
            return active && valid;
        }

        private void addTileOwner(int gx, int gy, int blockIndex, int blockId, String blockName) {
            TileOwnership ownership = ownership(gx, gy);
            if (ownership == null) {
                if (ownershipCount >= ownerships.length) {
                    ownerships = Arrays.copyOf(ownerships, ownerships.length * 2);
                }
                ownership = new TileOwnership();
                ownership.gx = gx;
                ownership.gy = gy;
                ownerships[ownershipCount++] = ownership;
            }
            Owner owner = new Owner();
            owner.blockIndex = blockIndex;
            owner.blockId = blockId;
            owner.blockName = blockName;
            ownership.add(owner);
        }

        private void finalizeSharedJunctions() {
            sharedJunctionCount = 0;
            for (int i = 0; i < ownershipCount; i++) {
                if (ownerships[i].ownerCount > 1) sharedJunctionCount++;
            }
        }

        TileOwnership ownership(int gx, int gy) {
            for (int i = 0; i < ownershipCount; i++) {
                TileOwnership ownership = ownerships[i];
                if (ownership.gx == gx && ownership.gy == gy) return ownership;
            }
            return null;
        }

        Owner exclusiveOwner(int gx, int gy) {
            TileOwnership ownership = ownership(gx, gy);
            if (ownership == null || ownership.ownerCount != 1) return null;
            return ownership.owners[0];
        }

        boolean isExclusiveOwner(int gx, int gy, int blockIndex) {
            Owner owner = exclusiveOwner(gx, gy);
            return owner != null && owner.blockIndex == blockIndex;
        }

        public boolean isShared(int gx, int gy) {
            TileOwnership ownership = ownership(gx, gy);
            return ownership != null && ownership.ownerCount > 1;
        }

        private void addBlock(BlockOrder block) {
            if (blockCount >= blocks.length) blocks = Arrays.copyOf(blocks, blocks.length * 2);
            blocks[blockCount++] = block;
        }

        private void assignBlockRanks() {
            for (int i = 0; i < blockCount - 1; i++) {
                for (int j = i + 1; j < blockCount; j++) {
                    if (compare(blocks[j], blocks[i]) < 0) {
                        BlockOrder tmp = blocks[i];
                        blocks[i] = blocks[j];
                        blocks[j] = tmp;
                    }
                }
            }
            for (int rank = 0; rank < blockCount; rank++) {
                blocks[rank].rank = rank;
            }
        }

        private int blockRank(int blockIndex) {
            for (int i = 0; i < blockCount; i++) {
                if (blocks[i].blockIndex == blockIndex) return blocks[i].rank;
            }
            return -1;
        }

        private static int compare(BlockOrder a, BlockOrder b) {
            if (a.minSortZ != b.minSortZ) return a.minSortZ < b.minSortZ ? -1 : 1;
            if (a.minTie != b.minTie) return a.minTie < b.minTie ? -1 : 1;
            return Integer.compare(a.blockIndex, b.blockIndex);
        }
    }

    private static final class Owner {
        int blockIndex;
        int blockId;
        String blockName;
    }

    private static final class TileOwnership {
        int gx;
        int gy;
        Owner[] owners = new Owner[2];
        int ownerCount;

        void add(Owner owner) {
            if (owner == null) return;
            for (int i = 0; i < ownerCount; i++) {
                if (owners[i].blockIndex == owner.blockIndex) return;
            }
            if (ownerCount >= owners.length) owners = Arrays.copyOf(owners, owners.length * 2);
            owners[ownerCount++] = owner;
        }
    }

    private static final class VerifyBlock {
        int blockId;
        int blockIndex;
        int[] anchorSlots = new int[0];
        int anchorCount;

        void ensureCapacity(int required) {
            if (required <= anchorSlots.length) return;
            int next = Math.max(4, anchorSlots.length);
            while (required > next) next <<= 1;
            anchorSlots = Arrays.copyOf(anchorSlots, next);
        }
    }

    private static final class BlockOrder {
        int blockIndex;
        int blockId;
        String blockName;
        int minSortZ;
        int minTie;
        int rank;
    }

    public enum DisabledReason {
        NONE,
        TIE_OVERFLOW,
        NO_EXCLUSIVE_ANCHOR,
        NOT_SPATIAL_LAYER,
        NOT_ISO,
        PROPERTY_DISABLED
    }
}
