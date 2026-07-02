package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialTiledSortTest {

    @Test
    public void defaultEnabledForIsoSpatialTiledLayer() {
        Fixture fixture = fixture(true, block(3, ref(3, 2)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks);

        Assert.assertTrue(context.applies());
        Assert.assertNotEquals(3, SpatialTiledSort.encodeTie(context, 3, 2, 3));
    }

    @Test
    public void explicitPropertyDisabledLeavesOldIsoSortKeyUnchanged() {
        Fixture fixture = fixture(true, block(3, ref(3, 2)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                false);

        long oldKey = key(0, -5, 3);
        long spatialKey = key(0, -5, SpatialTiledSort.encodeTie(context, 3, 2, 3));

        Assert.assertFalse(context.applies());
        Assert.assertEquals(SpatialTiledSort.DisabledReason.PROPERTY_DISABLED, context.disabledReason);
        Assert.assertEquals(oldKey, spatialKey);
    }

    @Test
    public void exclusiveAnchorsGroupWithinSameIsoSortZBeforeOriginalTie() {
        Fixture fixture = fixture(
                true,
                block(3, ref(5, 1), ref(3, 2)),
                block(4, ref(2, 3)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                true);

        long block3Old = key(0, -5, 3);
        long block4Old = key(0, -5, 2);
        Assert.assertTrue("Current ISO tie should put block 4 before block 3",
                Long.compareUnsigned(block4Old, block3Old) < 0);

        long block3Spatial = key(0, -5, SpatialTiledSort.encodeTie(context, 3, 2, 3));
        long block4Spatial = key(0, -5, SpatialTiledSort.encodeTie(context, 2, 3, 2));

        Assert.assertTrue(context.applies());
        Assert.assertTrue("Spatial block rank should put block 3 before block 4 inside the same sortZ",
                Long.compareUnsigned(block3Spatial, block4Spatial) < 0);
    }

    @Test
    public void differentIsoSortZValuesRemainPrimaryOrdering() {
        Fixture fixture = fixture(
                true,
                block(3, ref(5, 1), ref(3, 2)),
                block(4, ref(2, 3)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                true);

        long farther = key(0, -6, SpatialTiledSort.encodeTie(context, 5, 1, 5));
        long nearer = key(0, -5, SpatialTiledSort.encodeTie(context, 2, 3, 2));

        Assert.assertTrue(context.applies());
        Assert.assertTrue("sortZ must remain the primary ISO ordering field",
                Long.compareUnsigned(farther, nearer) < 0);
    }

    @Test
    public void multiOwnedAnchorBecomesSharedJunctionAndLayerStillApplies() {
        Fixture fixture = fixture(
                true,
                block(3, ref(2, 3), ref(3, 3)),
                block(4, ref(2, 3), ref(4, 3)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                true);

        Assert.assertTrue(context.applies());
        Assert.assertTrue(context.isShared(2, 3));
        Assert.assertEquals(1, context.sharedJunctionCount);
        Assert.assertNull(context.exclusiveOwner(2, 3));
    }

    @Test
    public void sharedJunctionAnchorKeepsNeutralTieAndIsNotAssignedToFirstOwner() {
        Fixture fixture = fixture(
                true,
                block(3, ref(2, 3), ref(3, 3)),
                block(4, ref(2, 3), ref(4, 3)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                true);

        Assert.assertTrue(context.applies());
        Assert.assertEquals(2, SpatialTiledSort.encodeTie(context, 2, 3, 2));
        Assert.assertNotEquals(3, SpatialTiledSort.encodeTie(context, 3, 3, 3));
        Assert.assertNotEquals(4, SpatialTiledSort.encodeTie(context, 4, 3, 4));
    }

    @Test
    public void nonSpatialTiledLayerLeavesSortKeyUnchanged() {
        Fixture fixture = fixture(false, block(3, ref(3, 2)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                true);

        long oldKey = key(0, -5, 3);
        long spatialKey = key(0, -5, SpatialTiledSort.encodeTie(context, 3, 2, 3));

        Assert.assertFalse(context.applies());
        Assert.assertEquals(oldKey, spatialKey);
    }

    @Test
    public void nonIsoTiledLayerLeavesSortKeyUnchanged() {
        Fixture fixture = fixtureWithProjection(true, 16, SceneMetaRuntime.TiledProjection.ORTHO,
                block(3, ref(3, 2)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                true);

        long oldKey = key(0, -5, 3);
        long spatialKey = key(0, -5, SpatialTiledSort.encodeTie(context, 3, 2, 3));

        Assert.assertFalse(context.applies());
        Assert.assertEquals(SpatialTiledSort.DisabledReason.NOT_ISO, context.disabledReason);
        Assert.assertEquals(oldKey, spatialKey);
    }

    @Test
    public void sharedJunctionAnchorDoesNotContributeToEitherBlockInterval() {
        Fixture fixture = fixture(
                true,
                block(3, ref(2, 3), ref(3, 3)),
                block(4, ref(2, 3), ref(4, 3)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                true);
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        int sharedSlot = fixture.tiled.data.slotForTile(2, 3);
        int block3Slot = fixture.tiled.data.slotForTile(3, 3);
        int block4Slot = fixture.tiled.data.slotForTile(4, 3);
        int[] slotToDrawIndex = new int[8192];
        for (int i = 0; i < slotToDrawIndex.length; i++) slotToDrawIndex[i] = -1;
        slotToDrawIndex[sharedSlot] = 50;
        slotToDrawIndex[block3Slot] = 10;
        slotToDrawIndex[block4Slot] = 90;

        new SpatialBlockAnchorResolver().resolve(
                fixture.blocks,
                fixture.tiled.data,
                slotToDrawIndex,
                cache,
                context);

        Assert.assertTrue(context.applies());
        Assert.assertEquals(10, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(10, cache.blockAnchorEndDrawIndex[0]);
        Assert.assertEquals(90, cache.blockAnchorStartDrawIndex[1]);
        Assert.assertEquals(90, cache.blockAnchorEndDrawIndex[1]);
        Assert.assertEquals(-1, cache.anchorDrawSlot[0]);
        Assert.assertEquals(-1, cache.anchorDrawSlot[2]);
    }

    @Test
    public void blockWithOnlySharedAnchorsContributesNoIntervalConstraints() {
        Fixture fixture = fixture(
                true,
                block(3, ref(2, 3)),
                block(4, ref(2, 3), ref(4, 3)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                true);
        SpatialBlocksRuntimeCache cache = new SpatialBlocksRuntimeCache();
        int sharedSlot = fixture.tiled.data.slotForTile(2, 3);
        int block4Slot = fixture.tiled.data.slotForTile(4, 3);
        int[] slotToDrawIndex = new int[8192];
        for (int i = 0; i < slotToDrawIndex.length; i++) slotToDrawIndex[i] = -1;
        slotToDrawIndex[sharedSlot] = 50;
        slotToDrawIndex[block4Slot] = 90;

        new SpatialBlockAnchorResolver().resolve(
                fixture.blocks,
                fixture.tiled.data,
                slotToDrawIndex,
                cache,
                context);

        Assert.assertTrue(context.applies());
        Assert.assertFalse(cache.hasResolvedBlock(0));
        Assert.assertTrue(cache.hasResolvedBlock(1));
        Assert.assertEquals(-1, cache.blockAnchorStartDrawIndex[0]);
        Assert.assertEquals(-1, cache.blockAnchorEndDrawIndex[0]);
        Assert.assertEquals(90, cache.blockAnchorStartDrawIndex[1]);
        Assert.assertEquals(90, cache.blockAnchorEndDrawIndex[1]);
    }

    @Test
    public void tieOverflowStillDisablesSpatialTiledSortClearly() {
        Fixture fixture = fixtureWithMapWidth(true, 8193, block(3, ref(3, 2)));
        SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                1,
                fixture.layer,
                fixture.tiled,
                fixture.blocks,
                true);

        Assert.assertFalse(context.applies());
        Assert.assertEquals(SpatialTiledSort.DisabledReason.TIE_OVERFLOW, context.disabledReason);
        Assert.assertTrue(context.tieOverflow);
        Assert.assertEquals(3, SpatialTiledSort.encodeTie(context, 3, 2, 3));
    }

    @Test
    public void verifyPropertyDoesNotChangeSortBehavior() {
        String previous = System.getProperty(SpatialTiledSort.VERIFY_PROPERTY);
        try {
            System.setProperty(SpatialTiledSort.VERIFY_PROPERTY, "true");
            Fixture fixture = fixture(true, block(3, ref(3, 2)));
            SpatialTiledSort.Context context = SpatialTiledSort.contextForLayer(
                    1,
                    fixture.layer,
                    fixture.tiled,
                    fixture.blocks,
                    true);

            Assert.assertTrue(context.applies());
            Assert.assertNotEquals(3, SpatialTiledSort.encodeTie(context, 3, 2, 3));
        } finally {
            if (previous == null) {
                System.clearProperty(SpatialTiledSort.VERIFY_PROPERTY);
            } else {
                System.setProperty(SpatialTiledSort.VERIFY_PROPERTY, previous);
            }
        }
    }

    private static long key(int layer, int z, int tie) {
        return SortKey64.packForBlend(0, BlendMode.ALPHA.id, 1, layer, z, tie);
    }

    private static Fixture fixture(boolean spatialEnabled, SpatialBlockData... blockData) {
        return fixtureWithMapWidth(spatialEnabled, 16, blockData);
    }

    private static Fixture fixtureWithMapWidth(boolean spatialEnabled,
                                               int mapWidth,
                                               SpatialBlockData... blockData) {
        return fixtureWithProjection(spatialEnabled, mapWidth, SceneMetaRuntime.TiledProjection.ISO, blockData);
    }

    private static Fixture fixtureWithProjection(boolean spatialEnabled,
                                                 int mapWidth,
                                                 SceneMetaRuntime.TiledProjection projection,
                                                 SpatialBlockData... blockData) {
        LayerComponent layer = new LayerComponent();
        layer.type = LayerComponent.TYPE_TILED;
        layer.layerIndex = 0;
        layer.spatialEnabled = spatialEnabled;

        TiledLayerComponent tiled = new TiledLayerComponent();
        tiled.spatialEnabled = spatialEnabled;
        tiled.data = new TiledMapLayerData(mapWidth, 16, 16, 16, 4, projection);
        tiled.data.initSlotRange(0, mapWidth * 16);
        assignRenderRefs(tiled.data, 0);
        tiled.data.spatialEnabled = spatialEnabled;

        SpatialBlocksComponent blocks = new SpatialBlocksComponent();
        for (int i = 0; i < blockData.length; i++) {
            blocks.blocks.add(blockData[i]);
        }
        return new Fixture(layer, tiled, blocks);
    }

    private static SpatialBlockData block(int id, SpatialBlockData.LinkedTileRef... refs) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        for (int i = 0; i < refs.length; i++) {
            block.linkedTileRefs.add(refs[i]);
        }
        return block;
    }

    private static SpatialBlockData.LinkedTileRef ref(int gx, int gy) {
        SpatialBlockData.LinkedTileRef ref = new SpatialBlockData.LinkedTileRef();
        ref.gx = gx;
        ref.gy = gy;
        return ref;
    }

    private static void assignRenderRefs(TiledMapLayerData map, int startRef) {
        int nextRef = startRef;
        for (int cy = 0; cy < map.getChunksY(); cy++) {
            for (int cx = 0; cx < map.getChunksX(); cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                if (chunk == null) continue;
                chunk.renderRefStartIndex = nextRef;
                chunk.renderRefCount = chunk.soaCount;
                nextRef += chunk.soaCount;
            }
        }
    }

    private static final class Fixture {
        final LayerComponent layer;
        final TiledLayerComponent tiled;
        final SpatialBlocksComponent blocks;

        Fixture(LayerComponent layer, TiledLayerComponent tiled, SpatialBlocksComponent blocks) {
            this.layer = layer;
            this.tiled = tiled;
            this.blocks = blocks;
        }
    }
}
