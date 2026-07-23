package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Json;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsShapeDataTest {
    @Test
    public void defaultsMatchCurrentFixtureDefaults() {
        PhysicsShapeData shape = new PhysicsShapeData();

        Assert.assertEquals(PhysicsShapeData.SHAPE_BOX, shape.shapeType);
        Assert.assertEquals(0.5f, shape.halfWidth, 0f);
        Assert.assertEquals(0.5f, shape.halfHeight, 0f);
        Assert.assertEquals(0.5f, shape.radius, 0f);
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
        copy.polygonVertices[0] = 99f;
        copy.density = 4f;

        Assert.assertEquals(0f, source.polygonVertices[0], 0f);
        Assert.assertEquals(1f, source.density, 0f);
        Assert.assertFalse(source.contentEquals(copy));
    }

    @Test
    public void structureValidationRejectsInvalidIdentityAndUnionLayout() {
        PhysicsShapeData shape = new PhysicsShapeData();
        expectInvalid(shape, "physicsShapeId");

        shape.physicsShapeId = 1;
        shape.shapeType = PhysicsShapeData.SHAPE_POLYGON;
        shape.polygonVertexCount = 3;
        shape.polygonVertices = new float[]{0f, 0f};
        expectInvalid(shape, "smaller");
    }

    @Test
    public void sourceAndCompiledDataRoundTripThroughLibgdxJson() {
        PhysicsShapeData source = polygon();
        source.offsetX = 3f;
        source.sensor = true;
        Json json = new Json();

        PhysicsShapeData restoredSource = json.fromJson(
                PhysicsShapeData.class, json.toJson(source));
        CompiledFixtureData compiled = new PhysicsShapeCompiler().compile(source)[0];
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
        shape.physicsShapeId = 9;
        shape.shapeType = PhysicsShapeData.SHAPE_POLYGON;
        shape.polygonVertexCount = 4;
        shape.polygonVertices = new float[]{0f, 0f, 2f, 0f, 2f, 2f, 0f, 2f};
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
