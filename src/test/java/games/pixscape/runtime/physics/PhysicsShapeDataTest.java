package games.pixscape.runtime.physics;

import com.badlogic.gdx.utils.Json;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsShapeDataTest {
    @Test
    public void defaultsMatchCurrentFixtureDefaults() {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.geometry = new PhysicsGeometryData();
        Assert.assertEquals(0, shape.spatialBlockId);
        Assert.assertEquals(PhysicsGeometryData.SHAPE_BOX, shape.geometry.shapeType);
        Assert.assertEquals(0.5f, shape.geometry.halfWidth, 0f);
        Assert.assertEquals(0.5f, shape.geometry.halfHeight, 0f);
        Assert.assertEquals(0.5f, shape.geometry.radius, 0f);
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
    public void copyIsDeepAndPreservesSpatialFootprintOwnership() {
        PhysicsShapeData source = polygon();
        source.spatialFootprint = true;
        source.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        source.geometry.radius = 1f;
        PhysicsShapeData copy = source.copy();

        Assert.assertTrue(source.contentEquals(copy));
        Assert.assertEquals(0, copy.spatialBlockId);
        Assert.assertTrue(copy.spatialFootprint);
        copy.geometry.polygonVertices[0] = 99f;
        copy.density = 4f;

        Assert.assertEquals(0f, source.geometry.polygonVertices[0], 0f);
        Assert.assertEquals(1f, source.density, 0f);
        Assert.assertFalse(source.contentEquals(copy));
    }

    @Test
    public void contentEqualsComparesSpatialBlockId() {
        PhysicsShapeData source = polygon();
        source.spatialBlockId = 17;
        source.geometry = null;
        PhysicsShapeData copy = source.copy();

        Assert.assertEquals(17, copy.spatialBlockId);
        Assert.assertTrue(source.contentEquals(copy));

        copy.spatialBlockId = 18;

        Assert.assertFalse(source.contentEquals(copy));
    }

    @Test
    public void structureValidationRejectsInvalidIdentityAndUnionLayout() {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.geometry = new PhysicsGeometryData();
        expectInvalid(shape, "physicsShapeId");

        shape.physicsShapeId = 1;
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        shape.geometry.polygonVertexCount = 3;
        shape.geometry.polygonVertices = new float[]{0f, 0f};
        expectInvalid(shape, "smaller");
    }

    @Test
    public void structureValidationAppliesLocalAuthoredInvariants() {
        PhysicsShapeData shape = polygon();
        shape.spatialBlockId = -1;
        expectInvalid(shape, "spatialBlockId");

        shape.spatialBlockId = 0;
        shape.geometry = null;
        expectInvalid(shape, "manual shape geometry");

        shape.geometry = new PhysicsGeometryData();
        shape.validateStructure();

        shape.spatialBlockId = 4;
        expectInvalid(shape, "linked shape geometry");

        shape.geometry = null;
        shape.validateStructure();
    }

    @Test
    public void spatialFootprintRequiresEnabledManualCircleWithPositiveRadius() {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = 1;
        shape.geometry = new PhysicsGeometryData();
        shape.spatialFootprint = true;

        expectInvalid(shape, "circle geometry");

        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        shape.geometry.polygonVertexCount = 3;
        shape.geometry.polygonVertices = new float[]{0f, 0f, 1f, 0f, 0f, 1f};
        expectInvalid(shape, "circle geometry");

        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        shape.sensor = true;
        shape.validateStructure();

        shape.enabled = false;
        expectInvalid(shape, "must be enabled");

        shape.enabled = true;
        shape.geometry.radius = 0f;
        expectInvalid(shape, "radius");

        shape.geometry.radius = Float.NaN;
        expectInvalid(shape, "finite");

        shape.geometry.radius = 1f;
        shape.spatialBlockId = 4;
        shape.geometry = null;
        expectInvalid(shape, "manual shape");
    }

    @Test
    public void sourceAndCompiledDataRoundTripThroughLibgdxJson() {
        PhysicsShapeData source = polygon();
        source.geometry.offsetX = 3f;
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
        Assert.assertEquals(compiled.spatialFootprint, restoredCompiled.spatialFootprint);
        Assert.assertEquals(compiled.partIndex, restoredCompiled.partIndex);
    }

    @Test
    public void explicitSpatialFootprintRoundTripsAndOldJsonDefaultsToFalse() {
        PhysicsShapeData explicit = new PhysicsShapeData();
        explicit.physicsShapeId = 9;
        explicit.geometry = new PhysicsGeometryData();
        explicit.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        explicit.spatialFootprint = true;
        Json json = new Json();

        PhysicsShapeData restoredExplicit = json.fromJson(
                PhysicsShapeData.class, json.toJson(explicit));
        PhysicsShapeData restoredLegacy = json.fromJson(PhysicsShapeData.class,
                "{physicsShapeId:9,spatialBlockId:0,geometry:{shapeType:1,radius:1}}");

        Assert.assertTrue(restoredExplicit.spatialFootprint);
        Assert.assertFalse(restoredLegacy.spatialFootprint);
    }

    private static PhysicsShapeData polygon() {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.geometry = new PhysicsGeometryData();
        shape.physicsShapeId = 9;
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_POLYGON;
        shape.geometry.polygonVertexCount = 4;
        shape.geometry.polygonVertices = new float[]{0f, 0f, 2f, 0f, 2f, 2f, 0f, 2f};
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
