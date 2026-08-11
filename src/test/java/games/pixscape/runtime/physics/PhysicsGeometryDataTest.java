package games.pixscape.runtime.physics;

import org.junit.Assert;
import org.junit.Test;

public class PhysicsGeometryDataTest {
    @Test
    public void copyIsExactAndOwnsItsPolygonVertices() {
        PhysicsGeometryData source = new PhysicsGeometryData();
        source.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        source.offsetX = 1f;
        source.offsetY = 2f;
        source.angleDegrees = 3f;
        source.radius = 4f;
        source.halfWidth = 5f;
        source.halfHeight = 6f;
        source.polygonVertices = new float[]{0f, 0f, 2f, 0f, 0f, 2f};
        source.polygonVertexCount = 3;

        PhysicsGeometryData copy = source.copy();

        Assert.assertTrue(source.contentEquals(copy));
        Assert.assertNotSame(source.polygonVertices, copy.polygonVertices);
        copy.polygonVertices[0] = 99f;
        Assert.assertEquals(0f, source.polygonVertices[0], 0f);
        Assert.assertFalse(source.contentEquals(copy));
    }

    @Test
    public void validatesBoxCircleAndPolygonGeometry() {
        PhysicsGeometryData geometry = new PhysicsGeometryData();
        geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
        geometry.validate(1);

        geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        geometry.validate(1);

        geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        geometry.polygonVertices = new float[]{0f, 0f, 1f, 0f, 0f, 1f};
        geometry.polygonVertexCount = 3;
        geometry.validate(1);
    }
}
