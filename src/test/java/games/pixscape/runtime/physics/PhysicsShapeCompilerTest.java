package games.pixscape.runtime.physics;

import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.Arrays;

public class PhysicsShapeCompilerTest {
    private final PhysicsShapeCompiler compiler = new PhysicsShapeCompiler();

    @Test
    public void compilesBoxAndCopiesAllPhysicalProperties() {
        PhysicsShapeData shape = base(PhysicsDirectGeometryData.SHAPE_BOX);
        shape.directGeometry.halfWidth = 2f;
        shape.directGeometry.halfHeight = 3f;
        applyDistinctProperties(shape);

        CompiledFixtureData[] result = compiler.compile(resolve(shape));

        Assert.assertEquals(1, result.length);
        CompiledFixtureData fixture = result[0];
        Assert.assertEquals(PhysicsDirectGeometryData.SHAPE_BOX, fixture.shapeType);
        Assert.assertEquals(2f, fixture.halfWidth, 0f);
        Assert.assertEquals(3f, fixture.halfHeight, 0f);
        assertProperties(shape, fixture);
        Assert.assertEquals(shape.physicsShapeId, fixture.physicsShapeId);
        Assert.assertEquals(0, fixture.partIndex);
    }

    @Test
    public void compilesCircleAndRejectsNonPositiveRadius() {
        PhysicsShapeData shape = base(PhysicsDirectGeometryData.SHAPE_CIRCLE);
        shape.directGeometry.radius = 1.25f;

        CompiledFixtureData[] result = compiler.compile(resolve(shape));

        Assert.assertEquals(1, result.length);
        Assert.assertEquals(1.25f, result[0].radius, 0f);
        shape.directGeometry.radius = 0f;
        expectCompilationFailure(shape, "radius");
        shape.directGeometry.radius = -1f;
        expectCompilationFailure(shape, "radius");
    }

    @Test
    public void compilesEverySupportedConvexPolygonCardinality() {
        for (int count = 3; count <= PolygonDecomposer.BOX2D_MAX_POLYGON_VERTICES; count++) {
            PhysicsShapeData shape = polygon(regularPolygon(count, 2f), count);

            CompiledFixtureData[] result = compiler.compile(resolve(shape));

            Assert.assertEquals("vertex count " + count, 1, result.length);
            Assert.assertEquals(count, result[0].polygonVertexCount);
            Assert.assertTrue(PolygonValidator.isConvex(
                    result[0].polygonVertices, result[0].polygonVertexCount));
        }
    }

    @Test
    public void normalizesClockwiseAndCounterClockwisePolygonsIdentically() {
        float[] counterClockwise = {0f, 0f, 3f, 0f, 3f, 2f, 0f, 2f};
        float[] clockwise = {0f, 2f, 3f, 2f, 3f, 0f, 0f, 0f};

        CompiledFixtureData ccw = compiler.compile(resolve(polygon(counterClockwise, 4)))[0];
        CompiledFixtureData cw = compiler.compile(resolve(polygon(clockwise, 4)))[0];

        Assert.assertArrayEquals(ccw.polygonVertices, cw.polygonVertices, 0f);
        Assert.assertTrue(PolygonValidator.signedArea(
                cw.polygonVertices, cw.polygonVertexCount) > 0f);
    }

    @Test
    public void compilationNeverMutatesPolygonSource() {
        PhysicsShapeData shape = polygon(
                new float[]{0f, 0f, 0f, 3f, 2f, 3f, 2f, 0f}, 4);
        float[] before = Arrays.copyOf(shape.directGeometry.polygonVertices, shape.directGeometry.polygonVertices.length);

        compiler.compile(resolve(shape));

        Assert.assertArrayEquals(before, shape.directGeometry.polygonVertices, 0f);
    }

    @Test
    public void concaveCompilationIsDeterministicAndConservesArea() {
        PhysicsShapeData shape = polygon(
                new float[]{
                        0f, 0f,
                        4f, 0f,
                        4f, 1f,
                        2f, 1f,
                        2f, 3f,
                        0f, 3f
                }, 6);
        applyDistinctProperties(shape);

        CompiledFixtureData[] first = compiler.compile(resolve(shape));
        CompiledFixtureData[] second = compiler.compile(resolve(shape));

        Assert.assertTrue(first.length > 1);
        Assert.assertEquals(first.length, second.length);
        float compiledArea = 0f;
        for (int i = 0; i < first.length; i++) {
            Assert.assertEquals(i, first[i].partIndex);
            Assert.assertArrayEquals(first[i].polygonVertices, second[i].polygonVertices, 0f);
            assertProperties(shape, first[i]);
            compiledArea += Math.abs(PolygonValidator.signedArea(
                    first[i].polygonVertices, first[i].polygonVertexCount));
        }
        float sourceArea = Math.abs(
                PolygonValidator.signedArea(shape.directGeometry.polygonVertices, shape.directGeometry.polygonVertexCount));
        Assert.assertEquals(sourceArea, compiledArea, 0.0001f);
    }

    @Test
    public void convexPolygonAboveBox2dLimitIsDecomposedSafely() {
        PhysicsShapeData shape = polygon(regularPolygon(10, 3f), 10);

        CompiledFixtureData[] result = compiler.compile(resolve(shape));

        Assert.assertTrue(result.length > 1);
        for (CompiledFixtureData fixture : result) {
            Assert.assertTrue(
                    fixture.polygonVertexCount <= PolygonDecomposer.BOX2D_MAX_POLYGON_VERTICES);
            Assert.assertTrue(PolygonValidator.isConvex(
                    fixture.polygonVertices, fixture.polygonVertexCount));
        }
    }

