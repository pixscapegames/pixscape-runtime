package games.pixscape.runtime.engine;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.prefab.RuntimePrefabFragment;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.WorldBlockMutationService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.PhysicsSpatialFootprintSyncSystem;
import games.pixscape.runtime.spatial.SpatialBlockData;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

public class PixscapeEnginePhysicsLifecycleTest {
    private Application previousApp;
    private GL20 previousGl;
    private GL20 previousGl20;
    private GL30 previousGl30;
    private Graphics previousGraphics;
    private com.badlogic.gdx.Files previousFiles;
    private final List<String> debugMessages = new ArrayList<>();

    @Before
    public void installApplication() {
        previousApp = Gdx.app;
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        previousGl30 = Gdx.gl30;
        previousGraphics = Gdx.graphics;
        previousFiles = Gdx.files;
        Gdx.app = (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[]{Application.class},
                (proxy, method, args) -> {
                    if ("debug".equals(method.getName())
                            && args != null && args.length >= 2) {
                        debugMessages.add(String.valueOf(args[1]));
                    }
                    return defaultValue(method.getReturnType());
                });
        int[] nextGlHandle = {1};
        GL30 gl = (GL30) Proxy.newProxyInstance(
                GL30.class.getClassLoader(),
                new Class<?>[]{GL30.class},
                (proxy, method, args) ->
                        glDefaultValue(method.getName(), args,
                                method.getReturnType(), nextGlHandle));
        Gdx.gl = gl;
        Gdx.gl20 = gl;
        Gdx.gl30 = gl;
        Gdx.graphics = (Graphics) Proxy.newProxyInstance(
                Graphics.class.getClassLoader(),
                new Class<?>[]{Graphics.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
        Gdx.files = (com.badlogic.gdx.Files) Proxy.newProxyInstance(
                com.badlogic.gdx.Files.class.getClassLoader(),
                new Class<?>[]{com.badlogic.gdx.Files.class},
                (proxy, method, args) -> {
                    if (args != null && args.length == 1
                            && args[0] instanceof String
                            && FileHandle.class.equals(method.getReturnType())) {
                        String path = (String) args[0];
                        if ("internal".equals(method.getName())
                                || "classpath".equals(method.getName())) {
                            return new FileHandle("src/main/resources")
                                    .child(path);
                        }
                        return new FileHandle(path);
                    }
                    return defaultValue(method.getReturnType());
                });
    }

    @After
    public void restoreApplication() {
        Gdx.app = previousApp;
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
        Gdx.gl30 = previousGl30;
        Gdx.graphics = previousGraphics;
        Gdx.files = previousFiles;
    }

    @Test
    public void disabledSceneDisposesAfterTeardownAndEnabledSceneRecreatesLazily()
            throws Exception {
        Box2dWorldService initial = new Box2dWorldService(
                100f, new Vector2(0f, -9.8f), true);
        Box2dSyncSystem sync = new Box2dSyncSystem(initial);
        World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(16), sync,
                        new PhysicsSpatialFootprintSyncSystem(100f))
                .build());
        PixscapeEngine engine = new PixscapeEngine();
        set(engine, "world", world);
        set(engine, "box2dSyncSystem", sync);
        set(engine, "box2dWorldService", initial);

        SceneMetaRuntime disabled = new SceneMetaRuntime();
        disabled.physicsEnabled = false;
        apply(engine, disabled, true);

        Assert.assertTrue(initial.isDisposed());
        Assert.assertNull(engine.getBox2dWorldService());
        Assert.assertNull(sync.getBox2d());
        Assert.assertFalse(sync.isEnabled());
        Assert.assertFalse(sync.isStepEnabled());

        SceneMetaRuntime enabled = new SceneMetaRuntime();
        enabled.physicsEnabled = true;
        enabled.pixelsPerMeter = 64f;
        enabled.gravityX = 1f;
        enabled.gravityY = -3f;
        enabled.doSleep = false;
        apply(engine, enabled, true);

        Box2dWorldService recreated = engine.getBox2dWorldService();
        Assert.assertNotNull(recreated);
        Assert.assertNotSame(initial, recreated);
        Assert.assertEquals(64f, recreated.ppm, 0f);
        Assert.assertFalse(recreated.isDoSleep());
        Assert.assertTrue(sync.isEnabled());
        Assert.assertTrue(sync.isStepEnabled());
        engine.dispose();
    }

