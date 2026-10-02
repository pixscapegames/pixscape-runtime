package games.pixscape.runtime.loading;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

/** Probes JSON tolerance that determines the published schema's null/unknown policy. */
public class SceneJsonToleranceTest {
    private static final String FIXTURE = "examples/minimal-export/scenes/scene1.json";

    @Test
    public void currentSerializerBehaviorForUnknownFieldsAndNulls() throws Exception {
        String source = new FileHandle(new File(FIXTURE)).readString("UTF-8");
        String transform = "\"TransformComponent\": { \"x\": 32, \"y\": 32 }";
        String visibility = "\"DimensionsComponent\": { \"width\": 16";
        Assert.assertTrue(source.contains(transform));
        Assert.assertTrue(source.contains(visibility));
        Assert.assertTrue("Unknown component fields are currently ignored by SceneLoader",
                accepts(source.replace(transform,
                        "\"TransformComponent\": { \"x\": 32, \"y\": 32, \"unknown\": 1 }")));
        Assert.assertFalse("Primitive scaleX cannot be null",
                accepts(source.replace(transform,
                        "\"TransformComponent\": { \"x\": 32, \"y\": 32, \"scaleX\": null }")));
        Assert.assertFalse("Primitive visible cannot be null",
                accepts(source.replace(visibility,
                        "\"VisibilityComponent\": { \"visible\": null }, \"DimensionsComponent\": { \"width\": 16")));
        String animated = source.replace("\"games.pixscape.runtime.component.RenderMaterialComponent\": \"RenderMaterialComponent\"",
                        "\"games.pixscape.runtime.component.RenderMaterialComponent\": \"RenderMaterialComponent\", \"games.pixscape.runtime.component.AnimationComponent\": \"AnimationComponent\"")
                .replace("\"RenderMaterialComponent\"]", "\"RenderMaterialComponent\", \"AnimationComponent\"]");
        Assert.assertTrue("A null animation IntArray currently passes isolated SceneLoader",
                accepts(animated.replace(visibility,
                        "\"AnimationComponent\": { \"animationAssetIds\": null }, \"DimensionsComponent\": { \"width\": 16")));
        Assert.assertTrue("A null IntArray.items currently passes isolated SceneLoader",
                accepts(animated.replace(visibility,
                        "\"AnimationComponent\": { \"animationAssetIds\": { \"size\": 0, \"items\": null } }, \"DimensionsComponent\": { \"width\": 16")));
    }

    @Test
    public void archetypeOnlyLayerConflictIsRejectedBeforeWorldReplacement() throws Exception {
        String source = new FileHandle(new File(FIXTURE)).readString("UTF-8");
        String changed = source.replace("\"RenderMaterialComponent\"]",
                "\"RenderMaterialComponent\", \"LayerComponent\"]")
                .replace("\"EntityIndexComponent\": { \"layerIndex\": 0, \"zIndex\": 0 },", "");
        Assert.assertNotEquals(source, changed);
        assertRejectedBeforeWorldReplacement(changed);
    }

    @Test
    public void archetypeIndexAndExplicitLayerConflictIsRejected() throws Exception {
        String source = new FileHandle(new File(FIXTURE)).readString("UTF-8");
        String changed = source.replace("\"RenderMaterialComponent\"]",
                "\"RenderMaterialComponent\", \"LayerComponent\"]")
                .replace("\"EntityIndexComponent\": { \"layerIndex\": 0, \"zIndex\": 0 },",
                        "\"LayerComponent\": { \"layerIndex\": 0 },");
        Assert.assertNotEquals(source, changed);
        assertRejectedBeforeWorldReplacement(changed);
    }

