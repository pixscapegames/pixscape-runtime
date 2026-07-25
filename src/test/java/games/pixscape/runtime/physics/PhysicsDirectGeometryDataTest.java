package games.pixscape.runtime.physics;

import org.junit.Assert;
import org.junit.Test;

public class PhysicsDirectGeometryDataTest {
    @Test
    public void copyOwnsItsPolygonVertices() {
        PhysicsDirectGeometryData source = new PhysicsDirectGeometryData();
        source.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
        source.polygonVertices = new float[]{0f, 0f, 2f, 0f, 0f, 2f};
        source.polygonVertexCount = 3;

        PhysicsDirectGeometryData copy = source.copy();
        copy.polygonVertices[0] = 99f;

        Assert.assertNotSame(source.polygonVertices, copy.polygonVertices);
        Assert.assertEquals(0f, source.polygonVertices[0], 0f);
    }
}
