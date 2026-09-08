package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;

import java.util.Arrays;

/** Runtime-owned canonical rank lookup for one spatial Tiled Map. */
public final class SpatialTileOrderCache {
    private final SpatialTileOrderCompiler compiler = new SpatialTileOrderCompiler();
    private TiledMapLayerData sourceMap;
    private int sourceMapRevision = Integer.MIN_VALUE;
    private SpatialBlocksComponent sourceBlocks;
    private int sourceBlocksRevision = Integer.MIN_VALUE;
    private int sourceCompiledRevision = Integer.MIN_VALUE;
    private TiledProjection projection;
    private int mapWidth;
    private int mapHeight;
    private int tileWidth;
    private int tileHeight;
    private float originX;
    private float originY;

    private int[] rankByCell = new int[0];
    private int[] ownerBlockIdByCell = new int[0];
    private int[] anchorStructureIdByCell = new int[0];
    private int orderRevision;
    private int appliedOrderRevision = Integer.MIN_VALUE;
    public int tileOrderCompileCount;
    public int tileOrderNodeCount;
    public int tileOrderSegmentCount;
    public int tileOrderEdgeCount;
    public int tileOrderCycleCount;

    public boolean ensure(int layerEntity,
                          TiledMapLayerData map,
                          SpatialBlocksComponent blocks,
                          SpatialCompiledLayerCache compiled) {
        if (map == null || compiled == null) return false;
        int mapRevision = map.contentStateRevision();
        int blocksRevision = blocks != null ? blocks.revision : 0;
        if (sourceMap == map && sourceMapRevision == mapRevision
                && sourceBlocks == blocks && sourceBlocksRevision == blocksRevision
                && sourceCompiledRevision == compiled.revision()
                && projection == map.projection && mapWidth == map.mapWidth && mapHeight == map.mapHeight
                && tileWidth == map.tileWidth && tileHeight == map.tileHeight
                && Float.compare(originX, map.originX) == 0
                && Float.compare(originY, map.originY) == 0) return false;

        rebuild(layerEntity, map, blocks, compiled);
        return true;
    }

    private void rebuild(int layerEntity,
                         TiledMapLayerData map,
                         SpatialBlocksComponent blocks,
                         SpatialCompiledLayerCache compiled) {
        try {
            compiler.compile(layerEntity, map, compiled, this);
            rebuildParticipation(map, blocks, compiled);
        } catch (SpatialTileOrderInvariantException invalid) {
            tileOrderCycleCount++;
            throw invalid;
        }
        sourceMap = map;
        sourceMapRevision = map.contentStateRevision();
        sourceBlocks = blocks;
        sourceBlocksRevision = blocks != null ? blocks.revision : 0;
        sourceCompiledRevision = compiled.revision();
        projection = map.projection;
        mapWidth = map.mapWidth;
        mapHeight = map.mapHeight;
        tileWidth = map.tileWidth;
        tileHeight = map.tileHeight;
        originX = map.originX;
        originY = map.originY;
        orderRevision++;
        tileOrderCompileCount++;
    }

    void publish(int[] ranks, int nodes, int segments, int edges) {
        rankByCell = ranks;
        tileOrderNodeCount = nodes;
        tileOrderSegmentCount = segments;
        tileOrderEdgeCount = edges;
    }

    public int rank(int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= mapWidth || gy >= mapHeight) return -1;
        return rankByCell[gy * mapWidth + gx];
    }

    /** True only for an authored owner, compiled anchor, or explicit per-cell spatial metadata. */
    public boolean requiresCanonicalRank(TiledMapLayerData map, int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= mapWidth || gy >= mapHeight) return false;
        int cell = gy * mapWidth + gx;
        return ownerBlockIdByCell[cell] != 0 || anchorStructureIdByCell[cell] != 0
                || map != null && map.hasTileSpatialOverride(gx, gy);
    }

    public int ownerBlockId(int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= mapWidth || gy >= mapHeight) return 0;
        return ownerBlockIdByCell[gy * mapWidth + gx];
    }

    public int anchorStructureId(int gx, int gy) {
        if (gx < 0 || gy < 0 || gx >= mapWidth || gy >= mapHeight) return 0;
        return anchorStructureIdByCell[gy * mapWidth + gx];
    }

    private void rebuildParticipation(TiledMapLayerData map,
                                      SpatialBlocksComponent blocks,
                                      SpatialCompiledLayerCache compiled) {
        int cells = map.mapWidth * map.mapHeight;
        if (ownerBlockIdByCell.length != cells) ownerBlockIdByCell = new int[cells];
        else Arrays.fill(ownerBlockIdByCell, 0);
        if (anchorStructureIdByCell.length != cells) anchorStructureIdByCell = new int[cells];
        else Arrays.fill(anchorStructureIdByCell, 0);

        if (blocks != null && blocks.blocks != null) {
            for (int wallIndex = 0; wallIndex < blocks.blocks.size; wallIndex++) {
                SpatialBlockData wall = blocks.blocks.get(wallIndex);
                if (wall == null || wall.linkedTileRefs == null) continue;
                for (int refIndex = 0; refIndex < wall.linkedTileRefs.size; refIndex++) {
                    SpatialBlockData.LinkedTileRef ref = wall.linkedTileRefs.get(refIndex);
                    if (ref == null || !inside(map, ref.gx, ref.gy)) continue;
                    int cell = ref.gy * map.mapWidth + ref.gx;
                    int previous = ownerBlockIdByCell[cell];
                    if (previous == 0 || wall.id > 0 && wall.id < previous) ownerBlockIdByCell[cell] = wall.id;
                }
            }
        }
        for (int structureIndex = 0; structureIndex < compiled.structureCount(); structureIndex++) {
            CompiledSpatialStructure structure = compiled.structure(structureIndex);
            CompiledSpatialStructure.FaceSet faces = structure.complete();
            for (int anchor = 0; anchor < faces.anchorCellTotal(); anchor++) {
                int gx = faces.anchorGx(anchor);
                int gy = faces.anchorGy(anchor);
                if (inside(map, gx, gy)) anchorStructureIdByCell[gy * map.mapWidth + gx] = structure.structureId();
            }
        }
    }

    private static boolean inside(TiledMapLayerData map, int gx, int gy) {
        return gx >= 0 && gy >= 0 && gx < map.mapWidth && gy < map.mapHeight;
    }

    public int orderRevision() { return orderRevision; }
    public int appliedOrderRevision() { return appliedOrderRevision; }
    public boolean needsKeyRefresh() { return appliedOrderRevision != orderRevision; }
    public void markKeysApplied() {
        appliedOrderRevision = orderRevision;
    }
}
