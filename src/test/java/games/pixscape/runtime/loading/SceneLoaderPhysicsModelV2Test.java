package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;
import com.badlogic.gdx.utils.JsonWriter;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PhysicsDirectGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import org.junit.Assert;
import org.junit.Test;

import java.io.OutputStream;
import java.io.File;

public class SceneLoaderPhysicsModelV2Test {
    @Test
    public void legacyPhysicsSceneIsRejectedBeforeWorldMutation() {
        FileHandle file = new FileHandle(new File(
                System.getProperty("java.io.tmpdir"), "pixscape-v1-physics-scene.json"));
        file.writeString("{\"Physics" + "FixturesComponent\":{}}", false, "UTF-8");
        World world = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        int existing = world.create();

        try {
            SceneLoader.loadScene(world, file, false, new SceneMetaRuntime());
            Assert.fail("Physics Model V1 scene must be rejected.");
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getMessage().contains("Physics Model V1"));
        }
        Assert.assertTrue(world.getEntityManager().isActive(existing));
    }

    @Test
    public void legacyPhysicsBodyEnabledFieldIsRejectedPrecisely() throws Exception {
        World source = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        int bodyEntityId = source.create();
        source.getMapper(PhysicsBodyComponent.class)
                .create(bodyEntityId).type = PhysicsBodyComponent.STATIC;
        source.process();
        WorldSerializationManager sourceSerialization =
                source.getSystem(WorldSerializationManager.class);
        sourceSerialization.setSerializer(new JsonArtemisSerializer(source));
        SaveFileFormat format = new SaveFileFormat();
        format.entities.add(bodyEntityId);
        FileHandle file = new FileHandle(
                File.createTempFile("pixscape-legacy-body-enabled", ".json"));
        try (OutputStream output = file.write(false)) {
            sourceSerialization.save(output, format);
        } finally {
            source.dispose();
        }

        JsonValue root = new JsonReader().parse(file.readString("UTF-8"));
        JsonValue body = findNamed(root, "PhysicsBodyComponent");
        Assert.assertNotNull(body);
        body.addChild("enabled", new JsonValue(true));
        file.writeString(root.toJson(JsonWriter.OutputType.json), false, "UTF-8");

        World target = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            SceneLoader.loadScene(target, file, false, new SceneMetaRuntime());
            Assert.fail("Legacy PhysicsBodyComponent.enabled must be rejected.");
        } catch (RuntimeException expected) {
            Assert.assertTrue(expected.getMessage().contains("enabled"));
        } finally {
            target.dispose();
        }
    }

    @Test
    public void physicsShapeEnabledFieldRemainsSupported() throws Exception {
        World source = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        int bodyEntityId = source.create();
        source.getMapper(PhysicsBodyComponent.class).create(bodyEntityId);
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = 1;
        shape.enabled = false;
        shape.directGeometry = new PhysicsDirectGeometryData();
        source.getMapper(PhysicsShapesComponent.class)
                .create(bodyEntityId).shapes.add(shape);
        source.process();
        WorldSerializationManager serialization =
                source.getSystem(WorldSerializationManager.class);
        serialization.setSerializer(new JsonArtemisSerializer(source));
        SaveFileFormat format = new SaveFileFormat();
        format.entities.add(bodyEntityId);
        FileHandle file = new FileHandle(
                File.createTempFile("pixscape-shape-enabled", ".json"));
        try (OutputStream output = file.write(false)) {
            serialization.save(output, format);
        } finally {
            source.dispose();
        }

        World target = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextPhysicsShapeId = 2;
            SceneLoader.loadScene(target, file, false, meta);
            PhysicsShapeData loaded = target.getMapper(PhysicsShapesComponent.class)
                    .get(target.getAspectSubscriptionManager()
                            .get(Aspect.all(PhysicsShapesComponent.class))
                            .getEntities().get(0)).shapes.first();
            Assert.assertFalse(loaded.enabled);
        } finally {
            target.dispose();
        }
    }

    private static JsonValue findNamed(JsonValue value, String suffix) {
        if (value == null) return null;
        if (value.isObject()
                && value.name != null
                && value.name.endsWith(suffix)) {
            return value;
        }
        for (JsonValue child = value.child; child != null; child = child.next) {
            JsonValue found = findNamed(child, suffix);
            if (found != null) return found;
        }
        return null;
    }
}
