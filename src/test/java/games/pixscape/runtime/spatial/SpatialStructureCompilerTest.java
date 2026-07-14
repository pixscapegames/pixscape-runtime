package games.pixscape.runtime.spatial;

import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.SpatialBlockData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialStructureCompilerTest {
    @Test
    public void isolatedBlockProducesCompleteEnvelope() {
        CompiledSpatialStructure compiled = compile(wall(1, 4, 2f, 3f, 5f, 2f));

        Assert.assertEquals(4, compiled.structureId());
        Assert.assertEquals(4, compiled.segmentCount());
        Assert.assertEquals(14f, perimeter(compiled), 0f);
        assertSegment(compiled, 2f, 3f, 7f, 3f, 0, -1);
        assertSegment(compiled, 2f, 5f, 7f, 5f, 0, 1);
        assertSegment(compiled, 2f, 3f, 2f, 5f, -1, 0);
        assertSegment(compiled, 7f, 3f, 7f, 5f, 1, 0);
        Assert.assertEquals(2f, compiled.lowerZ(), 0f);
        Assert.assertEquals(10f, compiled.upperZ(), 0f);
    }

    @Test
    public void fractionalContinuousFootprintsArePreservedExactly() {
        CompiledSpatialStructure compiled = compile(
                wall(1, 12, 10.14f, 6.38f, 4.2f, 0.19f),
                wall(2, 12, 13.9f, 6.38f, 3.86f, 0.19f));

        Assert.assertEquals(4, compiled.segmentCount());
        float maxX = 13.9f + 3.86f;
        float maxY = 6.38f + 0.19f;
        assertSegment(compiled, 10.14f, 6.38f, maxX, 6.38f, 0, -1);
        assertSegment(compiled, 10.14f, maxY, maxX, maxY, 0, 1);
    }

    @Test
    public void collinearOverlappingWallsRemoveInternalGeometryAndMergeOuterSides() {
        CompiledSpatialStructure compiled = compile(
                wall(1, 4, 0f, 1f, 4f, 1f),
                wall(2, 4, 3f, 1f, 4f, 1f));

        Assert.assertEquals(4, compiled.segmentCount());
        Assert.assertEquals(16f, perimeter(compiled), 0f);
        assertSegment(compiled, 0f, 1f, 7f, 1f, 0, -1);
        assertNoSegmentOnInteriorLine(compiled, 3f, true);
        assertNoSegmentOnInteriorLine(compiled, 4f, true);
    }

    @Test
    public void lJunctionKeepsOnlyConcaveAndConvexExterior() {
        CompiledSpatialStructure compiled = compile(
                wall(1, 2, 0f, 0f, 4f, 1f),
                wall(2, 2, 0f, 0f, 1f, 4f));

        Assert.assertEquals(6, compiled.segmentCount());
        Assert.assertEquals(16f, perimeter(compiled), 0f);
        assertSegment(compiled, 1f, 1f, 4f, 1f, 0, 1);
        assertSegment(compiled, 1f, 1f, 1f, 4f, 1, 0);
    }

    @Test
    public void tJunctionRemovesCoveredTerminatingEndCap() {
        CompiledSpatialStructure compiled = compile(
                wall(1, 3, 0f, 0f, 6f, 1f),
                wall(2, 3, 2f, 0f, 2f, 4f));

        Assert.assertEquals(8, compiled.segmentCount());
        Assert.assertEquals(20f, perimeter(compiled), 0f);
        assertNoSegment(compiled, 2f, 0f, 4f, 0f);
    }

    @Test
    public void crossJunctionContainsOnlyUnionBoundary() {
        CompiledSpatialStructure compiled = compile(
                wall(1, 5, 0f, 2f, 6f, 2f),
                wall(2, 5, 2f, 0f, 2f, 6f));

        Assert.assertEquals(12, compiled.segmentCount());
        Assert.assertEquals(24f, perimeter(compiled), 0f);
        assertNoSegment(compiled, 2f, 2f, 4f, 2f);
        assertNoSegment(compiled, 2f, 4f, 4f, 4f);
    }

    @Test
    public void partialOverlapRemovesOnlyCoveredIntervals() {
        CompiledSpatialStructure compiled = compile(
                wall(1, 6, 0f, 0f, 4f, 2f),
                wall(2, 6, 2f, 1f, 4f, 2f));

        Assert.assertEquals(8, compiled.segmentCount());
        Assert.assertEquals(18f, perimeter(compiled), 0f);
        assertSegment(compiled, 0f, 2f, 2f, 2f, 0, 1);
        assertSegment(compiled, 4f, 1f, 6f, 1f, 0, -1);
    }

    @Test
    public void closedRoomRetainsOuterAndInnerBoundaryLoops() {
        CompiledSpatialStructure compiled = compile(
                wall(1, 7, 0f, 0f, 6f, 1f),
                wall(2, 7, 0f, 5f, 6f, 1f),
                wall(3, 7, 0f, 1f, 1f, 4f),
                wall(4, 7, 5f, 1f, 1f, 4f));

        Assert.assertEquals(8, compiled.segmentCount());
        Assert.assertEquals(40f, perimeter(compiled), 0f);
        assertSegment(compiled, 1f, 1f, 5f, 1f, 0, 1);
        assertSegment(compiled, 1f, 5f, 5f, 5f, 0, -1);
    }

    @Test
    public void insertionOrderDoesNotChangeCompiledOutput() {
        SpatialBlockData a = wall(1, 9, 0f, 0f, 4f, 1f);
        SpatialBlockData b = wall(2, 9, 0f, 0f, 1f, 4f);
        SpatialBlockData c = wall(3, 9, 3f, 0f, 1f, 3f);

        assertSame(compile(a, b, c), compile(c, a, b));
    }

    @Test
    public void incompatibleBoundaryPropertiesPreventCollinearMerge() {
        SpatialBlockData first = wall(1, 10, 0f, 0f, 2f, 1f);
        SpatialBlockData second = wall(2, 10, 1f, 0f, 2f, 1f);
        second.actorOccluder = false;

        CompiledSpatialStructure compiled = compile(first, second);

        Assert.assertTrue(compiled.segmentCount() > 4);
        Assert.assertTrue(hasSegment(compiled, 0f, 0f, 2f, 0f));
        Assert.assertTrue(hasSegment(compiled, 2f, 0f, 3f, 0f));
    }

    private static CompiledSpatialStructure compile(SpatialBlockData... walls) {
        Array<SpatialBlockData> authored = new Array<>(SpatialBlockData[]::new);
        for (SpatialBlockData wall : walls) authored.add(wall);
        return SpatialStructureCompiler.compile(authored, walls[0].structureId);
    }

    private static SpatialBlockData wall(int id, int structureId, float x, float y, float width, float depth) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = structureId;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        wall.altitude = 2f;
        wall.height = 8f;
        return wall;
    }

    private static float perimeter(CompiledSpatialStructure compiled) {
        float length = 0f;
        for (int i = 0; i < compiled.segmentCount(); i++) {
            length += Math.abs(compiled.endX(i) - compiled.startX(i));
            length += Math.abs(compiled.endY(i) - compiled.startY(i));
        }
        return length;
    }

    private static void assertSegment(CompiledSpatialStructure compiled,
                                      float x0, float y0, float x1, float y1, int nx, int ny) {
        for (int i = 0; i < compiled.segmentCount(); i++) {
            if (Float.compare(compiled.startX(i), x0) == 0
                    && Float.compare(compiled.startY(i), y0) == 0
                    && Float.compare(compiled.endX(i), x1) == 0
                    && Float.compare(compiled.endY(i), y1) == 0
                    && compiled.normalX(i) == nx && compiled.normalY(i) == ny) return;
        }
        Assert.fail("Missing segment [" + x0 + "," + y0 + "]-[" + x1 + "," + y1 + "] normal=" + nx + "," + ny);
    }

    private static boolean hasSegment(CompiledSpatialStructure compiled, float x0, float y0, float x1, float y1) {
        for (int i = 0; i < compiled.segmentCount(); i++) {
            if (Float.compare(compiled.startX(i), x0) == 0
                    && Float.compare(compiled.startY(i), y0) == 0
                    && Float.compare(compiled.endX(i), x1) == 0
                    && Float.compare(compiled.endY(i), y1) == 0) return true;
        }
        return false;
    }

    private static void assertNoSegment(CompiledSpatialStructure compiled, float x0, float y0, float x1, float y1) {
        Assert.assertFalse(hasSegment(compiled, x0, y0, x1, y1));
    }

    private static void assertNoSegmentOnInteriorLine(CompiledSpatialStructure compiled,
                                                       float coordinate,
                                                       boolean vertical) {
        for (int i = 0; i < compiled.segmentCount(); i++) {
            boolean onLine = vertical
                    ? Float.compare(compiled.startX(i), coordinate) == 0
                    && Float.compare(compiled.endX(i), coordinate) == 0
                    : Float.compare(compiled.startY(i), coordinate) == 0
                    && Float.compare(compiled.endY(i), coordinate) == 0;
            if (onLine) Assert.fail("Unexpected boundary on internal line " + coordinate);
        }
    }

    private static void assertSame(CompiledSpatialStructure first, CompiledSpatialStructure second) {
        Assert.assertEquals(first.structureId(), second.structureId());
        Assert.assertEquals(first.segmentCount(), second.segmentCount());
        for (int i = 0; i < first.segmentCount(); i++) {
            Assert.assertEquals(first.startX(i), second.startX(i), 0f);
            Assert.assertEquals(first.startY(i), second.startY(i), 0f);
            Assert.assertEquals(first.endX(i), second.endX(i), 0f);
            Assert.assertEquals(first.endY(i), second.endY(i), 0f);
            Assert.assertEquals(first.normalX(i), second.normalX(i));
            Assert.assertEquals(first.normalY(i), second.normalY(i));
            Assert.assertEquals(first.actorOccluder(i), second.actorOccluder(i));
        }
    }
}
