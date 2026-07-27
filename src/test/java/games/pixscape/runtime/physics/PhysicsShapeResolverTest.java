package games.pixscape.runtime.physics;

import org.junit.Assert;
import org.junit.Test;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;

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

    @Test
    public void resolvesLinkedBlockIntoDetachedPolygonMeters() {
        PhysicsShapeData source = new PhysicsShapeData();
        source.physicsShapeId = 7;
        source.density = 2f;
        SpatialBlockData block = new SpatialBlockData();
        block.id = 3; block.x = 1f; block.y = 2f; block.width = 2f; block.depth = 1f; block.altitude = 4f;
        TiledMapLayerData map = new TiledMapLayerData();
        map.projection = SceneMetaRuntime.TiledProjection.ORTHO;
        map.tileWidth = 10; map.tileHeight = 20; map.originX = 5f; map.originY = 7f;
        ResolvedPhysicsShape resolved = resolver.resolveLinked(source, 9, block, map, 10f);
        Assert.assertEquals(ResolvedPhysicsShape.SOURCE_SPATIAL_BLOCK, resolved.sourceKind);
        Assert.assertEquals(9, resolved.spatialOwnerStableId);
        Assert.assertEquals(3, resolved.spatialBlockId);
        Assert.assertEquals(4, resolved.polygonVertexCount);
        Assert.assertArrayEquals(new float[]{1.5f, 5.1f, 3.5f, 5.1f, 3.5f, 7.1f, 1.5f, 7.1f}, resolved.polygonVertices, .0001f);
        resolved.polygonVertices[0] = 99f;
        Assert.assertEquals(1f, block.x, 0f);
    }

    @Test
    public void resolvesLinkedIsoWithMaterialsAndIndependentVertices() {
        PhysicsShapeData source = new PhysicsShapeData();
        source.physicsShapeId = 12; source.density = 3f; source.friction = .7f; source.restitution = .2f;
        source.sensor = true; source.categoryBits = 4; source.maskBits = 8; source.groupIndex = 2;
        SpatialBlockData block = new SpatialBlockData();
        block.id = 6; block.x = 1f; block.y = 2f; block.width = 2f; block.depth = 1f; block.altitude = 3f;
        TiledMapLayerData map = new TiledMapLayerData();
        map.projection = SceneMetaRuntime.TiledProjection.ISO; map.tileWidth = 20; map.tileHeight = 10; map.originX = 4f; map.originY = 9f;
        ResolvedPhysicsShape a = resolver.resolveLinked(source, 5, block, map, 50f);
        ResolvedPhysicsShape b = resolver.resolveLinked(source, 5, block, map, 100f);
        Assert.assertArrayEquals(new float[]{.08f, .54f, .48f, .74f, .28f, .84f, -.12f, .64f}, a.polygonVertices, .0001f);
        Assert.assertEquals(a.polygonVertices[0] * .5f, b.polygonVertices[0], .0001f);
        Assert.assertNotSame(a.polygonVertices, b.polygonVertices);
        Assert.assertEquals(3f, a.density, 0f); Assert.assertEquals(.7f, a.friction, 0f); Assert.assertEquals(.2f, a.restitution, 0f);
        Assert.assertEquals(4, a.categoryBits); Assert.assertEquals(8, a.maskBits); Assert.assertEquals(2, a.groupIndex); Assert.assertTrue(a.sensor);
        Assert.assertTrue(a.enabled); Assert.assertEquals(PhysicsDirectGeometryData.SHAPE_POLYGON, a.shapeType); Assert.assertEquals(4, a.polygonVertexCount);
        Assert.assertEquals(0f, a.offsetX, 0f); Assert.assertEquals(0f, a.offsetY, 0f); Assert.assertEquals(0f, a.angleDegrees, 0f);
        CompiledFixtureData[] compiled = new PhysicsShapeCompiler().compile(a);
        Assert.assertEquals(1, compiled.length);
        Assert.assertEquals(12, compiled[0].physicsShapeId);
        Assert.assertEquals(PhysicsDirectGeometryData.SHAPE_POLYGON, compiled[0].shapeType);
        Assert.assertEquals(3f, compiled[0].density, 0f);
        Assert.assertEquals(.7f, compiled[0].friction, 0f); Assert.assertEquals(.2f, compiled[0].restitution, 0f);
        Assert.assertTrue(compiled[0].sensor); Assert.assertEquals(4, compiled[0].categoryBits); Assert.assertEquals(8, compiled[0].maskBits); Assert.assertEquals(2, compiled[0].groupIndex);
        ResolvedPhysicsShape copy = a.copy(); Assert.assertNotSame(a.polygonVertices, copy.polygonVertices);
        Assert.assertEquals(5, copy.spatialOwnerStableId); Assert.assertEquals(6, copy.spatialBlockId);
        a.polygonVertices[0] = 99f; Assert.assertNotEquals(99f, b.polygonVertices[0], 0f);
    }

    @Test
    public void rejectsInvalidLinkedInputsWithContext() {
        rejectLinked(null, 1, block(), map(), 1f, "null");
        PhysicsShapeData source = linked(); source.physicsShapeId = 0;
        rejectLinked(source, 1, block(), map(), 1f, "physicsShapeId");
        source = linked(); source.directGeometry = new PhysicsDirectGeometryData();
        rejectLinked(source, 1, block(), map(), 1f, "directGeometry");
        source = linked(); source.enabled = false;
        rejectLinked(source, 1, block(), map(), 1f, "enabled");
        rejectLinked(linked(), 0, block(), map(), 1f, "ownerStableId");
        rejectLinked(linked(), 1, null, map(), 1f, "block must not be null");
        SpatialBlockData invalidBlock = block(); invalidBlock.id = 0;
        rejectLinked(linked(), 1, invalidBlock, map(), 1f, "blockId");
        rejectLinked(linked(), 1, block(), null, 1f, "map");
        for (float ppm : new float[]{0f, -1f, Float.NaN, Float.POSITIVE_INFINITY}) rejectLinked(linked(), 1, block(), map(), ppm, "pixelsPerMeter");
        source = linked(); source.density = -1f; rejectLinked(source, 1, block(), map(), 1f, "density");
        source = linked(); source.friction = Float.POSITIVE_INFINITY; rejectLinked(source, 1, block(), map(), 1f, "friction");
        source = linked(); source.restitution = Float.NaN; rejectLinked(source, 1, block(), map(), 1f, "restitution");
        invalidBlock = block(); invalidBlock.width = 0f; rejectLinked(linked(), 1, invalidBlock, map(), 1f, "width");
        invalidBlock = block(); invalidBlock.depth = -1f; rejectLinked(linked(), 1, invalidBlock, map(), 1f, "depth");
        invalidBlock = block(); invalidBlock.x = Float.NaN; rejectLinked(linked(), 1, invalidBlock, map(), 1f, "geometry");
    }

    @Test
    public void linkedNestedFailuresPreserveShapeOwnerAndBlockContext() {
        PhysicsShapeData invalidMaterial = linked();
        invalidMaterial.density = -1f;
        IllegalArgumentException materialFailure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveLinked(invalidMaterial, 9, block(), map(), 100f));
        assertLinkedContext(materialFailure, invalidMaterial.physicsShapeId, 9, 4, "density");
        Assert.assertNotNull(materialFailure.getCause());

        PhysicsShapeData source = linked();
        SpatialBlockData invalidBlock = block();
        invalidBlock.width = 0f;
        IllegalArgumentException geometryFailure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> resolver.resolveLinked(source, 9, invalidBlock, map(), 100f));
        assertLinkedContext(geometryFailure, source.physicsShapeId, 9, invalidBlock.id, "width");
        Assert.assertNotNull(geometryFailure.getCause());
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
        Assert.assertEquals(ResolvedPhysicsShape.SOURCE_DIRECT, resolved.sourceKind);
        Assert.assertEquals(0, resolved.spatialOwnerStableId);
        Assert.assertEquals(0, resolved.spatialBlockId);
        Assert.assertNotSame(sourceVertices, resolved.polygonVertices);
        if (sourceVertices.length > 0) {
            Assert.assertEquals(0f, sourceVertices[0], 0f);
        }
    }

    private static PhysicsShapeData linked() { PhysicsShapeData value = new PhysicsShapeData(); value.physicsShapeId = 17; return value; }
    private static SpatialBlockData block() { SpatialBlockData value = new SpatialBlockData(); value.id = 4; value.x = 1f; value.y = 2f; value.width = 2f; value.depth = 1f; return value; }
    private static TiledMapLayerData map() { TiledMapLayerData value = new TiledMapLayerData(); value.tileWidth = 10; value.tileHeight = 10; return value; }
    private void rejectLinked(PhysicsShapeData source, int owner, SpatialBlockData block, TiledMapLayerData map, float ppm, String fragment) {
        IllegalArgumentException failure = Assert.assertThrows(IllegalArgumentException.class, () -> resolver.resolveLinked(source, owner, block, map, ppm));
        Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(fragment));
    }

    private static void assertLinkedContext(IllegalArgumentException failure, int physicsShapeId,
                                            int ownerStableId, int blockId, String invariant) {
        String message = failure.getMessage();
        Assert.assertTrue(message, message.contains("linked PhysicsShapeData " + physicsShapeId));
        Assert.assertTrue(message, message.contains("ownerStableId " + ownerStableId));
        Assert.assertTrue(message, message.contains("blockId " + blockId));
        Assert.assertTrue(message, message.contains(invariant));
    }
}
