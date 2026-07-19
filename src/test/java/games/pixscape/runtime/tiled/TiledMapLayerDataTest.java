package games.pixscape.runtime.tiled;

import games.pixscape.runtime.loading.SceneMetaRuntime;
import org.junit.Assert;
import org.junit.Test;

public class TiledMapLayerDataTest {

    @Test
    public void atomicCandidateIsReadableWithoutPublishingRevisionOrDirtyState() {
        TiledMapLayerData map = cleanMap();
        int revision = map.contentRevision();
        int stateRevision = map.contentStateRevision();

        map.beginAtomicMutation();
        map.setTileStaged(1, 1, 17, TileTransformFlags.FLIP_H);

        Assert.assertEquals(17, map.getTile(1, 1));
        Assert.assertEquals(TileTransformFlags.FLIP_H, map.getTileTransformFlags(1, 1));
        Assert.assertEquals(revision, map.contentRevision());
        Assert.assertEquals(stateRevision, map.contentStateRevision());
        assertChunkClean(map.getChunk(0, 0));
    }

    @Test
    public void atomicRollbackRestoresCellsFlagsAndDirtyMetadataExactly() {
        TiledMapLayerData map = cleanMap();
        map.setTile(1, 1, 4, TileTransformFlags.FLIP_V);
        int revision = map.contentRevision();
        TileChunk chunk = map.getChunk(0, 0);
        resetChunkDirtyState(chunk);

        map.beginAtomicMutation();
        map.setTileStaged(1, 1, 0, TileTransformFlags.NONE);
        map.setTileStaged(2, 1, 9, TileTransformFlags.FLIP_D);
        map.rollbackAtomicMutation();

        Assert.assertEquals(4, map.getTile(1, 1));
        Assert.assertEquals(TileTransformFlags.FLIP_V, map.getTileTransformFlags(1, 1));
        Assert.assertEquals(0, map.getTile(2, 1));
        Assert.assertEquals(revision, map.contentRevision());
        assertChunkClean(chunk);
    }

    @Test
    public void atomicCommitPublishesOneRevisionAndOnlyTouchedChunks() {
        TiledMapLayerData map = cleanMap();
        int revision = map.contentRevision();

        map.beginAtomicMutation();
        map.setTileStaged(0, 0, 1, TileTransformFlags.NONE);
        map.setTileStaged(1, 1, 2, TileTransformFlags.FLIP_H);
        map.commitAtomicMutation();

        Assert.assertEquals(revision + 1, map.contentRevision());
        Assert.assertEquals(TileChunk.DirtyState.PARTIAL, map.getChunk(0, 0).dirtyState);
        Assert.assertEquals(2, map.getChunk(0, 0).dirtyLocalIndices.size);
        assertChunkClean(map.getChunk(1, 0));
        assertChunkClean(map.getChunk(0, 1));
        assertChunkClean(map.getChunk(1, 1));
    }

    @Test(expected = IllegalStateException.class)
    public void nestedAtomicMutationIsRejected() {
        TiledMapLayerData map = cleanMap();
        map.beginAtomicMutation();
        try {
            map.beginAtomicMutation();
        } finally {
            map.rollbackAtomicMutation();
        }
    }

    @Test
    public void orthoFloatTileToWorldUsesContinuousGridCoordinates() {
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 40, 20, 4);
        map.originX = 3f;
        map.originY = -7f;

        Assert.assertEquals(63f, map.tileToWorldX(1.5f, 2.25f), 0.0001f);
        Assert.assertEquals(38f, map.tileToWorldY(1.5f, 2.25f), 0.0001f);
    }

    @Test
    public void isoFloatTileToWorldUsesConfiguredTileRatio() {
        TiledMapLayerData map = new TiledMapLayerData(
                8,
                8,
                90,
                30,
                4,
                SceneMetaRuntime.TiledProjection.ISO
        );
        map.originX = 10f;
        map.originY = 20f;

        Assert.assertEquals(77.5f, map.tileToWorldX(2.5f, 1f), 0.0001f);
        Assert.assertEquals(72.5f, map.tileToWorldY(2.5f, 1f), 0.0001f);
    }

    @Test
    public void floatTileProjectionRoundTripsThroughContinuousWorldProjection() {
        TiledMapLayerData map = new TiledMapLayerData(
                8,
                8,
                70,
                22,
                4,
                SceneMetaRuntime.TiledProjection.ISO
        );
        map.originX = -13f;
        map.originY = 5f;

        float gx = 3.25f;
        float gy = 1.75f;
        float worldX = map.tileToWorldX(gx, gy);
        float worldY = map.tileToWorldY(gx, gy);

        Assert.assertEquals(gx, map.projectWorldToTileX(worldX, worldY), 0.0001f);
        Assert.assertEquals(gy, map.projectWorldToTileY(worldX, worldY), 0.0001f);
    }

    @Test
    public void isoWorldToTileHandlesOriginAndNegativeWorldX() {
        TiledMapLayerData map = new TiledMapLayerData(
                8,
                8,
                64,
                32,
                4,
                SceneMetaRuntime.TiledProjection.ISO
        );
        map.originX = -128f;
        map.originY = 256f;

        assertTileCenterMapsBackToCell(map, 0, 0);
        assertTileCenterMapsBackToCell(map, 0, 2);
        assertTileCenterMapsBackToCell(map, 3, 1);
    }

    private static void assertTileCenterMapsBackToCell(TiledMapLayerData map, int gx, int gy) {
        float worldX = map.tileToWorldX(gx, gy) + map.tileWidth * 0.5f;
        float worldY = map.tileToWorldY(gx, gy) + map.tileHeight * 0.5f;

        Assert.assertEquals(gx, map.worldToTileX(worldX, worldY));
        Assert.assertEquals(gy, map.worldToTileY(worldX, worldY));
    }

    private static TiledMapLayerData cleanMap() {
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 32, 16, 4);
        for (int cy = 0; cy < map.getChunksY(); cy++) {
            for (int cx = 0; cx < map.getChunksX(); cx++) {
                resetChunkDirtyState(map.getChunk(cx, cy));
            }
        }
        map.visualBoundsDirty = false;
        return map;
    }

    private static void resetChunkDirtyState(TileChunk chunk) {
        chunk.dirtyState = TileChunk.DirtyState.CLEAN;
        chunk.dirtyLocalIndices.clear();
        chunk.contentDirty = false;
        chunk.collisionDirty = false;
    }

    private static void assertChunkClean(TileChunk chunk) {
        Assert.assertEquals(TileChunk.DirtyState.CLEAN, chunk.dirtyState);
        Assert.assertEquals(0, chunk.dirtyLocalIndices.size);
        Assert.assertFalse(chunk.contentDirty);
        Assert.assertFalse(chunk.collisionDirty);
    }
}
