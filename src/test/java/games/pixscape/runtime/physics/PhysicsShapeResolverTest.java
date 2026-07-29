package games.pixscape.runtime.physics;

import com.badlogic.gdx.math.MathUtils;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsShapeResolverTest {
    private static final float EPSILON = 0.0001f;
    private final PhysicsShapeResolver resolver = new PhysicsShapeResolver();

    @Test
    public void resolvesEveryManualKindWithoutMutatingOrAliasingTheSource() {
        assertResolved(PhysicsGeometryData.SHAPE_BOX);
        assertResolved(PhysicsGeometryData.SHAPE_CIRCLE);
        assertResolved(PhysicsGeometryData.SHAPE_POLYGON);
    }

    @Test
    public void resolvesOrthoFootprintThroughMapProjectionInAuthoredCornerOrder() {
        PhysicsShapeData source = linkedShape();
        SpatialBlockData block = block(1f, 2f, 2f, 3f);
        TiledMapLayerData map = map(SceneMetaRuntime.TiledProjection.ORTHO);

        ResolvedPhysicsShape resolved = resolver.resolveLinked(
                source, block, map, 0f, 0f, 0f, 16f, 4);

        assertProjectedVertices(resolved, map, block, 0f, 0f, 0f, 16f);
        Assert.assertEquals(PhysicsGeometryData.SHAPE_POLYGON, resolved.shapeType);
        Assert.assertEquals(4, resolved.polygonVertexCount);
        Assert.assertEquals(0f, resolved.offsetX, 0f);
        Assert.assertEquals(0f, resolved.offsetY, 0f);
        Assert.assertEquals(0f, resolved.angleDegrees, 0f);
    }

    @Test
    public void resolvesIsoFootprintThroughExistingMapProjection() {
        PhysicsShapeData source = linkedShape();
        SpatialBlockData block = block(1.5f, 2.25f, 2f, 3f);
        TiledMapLayerData map = map(SceneMetaRuntime.TiledProjection.ISO);

        ResolvedPhysicsShape resolved = resolver.resolveLinked(
                source, block, map, 0f, 0f, 0f, 32f, 9);

        assertProjectedVertices(resolved, map, block, 0f, 0f, 0f, 32f);
    }

    @Test
    public void convertsTranslatedRotatedBodyFootprintToLocalMeters() {
        PhysicsShapeData source = linkedShape();
        SpatialBlockData block = block(2f, 1f, 2f, 3f);
        TiledMapLayerData map = map(SceneMetaRuntime.TiledProjection.ORTHO);
        float rotation = 0.63f;

        ResolvedPhysicsShape resolved = resolver.resolveLinked(
                source, block, map, 80f, -24f, rotation, 64f, 12);

        assertProjectedVertices(resolved, map, block, 80f, -24f, rotation, 64f);
    }

    @Test
    public void localCoordinatesScaleInverselyWithPixelsPerMeter() {
        PhysicsShapeData source = linkedShape();
        SpatialBlockData block = block(1f, 2f, 2f, 3f);
        TiledMapLayerData map = map(SceneMetaRuntime.TiledProjection.ORTHO);

        ResolvedPhysicsShape first = resolver.resolveLinked(
                source, block, map, 0f, 0f, 0f, 32f, 1);
        ResolvedPhysicsShape second = resolver.resolveLinked(
                source, block, map, 0f, 0f, 0f, 64f, 1);

        for (int i = 0; i < first.polygonVertices.length; i++) {
            Assert.assertEquals(first.polygonVertices[i] * 0.5f,
                    second.polygonVertices[i], EPSILON);
        }
    }

    @Test
    public void preservesLinkedPhysicalPropertiesAndLeavesAuthoredGeometryNull() {
        PhysicsShapeData source = linkedShape();
        source.density = 2.5f;
        source.friction = 0.7f;
        source.restitution = 0.3f;
        source.sensor = true;
        source.categoryBits = 3;
        source.maskBits = 5;
        source.groupIndex = -2;
        source.enabled = false;

        ResolvedPhysicsShape resolved = resolver.resolveLinked(
                source, block(0f, 0f, 1f, 1f),
                map(SceneMetaRuntime.TiledProjection.ORTHO),
                0f, 0f, 0f, 32f, 3);

        Assert.assertEquals(source.physicsShapeId, resolved.physicsShapeId);
        Assert.assertEquals(2.5f, resolved.density, 0f);
        Assert.assertEquals(0.7f, resolved.friction, 0f);
        Assert.assertEquals(0.3f, resolved.restitution, 0f);
        Assert.assertTrue(resolved.sensor);
        Assert.assertEquals(3, resolved.categoryBits);
        Assert.assertEquals(5, resolved.maskBits);
        Assert.assertEquals(-2, resolved.groupIndex);
        Assert.assertFalse(resolved.enabled);
        Assert.assertEquals("spatial-block(7)", resolved.diagnosticSource);
        Assert.assertNull(source.geometry);
    }

    @Test
    public void rejectsInvalidLinkedContractsWithContext() {
        PhysicsShapeData source = linkedShape();
        SpatialBlockData valid = block(0f, 0f, 1f, 1f);
        TiledMapLayerData map = map(SceneMetaRuntime.TiledProjection.ORTHO);

        assertLinkedRejected(source, null, map, 32f,
                "referenced SpatialBlockData is absent");

        SpatialBlockData mismatch = block(0f, 0f, 1f, 1f);
        mismatch.id = 8;
        assertLinkedRejected(source, mismatch, map, 32f, "block.id");

        SpatialBlockData invalidWidth = block(0f, 0f, 0f, 1f);
        assertLinkedRejected(source, invalidWidth, map, 32f, "block.width");

        SpatialBlockData invalidDepth = block(0f, 0f, 1f, Float.NaN);
        assertLinkedRejected(source, invalidDepth, map, 32f, "block.depth");

        assertLinkedRejected(source, valid, null, 32f, "map");
        assertLinkedRejected(source, valid, map, 0f, "pixelsPerMeter");

        try {
            resolver.resolve(source);
            Assert.fail("Manual resolution must reject linked shapes.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains(
                    "Linked physics shape resolution is not available"));
        }
    }

    private void assertLinkedRejected(
            PhysicsShapeData source,
            SpatialBlockData block,
            TiledMapLayerData map,
            float pixelsPerMeter,
            String expectedDetail) {
        try {
            resolver.resolveLinked(
                    source, block, map, 0f, 0f, 0f, pixelsPerMeter, 11);
            Assert.fail("Invalid linked shape contract must be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage(),
                    expected.getMessage().contains("physicsShapeId=19"));
            Assert.assertTrue(expected.getMessage(),
                    expected.getMessage().contains("spatialBlockId=7"));
            Assert.assertTrue(expected.getMessage(),
                    expected.getMessage().contains("ownerEntityId=11"));
            Assert.assertTrue(expected.getMessage(),
                    expected.getMessage().contains(expectedDetail));
        }
    }

    private static void assertProjectedVertices(
            ResolvedPhysicsShape resolved,
            TiledMapLayerData map,
            SpatialBlockData block,
            float bodyX,
            float bodyY,
            float rotation,
            float pixelsPerMeter) {
        float[] projected = new float[8];
        map.projectSpatialPoint(block.x, block.y, block.altitude, projected, 0);
        map.projectSpatialPoint(
                block.x + block.width, block.y, block.altitude, projected, 2);
        map.projectSpatialPoint(
                block.x + block.width, block.y + block.depth,
                block.altitude, projected, 4);
        map.projectSpatialPoint(
                block.x, block.y + block.depth, block.altitude, projected, 6);
        float cos = MathUtils.cos(rotation);
        float sin = MathUtils.sin(rotation);
        for (int i = 0; i < projected.length; i += 2) {
            float dx = projected[i] - bodyX;
            float dy = projected[i + 1] - bodyY;
            Assert.assertEquals((cos * dx + sin * dy) / pixelsPerMeter,
                    resolved.polygonVertices[i], EPSILON);
            Assert.assertEquals((-sin * dx + cos * dy) / pixelsPerMeter,
                    resolved.polygonVertices[i + 1], EPSILON);
        }
    }

    private static PhysicsShapeData linkedShape() {
        PhysicsShapeData source = new PhysicsShapeData();
        source.physicsShapeId = 19;
        source.spatialBlockId = 7;
        return source;
    }

    private static SpatialBlockData block(
            float x, float y, float width, float depth) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = 7;
        block.x = x;
        block.y = y;
        block.width = width;
        block.depth = depth;
        block.altitude = 12f;
        return block;
    }

    private static TiledMapLayerData map(
            SceneMetaRuntime.TiledProjection projection) {
        TiledMapLayerData map =
                new TiledMapLayerData(20, 20, 64, 32, 8, projection);
        map.originX = 10f;
        map.originY = -6f;
        return map;
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
