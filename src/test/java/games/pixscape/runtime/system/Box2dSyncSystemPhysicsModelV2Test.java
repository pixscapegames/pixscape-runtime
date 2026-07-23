package games.pixscape.runtime.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.Body;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsFixtureProvenance;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.Box2dWorldService;
import org.junit.Assert;
import org.junit.Test;

public class Box2dSyncSystemPhysicsModelV2Test {
    @Test
    public void invalidRecompileKeepsPreviousNativeBodyAndCompiledCache() {
        Harness harness = new Harness();
        harness.world.process();

        PhysicsRuntimeBodyComponent runtime =
                harness.world.getMapper(PhysicsRuntimeBodyComponent.class).get(harness.entityId);
        PhysicsCompiledFixturesComponent compiled =
                harness.world.getMapper(PhysicsCompiledFixturesComponent.class).get(harness.entityId);
        Body originalBody = runtime.body;
        int originalGeneration = compiled.generation;

        harness.source.shapeType = PhysicsShapeData.SHAPE_POLYGON;
        harness.source.polygonVertices = new float[]{0f, 0f, 1f, 0f};
        harness.source.polygonVertexCount = 2;
        harness.dirty.physics(harness.entityId, PhysicsDirtyBits.ALL);

        try {
            harness.world.process();
            Assert.fail("Invalid polygon source must reject the rebuild.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("physicsShapeId 1"));
        }

        Assert.assertSame(originalBody, runtime.body);
        Assert.assertEquals(1, harness.box2d.world.getBodyCount());
        Assert.assertEquals(1, runtime.body.getFixtureList().size);
        Assert.assertEquals(originalGeneration, compiled.generation);
        Assert.assertTrue(compiled.valid);
    }

    @Test
    public void concaveSourceBuildsPartsWithSourceProvenanceAndDisablePreservesSource() {
        Harness harness = new Harness();
        harness.source.shapeType = PhysicsShapeData.SHAPE_POLYGON;
        harness.source.polygonVertices = new float[]{
                0f, 0f, 2f, 0f, 2f, 2f, 1f, 1f, 0f, 2f
        };
        harness.source.polygonVertexCount = 5;
        harness.world.process();

        PhysicsRuntimeBodyComponent runtime =
                harness.world.getMapper(PhysicsRuntimeBodyComponent.class).get(harness.entityId);
        Assert.assertTrue(runtime.body.getFixtureList().size > 1);
        for (int i = 0; i < runtime.body.getFixtureList().size; i++) {
            Object userData = runtime.body.getFixtureList().get(i).getUserData();
            Assert.assertTrue(userData instanceof PhysicsFixtureProvenance);
            PhysicsFixtureProvenance provenance = (PhysicsFixtureProvenance) userData;
            Assert.assertEquals(harness.entityId, provenance.bodyEntityId);
            Assert.assertEquals(1, provenance.physicsShapeId);
            Assert.assertEquals(i, provenance.partIndex);
        }

        harness.body.enabled = false;
        harness.dirty.physics(harness.entityId, PhysicsDirtyBits.ALL);
        harness.world.process();

        Assert.assertEquals(0, harness.box2d.world.getBodyCount());
        Assert.assertEquals(1, harness.shapes.shapes.size);
        Assert.assertSame(harness.source, harness.shapes.shapes.first());
    }

    private static final class Harness {
        final Box2dWorldService box2d;
        final DirtyTrackerSystem dirty;
        final World world;
        final int entityId;
        final PhysicsBodyComponent body;
        final PhysicsShapesComponent shapes;
        final PhysicsShapeData source;

        Harness() {
            GdxNativesLoader.load();
            box2d = new Box2dWorldService(100f, new Vector2());
            dirty = new DirtyTrackerSystem(16);
            Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
            world = new World(new WorldConfigurationBuilder().with(dirty, sync).build());
            entityId = world.create();
            world.getMapper(TransformComponent.class).create(entityId);
            body = world.getMapper(PhysicsBodyComponent.class).create(entityId);
            shapes = world.getMapper(PhysicsShapesComponent.class).create(entityId);
            source = new PhysicsShapeData();
            source.physicsShapeId = 1;
            source.shapeType = PhysicsShapeData.SHAPE_BOX;
            shapes.add(source);
        }
    }
}
