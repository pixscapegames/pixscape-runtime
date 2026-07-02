package games.pixscape.runtime.render;

import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class TiledMapRenderStateTest {

    @Test
    public void initialCapacityAllocatesVisibleRefsAndColumns() {
        TiledMapRenderState state = new TiledMapRenderState(4);

        Assert.assertEquals(4, state.getCapacity());
        Assert.assertEquals(0, state.getVisibleRefCount());
        Assert.assertEquals(0, state.getRefCount());
        Assert.assertNotNull(state.getVisibleRefs());
        Assert.assertNotNull(state.textureHandle);
        Assert.assertNotNull(state.sortKey);
        Assert.assertNotNull(state.x1);
        Assert.assertNotNull(state.repeatFlags);
        Assert.assertEquals(4, state.getVisibleRefs().length);
        Assert.assertEquals(4, state.textureHandle.length);
        Assert.assertEquals(4, state.sortKey.length);
    }

    @Test
    public void clearVisibleRefsKeepsExistingArrays() {
        TiledMapRenderState state = new TiledMapRenderState(4);
        int refA = state.registerRef();
        int refB = state.registerRef();
        state.addVisibleRef(refA);
        state.addVisibleRef(refB);
        int[] visibleBefore = state.getVisibleRefs();
        int[] textureBefore = state.textureHandle;

        state.clearVisibleRefs();

        Assert.assertSame(visibleBefore, state.getVisibleRefs());
        Assert.assertSame(textureBefore, state.textureHandle);
        Assert.assertEquals(0, state.getVisibleRefCount());
        Assert.assertEquals(2, state.getRefCount());
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
    public void ensureCapacityPreservesVisibleRefsAndRenderData() {
        TiledMapRenderState state = new TiledMapRenderState(2);
        int refA = state.registerRef();
        int refB = state.registerRef();
        state.addVisibleRef(refA);
        state.addVisibleRef(refB);
        writeRenderData(state, refA, 10);
        writeRenderData(state, refB, 20);

        state.ensureCapacity(5);

        Assert.assertTrue(state.getCapacity() >= 5);
        Assert.assertEquals(2, state.getVisibleRefCount());
        Assert.assertEquals(refA, state.getVisibleRefs()[0]);
        Assert.assertEquals(refB, state.getVisibleRefs()[1]);
        Assert.assertEquals(2, state.getRefCount());
        assertRenderData(state, refA, 10);
        assertRenderData(state, refB, 20);
    }

    @Test
    public void setRenderDataForRefWritesAllDrawReadyFields() {
        TiledMapRenderState state = new TiledMapRenderState(2);
        int ref = state.registerRef();

        writeRenderData(state, ref, 40);

        Assert.assertTrue(state.isRenderableRef(ref));
        assertRenderData(state, ref, 40);
    }

    @Test
    public void clearVisibleRefsDoesNotWipeRenderData() {
        TiledMapRenderState state = new TiledMapRenderState(2);
        int ref = state.registerRef();
        state.addVisibleRef(ref);
        writeRenderData(state, ref, 50);

        state.clearVisibleRefs();

        Assert.assertEquals(0, state.getVisibleRefCount());
        Assert.assertTrue(state.isRenderableRef(ref));
        assertRenderData(state, ref, 50);
    }

    @Test
    public void registerRefsCreatesDenseStableRefs() {
        TiledMapRenderState state = new TiledMapRenderState(2);

        int refStart = state.registerRefs(3);

        Assert.assertEquals(0, refStart);
        Assert.assertEquals(3, state.getRefCount());
        Assert.assertTrue(state.getCapacity() >= 3);
    }

    @Test
    public void tiledMapResolvesTileToRenderRef() {
        TiledMapRenderState state = new TiledMapRenderState(2);
        TiledMapLayerData map = new TiledMapLayerData(2, 2, 16, 16, 2);
        TileChunk chunk = map.getChunk(0, 0);
        int refStart = state.registerRefs(chunk.cellCount());
        chunk.renderRefStartIndex = refStart;
        chunk.renderRefCount = chunk.cellCount();

        int tiledRenderRef = map.tiledRenderRefForTile(1, 1);

        Assert.assertEquals(refStart + 3, tiledRenderRef);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsInvalidCapacity() {
        new TiledMapRenderState(0);
    }

    private static void writeRenderData(TiledMapRenderState state, int ref, int base) {
        state.setRenderDataForRef(
                ref,
                base + 1,
                base + 2,
                base + 3,
                base + 4,
                base + 5,
                base + 6,
                base + 7L,
                base + 0.1f,
                base + 0.2f,
                base + 0.3f,
                base + 0.4f,
                base + 0.5f,
                base + 0.6f,
                base + 0.7f,
                base + 0.8f,
                base + 0.9f,
                base + 1.1f,
                base + 1.2f,
                base + 1.3f,
                base + 1.4f,
                base + 1.5f,
                RenderRepeatFlags.REPEAT_X
        );
    }

    private static void assertRenderData(TiledMapRenderState state, int ref, int base) {
        Assert.assertEquals(base + 1, state.textureHandle[ref]);
        Assert.assertEquals(base + 2, state.shader[ref]);
        Assert.assertEquals(base + 3, state.blend[ref]);
        Assert.assertEquals(base + 4, state.layerIndex[ref]);
        Assert.assertEquals(base + 5, state.paramsId[ref]);
        Assert.assertEquals(base + 6, state.customParamsId[ref]);
        Assert.assertEquals(base + 7L, state.sortKey[ref]);
        Assert.assertEquals(base + 0.1f, state.x1[ref], 0f);
        Assert.assertEquals(base + 0.2f, state.y1[ref], 0f);
        Assert.assertEquals(base + 0.3f, state.x2[ref], 0f);
        Assert.assertEquals(base + 0.4f, state.y2[ref], 0f);
        Assert.assertEquals(base + 0.5f, state.x3[ref], 0f);
        Assert.assertEquals(base + 0.6f, state.y3[ref], 0f);
        Assert.assertEquals(base + 0.7f, state.x4[ref], 0f);
        Assert.assertEquals(base + 0.8f, state.y4[ref], 0f);
        Assert.assertEquals(base + 0.9f, state.u1[ref], 0f);
        Assert.assertEquals(base + 1.1f, state.v1[ref], 0f);
        Assert.assertEquals(base + 1.2f, state.u2[ref], 0f);
        Assert.assertEquals(base + 1.3f, state.v2[ref], 0f);
        Assert.assertEquals(base + 1.4f, state.colorPacked[ref], 0f);
        Assert.assertEquals(base + 1.5f, state.alpha[ref], 0f);
        Assert.assertEquals(RenderRepeatFlags.REPEAT_X, state.repeatFlags[ref]);
    }
}
