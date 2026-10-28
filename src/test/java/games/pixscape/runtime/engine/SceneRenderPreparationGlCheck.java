package games.pixscape.runtime.engine;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.loading.SceneLoadHandle;
import games.pixscape.runtime.system.RenderSubmitSystem;
import org.junit.Assert;

import java.io.OutputStream;
import java.lang.reflect.Field;
import java.nio.file.Files;

/** Real GL coverage invoked by the existing lightCompositionGlSmoke task. */
public final class SceneRenderPreparationGlCheck {
    private SceneRenderPreparationGlCheck() {}

    public static void verify() {
        final Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(64, 32);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override public void create() {
                try { checkScenes(); }
                catch (Throwable error) { failure[0] = error; }
                finally { Gdx.app.exit(); }
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Scene render preparation", failure[0]);
    }

    private static void checkScenes() throws Exception {
        FileHandle root = new FileHandle(Files.createTempDirectory("pixscape-render-ready-").toFile());
        FileHandle project = root.child(PixscapeEngine.RUNTIME_DIR_NAME);
        project.child("project.json").writeString("{\"projectFileName\":\"test\",\"version\":\"1\","
                + "\"currentSceneName\":\"test\",\"scenes\":{\"test\":{\"name\":\"test\","
                + "\"file\":\"test.json\",\"sceneSchemaVersion\":3,\"nextEntityStableId\":3,"
                + "\"nextPhysicsShapeId\":1}}}", false, "UTF-8");
        project.child("atlases/test.atlas").writeString("", false, "UTF-8");
        writeScene(project.child("scenes/test.json"));
        try {
                int[] processed = {0};
                PixscapeEngine engine = new PixscapeEngine().setConfigurationCustomizer(builder ->
                        builder.with(new BaseSystem() {
                            @Override protected void processSystem() { processed[0]++; }
                        }));
                try {
                    engine.loadProject(root);
                    engine.resize(64, 32);
                    for (int scene = 0; scene < 3; scene++) {
                        Gdx.gl.glViewport(0, 0, 64, 32);
                        int beforeProcesses = processed[0];
                        SceneLoadHandle load = engine.beginLoadScene("test");
                        while (!load.isReady() && !load.isFailed()) load.update();
                        Assert.assertFalse(String.valueOf(load.failure()), load.isFailed());
                        Assert.assertTrue(load.isReady());
                        Assert.assertEquals("Loading must not process gameplay", beforeProcesses, processed[0]);
                        RenderSubmitSystem submit = engine.system(RenderSubmitSystem.class);
                        Object renderer = submit.lightingRenderer();
                        Object batch = get(renderer, "batch");
                        Object scratch = get(renderer, "scratch");
                        Object atlas = get(renderer, "atlas");
                        Object secondAtlas = get(renderer, "secondAtlas");
                        Assert.assertNotNull("Lighting batch must exist at READY", batch);
                        Assert.assertNotNull(scratch);
                        Assert.assertNotNull(atlas);Assert.assertNotNull(secondAtlas);
                        for (int repeat = 0; repeat < 3; repeat++) { load.update(); submit.prepareComposition(); }
                        engine.update(0);
                        engine.render();
                        Assert.assertSame(batch, get(renderer, "batch"));
                        Assert.assertSame(scratch, get(renderer, "scratch"));
                        Assert.assertSame(atlas, get(renderer, "atlas"));
                        Assert.assertSame(secondAtlas, get(renderer, "secondAtlas"));

                        // A legitimate viewport change replaces only size-dependent targets once.
                        Gdx.gl.glViewport(0, 0, 48, 24);
                        submit.prepareComposition();
                        Object resized = get(renderer, "scratch");
                        Assert.assertNotSame(scratch, resized);
                        submit.prepareComposition();
                        Assert.assertSame(resized, get(renderer, "scratch"));
                        Assert.assertSame(batch, get(renderer, "batch"));

                        PointLightComponent light = engine.getWorld().getMapper(PointLightComponent.class)
                                .get(engine.findEntityByStableId(2));
                        light.shadowResolution = 512;
                        submit.prepareComposition();
                        Assert.assertNotSame(atlas, get(renderer, "atlas"));
                        Assert.assertSame(batch, get(renderer, "batch"));
                        light.shadowQuality = 0;
                        submit.prepareComposition();
                        Assert.assertNull(get(renderer, "atlas"));Assert.assertNull(get(renderer, "secondAtlas"));
                        light.shadowQuality = 2;
                        submit.prepareComposition();
                        Assert.assertNotNull(get(renderer, "atlas"));Assert.assertNotNull(get(renderer, "secondAtlas"));
                        int added = engine.getWorld().create();
                        PointLightComponent addedLight = engine.getWorld().getMapper(PointLightComponent.class).create(added);
                        addedLight.shadowQuality = 1;
                        addedLight.shadowResolution = 1024;
                        submit.prepareComposition();
                        Assert.assertEquals(1024, get(renderer, "resolution"));
                        Assert.assertSame(batch, get(renderer, "batch"));
                        engine.getWorld().delete(added);
                        submit.prepareComposition();
                        Assert.assertEquals(512, get(renderer, "resolution"));
                        Assert.assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    }
                } finally { engine.dispose(); engine.dispose(); }
        } finally { root.deleteDirectory(); }
    }

    private static void writeScene(FileHandle file) throws Exception {
        World world = new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
        try {
            WorldSerializationManager serializer = world.getSystem(WorldSerializationManager.class);
            serializer.setSerializer(new JsonArtemisSerializer(world));
            int layer = world.create();
            world.getMapper(LayerComponent.class).create(layer).layerIndex = 0;
            world.getMapper(PixscapeIdentityComponent.class).create(layer).stableId = 1;
            int entity = world.create();
            world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = 2;
            PointLightComponent light = world.getMapper(PointLightComponent.class).create(entity);
            light.shadowResolution = 256;
            light.shadowQuality = 1;
            world.process();
            file.parent().mkdirs();
            try (OutputStream out = file.write(false)) {
                serializer.save(out, new SaveFileFormat(world.getAspectSubscriptionManager()
                        .get(com.artemis.Aspect.all()).getEntities()));
            }
        } finally { world.dispose(); }
    }

    private static Object get(Object object, String name) throws Exception {
        Field field = object.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(object);
    }
}
