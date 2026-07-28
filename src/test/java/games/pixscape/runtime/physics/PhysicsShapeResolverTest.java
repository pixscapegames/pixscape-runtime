package games.pixscape.runtime.physics;

import org.junit.Assert;
import org.junit.Test;

public class PhysicsShapeResolverTest {
    private final PhysicsShapeResolver resolver = new PhysicsShapeResolver();

    @Test
    public void resolvesEveryManualKindWithoutMutatingOrAliasingTheSource() {
        assertResolved(PhysicsGeometryData.SHAPE_BOX);
        assertResolved(PhysicsGeometryData.SHAPE_CIRCLE);
        assertResolved(PhysicsGeometryData.SHAPE_POLYGON);
    }

    @Test
    public void rejectsLinkedShapeBeforeSpatialResolverSlice() {
        PhysicsShapeData source = new PhysicsShapeData();
        source.physicsShapeId = 7;
        source.spatialBlockId = 3;

        try {
            resolver.resolve(source);
            Assert.fail("Linked shape resolution must be unavailable in this slice.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "Linked physics shape resolution is not available before "
                            + "the Spatial resolver slice."));
        }
    }

    private void assertResolved(int shapeType) {
        PhysicsShapeData source = new PhysicsShapeData();
        source.physicsShapeId = shapeType + 1;
        source.geometry = new PhysicsGeometryData();
        source.geometry.shapeType = shapeType;
        if (shapeType == PhysicsGeometryData.SHAPE_POLYGON) {
            source.geometry.polygonVertices =
                    new float[]{0f, 0f, 2f, 0f, 0f, 2f};
            source.geometry.polygonVertexCount = 3;
        }
        float[] sourceVertices = source.geometry.polygonVertices;

        ResolvedPhysicsShape resolved = resolver.resolve(source);
        if (resolved.polygonVertices.length > 0) {
            resolved.polygonVertices[0] = 42f;
        }

        Assert.assertEquals(source.physicsShapeId, resolved.physicsShapeId);
        Assert.assertEquals(shapeType, resolved.shapeType);
        Assert.assertNotSame(sourceVertices, resolved.polygonVertices);
        if (sourceVertices.length > 0) {
            Assert.assertEquals(0f, sourceVertices[0], 0f);
        }
    }
}
