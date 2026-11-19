package games.pixscape.runtime.spatial;

import org.junit.Test;
import static org.junit.Assert.*;

public class ActorSpatialGeometryTest {
    @Test
    public void missingRepresentationCannotBecomeAnImageSizedRectangle() {
        ActorSpatialGeometry geometry = new ActorSpatialGeometry();
        try {
            update(geometry, null, false, false);
            fail("A missing spatial representation must be reported, not synthesized");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("representation"));
        }
    }

    @Test
    public void warmedStableAndMovingZonesReuseStorageWithoutRecurringAllocations() {
        java.lang.management.ThreadMXBean base = java.lang.management.ManagementFactory.getThreadMXBean();
        org.junit.Assume.assumeTrue(base instanceof com.sun.management.ThreadMXBean);
        com.sun.management.ThreadMXBean memory = (com.sun.management.ThreadMXBean) base;
        org.junit.Assume.assumeTrue(memory.isThreadAllocatedMemorySupported());
        memory.setThreadAllocatedMemoryEnabled(true);
        long thread = Thread.currentThread().getId();
        ActorSpatialGeometry geometry = new ActorSpatialGeometry();
        ActorSpatialRectangleData zone = new ActorSpatialRectangleData();
        zone.x = .1f;
        zone.y = .2f;
        zone.width = .7f;
        zone.height = .5f;
        float[] storage = geometry.vertices;
        for (boolean moving : new boolean[]{false, true}) {
            for (int i = 0; i < 100000; i++) {
                geometry.update(zone, moving ? i % 11 : 0, 0, -20, 100, 120, 140, 80, -10, false, false);
            }
            long minimumBytes = Long.MAX_VALUE, minimumNanos = Long.MAX_VALUE;
            for (int window = 0; window < 5; window++) {
                long allocated = memory.getThreadAllocatedBytes(thread);
                long start = System.nanoTime();
                for (int i = 0; i < 100000; i++) {
                    geometry.update(zone, moving ? i % 11 : 0, 0, -20, 100, 120, 140, 80, -10, false, false);
                }
                minimumNanos = Math.min(minimumNanos, System.nanoTime() - start);
                minimumBytes = Math.min(minimumBytes, memory.getThreadAllocatedBytes(thread) - allocated);
            }
            assertSame(storage, geometry.vertices);
            assertEquals(0, minimumBytes);
            assertTrue(geometry.vertexCount > 6);
            System.out.println("ActorSpatialGeometry " + (moving ? "moving" : "stable")
                    + ": best warmed window ns/update=" + minimumNanos / 100000.0
                    + ", recurring bytes=" + minimumBytes + ", vertices=" + geometry.vertexCount);
        }
    }

    private static void update(ActorSpatialGeometry geometry, ActorSpatialRectangleData zone, boolean flipX, boolean flipY) {
        geometry.update(zone, 0, 0, -20, 100, 120, 140, 80, -10, flipX, flipY);
    }

    @Test
    public void nativeTrianglesAndFlipsRoundTripOnDeformedQuad() {
        ActorSpatialGeometry geometry = new ActorSpatialGeometry();
        float[] point = new float[2], local = new float[2];
        for (int flip = 0; flip < 4; flip++) {
            update(geometry, new ActorSpatialRectangleData(), (flip & 1) != 0, (flip & 2) != 0);
            assertEquals(6, geometry.vertexCount);
            for (int x = 0; x <= 10; x++) {
                for (int y = 0; y <= 10; y++) {
                    float u = x / 10f, v = y / 10f;
                    geometry.localToWorld(u, v, point, 0);
                    assertTrue(geometry.worldToLocal(point[0], point[1], geometry.triangleAt(u, v), local, 0));
                    assertEquals(u, local[0], .00001f);
                    assertEquals(v, local[1], .00001f);
                }
            }
        }
        update(geometry, new ActorSpatialRectangleData(), false, false);
        geometry.localToWorld(.5f, .5f, point, 0);
        assertArrayEquals(new float[]{60, 70}, point, .00001f);
    }

    @Test
    public void clippedTrianglesCoverExactlyTheZoneAndDoNotAllocateAgain() {
        ActorSpatialGeometry geometry = new ActorSpatialGeometry();
        ActorSpatialRectangleData zone = new ActorSpatialRectangleData();
        zone.x = .1f;
        zone.y = .2f;
        zone.width = .7f;
        zone.height = .5f;
        float[] storage = geometry.vertices;
        for (int flip = 0; flip < 4; flip++) {
            boolean fx = (flip & 1) != 0, fy = (flip & 2) != 0;
            update(geometry, zone, fx, fy);
            float area = 0;
            for (int i = 0; i < geometry.vertexCount; i += 3) {
                int a = i * 4 + 2, b = a + 4, c = b + 4;
                float[] v = geometry.vertices;
                area += Math.abs((v[b] - v[a]) * (v[c + 1] - v[a + 1])
                        - (v[b + 1] - v[a + 1]) * (v[c] - v[a])) * .5f;
            }
            assertEquals(.35f, area, .00001f);
            int revision = geometry.revision;
            for (int repeat = 0; repeat < 1000; repeat++) {
                update(geometry, zone, fx, fy);
            }
            assertEquals(revision, geometry.revision);
            assertSame(storage, geometry.vertices);
        }
        zone.x = 2;
        zone.validate();
        update(geometry, zone, false, false);
        assertEquals(0, geometry.vertexCount);
    }

    @Test
    public void outsideZoneClipsToImageAndSingularInverseLeavesOutputIntact() {
        ActorSpatialGeometry geometry = new ActorSpatialGeometry();
        ActorSpatialRectangleData zone = new ActorSpatialRectangleData();
        zone.x = zone.y = -1;
        zone.width = zone.height = 3;
        update(geometry, zone, false, false);
        assertEquals(6, geometry.vertexCount);
        zone.validate();
        geometry.update(zone, 0, 0, 0, 0, 0, 0, 0, 0, false, false);
        float[] point = {7, 8};
        assertFalse(geometry.worldToLocal(2, 3, 0, point, 0));
        assertArrayEquals(new float[]{7, 8}, point, 0);
        assertEquals(0, geometry.vertexCount);
        assertEquals("Deformation does not change configured width", 3, zone.width, 0);
        assertEquals("Deformation does not change configured height", 3, zone.height, 0);
    }
}