    @Test
    public void validLayerAndIndexedContentLoadWithTheirSeparateComponents() throws Exception {
        String source = new FileHandle(new File(FIXTURE)).readString("UTF-8");
        FileHandle file = temporaryScene(source);
        World world = world();
        try {
            SceneLoader.loadScene(world, file, true, meta());
            Assert.assertEquals(1, world.getAspectSubscriptionManager()
                    .get(Aspect.all(LayerComponent.class).exclude(EntityIndexComponent.class))
                    .getEntities().size());
            Assert.assertEquals(1, world.getAspectSubscriptionManager()
                    .get(Aspect.all(EntityIndexComponent.class).exclude(LayerComponent.class))
                    .getEntities().size());
        } finally {
            world.dispose();
            file.delete();
        }
    }

    @Test
    public void validationIgnoresUnrelatedEntitiesAlreadyInTargetWorld() throws Exception {
        String source = new FileHandle(new File(FIXTURE)).readString("UTF-8");
        FileHandle file = temporaryScene(source);
        World world = world();
        try {
            int unrelated = world.create();
            world.getMapper(LayerComponent.class).create(unrelated);
            world.getMapper(EntityIndexComponent.class).create(unrelated);
            world.process();
            SceneLoader.loadScene(world, file, false, meta());
            Assert.assertTrue(world.getEntityManager().isActive(unrelated));
        } finally {
            world.dispose();
            file.delete();
        }
    }

    private static void assertRejectedBeforeWorldReplacement(String changed) throws Exception {
        FileHandle file = temporaryScene(changed);
        World world = world();
        int existing = world.create();
        world.process();
        try {
            RuntimeException failure = Assert.assertThrows(RuntimeException.class,
                    () -> SceneLoader.loadScene(world, file, true, meta()));
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains(file.path()));
            Assert.assertTrue(failure.getMessage(), failure.getMessage().contains("entityId="));
            Assert.assertTrue(failure.getMessage(), world.getEntityManager().isActive(existing));
        } finally {
            world.dispose();
            file.delete();
        }
    }

    private static FileHandle temporaryScene(String content) throws Exception {
        FileHandle file = new FileHandle(File.createTempFile("pixscape-archetype-layer-", ".json"));
        file.writeString(content, false, "UTF-8");
        return file;
    }

    private static World world() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }

    private static SceneMetaRuntime meta() {
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.sceneSchemaVersion = 3;
        meta.nextEntityStableId = 3;
        meta.nextPhysicsShapeId = 1;
        return meta;
    }

    @Test
    public void projectReaderNormalizesNullVersionAndDirectoryButRejectsNullName() throws Exception {
        String source = new FileHandle(new File("examples/minimal-export/project.json"))
                .readString("UTF-8");
        FileHandle dir = new FileHandle(new File(System.getProperty("java.io.tmpdir"),
                "pixscape-project-null-tolerance-" + System.nanoTime()));
        dir.mkdirs();
        try {
            FileHandle file = dir.child("project.json");
            file.writeString(source.replace("\"version\": \"1\"", "\"version\": null")
                    .replace("\"scenesDir\": \"scenes\"", "\"scenesDir\": null")
                    .replace("\"projectKind\": \"pixscape-runtime-project\"",
                            "\"projectKind\": null, \"unknown\": 1"), false, "UTF-8");
            Assert.assertEquals("1", RuntimeProjectIO.loadProject(dir).version);
            Assert.assertEquals("scenes", RuntimeProjectIO.loadProject(dir).scenesDir);
            file.writeString(source.replace("\"projectFileName\": \"pixscape-minimal-export\"",
                    "\"projectFileName\": null"), false, "UTF-8");
            Assert.assertThrows(RuntimeException.class, () -> RuntimeProjectIO.loadProject(dir));
        } finally {
            dir.deleteDirectory();
        }
    }

    private static boolean accepts(String content) throws Exception {
        FileHandle file = new FileHandle(File.createTempFile("pixscape-schema-tolerance-", ".json"));
        file.writeString(content, false, "UTF-8");
        World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        try {
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.sceneSchemaVersion = 3;
            meta.nextEntityStableId = 3;
            meta.nextPhysicsShapeId = 1;
            SceneLoader.loadScene(world, file, true, meta);
            return true;
        } catch (RuntimeException rejected) {
            return false;
        } finally {
            world.dispose();
            file.delete();
        }
    }
}