    @Test
    public void failedReplacementDiscardsCandidateAndAllowsRetry()
            throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            engine.loadScene("A");

            World worldA = engine.getWorld();
            Assert.assertNotNull(worldA);
            Assert.assertSame(fixture.sceneA, engine.getActiveSceneMeta());
            Assert.assertTrue(engine.findEntityByStableId(7) >= 0);
            Assert.assertTrue(engine.firstEntityByTag("hero") >= 0);

            int buildsBeforeFailure = fixture.worldProbe.buildCount;
            RuntimeException failure = Assert.assertThrows(
                    RuntimeException.class,
                    () -> engine.loadScene("B"));

            Assert.assertTrue(failure.getMessage(),
                    failure.getMessage().contains("duplicate ID"));
            Assert.assertEquals(buildsBeforeFailure + 1,
                    fixture.worldProbe.buildCount);
            Assert.assertNotNull(fixture.worldProbe.latestWorld);
            Assert.assertNotSame(worldA, fixture.worldProbe.latestWorld);
            Assert.assertTrue(debugMessages.stream().anyMatch(
                    message -> message.contains("enabled ppm=64.0")));

            Assert.assertTrue(engine.isLoaded());
            Assert.assertFalse((Boolean) get(engine, "sceneLoaded"));
            Assert.assertNull(engine.getActiveSceneMeta());
            Assert.assertNull(engine.getWorld());
            Assert.assertNull(engine.getBox2dWorldService());
            Assert.assertNull(engine.getBox2dSyncSystem());
            Assert.assertEquals(-1, engine.findEntityByStableId(7));
            Assert.assertEquals(-1, engine.firstEntityByTag("hero"));
            IntBag tagged = new IntBag();
            engine.findEntitiesByTag("hero", tagged);
            Assert.assertEquals(0, tagged.size());
            Assert.assertEquals(0, engine.getDrawList().size);
            Assert.assertEquals(0, engine.getFrameQueue().size);
            Assert.assertTrue(Float.isNaN(engine.getLayerState().physicsParallaxX));
            Assert.assertTrue(Float.isNaN(engine.getLayerState().physicsParallaxY));
            Assert.assertEquals(1f, (Float) get(engine, "ambientMulR"), 0f);
            Assert.assertEquals(1f, (Float) get(engine, "ambientMulG"), 0f);
            Assert.assertEquals(1f, (Float) get(engine, "ambientMulB"), 0f);
            Assert.assertSame(fixture.config, engine.config());
            Assert.assertSame(fixture.projectDir, engine.runtimeProjectDir());
            Assert.assertSame(fixture.atlasService,
                    get(engine, "atlasRuntimeService"));
            Assert.assertSame(fixture.metricsBatch, get(engine, "metricsBatch"));

            engine.render();
            Assert.assertThrows(IllegalStateException.class,
                    () -> engine.mapper(PixscapeIdentityComponent.class));
            Assert.assertThrows(IllegalStateException.class,
                    () -> engine.system(DirtyTrackerSystem.class));
            Assert.assertThrows(IllegalStateException.class,
                    () -> engine.spawnPrefabFragment(
                            new RuntimePrefabFragment(), 0f, 0f));
            Assert.assertThrows(IllegalStateException.class,
                    () -> engine.spawnPrefab("unused", 0f, 0f));

            engine.loadScene("A");

