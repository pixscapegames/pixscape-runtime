package games.pixscape.runtime.physics;

import org.junit.Assert;
import org.junit.Test;

public class PhysicsShapeResolverTest {
    private final PhysicsShapeResolver resolver = new PhysicsShapeResolver();

    @Test
    public void resolvesEveryDirectKindWithoutMutatingOrAliasingTheSource() {
        assertResolved(PhysicsDirectGeometryData.SHAPE_BOX);
        assertResolved(PhysicsDirectGeometryData.SHAPE_CIRCLE);
        assertResolved(PhysicsDirectGeometryData.SHAPE_POLYGON);
    }

    @Test
    public void rejectsMissingDirectGeometryBeforePublication() {
        PhysicsShapeData source = new PhysicsShapeData();
        source.physicsShapeId = 7;

        try {
            resolver.resolve(source);
            Assert.fail("Phase B must reject unresolved external geometry.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "external/spatial geometry is unavailable before binding Phase D"));
        }
    }

    private void assertResolved(int shapeType) {
        PhysicsShapeData source = new PhysicsShapeData();
        source.physicsShapeId = shapeType + 1;
        source.directGeometry = new PhysicsDirectGeometryData();
        source.directGeometry.shapeType = shapeType;
        if (shapeType == PhysicsDirectGeometryData.SHAPE_POLYGON) {
            source.directGeometry.polygonVertices =
                    new float[]{0f, 0f, 2f, 0f, 0f, 2f};
            source.directGeometry.polygonVertexCount = 3;
        }
        float[] sourceVertices = source.directGeometry.polygonVertices;

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
