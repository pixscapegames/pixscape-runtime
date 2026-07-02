package games.pixscape.runtime.render;

import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class TiledMapRenderStateTest {

    @Test
    public void initialCapacityAllocatesVisibleRefsAndMapping() {
        TiledMapRenderState state = new TiledMapRenderState(4);

        Assert.assertEquals(4, state.getCapacity());
        Assert.assertEquals(0, state.getVisibleRefCount());
        Assert.assertEquals(0, state.getRefCount());
        Assert.assertNotNull(state.getVisibleRefs());
        Assert.assertNotNull(state.getRefToLegacySlots());
        Assert.assertEquals(4, state.getVisibleRefs().length);
        Assert.assertEquals(4, state.getRefToLegacySlots().length);
    }

    @Test
    public void clearVisibleRefsKeepsExistingArraysAndMapping() {
        TiledMapRenderState state = new TiledMapRenderState(4);
        int refA = state.registerLegacySlot(120);
        int refB = state.registerLegacySlot(121);
        state.addVisibleRef(refA);
        state.addVisibleRef(refB);
        int[] visibleBefore = state.getVisibleRefs();
        int[] mappingBefore = state.getRefToLegacySlots();

        state.clearVisibleRefs();

        Assert.assertSame(visibleBefore, state.getVisibleRefs());
        Assert.assertSame(mappingBefore, state.getRefToLegacySlots());
        Assert.assertEquals(0, state.getVisibleRefCount());
        Assert.assertEquals(120, state.legacySlotForRef(refA));
        Assert.assertEquals(121, state.legacySlotForRef(refB));
    }

    @Test
    public void addVisibleRefPreservesInsertionOrder() {
        TiledMapRenderState state = new TiledMapRenderState(4);

        state.addVisibleRef(3);
        state.addVisibleRef(1);
        state.addVisibleRef(2);

        Assert.assertEquals(3, state.getVisibleRefCount());
        Assert.assertEquals(3, state.getVisibleRefs()[0]);
        Assert.assertEquals(1, state.getVisibleRefs()[1]);
        Assert.assertEquals(2, state.getVisibleRefs()[2]);
    }

    @Test
    public void ensureCapacityPreservesVisibleRefsAndLegacyMapping() {
        TiledMapRenderState state = new TiledMapRenderState(2);
        int refA = state.registerLegacySlot(300);
        int refB = state.registerLegacySlot(301);
        state.addVisibleRef(refA);
        state.addVisibleRef(refB);

        state.ensureCapacity(5);

        Assert.assertTrue(state.getCapacity() >= 5);
        Assert.assertEquals(2, state.getVisibleRefCount());
        Assert.assertEquals(refA, state.getVisibleRefs()[0]);
        Assert.assertEquals(refB, state.getVisibleRefs()[1]);
        Assert.assertEquals(300, state.legacySlotForRef(refA));
        Assert.assertEquals(301, state.legacySlotForRef(refB));
    }

    @Test
    public void registerLegacyRangeCreatesDenseStableRefs() {
        TiledMapRenderState state = new TiledMapRenderState(2);

        int refStart = state.registerLegacyRange(900, 3);

        Assert.assertEquals(0, refStart);
        Assert.assertEquals(3, state.getRefCount());
        Assert.assertEquals(900, state.legacySlotForRef(refStart));
        Assert.assertEquals(901, state.legacySlotForRef(refStart + 1));
        Assert.assertEquals(902, state.legacySlotForRef(refStart + 2));
    }

    @Test
    public void tiledMapResolvesTileToRenderRefAndLegacySlot() {
        TiledMapRenderState state = new TiledMapRenderState(2);
        TiledMapLayerData map = new TiledMapLayerData(2, 2, 16, 16, 2);
        map.initSlotRange(100, 104);
        TileChunk chunk = map.getChunk(0, 0);
        int refStart = state.registerLegacyRange(chunk.soaStartIndex, chunk.soaCount);
        chunk.renderRefStartIndex = refStart;
        chunk.renderRefCount = chunk.soaCount;

        int tiledRenderRef = map.tiledRenderRefForTile(1, 1);

        Assert.assertEquals(refStart + 3, tiledRenderRef);
        Assert.assertEquals(map.slotForTile(1, 1), state.legacySlotForRef(tiledRenderRef));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidCapacity() {
        new TiledMapRenderState(0);
    }
}
