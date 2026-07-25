package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Json;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsShapeDataTest {
    @Test
    public void defaultsMatchCurrentFixtureDefaults() {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.directGeometry = new PhysicsDirectGeometryData();
        Assert.assertEquals(PhysicsDirectGeometryData.SHAPE_BOX, shape.directGeometry.shapeType);
        Assert.assertEquals(0.5f, shape.directGeometry.halfWidth, 0f);
        Assert.assertEquals(0.5f, shape.directGeometry.halfHeight, 0f);
        Assert.assertEquals(0.5f, shape.directGeometry.radius, 0f);
        Assert.assertEquals(1f, shape.density, 0f);
        Assert.assertEquals(0.2f, shape.friction, 0f);
        Assert.assertEquals(0f, shape.restitution, 0f);
        Assert.assertFalse(shape.sensor);
        Assert.assertEquals(1, shape.categoryBits);
        Assert.assertEquals((short) 0xFFFF, shape.maskBits);
        Assert.assertEquals(0, shape.groupIndex);
        Assert.assertTrue(shape.enabled);
    }

    @Test
    public void copyIsDeepAndContentEqualBeforeMutation() {
        PhysicsShapeData source = polygon();
        PhysicsShapeData copy = source.copy();

        Assert.assertTrue(source.contentEquals(copy));
        copy.directGeometry.polygonVertices[0] = 99f;
        copy.density = 4f;

        Assert.assertEquals(0f, source.directGeometry.polygonVertices[0], 0f);
        Assert.assertEquals(1f, source.density, 0f);
        Assert.assertFalse(source.contentEquals(copy));
    }

    @Test
    public void structureValidationRejectsInvalidIdentityAndUnionLayout() {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.directGeometry = new PhysicsDirectGeometryData();
        expectInvalid(shape, "physicsShapeId");

        shape.physicsShapeId = 1;
        shape.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
        shape.directGeometry.polygonVertexCount = 3;
        shape.directGeometry.polygonVertices = new float[]{0f, 0f};
        expectInvalid(shape, "smaller");
    }

    @Test
    public void sourceAndCompiledDataRoundTripThroughLibgdxJson() {
        PhysicsShapeData source = polygon();
        source.directGeometry.offsetX = 3f;
        source.sensor = true;
        Json json = new Json();

        PhysicsShapeData restoredSource = json.fromJson(
                PhysicsShapeData.class, json.toJson(source));
        CompiledFixtureData compiled = new PhysicsShapeCompiler().compile(
                new PhysicsShapeResolver().resolve(source))[0];
        CompiledFixtureData restoredCompiled = json.fromJson(
                CompiledFixtureData.class, json.toJson(compiled));

        Assert.assertTrue(source.contentEquals(restoredSource));
        Assert.assertArrayEquals(
                compiled.polygonVertices, restoredCompiled.polygonVertices, 0f);
        Assert.assertEquals(compiled.physicsShapeId, restoredCompiled.physicsShapeId);
        Assert.assertEquals(compiled.partIndex, restoredCompiled.partIndex);
    }

    private static PhysicsShapeData polygon() {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.directGeometry = new PhysicsDirectGeometryData();
        shape.physicsShapeId = 9;
        shape.directGeometry.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
        shape.directGeometry.polygonVertexCount = 4;
        shape.directGeometry.polygonVertices = new float[]{0f, 0f, 2f, 0f, 2f, 2f, 0f, 2f};
        return shape;
    }

    private static void expectInvalid(PhysicsShapeData shape, String messagePart) {
        try {
            shape.validateStructure();
            Assert.fail("Expected validation failure.");
        } catch (IllegalArgumentException ex) {
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains(messagePart));
        }
    }
}