            Assert.assertNotNull(engine.getWorld());
            Assert.assertNotSame(worldA, engine.getWorld());
            Assert.assertSame(fixture.sceneA, engine.getActiveSceneMeta());
            Assert.assertTrue(engine.findEntityByStableId(7) >= 0);
            Assert.assertTrue(engine.firstEntityByTag("hero") >= 0);
            engine.render();
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void unknownSceneLeavesActiveSceneIntact() throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            engine.loadScene("A");
            World worldA = engine.getWorld();
            SceneMetaRuntime metaA = engine.getActiveSceneMeta();
            int entityA = engine.findEntityByStableId(7);

            IllegalArgumentException failure = Assert.assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.loadScene("UnknownScene"));

            Assert.assertTrue(failure.getMessage().contains("Unknown scene"));
            Assert.assertSame(worldA, engine.getWorld());
            Assert.assertSame(metaA, engine.getActiveSceneMeta());
            Assert.assertEquals(entityA, engine.findEntityByStableId(7));
            Assert.assertTrue(engine.firstEntityByTag("hero") >= 0);
            engine.render();
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void structurallyValidLinkedSceneBuildsCacheAndAllowsDirectRetry()
            throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            engine.loadScene("A");
            int buildsBeforeLinked = fixture.worldProbe.buildCount;

            engine.loadScene("C");
            Assert.assertEquals(buildsBeforeLinked + 1,
                    fixture.worldProbe.buildCount);
            Assert.assertTrue(engine.isLoaded());
            Assert.assertTrue((Boolean) get(engine, "sceneLoaded"));
            int owner = engine.findEntityByStableId(1);
            Assert.assertTrue(owner >= 0);
            PhysicsCompiledFixturesComponent compiled = engine
                    .mapper(PhysicsCompiledFixturesComponent.class).get(owner);
            Assert.assertNotNull(compiled);
            Assert.assertTrue(compiled.valid);
            Assert.assertEquals(1, compiled.fixtures.size);
            int generation = compiled.generation;
            engine.render();
            PhysicsRuntimeBodyComponent runtimeBody = engine
                    .mapper(PhysicsRuntimeBodyComponent.class).get(owner);
            Assert.assertNotNull(runtimeBody);
            Assert.assertNotNull(runtimeBody.body);
            Assert.assertEquals(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody,
                    runtimeBody.body.getType());
            Assert.assertEquals(1, runtimeBody.body.getFixtureList().size);
            com.badlogic.gdx.physics.box2d.Fixture nativeFixture =
                    runtimeBody.body.getFixtureList().first();
            Assert.assertEquals(2f, nativeFixture.getDensity(), 0f);
            Assert.assertEquals(.3f, nativeFixture.getFriction(), 0f);
            Assert.assertEquals(.1f, nativeFixture.getRestitution(), 0f);
            Assert.assertTrue(nativeFixture.isSensor());
            Assert.assertEquals(2, nativeFixture.getFilterData().categoryBits);
            Assert.assertEquals(4, nativeFixture.getFilterData().maskBits);
            Assert.assertEquals(6, nativeFixture.getFilterData().groupIndex);
            Object nativeBody = runtimeBody.body;
            engine.render();
            Assert.assertSame(nativeBody, engine.mapper(PhysicsRuntimeBodyComponent.class)
                    .get(owner).body);
            Assert.assertEquals(generation, compiled.generation);

            engine.loadScene("A");

            Assert.assertNotNull(engine.getWorld());
            Assert.assertSame(fixture.sceneA, engine.getActiveSceneMeta());
            Assert.assertTrue(engine.findEntityByStableId(7) >= 0);
            engine.render();
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void blockMutationServiceRebuildsOneReservedNativeBodyInAuthoredFixtureOrder()
            throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            engine.loadScene("C");
            int owner = engine.findEntityByStableId(1);
            engine.render();
            PhysicsRuntimeBodyComponent before = engine
                    .mapper(PhysicsRuntimeBodyComponent.class).get(owner);
            Object originalNativeBody = before.body;

            int physicsShapeId = engine.getWorldBlockMutationService()
                    .bindBlockCollision(1, 2);
            PhysicsCompiledFixturesComponent compiled = engine
                    .mapper(PhysicsCompiledFixturesComponent.class).get(owner);
            Assert.assertEquals(2, compiled.fixtures.size);
            Assert.assertEquals(10, compiled.fixtures.get(0).physicsShapeId);
            Assert.assertEquals(physicsShapeId, compiled.fixtures.get(1).physicsShapeId);
            int generation = compiled.generation;

            engine.render();
            PhysicsRuntimeBodyComponent after = engine
                    .mapper(PhysicsRuntimeBodyComponent.class).get(owner);
            Assert.assertNotNull(after.body);
            Assert.assertNotSame(originalNativeBody, after.body);
            Assert.assertEquals(com.badlogic.gdx.physics.box2d.BodyDef.BodyType.StaticBody,
                    after.body.getType());
            Assert.assertEquals(2, after.body.getFixtureList().size);
            Assert.assertEquals(2f, after.body.getFixtureList().get(0).getDensity(), 0f);
            Assert.assertEquals(1f, after.body.getFixtureList().get(1).getDensity(), 0f);

            Object rebuiltNativeBody = after.body;
            engine.render();
            Assert.assertSame(rebuiltNativeBody, engine
                    .mapper(PhysicsRuntimeBodyComponent.class).get(owner).body);
            Assert.assertEquals(generation, compiled.generation);
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void publicPrefabFragmentPathRejectsSpatialBlockPhysicsBeforeAllocation()
            throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            engine.loadScene("A");
            World world = engine.getWorld();
            int source = world.create();
            world.getMapper(BlockPhysicsBindingsComponent.class)
                    .create(source);
            world.process();
            RuntimePrefabFragment fragment = new RuntimePrefabFragment();
            fragment.entities.add(source);
            int entityCount = world.getAspectSubscriptionManager()
                    .get(com.artemis.Aspect.all()).getEntities().size();
            int nextEntityStableId =
                    engine.getActiveSceneMeta().nextEntityStableId;
            int nextPhysicsShapeId =
                    engine.getActiveSceneMeta().nextPhysicsShapeId;

            IllegalArgumentException failure = Assert.assertThrows(
                    IllegalArgumentException.class,
                    () -> engine.spawnPrefabFragment(fragment, 0f, 0f));

            Assert.assertTrue(failure.getMessage(),
                    failure.getMessage().contains("Runtime actor prefabs do not support spatial block physics"));
            Assert.assertEquals(entityCount,
                    world.getAspectSubscriptionManager()
                            .get(com.artemis.Aspect.all())
                            .getEntities().size());
            Assert.assertEquals(nextEntityStableId,
                    engine.getActiveSceneMeta().nextEntityStableId);
            Assert.assertEquals(nextPhysicsShapeId,
                    engine.getActiveSceneMeta().nextPhysicsShapeId);
        } finally {
            engine.dispose();
        }
    }

    private static EngineFixture createEngineFixture() throws Exception {
        GdxNativesLoader.load();

        File directory = Files.createTempDirectory(
                "pixscape-engine-fail-closed-").toFile();
        directory.deleteOnExit();
        FileHandle userRoot = new FileHandle(directory);
        FileHandle projectDir =
                userRoot.child(PixscapeEngine.RUNTIME_DIR_NAME);
        projectDir.mkdirs();
        FileHandle scenesDir = projectDir.child("scenes");
        scenesDir.mkdirs();

        projectDir.child("project.json").writeString(
                projectJson(), false, "UTF-8");
        writeScene(scenesDir.child("a.json"), false);
        writeScene(scenesDir.child("b.json"), true);
        writeLinkedScene(scenesDir.child("c.json"));
        writeInvalidLinkedScene(scenesDir.child("d.json"));

        PixscapeEngine engine = new PixscapeEngine();
        CandidateWorldProbe worldProbe = new CandidateWorldProbe();
        engine.setConfigurationCustomizer(builder ->
                builder.with(new CandidateWorldProbeSystem(worldProbe)));
        engine.loadProject(userRoot);

        projectDir = engine.runtimeProjectDir();
        RuntimeConfig config = engine.config();
        SceneMetaRuntime sceneA = config.getSceneMeta("A");
        NoOpAtlasRuntimeService atlasService =
                new NoOpAtlasRuntimeService();
        NoOpMetricsBatch metricsBatch = new NoOpMetricsBatch();
        engine.getMetricsBatch().close();
        engine.getAtlasRuntimeService().unloadAll();
        set(engine, "metricsBatch", metricsBatch);
        set(engine, "atlasRuntimeService", atlasService);

        return new EngineFixture(
                engine, config, sceneA, projectDir, atlasService,
                metricsBatch, worldProbe);
    }

    private static String projectJson() {
        return "{"
                + "\"projectFileName\":\"test-project.json\","
                + "\"version\":\"1\","
                + "\"currentSceneName\":\"A\","
                + "\"scenes\":{"
                + "\"A\":{"
                + "\"sceneSchemaVersion\":1,"
                + "\"name\":\"A\","
                + "\"file\":\"a.json\","
                + "\"nextEntityStableId\":8,"
                + "\"nextPhysicsShapeId\":1"
                + "},"
                + "\"B\":{"
                + "\"sceneSchemaVersion\":1,"
                + "\"name\":\"B\","
                + "\"file\":\"b.json\","
                + "\"nextEntityStableId\":10,"
                + "\"nextPhysicsShapeId\":1,"
                + "\"physicsEnabled\":true,"
                + "\"pixelsPerMeter\":64,"
                + "\"gravityX\":1,"
                + "\"gravityY\":-3,"
                + "\"doSleep\":false"
                + "},"
                + "\"C\":{"
                + "\"sceneSchemaVersion\":1,"
                + "\"name\":\"C\","
                + "\"file\":\"c.json\","
                + "\"nextEntityStableId\":2,"
                + "\"nextPhysicsShapeId\":11,"
                + "\"physicsEnabled\":true"
                + "},"
                + "\"D\":{"
                + "\"sceneSchemaVersion\":1,"
                + "\"name\":\"D\","
                + "\"file\":\"d.json\","
                + "\"nextEntityStableId\":2,"
                + "\"nextPhysicsShapeId\":11,"
                + "\"physicsEnabled\":true"
                + "}"
                + "}}";
    }

    @Test
    public void invalidLinkedSceneFailsClosedAndDirectRetrySucceeds() throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            engine.loadScene("A");
            Assert.assertThrows(RuntimeException.class, () -> engine.loadScene("D"));
            Assert.assertFalse((Boolean) get(engine, "sceneLoaded"));
            Assert.assertNull(engine.getActiveSceneMeta());
            Assert.assertNull(engine.getWorld());
            engine.loadScene("A");
            Assert.assertTrue((Boolean) get(engine, "sceneLoaded"));
            Assert.assertSame(fixture.sceneA, engine.getActiveSceneMeta());
            engine.render();
        } finally { engine.dispose(); }
    }

    @Test
    public void retainedMutationServiceIsDetachedAcrossReplacementFailureAndDispose() throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        engine.loadScene("A");
        WorldBlockMutationService serviceA = engine.getWorldBlockMutationService();
        engine.loadScene("C");
        assertDetached(serviceA);
        WorldBlockMutationService serviceC = engine.getWorldBlockMutationService();
        Assert.assertNotSame(serviceA, serviceC);
        Assert.assertThrows(RuntimeException.class, () -> engine.loadScene("D"));
        assertDetached(serviceC);
        engine.loadScene("A");
        WorldBlockMutationService retry = engine.getWorldBlockMutationService();
        Assert.assertNotSame(serviceC, retry);
        engine.dispose();
        assertDetached(retry);
    }

    private static void assertDetached(WorldBlockMutationService service) {
        try {
            service.bindBlockCollision(1, 1);
            Assert.fail("Retained mutation service must be detached.");
        } catch (IllegalStateException expected) {
            Assert.assertTrue(expected.getMessage().contains("detached"));
        }
    }

    private static void writeInvalidLinkedScene(FileHandle file) throws Exception {
        World source = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        try {
            int owner = source.create();
            source.getMapper(PixscapeIdentityComponent.class).create(owner).stableId = 1;
            SpatialBlocksComponent blocks = source.getMapper(SpatialBlocksComponent.class).create(owner);
            SpatialBlockData block = new SpatialBlockData(); block.id = 1; block.structureId = 1; block.width = 1f; block.depth = 1f;
            blocks.blocks.add(block); blocks.nextSpatialBlockId = 2;
            PhysicsShapeData shape = new PhysicsShapeData(); shape.physicsShapeId = 10;
            source.getMapper(PhysicsShapesComponent.class).create(owner).shapes.add(shape);
            source.getMapper(TransformComponent.class).create(owner);
            source.getMapper(TiledLayerComponent.class).create(owner);
            source.getMapper(BlockPhysicsBindingsComponent.class).create(owner).bindings.add(binding(1, 10));
            source.process();
            WorldSerializationManager serialization = source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(source));
            SaveFileFormat format = new SaveFileFormat(source.getAspectSubscriptionManager().get(com.artemis.Aspect.all()).getEntities());
            try (OutputStream output = file.write(false)) { serialization.save(output, format); }
        } finally { source.dispose(); }
    }

    private static BlockPhysicsBindingData binding(int blockId, int shapeId) {
        BlockPhysicsBindingData binding = new BlockPhysicsBindingData(); binding.spatialBlockId = blockId; binding.physicsShapeId = shapeId; return binding;
    }

    private static void writeLinkedScene(FileHandle file) throws Exception {
        World source = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            int owner = source.create();
            PixscapeIdentityComponent identity = source
                    .getMapper(PixscapeIdentityComponent.class).create(owner);
            identity.stableId = 1;
            SpatialBlocksComponent blocks = source
                    .getMapper(SpatialBlocksComponent.class).create(owner);
            SpatialBlockData block = new SpatialBlockData();
            block.id = 1;
            block.structureId = 1;
            block.width = 1;
            block.depth = 1;
            blocks.blocks.add(block);
            SpatialBlockData secondBlock = block.copy();
            secondBlock.id = 2;
            secondBlock.x = 1;
            blocks.blocks.add(secondBlock);
            blocks.nextSpatialBlockId = 3;
            PhysicsShapeData shape = new PhysicsShapeData();
            shape.physicsShapeId = 10;
            shape.directGeometry = null;
            shape.enabled = true;
            shape.density = 2f;
            shape.friction = .3f;
            shape.restitution = .1f;
            shape.sensor = true;
            shape.categoryBits = 2;
            shape.maskBits = 4;
            shape.groupIndex = 6;
            source.getMapper(PhysicsShapesComponent.class)
                    .create(owner).shapes.add(shape);
            PhysicsBodyComponent body = source
                    .getMapper(PhysicsBodyComponent.class).create(owner);
            body.type = PhysicsBodyComponent.STATIC;
            body.fixedRotation = true;
            source.getMapper(TransformComponent.class).create(owner);
            TiledLayerComponent tiled = source
                    .getMapper(TiledLayerComponent.class).create(owner);
            tiled.mapWidthCells = 2;
            tiled.mapHeightCells = 2;
            BlockPhysicsBindingData binding =
                    new BlockPhysicsBindingData();
            binding.spatialBlockId = 1;
            binding.physicsShapeId = 10;
            source.getMapper(BlockPhysicsBindingsComponent.class)
                    .create(owner).bindings.add(binding);

            source.process();
            WorldSerializationManager serialization =
                    source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(source));
            SaveFileFormat format = new SaveFileFormat(
                    source.getAspectSubscriptionManager()
                            .get(com.artemis.Aspect.all()).getEntities());
            try (OutputStream output = file.write(false)) {
                serialization.save(output, format);
            }
        } finally {
            source.dispose();
        }
    }

    private static void writeScene(FileHandle file, boolean duplicateIds)
            throws Exception {
        World source = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            int first = source.create();
            PixscapeIdentityComponent identity =
                    source.getMapper(PixscapeIdentityComponent.class)
                            .create(first);
            identity.stableId = duplicateIds ? 9 : 7;
            identity.name = duplicateIds ? "invalid-a" : "hero";
            if (!duplicateIds) {
                PixscapeTagComponent tags =
                        source.getMapper(PixscapeTagComponent.class)
                                .create(first);
                tags.tags.add("hero");
            }

            if (duplicateIds) {
                int second = source.create();
                PixscapeIdentityComponent duplicate =
                        source.getMapper(PixscapeIdentityComponent.class)
                                .create(second);
                duplicate.stableId = 9;
                duplicate.name = "invalid-b";
            }

            source.process();
            WorldSerializationManager serialization =
                    source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(new JsonArtemisSerializer(source));
            SaveFileFormat format = new SaveFileFormat(
                    source.getAspectSubscriptionManager()
                            .get(com.artemis.Aspect.all()).getEntities());
            try (OutputStream output = file.write(false)) {
                serialization.save(output, format);
            }
        } finally {
            source.dispose();
        }
    }

    private static void apply(
            PixscapeEngine engine, SceneMetaRuntime meta, boolean activate)
            throws Exception {
        Method method = PixscapeEngine.class.getDeclaredMethod(
                "applyPhysicsFromScene", SceneMetaRuntime.class, boolean.class);
        method.setAccessible(true);
        method.invoke(engine, meta, activate);
    }

    private static void set(Object target, String name, Object value)
            throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object get(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.get(target);
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Boolean.TYPE) return false;
        if (type == Byte.TYPE) return (byte) 0;
        if (type == Short.TYPE) return (short) 0;
        if (type == Character.TYPE) return (char) 0;
        if (type == Integer.TYPE) return 0;
        if (type == Long.TYPE) return 0L;
        if (type == Float.TYPE) return 0f;
        if (type == Double.TYPE) return 0d;
        return null;
    }

    private static Object glDefaultValue(
            String methodName, Object[] args, Class<?> returnType,
            int[] nextHandle) {
        if ("glCreateShader".equals(methodName)
                || "glCreateProgram".equals(methodName)
                || "glGenTexture".equals(methodName)
                || "glGenBuffer".equals(methodName)
                || "glGenVertexArray".equals(methodName)) {
            return nextHandle[0]++;
        }
        if ("glGetShaderiv".equals(methodName)
                && args != null && args.length >= 3) {
            ((java.nio.IntBuffer) args[2]).put(0, 1);
            return null;
        }
        if ("glGetProgramiv".equals(methodName)
                && args != null && args.length >= 3) {
            int parameter = (Integer) args[1];
            int value = parameter == GL20.GL_LINK_STATUS
                    || parameter == GL20.GL_VALIDATE_STATUS ? 1 : 0;
            ((java.nio.IntBuffer) args[2]).put(0, value);
            return null;
        }
        if (methodName.startsWith("glGen")
                && args != null && args.length >= 2
                && args[1] instanceof java.nio.IntBuffer) {
            java.nio.IntBuffer handles = (java.nio.IntBuffer) args[1];
            int count = (Integer) args[0];
            for (int i = 0; i < count; i++) {
                handles.put(i, nextHandle[0]++);
            }
            return null;
        }
        if ("glCheckFramebufferStatus".equals(methodName)) {
            return GL20.GL_FRAMEBUFFER_COMPLETE;
        }
        return defaultValue(returnType);
    }

    private static final class EngineFixture {
        private final PixscapeEngine engine;
        private final RuntimeConfig config;
        private final SceneMetaRuntime sceneA;
        private final FileHandle projectDir;
        private final AtlasRuntimeService atlasService;
        private final MetricsBatch metricsBatch;
        private final CandidateWorldProbe worldProbe;

        private EngineFixture(
                PixscapeEngine engine,
                RuntimeConfig config,
                SceneMetaRuntime sceneA,
                FileHandle projectDir,
                AtlasRuntimeService atlasService,
                MetricsBatch metricsBatch,
                CandidateWorldProbe worldProbe) {
            this.engine = engine;
            this.config = config;
            this.sceneA = sceneA;
            this.projectDir = projectDir;
            this.atlasService = atlasService;
            this.metricsBatch = metricsBatch;
            this.worldProbe = worldProbe;
        }
    }

    private static final class CandidateWorldProbe {
        private int buildCount;
        private World latestWorld;
        private int compiledFixturesAtDispose;
        private int nativeBodiesAtDispose;
    }

    private static final class CandidateWorldProbeSystem extends BaseSystem {
        private final CandidateWorldProbe probe;

        private CandidateWorldProbeSystem(CandidateWorldProbe probe) {
            this.probe = probe;
        }

        @Override
        protected void initialize() {
            probe.buildCount++;
            probe.latestWorld = world;
        }

        @Override
        protected void processSystem() {
        }

        @Override
        protected void dispose() {
            probe.compiledFixturesAtDispose = world
                    .getAspectSubscriptionManager()
                    .get(com.artemis.Aspect.all(
                            PhysicsCompiledFixturesComponent.class))
                    .getEntities().size();
            IntBag bodies = world.getAspectSubscriptionManager()
                    .get(com.artemis.Aspect.all(
                            PhysicsRuntimeBodyComponent.class))
                    .getEntities();
            int nativeBodies = 0;
            for (int i = 0; i < bodies.size(); i++) {
                if (world.getMapper(PhysicsRuntimeBodyComponent.class)
                        .get(bodies.get(i)).body != null) {
                    nativeBodies++;
                }
            }
            probe.nativeBodiesAtDispose = nativeBodies;
        }
    }

    private static final class NoOpAtlasRuntimeService
            extends AtlasRuntimeService {
        private final TextureAtlas emptyAtlas = new TextureAtlas();

        @Override
        public TextureAtlas getAtlas(String tag) {
            return emptyAtlas;
        }

        @Override
        public TextureArrayBundle rebuildBundle(String tag) {
            return null;
        }

        @Override
        public TextureArrayBundle bundle(String tag) {
            return null;
        }
    }

    private static final class NoOpMetricsBatch implements MetricsBatch {
        @Override
        public void begin(Matrix4 combined, RenderStats stats) {
        }

        @Override
        public void setShader(ShaderProgram shader, RenderStats stats) {
        }

        @Override
        public void setBlendMode(
                boolean enabled, int sfactor, int dfactor,
                RenderStats stats) {
        }

        @Override
        public void setColor(float r, float g, float b, float a) {
        }

        @Override
        public void draw(
                int textureHandle,
                float x1, float y1, float x2, float y2,
                float x3, float y3, float x4, float y4,
                float u, float v, float u2, float v2,
                RenderStats stats) {
        }

        @Override
        public void flush(RenderStats stats) {
        }

        @Override
        public void end(RenderStats stats) {
        }

        @Override
        public void close() {
        }
    }
}
