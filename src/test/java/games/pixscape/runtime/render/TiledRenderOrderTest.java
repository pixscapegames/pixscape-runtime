package games.pixscape.runtime.render;

import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import java.util.Arrays;
import java.util.Random;

public class TiledRenderOrderTest {
    @Test public void filteringMatchesStableUnsignedSortIncludingEqualKeysAcrossChunks() {
        TiledMapRenderState state = new TiledMapRenderState(128);
        TiledMapLayerData map = map(state);
        TiledRenderOrder order = state.prepareOrderForMap(7, map);
        Random random = new Random(371193);
        for (int ref = 0; ref < state.getRefCount(); ref++) {
            state.sortKey[ref] = random.nextInt(5) | (random.nextBoolean() ? Long.MIN_VALUE : 0);
        }
        order.prepare(state);
        long storage = order.storageBytes();
        int[] visible = new int[64];
        for (int frame = 0; frame < 200; frame++) {
            int n = 0;
            for (int cy = 0; cy < map.getChunksY(); cy++) for (int cx = 0; cx < map.getChunksX(); cx++) {
                TileChunk chunk = map.getChunk(cx, cy);
                for (int i = 0; i < chunk.getRenderableRefCount(); i++) {
                    if (random.nextBoolean()) visible[n++] = chunk.renderRefStartIndex + chunk.renderableLocalIndices.get(i);
                }
            }
            Integer[] expected = new Integer[n];
            for (int i = 0; i < n; i++) expected[i] = visible[i];
            Arrays.sort(expected, (a, b) -> Long.compareUnsigned(state.sortKey[a], state.sortKey[b]));
            order.prepare(state);
            order.filterVisible(state, visible, 0, n);
            for (int i = 0; i < n; i++) Assert.assertEquals((int) expected[i], visible[i]);
        }
        Assert.assertEquals(1, order.preparationCount());
        Assert.assertEquals(storage, order.storageBytes());
    }

    @Test public void mapsHaveIndependentRanksAndReplacementReleasesTheOldOwner() {
        TiledMapRenderState state = new TiledMapRenderState(8);
        TiledMapLayerData first = map(state), second = map(state);
        TiledRenderOrder a = state.prepareOrderForMap(1, first), b = state.prepareOrderForMap(2, second);
        a.prepare(state); b.prepare(state);
        int[] refs = {second.getChunk(0, 0).renderRefStartIndex + 3, second.getChunk(0, 0).renderRefStartIndex};
        state.sortKey[refs[0]] = 0; state.sortKey[refs[1]] = 1;
        b.invalidate(); b.prepare(state); b.filterVisible(state, refs, 0, 2);
        Assert.assertEquals(second.getChunk(0, 0).renderRefStartIndex + 3, refs[0]);
        Assert.assertEquals(1, a.preparationCount());
        Assert.assertEquals(2, b.preparationCount());
        Assert.assertNotSame(a, state.prepareOrderForMap(1, second));
        state.removeOrderForMap(2);
        Assert.assertNull(state.orderForMap(2));
        state.setCapacity(8);
        Assert.assertNull(state.orderForMap(1));
    }

    @Test public void preparedFilteringDoesNotAllocate() {
        java.lang.management.ThreadMXBean base = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.Assume.assumeTrue(base instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean bean = (com.sun.management.ThreadMXBean) base;
        bean.setThreadAllocatedMemoryEnabled(true);
        TiledMapRenderState state = new TiledMapRenderState(128);
        TiledRenderOrder order = state.prepareOrderForMap(7, map(state)); order.prepare(state);
        int[] refs = new int[64];
        for (int warm = 0; warm < 10000; warm++) filterAll(order, state, refs);
        long thread = Thread.currentThread().getId(), before = bean.getThreadAllocatedBytes(thread);
        for (int i = 0; i < 1000; i++) filterAll(order, state, refs);
        Assert.assertEquals(0, bean.getThreadAllocatedBytes(thread) - before);
    }

    @Test public void externalPublicationInvalidatesPreparedKeysAndRawGroupsKeepTheirOwnTieOrder() {
        TiledMapRenderState state = new TiledMapRenderState(128);
        TiledRenderOrder order = state.prepareOrderForMap(7, map(state)); order.prepare(state);
        Assert.assertTrue(order.isPrepared());
        state.setRenderDataForRef(0, 1, 0, 1, 0, 0, 0, Long.MAX_VALUE,
                0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 1, (byte) 0);
        Assert.assertFalse(order.isPrepared());
        order.prepare(state);
        Assert.assertTrue(order.isPrepared());
        state.addVisibleRef(3); state.addVisibleRef(0);
        state.addVisibleMap(7, 0, 0, 0, 0, 2);
        Assert.assertFalse(order.isPrepared());
        Assert.assertNull(state.visibleMapPreparedOrder(0));
        state.removeOrderForMap(7);
        Assert.assertNull(state.preparedOrderOwner[0]);
        Assert.assertNull(state.preparedOrderOwner[3]);
    }

    private static void filterAll(TiledRenderOrder order, TiledMapRenderState state, int[] refs) {
        for (int i = 0; i < refs.length; i++) refs[i] = i;
        order.prepare(state); order.filterVisible(state, refs, 0, refs.length);
    }

    @Test public void standalonePublisherCanAddPreviouslyUnpreparedRefs() {
        TiledMapRenderState state = new TiledMapRenderState(128);
        TiledMapLayerData map = map(state);
        TileChunk chunk = map.getChunk(0, 0);
        chunk.setRenderableLocalIndex(0, false);
        TiledRenderOrder order = state.prepareOrderForMap(7, map); order.prepare(state);
        Assert.assertNull(state.preparedOrderOwner[0]);
        state.setRenderDataForRef(0, 1, 0, 1, 0, 0, 0, 99,
                0, 0, 1, 0, 1, 1, 0, 1, 0, 0, 1, 1, 1, 1, (byte) 0);
        chunk.setRenderableLocalIndex(0, true);
        state.addVisibleRef(0); state.addVisibleMap(7, 0, 0, 0, 0, 1);
        Assert.assertFalse(order.isPrepared());
        order.prepare(state);
        int[] refs = {0, 3}; order.filterVisible(state, refs, 0, 2);
        Assert.assertArrayEquals(new int[]{3, 0}, refs);
        Assert.assertEquals(64, order.refCount());
    }

    private static TiledMapLayerData map(TiledMapRenderState state) {
        TiledMapLayerData map = new TiledMapLayerData(8, 8, 16, 16, 2);
        for (int cy = 0; cy < map.getChunksY(); cy++) for (int cx = 0; cx < map.getChunksX(); cx++) {
            TileChunk chunk = map.getChunk(cx, cy);
            chunk.renderRefStartIndex = state.registerRefs(chunk.cellCount());
            for (int local = chunk.cellCount() - 1; local >= 0; local--) {
                int ref = chunk.renderRefStartIndex + local;
                state.enabled[ref] = true; state.sortKey[ref] = ref;
                chunk.setRenderableLocalIndex(local, true);
            }
        }
        return map;
    }
}
