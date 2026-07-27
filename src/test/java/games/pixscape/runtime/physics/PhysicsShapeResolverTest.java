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
        Assert.assertEquals(3f, a.density, 0f); Assert.assertEquals(4, a.categoryBits); Assert.assertTrue(a.sensor);
        CompiledFixtureData[] compiled = new PhysicsShapeCompiler().compile(a);
        Assert.assertEquals(1, compiled.length);
        Assert.assertEquals(12, compiled[0].physicsShapeId);
        Assert.assertEquals(PhysicsDirectGeometryData.SHAPE_POLYGON, compiled[0].shapeType);
        Assert.assertEquals(3f, compiled[0].density, 0f);
        ResolvedPhysicsShape copy = a.copy(); Assert.assertNotSame(a.polygonVertices, copy.polygonVertices);
        Assert.assertEquals(5, copy.spatialOwnerStableId); Assert.assertEquals(6, copy.spatialBlockId);
        a.polygonVertices[0] = 99f; Assert.assertNotEquals(99f, b.polygonVertices[0], 0f);
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
