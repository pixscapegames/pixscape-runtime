package games.pixscape.runtime.service;

import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.CompiledFixtureData;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class PhysicsLinkedBodyCompilationTest {
    private static final float PPM = 32f;

    @Test
    public void manualOnlyBodyUsesExistingPreparationWithoutWorldContext() {
        Array<PhysicsShapeData> sources =
                new Array<>(true, 1, PhysicsShapeData.class);
        sources.add(manual(1));

        PreparedPhysicsBodyCandidate prepared =
                PhysicsService.prepareBodyCandidate(null, -1, sources, 0f);

        PhysicsShapesComponent shapes = new PhysicsShapesComponent();
        PhysicsCompiledFixturesComponent compiled =
                new PhysicsCompiledFixturesComponent();
        PhysicsService.publishPreparedCandidate(shapes, compiled, prepared);
        Assert.assertTrue(compiled.valid);
        Assert.assertEquals(1, compiled.fixtures.size);
        Assert.assertEquals(1, compiled.fixtures.first().physicsShapeId);
    }

    @Test
    public void linkedOnlyBodyCompilesPolygonWithoutMutatingAuthoredGeometry() {
        World world = new World();
        int owner = createOwner(world, block(7, 1f), block(8, 4f));
        PhysicsShapeData linked = linked(2, 7);
        Array<PhysicsShapeData> sources =
                new Array<>(true, 1, PhysicsShapeData.class);
        sources.add(linked);

        PreparedPhysicsBodyCandidate prepared =
                PhysicsService.prepareBodyCandidate(world, owner, sources, PPM);
        PhysicsShapesComponent shapes = new PhysicsShapesComponent();
        PhysicsCompiledFixturesComponent compiled =
                new PhysicsCompiledFixturesComponent();
        PhysicsService.publishPreparedCandidate(shapes, compiled, prepared);

        Assert.assertEquals(1, compiled.fixtures.size);
        Assert.assertEquals(PhysicsGeometryData.SHAPE_POLYGON,
                compiled.fixtures.first().shapeType);
        Assert.assertEquals(4, compiled.fixtures.first().polygonVertexCount);
        Assert.assertNull(linked.geometry);
        Assert.assertNull(shapes.shapes.first().geometry);
    }

    @Test
    public void mixedAndMultipleLinkedShapesPreserveAuthoredOrderAndIds() {
        World world = new World();
        int owner = createOwner(world, block(7, 1f), block(8, 5f));
        PhysicsShapeData manual = manual(10);
        PhysicsShapeData linkedA = linked(11, 7);
        PhysicsShapeData linkedB = linked(12, 8);
        Array<PhysicsShapeData> sources =
                new Array<>(true, 3, PhysicsShapeData.class);
        sources.add(manual);
        sources.add(linkedA);
        sources.add(linkedB);

        PreparedPhysicsBodyCandidate prepared =
                PhysicsService.prepareBodyCandidate(world, owner, sources, PPM);
        PhysicsShapesComponent shapes = new PhysicsShapesComponent();
        PhysicsCompiledFixturesComponent compiled =
                new PhysicsCompiledFixturesComponent();
        PhysicsService.publishPreparedCandidate(shapes, compiled, prepared);

        Assert.assertEquals(3, compiled.fixtures.size);
        Assert.assertEquals(10, compiled.fixtures.get(0).physicsShapeId);
        Assert.assertEquals(11, compiled.fixtures.get(1).physicsShapeId);
        Assert.assertEquals(12, compiled.fixtures.get(2).physicsShapeId);
        Assert.assertEquals(PhysicsGeometryData.SHAPE_BOX,
                compiled.fixtures.get(0).shapeType);
        Assert.assertEquals(PhysicsGeometryData.SHAPE_POLYGON,
                compiled.fixtures.get(1).shapeType);
        Assert.assertEquals(PhysicsGeometryData.SHAPE_POLYGON,
                compiled.fixtures.get(2).shapeType);
        Assert.assertNotEquals(
                compiled.fixtures.get(1).polygonVertices[0],
                compiled.fixtures.get(2).polygonVertices[0], 0f);
        Assert.assertNull(linkedA.geometry);
        Assert.assertNull(linkedB.geometry);
        Assert.assertNull(shapes.shapes.get(1).geometry);
        Assert.assertNull(shapes.shapes.get(2).geometry);
    }

    @Test
    public void missingBlockRejectsPreparationWithoutMutatingAuthoredSources() {
        World world = new World();
        int owner = createOwner(world, block(7, 1f));
        PhysicsShapeData linked = linked(4, 99);
        Array<PhysicsShapeData> sources =
                new Array<>(true, 1, PhysicsShapeData.class);
        sources.add(linked);

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> PhysicsService.prepareBodyCandidate(
                        world, owner, sources, PPM));

        Assert.assertTrue(failure.getMessage().contains("spatialBlockId=99"));
        Assert.assertTrue(failure.getMessage().contains("absent"));
        Assert.assertSame(linked, sources.first());
        Assert.assertNull(linked.geometry);
    }

    @Test
    public void missingTransformOrTiledRuntimeDataIsRejectedClearly() {
        World world = new World();
        int owner = world.create();
        world.getMapper(TiledLayerComponent.class).create(owner).data =
                tiledData();
        SpatialBlocksComponent blocks =
                world.getMapper(SpatialBlocksComponent.class).create(owner);
        blocks.blocks.add(block(7, 1f));
        Array<PhysicsShapeData> sources =
                new Array<>(true, 1, PhysicsShapeData.class);
        sources.add(linked(5, 7));

        IllegalArgumentException missingTransform = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> PhysicsService.prepareBodyCandidate(
                        world, owner, sources, PPM));
        Assert.assertTrue(missingTransform.getMessage().contains(
                "TransformComponent"));

        world.getMapper(TransformComponent.class).create(owner);
        world.getMapper(TiledLayerComponent.class).get(owner).data = null;
        IllegalArgumentException missingData = Assert.assertThrows(
                IllegalArgumentException.class,
                () -> PhysicsService.prepareBodyCandidate(
                        world, owner, sources, PPM));
        Assert.assertTrue(missingData.getMessage().contains(
                "TiledLayerComponent.data"));
    }

    @Test
    public void contextualRebuildPreparesEveryBodyBeforePublishingAny() {
        World world = new World();
        int first = createOwner(world, block(7, 1f));
        world.getMapper(PhysicsBodyComponent.class).create(first);
        world.getMapper(PhysicsShapesComponent.class).create(first)
                .shapes.add(linked(20, 7));
        PhysicsCompiledFixturesComponent sentinel =
                world.getMapper(PhysicsCompiledFixturesComponent.class)
                        .create(first);
        CompiledFixtureData sentinelFixture = new CompiledFixtureData();
        sentinel.fixtures.add(sentinelFixture);
        sentinel.generation = 9;
        sentinel.valid = true;

        int second = createOwner(world, block(8, 2f));
        world.getMapper(PhysicsBodyComponent.class).create(second);
        world.getMapper(PhysicsShapesComponent.class).create(second)
                .shapes.add(linked(21, 99));

        Assert.assertThrows(IllegalArgumentException.class,
                () -> PhysicsService.rebuildPreparedBodyCaches(world, PPM));

        Assert.assertSame(sentinel, world.getMapper(
                PhysicsCompiledFixturesComponent.class).get(first));
        Assert.assertSame(sentinelFixture, sentinel.fixtures.first());
        Assert.assertEquals(1, sentinel.fixtures.size);
        Assert.assertEquals(9, sentinel.generation);
        Assert.assertTrue(sentinel.valid);
        Assert.assertFalse(world.getMapper(
                PhysicsCompiledFixturesComponent.class).has(second));
    }

    private static int createOwner(World world, SpatialBlockData... sourceBlocks) {
        int owner = world.create();
        world.getMapper(TransformComponent.class).create(owner);
        world.getMapper(TiledLayerComponent.class).create(owner).data =
                tiledData();
        SpatialBlocksComponent blocks =
                world.getMapper(SpatialBlocksComponent.class).create(owner);
        for (SpatialBlockData block : sourceBlocks) {
            blocks.blocks.add(block);
        }
        return owner;
    }

    private static TiledMapLayerData tiledData() {
        return new TiledMapLayerData(
                20, 20, 32, 16, 8, SceneMetaRuntime.TiledProjection.ORTHO);
    }

    private static SpatialBlockData block(int id, float x) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.x = x;
        block.y = 2f;
        block.width = 2f;
        block.depth = 3f;
        block.altitude = 4f;
        return block;
    }

    private static PhysicsShapeData linked(int shapeId, int blockId) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = shapeId;
        shape.spatialBlockId = blockId;
        return shape;
    }

    private static PhysicsShapeData manual(int shapeId) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = shapeId;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
        shape.geometry.halfWidth = 1f;
        shape.geometry.halfHeight = 2f;
        return shape;
    }
}
