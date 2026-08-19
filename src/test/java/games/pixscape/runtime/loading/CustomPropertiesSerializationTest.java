package games.pixscape.runtime.loading;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyType;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

public class CustomPropertiesSerializationTest {
    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void artemisRoundTripPreservesAllTypesNamesAndContents() {
        World source = serializationWorld();
        int entity = source.create();
        PropertySet properties = source.getMapper(CustomPropertiesComponent.class)
                .create(entity).properties;
        properties.putString("Display Name", "first line\nsecond line")
                .putBoolean("locked", true)
                .putInt("damage", -20)
                .putFloat("spawnRate", 0.5f);

        byte[] bytes = saveEntity(source, entity);
        World target = serializationWorld();
        SaveFileFormat loaded = load(target, bytes);

        Assert.assertEquals(1, loaded.entities.size());
        CustomPropertiesComponent component = target
                .getMapper(CustomPropertiesComponent.class)
                .get(loaded.entities.get(0));
        Assert.assertNotNull(component);
        Assert.assertEquals(4, component.properties.size());
        Assert.assertEquals(PropertyType.STRING, component.properties.typeOf("Display Name"));
        Assert.assertEquals(PropertyType.BOOLEAN, component.properties.typeOf("locked"));
        Assert.assertEquals(PropertyType.INTEGER, component.properties.typeOf("damage"));
        Assert.assertEquals(PropertyType.FLOAT, component.properties.typeOf("spawnRate"));
        Assert.assertEquals("first line\nsecond line",
                component.properties.getString("Display Name", ""));
        Assert.assertTrue(component.properties.getBoolean("locked", false));
        Assert.assertEquals(-20, component.properties.getInt("damage", 0));
        Assert.assertEquals(0.5f, component.properties.getFloat("spawnRate", 0f), 0f);

        source.dispose();
        target.dispose();
    }

    @Test
    public void entityWithoutComponentRemainsValidAfterRoundTrip() {
        World source = serializationWorld();
        int entity = source.create();
        source.getMapper(TransformComponent.class).create(entity).x = 4f;

        byte[] bytes = saveEntity(source, entity);
        World target = serializationWorld();
        SaveFileFormat loaded = load(target, bytes);
        int loadedEntity = loaded.entities.get(0);

        Assert.assertFalse(target.getMapper(CustomPropertiesComponent.class).has(loadedEntity));
        Assert.assertEquals(4f,
                target.getMapper(TransformComponent.class).get(loadedEntity).x, 0f);

        source.dispose();
        target.dispose();
    }

    @Test
    public void explicitlyAttachedEmptyComponentRoundTripsAsEmpty() {
        World source = serializationWorld();
        int entity = source.create();
        source.getMapper(CustomPropertiesComponent.class).create(entity);

        byte[] bytes = saveEntity(source, entity);
        World target = serializationWorld();
        SaveFileFormat loaded = load(target, bytes);
        CustomPropertiesComponent component = target
                .getMapper(CustomPropertiesComponent.class)
                .get(loaded.entities.get(0));

        Assert.assertNotNull(component);
        Assert.assertTrue(component.properties.isEmpty());

        source.dispose();
        target.dispose();
    }

    @Test
    public void sceneLoaderPreservesCustomPropertiesWithoutANewSchemaVersion() throws Exception {
        World source = serializationWorld();
        int entity = source.create();
        source.getMapper(CustomPropertiesComponent.class).create(entity).properties
                .putInt("damage", 20);
        byte[] bytes = saveEntity(source, entity);
        FileHandle sceneFile = new FileHandle(temporaryFolder.newFile("scene.json"));
        sceneFile.writeBytes(bytes, false);

        World target = serializationWorld();
        SceneMetaRuntime sceneMeta = new SceneMetaRuntime("test", sceneFile.path());
        Assert.assertEquals(SceneMetaRuntime.CURRENT_SCENE_SCHEMA_VERSION,
                sceneMeta.sceneSchemaVersion);

        SaveFileFormat loaded = SceneLoader.loadScene(target, sceneFile, true, sceneMeta);

        CustomPropertiesComponent component = target
                .getMapper(CustomPropertiesComponent.class)
                .get(loaded.entities.get(0));
        Assert.assertNotNull(component);
        Assert.assertEquals(20, component.properties.getInt("damage", 0));

        source.dispose();
        target.dispose();
    }

    private static byte[] saveEntity(World world, int entity) {
        WorldSerializationManager serialization = world.getSystem(WorldSerializationManager.class);
        serialization.setSerializer(new JsonArtemisSerializer(world));
        SaveFileFormat request = new SaveFileFormat();
        request.entities.add(entity);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        serialization.save(out, request);
        return out.toByteArray();
    }

    private static SaveFileFormat load(World world, byte[] bytes) {
        WorldSerializationManager serialization = world.getSystem(WorldSerializationManager.class);
        serialization.setSerializer(new JsonArtemisSerializer(world));
        SaveFileFormat loaded = serialization.load(
                new ByteArrayInputStream(bytes), SaveFileFormat.class);
        world.process();
        return loaded;
    }

    private static World serializationWorld() {
        return new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager())
                .build());
    }
}
