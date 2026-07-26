package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsMotorJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;

public class SceneLoaderPhysicsSchemaTest {
    @Test
    public void disabledPhysicsAcceptsSceneWithoutBodiesOrJoints() throws Exception {
        FileHandle file = writeScene(new WorldSetup() {
            @Override
            public void apply(World world) {
            }
        });
        World target = world();
        try {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.physicsEnabled = false;

            SceneLoader.loadScene(target, file, false, meta);

            Assert.assertEquals(0, target.getAspectSubscriptionManager()
                    .get(Aspect.all(PhysicsBodyComponent.class))
                    .getEntities().size());
            Assert.assertEquals(0, target.getAspectSubscriptionManager()
                    .get(Aspect.all(PhysicsJointComponent.class))
                    .getEntities().size());
        } finally {
            target.dispose();
        }
    }

    @Test
    public void disabledPhysicsRejectsBodyWithoutMutatingTarget() throws Exception {
        FileHandle file = writeScene(new WorldSetup() {
            @Override
            public void apply(World world) {
                int entityId = world.create();
                world.getMapper(PhysicsBodyComponent.class).create(entityId);
            }
        });
        assertRejectedWithoutMutation(
                file, "PhysicsBodyComponent");
    }

    @Test
    public void disabledPhysicsRejectsJointWithoutMutatingTarget() throws Exception {
        FileHandle file = writeScene(new WorldSetup() {
            @Override
            public void apply(World world) {
                int bodyA = world.create();
                int bodyB = world.create();
                int entityId = world.create();
                PhysicsJointComponent joint = world.getMapper(
                        PhysicsJointComponent.class).create(entityId);
                joint.type = PhysicsJointComponent.TYPE_DISTANCE;
                joint.aEid = bodyA;
                joint.bEid = bodyB;
            }
        });
        assertRejectedWithoutMutation(
                file, "PhysicsJointComponent");
    }

    @Test
    public void disabledPhysicsRejectsEmptyShapesWithoutMutatingTarget()
            throws Exception {
        FileHandle file = writeScene(new WorldSetup() {
            @Override
            public void apply(World world) {
                int entityId = world.create();
                world.getMapper(PhysicsShapesComponent.class).create(entityId);
            }
        });
        assertRejectedWithoutMutation(file, "PhysicsShapesComponent");
    }

    @Test
    public void disabledPhysicsRejectsNonEmptyShapesWithoutMutatingTarget()
            throws Exception {
        FileHandle file = writeScene(new WorldSetup() {
            @Override
            public void apply(World world) {
                int entityId = world.create();
                PhysicsShapesComponent shapes = world.getMapper(
                        PhysicsShapesComponent.class).create(entityId);
                PhysicsShapeData shape = new PhysicsShapeData();
                shape.physicsShapeId = 1;
                shape.directGeometry = new PhysicsDirectGeometryData();
                shapes.add(shape);
            }
        });
        assertRejectedWithoutMutation(file, "PhysicsShapesComponent");
    }

    @Test
    public void disabledPhysicsRejectsOrphanJointComponentWithoutMutatingTarget()
            throws Exception {
        FileHandle file = writeScene(new WorldSetup() {
            @Override
            public void apply(World world) {
                int entityId = world.create();
                world.getMapper(PhysicsMotorJointComponent.class).create(entityId);
            }
        });
        assertRejectedWithoutMutation(file, "PhysicsMotorJointComponent");
    }

    @Test
    public void enabledPhysicsAcceptsDormantBodyWithValidEmptyCache()
            throws Exception {
        FileHandle file = writeScene(new WorldSetup() {
            @Override
            public void apply(World world) {
                int entityId = world.create();
                world.getMapper(PhysicsBodyComponent.class).create(entityId);
                world.getMapper(PhysicsShapesComponent.class).create(entityId);
                PhysicsCompiledFixturesComponent compiled = world.getMapper(
                        PhysicsCompiledFixturesComponent.class).create(entityId);
                compiled.valid = true;
            }
        });
        World target = world();
        try {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.physicsEnabled = true;

            SceneLoader.loadScene(target, file, false, meta);

            int entityId = target.getAspectSubscriptionManager()
                    .get(Aspect.all(PhysicsBodyComponent.class))
                    .getEntities().get(0);
            Assert.assertEquals(0, target.getMapper(
                    PhysicsShapesComponent.class).get(entityId).shapes.size);
            PhysicsCompiledFixturesComponent compiled = target.getMapper(
                    PhysicsCompiledFixturesComponent.class).get(entityId);
            Assert.assertTrue(compiled.valid);
            Assert.assertEquals(0, compiled.fixtures.size);
        } finally {
            target.dispose();
        }
    }

    private static void assertRejectedWithoutMutation(
            FileHandle file, String expectedComponent) {
        World target = world();
        int existingEntityId = target.create();
        target.process();
        try {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.physicsEnabled = false;
            meta.nextPhysicsShapeId = 2;

            try {
                SceneLoader.loadScene(target, file, false, meta);
                Assert.fail("Scene content must be rejected.");
            } catch (RuntimeException expected) {
                Assert.assertTrue(
                        expected.getMessage().contains(expectedComponent));
            }

            Assert.assertTrue(
                    target.getEntityManager().isActive(existingEntityId));
            Assert.assertEquals(1, target.getAspectSubscriptionManager()
                    .get(Aspect.all()).getEntities().size());
        } finally {
            target.dispose();
        }
    }

    private static FileHandle writeScene(WorldSetup setup) throws Exception {
        World source = world();
        try {
            setup.apply(source);
            source.process();
            WorldSerializationManager serialization =
                    source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(source));
            SaveFileFormat format = new SaveFileFormat(
                    source.getAspectSubscriptionManager()
                            .get(Aspect.all()).getEntities());
            FileHandle file = new FileHandle(
                    File.createTempFile("pixscape-scene-schema-", ".json"));
            try (OutputStream output = file.write(false)) {
                serialization.save(output, format);
            }
            return file;
        } finally {
            source.dispose();
        }
    }

    private static World world() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }

    private interface WorldSetup {
        void apply(World world);
    }
}
