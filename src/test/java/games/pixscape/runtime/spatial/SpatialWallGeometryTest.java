package games.pixscape.runtime.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

import static games.pixscape.runtime.spatial.SpatialWallGeometry.CoverageValidation;
import static games.pixscape.runtime.spatial.SpatialWallGeometry.JunctionClassification;

public class SpatialWallGeometryTest {
    private final SpatialWallGeometry.Bounds aBounds = new SpatialWallGeometry.Bounds();
    private final SpatialWallGeometry.Bounds bBounds = new SpatialWallGeometry.Bounds();
    private final SpatialWallGeometry.Junction junction = new SpatialWallGeometry.Junction();

    @Test
    public void fractionalInsetFootprintIsValidAndCopyPreservesExactValues() {
        TiledMapLayerData map = occupiedMap(20, 10);
        SpatialBlockData wall = linkedWall(9, 13, 10, 6, 17, 6);
        wall.x = 10.14f;
        wall.y = 6.38f;
        wall.width = 7.62f;
        wall.depth = 0.19f;

        Assert.assertEquals(CoverageValidation.VALID, validate(wall, map));
        SpatialBlockData copy = wall.copy();
        Assert.assertEquals(Float.floatToIntBits(wall.x), Float.floatToIntBits(copy.x));
        Assert.assertEquals(Float.floatToIntBits(wall.y), Float.floatToIntBits(copy.y));
        Assert.assertEquals(Float.floatToIntBits(wall.width), Float.floatToIntBits(copy.width));
        Assert.assertEquals(Float.floatToIntBits(wall.depth), Float.floatToIntBits(copy.depth));
        Assert.assertEquals(13, copy.structureId);
    }

    @Test
    public void rejectsMalformedOrOutsideFootprintButNotSubCellDimensions() {
        TiledMapLayerData map = occupiedMap(4, 4);
        SpatialBlockData wall = linkedWall(1, 1, 1, 1, 2, 2);
        wall.x = 1.2f;
        wall.y = 1.3f;
        wall.width = 0.2f;
        wall.depth = 0.15f;
        Assert.assertEquals(CoverageValidation.VALID, validate(wall, map));

        wall.x = 0.99f;
        Assert.assertEquals(CoverageValidation.FOOTPRINT_OUTSIDE_LINKED_REGION, validate(wall, map));
        wall.x = 1.2f;
        wall.width = 1.81f;
        Assert.assertEquals(CoverageValidation.FOOTPRINT_OUTSIDE_LINKED_REGION, validate(wall, map));
        wall.width = Float.NaN;
        Assert.assertEquals(CoverageValidation.INVALID_GEOMETRY, validate(wall, map));
        wall.width = SpatialWallGeometry.GEOMETRY_EPSILON * 0.5f;
        Assert.assertEquals(CoverageValidation.INVALID_GEOMETRY, validate(wall, map));
        wall.width = 0.2f;
        Assert.assertEquals(CoverageValidation.VALID, validate(wall, map));
    }

    @Test
    public void linkedRefsRemainOneCompleteOccupiedRectangleIndependentOfFootprint() {
        TiledMapLayerData map = occupiedMap(4, 4);
        SpatialBlockData wall = linkedWall(1, 1, 1, 1, 2, 2);
        wall.x = 1.25f;
        wall.y = 1.25f;
        wall.width = 0.5f;
        wall.depth = 0.5f;
        Assert.assertEquals(CoverageValidation.VALID, validate(wall, map));

        wall.linkedTileRefs.pop();
        Assert.assertEquals(CoverageValidation.WRONG_REF_COUNT, validate(wall, map));
        wall.addLinkedTileRef(3, 3, 9);
        Assert.assertEquals(CoverageValidation.WRONG_REF_COUNT, validate(wall, map));
        wall.linkedTileRefs.set(3, wall.linkedTileRefs.get(0));
        Assert.assertEquals(CoverageValidation.DUPLICATE_REF, validate(wall, map));

        wall = linkedWall(1, 1, 1, 1, 2, 2);
        map.setTile(2, 2, 0);
        Assert.assertEquals(CoverageValidation.EMPTY_MAP_CELL, validate(wall, map));
    }

    @Test
    public void linkedRefOrderingAndAssetChangesDoNotAffectValidity() {
        TiledMapLayerData map = occupiedMap(3, 3);
        SpatialBlockData wall = linkedWall(1, 1, 0, 0, 1, 1);
        wall.linkedTileRefs.get(0).tileAssetId = 999;
        wall.linkedTileRefs.swap(0, 3);
        Assert.assertEquals(CoverageValidation.VALID, validate(wall, map));
    }

    @Test
    public void continuousJunctionsRequirePositiveArea() {
        assertJunction(footprint(0.1f, 0.4f, 2.7f, 0.2f),
                footprint(1.2f, 0.1f, 0.15f, 1.2f), JunctionClassification.VALID_RECTANGULAR_JUNCTION);
        assertJunction(footprint(0f, 0f, 2f, 0.3f),
                footprint(1f, 0.1f, 2f, 1.4f), JunctionClassification.VALID_RECTANGULAR_JUNCTION);
        assertJunction(footprint(0f, 0f, 1f, 1f),
                footprint(1f, 0f, 1f, 1f), JunctionClassification.NONE);
        assertJunction(footprint(0f, 0f, 1f, 1f),
                footprint(1f, 1f, 1f, 1f), JunctionClassification.NONE);
    }

    @Test
    public void continuousDuplicateContainmentAndMalformedAreRejected() {
        assertJunction(footprint(0.1f, 0.2f, 1.3f, 0.4f),
                footprint(0.1f, 0.2f, 1.3f, 0.4f), JunctionClassification.DUPLICATE);
        assertJunction(footprint(0f, 0f, 4f, 4f),
                footprint(1.1f, 1.2f, 0.3f, 0.4f), JunctionClassification.CONTAINMENT);
        SpatialBlockData invalid = footprint(0f, 0f, 1f, 1f);
        invalid.depth = Float.NaN;
        assertJunction(invalid, footprint(0f, 0f, 1f, 1f), JunctionClassification.INVALID);
    }

    private CoverageValidation validate(SpatialBlockData wall, TiledMapLayerData map) {
        return SpatialWallGeometry.validateAuthoredWall(wall, map, aBounds);
    }

    private void assertJunction(SpatialBlockData a, SpatialBlockData b, JunctionClassification expected) {
        Assert.assertEquals(expected, SpatialWallGeometry.classifyJunction(a, b, aBounds, bBounds, junction));
    }

    private static SpatialBlockData footprint(float x, float y, float width, float depth) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = 1;
        wall.structureId = 1;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        return wall;
    }

    private static SpatialBlockData linkedWall(int id, int structureId,
                                               int minGx, int minGy, int maxGx, int maxGy) {
        SpatialBlockData wall = footprint(minGx, minGy, maxGx - minGx + 1, maxGy - minGy + 1);
        wall.id = id;
        wall.structureId = structureId;
        wall.beginAuthoredLinkedTileRefs();
        for (int gy = minGy; gy <= maxGy; gy++) {
            for (int gx = minGx; gx <= maxGx; gx++) wall.addLinkedTileRef(gx, gy, 1);
        }
        return wall;
    }

    private static TiledMapLayerData occupiedMap(int width, int height) {
        TiledMapLayerData map = new TiledMapLayerData(width, height, 16, 16, 2);
        for (int gy = 0; gy < height; gy++) for (int gx = 0; gx < width; gx++) map.setTile(gx, gy, 1);
        return map;
    }
}
