package games.pixscape.runtime.spatial;

import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledProjection;
import org.junit.Test;

import static org.junit.Assert.*;

public class SpatialSharedAnchorIndexTest {
    @Test
    public void privateArrayFactoriesGrowAndReusePooledMemberships() {
        SpatialSharedAnchorIndex index = new SpatialSharedAnchorIndex();
        TiledMapLayerData map = map();
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        faces.anchorCount = 24;
        faces.anchorGx = new int[24];
        faces.anchorGy = new int[24];
        for (int i = 0; i < faces.anchorCount; i++) {
            faces.anchorGx[i] = i % 8;
            faces.anchorGy[i] = i / 8;
        }
        // Grow the context/cohort arrays, then members and private Entry storage.
        for (int owner = 0; owner < 20; owner++) {
            index.publish(owner, map, faces, owner, 0);
        }
        for (int owner = 0; owner < 20; owner++) {
            index.publish(owner, map, faces, 0, 0);
        }
        assertEquals(20, index.sharedContextCount());
        SpatialSharedAnchorIndex.Context removed = index.publish(19, map, faces, 0, 0);
        Object firstEntry = removed.owned.first();
        assertEquals(24, removed.owned.size);
        assertTrue(index.remove(19));
        assertNull(removed.map);
        assertNull(removed.faces);
        SpatialSharedAnchorIndex.Context reused = index.publish(19, map, faces, 0, 0);
        assertSame(removed, reused);
        assertSame(firstEntry, reused.owned.peek());
        assertEquals(20, index.sharedContextCount());
        index.clear();
        assertEquals(0, index.contextCount());
    }

    @Test
    public void collidingSignaturesKeepDistinctCohortsAndAbsentOwnersStayAbsent() {
        SpatialSharedAnchorIndex index = new SpatialSharedAnchorIndex();
        TiledMapLayerData a = map();
        TiledMapLayerData b = map();
        a.originX = a.originY = 1f;
        b.originX = Float.intBitsToFloat(Float.floatToIntBits(1f) + 1);
        b.originY = Float.intBitsToFloat(Float.floatToIntBits(1f) - 31);
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache();
        SpatialSharedAnchorIndex.Context first = index.publish(1, a, faces, 0, 0);
        index.publish(2, b, faces, 0, 0);
        assertEquals(0, index.sharedContextCount());
        assertSame(first, index.publish(1, a, faces, 0, 0));
        index.publish(3, a, faces, 0, 0);
        assertEquals(2, index.sharedContextCount());
        for (int i = 0; i < 1000; i++) {
            assertFalse(index.remove(-1));
            assertFalse(index.discardReplacedMap(-1, a));
        }
        assertEquals(3, index.contextCount());
        assertTrue(index.remove(1));
        assertNull(first.map);
        assertNull(first.faces);
        assertEquals(0, index.sharedContextCount());
        index.clear();
        assertEquals(0, index.contextCount());
    }

    @Test
    public void cacheIdentityAndAllWindowParametersInvalidateButAltitudeDoesNotSplitCohorts() {
        SpatialSharedAnchorIndex index = new SpatialSharedAnchorIndex();
        TiledMapLayerData a = map(), b = map();
        SpatialProjectedFaceCache f = new SpatialProjectedFaceCache();
        SpatialSharedAnchorIndex.Context handle = index.publish(1, a, f, 0, 0);
        index.publish(2, b, new SpatialProjectedFaceCache(), 0, 0);
        long compiled = index.reindexCount;
        for (int i = 0; i < 1000; i++) {
            assertSame(handle, index.publish(1, a, f, 0, 0));
        }
        assertEquals(compiled, index.reindexCount);
        index.publish(1, a, new SpatialProjectedFaceCache(), 0, 0);
        assertEquals(compiled + 1, index.reindexCount);
        a.defaultTileAltitude = 2048;
        index.publish(1, a, f, 0, 0);
        assertEquals(2, index.sharedContextCount());
        for (int change = 0; change < 12; change++) {
            TiledMapLayerData altered = map();
            float dx = 0, dy = 0;
            switch (change) {
                case 0: altered.projection = TiledProjection.ORTHO; break;
                case 1: altered.tileWidth++; break;
                case 2: altered.tileHeight++; break;
                case 3: altered.mapWidth++; break;
                case 4: altered.mapHeight++; break;
                case 5: altered.originX++; break;
                case 6: altered.originY++; break;
                case 7: dx++; break;
                case 8: dy++; break;
                case 9: altered.visualPaddingTop++; break;
                case 10: altered.visualPaddingLeft++; break;
                case 11: altered.visualPaddingRight++; break;
                default: throw new AssertionError();
            }
            index.publish(1, altered, f, dx, dy);
            assertEquals(0, index.sharedContextCount());
            index.publish(1, a, f, 0, 0);
            assertEquals(2, index.sharedContextCount());
        }
        a.visualPaddingBottom++;
        index.publish(1, a, f, 0, 0);
        assertEquals(0, index.sharedContextCount());
        assertTrue(index.discardReplacedMap(1, map()));
        assertNull(handle.map);
        assertNull(handle.faces);
        index.clear();
    }

    private static TiledMapLayerData map() {
        return new TiledMapLayerData(8, 8, 64, 32, 8, TiledProjection.ISO);
    }
}