    @Test
    public void rejectsInvalidPolygonsWithoutBoxFallback() {
        expectCompilationFailure(
                polygon(new float[]{0f, 0f, 1f, 0f}, 2),
                "at least 3");
        expectCompilationFailure(
                polygon(new float[]{0f, 0f, 2f, 2f, 0f, 2f, 2f, 0f}, 4),
                "self-intersections");
        expectCompilationFailure(
                polygon(new float[]{0f, 0f, 1f, 0f, 2f, 0f}, 3),
                "area");
        expectCompilationFailure(
                polygon(new float[]{0f, 0f, Float.NaN, 1f, 1f, 0f}, 3),
                "non-finite");
    }

    @Test
    public void compiledMutationCannotReachSource() {
        PhysicsShapeData shape = polygon(
                new float[]{0f, 0f, 2f, 0f, 2f, 2f, 0f, 2f}, 4);

        CompiledFixtureData compiled = compiler.compile(resolve(shape))[0];
        compiled.polygonVertices[0] = 100f;

        Assert.assertEquals(0f, shape.directGeometry.polygonVertices[0], 0f);
    }

    @Test
    public void disabledShapeProducesNoMaterializedFixture() {
        PhysicsShapeData shape = base(PhysicsDirectGeometryData.SHAPE_BOX);
        shape.enabled = false;

        Assert.assertEquals(0, compiler.compile(resolve(shape)).length);
    }

    @Test
    public void compilerAcceptsExternallyResolvedGeometryWithoutSpatialDependency() {
        PhysicsShapeData properties = base(PhysicsDirectGeometryData.SHAPE_BOX);
        ResolvedPhysicsShape resolved = ResolvedPhysicsShape.fromDirect(properties);
        resolved.shapeType = PhysicsDirectGeometryData.SHAPE_POLYGON;
        resolved.polygonVertexCount = 3;
        resolved.polygonVertices = new float[]{0f, 0f, 2f, 0f, 0f, 2f};
        resolved.diagnosticSource = "external-test";

        CompiledFixtureData[] result = compiler.compile(resolved);

        Assert.assertEquals(1, result.length);
        Assert.assertEquals(PhysicsDirectGeometryData.SHAPE_POLYGON, result[0].shapeType);
    }

    @Test
    public void compiledDescriptorHasNoFixtureIdField() {
        for (Field field : CompiledFixtureData.class.getDeclaredFields()) {
            Assert.assertNotEquals("fixture" + "Id", field.getName());
        }
    }

    private static PhysicsShapeData base(int shapeType) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.directGeometry = new PhysicsDirectGeometryData();
        shape.physicsShapeId = 17;
        shape.directGeometry.shapeType = shapeType;
        return shape;
    }

    private static PhysicsShapeData polygon(float[] vertices, int count) {
        PhysicsShapeData shape = base(PhysicsDirectGeometryData.SHAPE_POLYGON);
        shape.directGeometry.polygonVertices = vertices;
        shape.directGeometry.polygonVertexCount = count;
        return shape;
    }

    private static void applyDistinctProperties(PhysicsShapeData shape) {
        shape.directGeometry.offsetX = 1.5f;
        shape.directGeometry.offsetY = -2.5f;
        shape.directGeometry.angleDegrees = 32f;
        shape.density = 2.25f;
        shape.friction = 0.45f;
        shape.restitution = 0.75f;
        shape.sensor = true;
        shape.categoryBits = 0x0004;
        shape.maskBits = 0x0007;
        shape.groupIndex = -3;
    }

    private static void assertProperties(
            PhysicsShapeData source, CompiledFixtureData compiled) {
        Assert.assertEquals(source.directGeometry.offsetX, compiled.offsetX, 0f);
        Assert.assertEquals(source.directGeometry.offsetY, compiled.offsetY, 0f);
        Assert.assertEquals(source.directGeometry.angleDegrees, compiled.angleDegrees, 0f);
        Assert.assertEquals(source.density, compiled.density, 0f);
        Assert.assertEquals(source.friction, compiled.friction, 0f);
        Assert.assertEquals(source.restitution, compiled.restitution, 0f);
        Assert.assertEquals(source.sensor, compiled.sensor);
        Assert.assertEquals(source.categoryBits, compiled.categoryBits);
        Assert.assertEquals(source.maskBits, compiled.maskBits);
        Assert.assertEquals(source.groupIndex, compiled.groupIndex);
    }

    private void expectCompilationFailure(PhysicsShapeData source, String messagePart) {
        try {
            CompiledFixtureData[] result = compiler.compile(resolve(source));
            for (CompiledFixtureData fixture : result) {
                Assert.assertNotEquals(PhysicsDirectGeometryData.SHAPE_BOX, fixture.shapeType);
            }
            Assert.fail("Expected compilation failure.");
        } catch (PhysicsShapeCompilationException ex) {
            Assert.assertEquals(source.physicsShapeId, ex.physicsShapeId());
            Assert.assertTrue(ex.getMessage(), ex.getMessage().contains(messagePart));
        }
    }

    private static float[] regularPolygon(int count, float radius) {
        float[] vertices = new float[count * 2];
        for (int i = 0; i < count; i++) {
            double angle = Math.PI * 2.0 * i / count;
            vertices[i * 2] = (float) (Math.cos(angle) * radius);
            vertices[i * 2 + 1] = (float) (Math.sin(angle) * radius);
        }
        return vertices;
    }

    private static ResolvedPhysicsShape resolve(PhysicsShapeData source) {
        return ResolvedPhysicsShape.fromDirect(source);
    }
}
