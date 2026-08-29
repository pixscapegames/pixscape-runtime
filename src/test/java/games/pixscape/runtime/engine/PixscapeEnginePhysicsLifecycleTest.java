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
import com.badlogic.gdx.assets.AssetDescriptor;
import com.badlogic.gdx.assets.AssetLoaderParameters;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.SynchronousAssetLoader;
import com.badlogic.gdx.assets.loaders.TextureAtlasLoader;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.GL30;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.loading.SceneLoadHandle;
import games.pixscape.runtime.loading.SceneLoadPhase;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.prefab.RuntimePrefabFragment;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.PhysicsSpatialFootprintSyncSystem;
import games.pixscape.runtime.system.RenderTiledSyncSystem;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.OutputStream;
import java.io.StringWriter;
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
    public void progressiveLoadIsMonotonicReadyAndDoesNotProcessGameplayWorld()
            throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            Assert.assertEquals(0, fixture.worldProbe.buildCount);

            SceneLoadHandle load = engine.beginLoadScene("A");
            float previousProgress = 0f;
            int previousPhase = SceneLoadPhase.FILES.ordinal();
            while (!load.isReady() && !load.isFailed()) {
                load.update();
                Assert.assertTrue(load.progress() >= previousProgress);
                Assert.assertTrue(load.phase().ordinal() >= previousPhase);
                Assert.assertTrue(load.progress() < 1f || load.isReady());
                previousProgress = load.progress();
                previousPhase = load.phase().ordinal();
            }

            Assert.assertFalse(load.isFailed());
            Assert.assertNull(load.failure());
            Assert.assertEquals(SceneLoadPhase.READY, load.phase());
            Assert.assertEquals(1f, load.progress(), 0f);
            Assert.assertSame(fixture.sceneA, engine.getActiveSceneMeta());
            Assert.assertTrue(engine.findEntityByStableId(7) >= 0);
            Assert.assertEquals(1, fixture.worldProbe.buildCount);
            Assert.assertEquals(0, fixture.worldProbe.processCount);

            engine.render();
            Assert.assertEquals(1, fixture.worldProbe.processCount);
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void progressiveLoadRejectsParallelOperationAndExposesRootFailure()
            throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            SceneLoadHandle active = engine.beginLoadScene("A");
            Assert.assertThrows(IllegalStateException.class,
                    () -> engine.beginLoadScene("D"));
            while (!active.isReady() && !active.isFailed()) active.update();
            Assert.assertTrue(active.isReady());

            SceneLoadHandle failed = engine.beginLoadScene("B");
            while (!failed.isReady() && !failed.isFailed()) failed.update();
            Assert.assertTrue(failed.isFailed());
            Assert.assertNotNull(failed.failure());
            Assert.assertTrue(failed.failure().getMessage(),
                    failed.failure().getMessage().contains("duplicate ID"));
            Assert.assertTrue(failed.progress() < 1f);
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void synchronousAndProgressiveLoadsPublishEquivalentRuntimeState()
            throws Exception {
        EngineFixture synchronous = createEngineFixture();
        int synchronousBodies;
        int synchronousFixtures;
        try {
            synchronous.engine.loadScene("D");
            int owner = synchronous.engine.findEntityByStableId(11);
            synchronousBodies = synchronous.engine.getBox2dWorldService()
                    .world.getBodyCount();
            synchronousFixtures = synchronous.engine.getWorld()
                    .getMapper(PhysicsCompiledFixturesComponent.class)
                    .get(owner).fixtures.size;
        } finally {
            synchronous.engine.dispose();
        }

        EngineFixture progressive = createEngineFixture();
        try {
            SceneLoadHandle load = progressive.engine.beginLoadScene("D");
            while (!load.isReady() && !load.isFailed()) load.update();
            Assert.assertTrue(load.isReady());
            int owner = progressive.engine.findEntityByStableId(11);
            Assert.assertTrue(owner >= 0);
            Assert.assertEquals(synchronousBodies,
                    progressive.engine.getBox2dWorldService().world.getBodyCount());
            Assert.assertEquals(synchronousFixtures,
                    progressive.engine.getWorld()
                            .getMapper(PhysicsCompiledFixturesComponent.class)
                            .get(owner).fixtures.size);
        } finally {
            progressive.engine.dispose();
        }
    }

    @Test
    public void readyIncludesDeclaredPrefabAndSpawnNeverRequestsUndeclaredPrefab()
            throws Exception {
        EngineFixture fixture = createEngineFixture();
        try {
            SceneLoadHandle load = fixture.engine.beginLoadScene("A");
            while (!load.isReady() && !load.isFailed()) load.update();

            Assert.assertTrue(load.isReady());
            Assert.assertEquals(0,
                    fixture.engine.spawnPrefab("declared", 0f, 0f)
                            .createdEntityIds().size());
            RenderParticleSyncSystem particles = fixture.engine.getWorld()
                    .getSystem(RenderParticleSyncSystem.class);
            particles.requirePrepared("a", "declared.p");
            Assert.assertTrue(fixture.engine.api().particles()
                    .spawn("declared.p", 0f, 0f).entity().exists());

            int requestsAtReady = fixture.assetManager.loadCalls;
            RuntimeException missing = Assert.assertThrows(
                    RuntimeException.class,
                    () -> fixture.engine.spawnPrefab("undeclared", 0f, 0f));
            Assert.assertTrue(missing.getMessage().contains("Prefab fragment not found"));
            Assert.assertEquals(requestsAtReady, fixture.assetManager.loadCalls);
        } finally {
            fixture.engine.dispose();
        }
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
            engine.dispose();
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void linkedValidationFailureDiscardsCandidateAndAllowsRetry()
            throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            engine.loadScene("A");
            World worldA = engine.getWorld();

            RuntimeException failure = Assert.assertThrows(
                    RuntimeException.class,
                    () -> engine.loadScene("C"));

            Assert.assertTrue(failure.getMessage(),
                    failure.getMessage().contains(
                            "SpatialBlocksComponent"));
            Assert.assertNull(engine.getWorld());

            engine.loadScene("A");
            Assert.assertNotSame(worldA, engine.getWorld());
        } finally {
            engine.dispose();
        }
    }

    @Test
    public void runtimeLoadRebuildsTiledDataBeforeCompilingLinkedFixture()
            throws Exception {
        EngineFixture fixture = createEngineFixture();
        PixscapeEngine engine = fixture.engine;
        try {
            engine.loadScene("D");

            World world = engine.getWorld();
            int owner = engine.findEntityByStableId(11);
            Assert.assertTrue(owner >= 0);
            TiledLayerComponent tiledMap = world.getMapper(
                    TiledLayerComponent.class).get(owner);
            Assert.assertNotNull(tiledMap.data);
            Assert.assertEquals(TiledProjection.ISO, tiledMap.data.projection);
            Assert.assertEquals(64, tiledMap.data.tileWidth);
            Assert.assertEquals(32, tiledMap.data.tileHeight);
            Assert.assertEquals(8, tiledMap.data.chunkSize);
            Assert.assertEquals(5f, tiledMap.data.originX, 0f);
            Assert.assertEquals(7f, tiledMap.data.originY, 0f);
            PhysicsCompiledFixturesComponent compiled = world.getMapper(
                    PhysicsCompiledFixturesComponent.class).get(owner);
            Assert.assertNotNull(compiled);
            Assert.assertTrue(compiled.valid);
            Assert.assertEquals(1, compiled.fixtures.size);
            Assert.assertEquals(31,
                    compiled.fixtures.first().physicsShapeId);
            Assert.assertEquals(PhysicsGeometryData.SHAPE_POLYGON,
                    compiled.fixtures.first().shapeType);
            Assert.assertEquals(4,
                    compiled.fixtures.first().polygonVertexCount);
            Assert.assertNull(world.getMapper(PhysicsShapesComponent.class)
                    .get(owner).shapes.first().geometry);
            Assert.assertNotNull(engine.getBox2dWorldService());
            Assert.assertEquals(1,
                    engine.getBox2dWorldService().world.getBodyCount());
            Assert.assertTrue(engine.getBox2dSyncSystem().isEnabled());
            Assert.assertTrue(engine.getBox2dSyncSystem().isStepEnabled());

            RenderTiledSyncSystem tiled =
                    world.getSystem(RenderTiledSyncSystem.class);
            int compiledChunks = tiled.persistentChunkCompilationCount();
            int nativeBodies = engine.getBox2dWorldService().world.getBodyCount();
            engine.render();
            Assert.assertEquals(compiledChunks,
                    tiled.persistentChunkCompilationCount());
            Assert.assertEquals(nativeBodies,
                    engine.getBox2dWorldService().world.getBodyCount());
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
        FileHandle prefabsDir = projectDir.child("prefabs");
        prefabsDir.mkdirs();
        FileHandle effectsDir = projectDir.child("effects");
        effectsDir.mkdirs();

        projectDir.child("project.json").writeString(
                projectJson(), false, "UTF-8");
        writeScene(scenesDir.child("a.json"), false, false);
        writeScene(scenesDir.child("b.json"), true, false);
        writeScene(scenesDir.child("c.json"), false, true);
        writeLinkedScene(scenesDir.child("d.json"));
        writeEmptyPrefab(prefabsDir.child("declared.pixfragment.json"));
        writeEmptyEffect(effectsDir.child("declared.p"));

        final CountingAssetManager[] createdManager = new CountingAssetManager[1];
        PixscapeEngine engine = new PixscapeEngine(new PixscapeEngine.AssetManagerFactory() {
            @Override
            public AssetManager create(FileHandleResolver resolver) {
                CountingAssetManager manager = new CountingAssetManager(resolver);
                manager.setLoader(TextureAtlas.class, new EmptyAtlasLoader(resolver));
                createdManager[0] = manager;
                return manager;
            }
        });
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
                metricsBatch, worldProbe, createdManager[0]);
    }

    private static String projectJson() {
        return "{"
                + "\"projectFileName\":\"test-project.json\","
                + "\"version\":\"1\","
                + "\"currentSceneName\":\"A\","
                + "\"scenes\":{"
                + "\"A\":{"
                + "\"sceneSchemaVersion\":3,"
                + "\"name\":\"A\","
                + "\"file\":\"a.json\","
                + "\"nextEntityStableId\":8,"
                + "\"nextPhysicsShapeId\":1,"
                + "\"runtimeAvailability\":{"
                + "\"prefabs\":[\"declared\"],"
                + "\"particles\":[\"declared.p\"]}"
                + "},"
                + "\"B\":{"
                + "\"sceneSchemaVersion\":3,"
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
                + "\"sceneSchemaVersion\":3,"
                + "\"name\":\"C\","
                + "\"file\":\"c.json\","
                + "\"nextEntityStableId\":10,"
                + "\"nextPhysicsShapeId\":2,"
                + "\"physicsEnabled\":true"
                + "},"
                + "\"D\":{"
                + "\"sceneSchemaVersion\":3,"
                + "\"name\":\"D\","
                + "\"file\":\"d.json\","
                + "\"nextEntityStableId\":12,"
                + "\"nextPhysicsShapeId\":32,"
                + "\"physicsEnabled\":true,"
                + "\"pixelsPerMeter\":64,"
                + "\"tileWidth\":16,"
                + "\"tileHeight\":16,"
                + "\"chunkSize\":4,"
                + "\"tiledProjection\":\"ORTHO\""
                + "}"
                + "}}";
    }

    private static void writeScene(
            FileHandle file,
            boolean duplicateIds,
            boolean invalidLinkedShape)
            throws Exception {
        World source = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            int first = source.create();
            PixscapeIdentityComponent identity =
                    source.getMapper(PixscapeIdentityComponent.class)
                            .create(first);
            identity.stableId = duplicateIds || invalidLinkedShape ? 9 : 7;
            identity.name = invalidLinkedShape
                    ? "invalid-linked"
                    : duplicateIds ? "invalid-a" : "hero";
            if (!duplicateIds && !invalidLinkedShape) {
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

            if (invalidLinkedShape) {
                PhysicsShapesComponent shapes =
                        source.getMapper(PhysicsShapesComponent.class)
                                .create(first);
                PhysicsShapeData linked = new PhysicsShapeData();
                linked.physicsShapeId = 1;
                linked.spatialBlockId = 7;
                shapes.shapes.add(linked);
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

    private static void writeLinkedScene(FileHandle file) throws Exception {
        World source = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            int host = source.create();
            LayerComponent hostLayer = source.getMapper(LayerComponent.class).create(host);
            hostLayer.layerIndex = 0;
            hostLayer.type = LayerComponent.TYPE_TILED;
            int owner = source.create();
            EntityIndexComponent ownerIndex = source.getMapper(
                    EntityIndexComponent.class).create(owner);
            ownerIndex.layerIndex = 0;
            ownerIndex.zIndex = 0;
            PixscapeIdentityComponent identity = source.getMapper(
                    PixscapeIdentityComponent.class).create(owner);
            identity.stableId = 11;
            identity.name = "linked-spatial-body";
            TransformComponent transform = source.getMapper(
                    TransformComponent.class).create(owner);
            transform.x = 24f;
            transform.y = -12f;
            transform.rotationRad = 0.25f;

            TiledLayerComponent tiled = source.getMapper(
                    TiledLayerComponent.class).create(owner);
            tiled.projection = TiledProjection.ISO;
            tiled.tileWidth = 64;
            tiled.tileHeight = 32;
            tiled.mapWidthCells = 20;
            tiled.mapHeightCells = 20;
            tiled.chunkSize = 8;
            tiled.originX = 5f;
            tiled.originY = 7f;

            SpatialBlocksComponent blocks = source.getMapper(
                    SpatialBlocksComponent.class).create(owner);
            blocks.nextSpatialBlockId = 8;
            SpatialBlockData block = new SpatialBlockData();
            block.id = 7;
            block.structureId = 1;
            block.x = 2f;
            block.y = 3f;
            block.width = 2f;
            block.depth = 3f;
            block.altitude = 4f;
            blocks.blocks.add(block);

            source.getMapper(PhysicsBodyComponent.class).create(owner);
            PhysicsShapesComponent shapes = source.getMapper(
                    PhysicsShapesComponent.class).create(owner);
            PhysicsShapeData linked = new PhysicsShapeData();
            linked.physicsShapeId = 31;
            linked.spatialBlockId = 7;
            shapes.shapes.add(linked);

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

    private static void writeEmptyPrefab(FileHandle file) throws Exception {
        World source = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            WorldSerializationManager serialization =
                    source.getSystem(WorldSerializationManager.class);
            serialization.setSerializer(
                    new JsonArtemisSerializer(source).setUsePrototypes(false));
            try (OutputStream output = file.write(false)) {
                serialization.save(output, new RuntimePrefabFragment());
            }
        } finally {
            source.dispose();
        }
    }

    private static void writeEmptyEffect(FileHandle file) throws Exception {
        ParticleEffect effect = new ParticleEffect();
        effect.getEmitters().add(new ParticleEmitter());
        StringWriter writer = new StringWriter();
        effect.save(writer);
        file.writeString(writer.toString(), false, "UTF-8");
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
        private final CountingAssetManager assetManager;

        private EngineFixture(
                PixscapeEngine engine,
                RuntimeConfig config,
                SceneMetaRuntime sceneA,
                FileHandle projectDir,
                AtlasRuntimeService atlasService,
                MetricsBatch metricsBatch,
                CandidateWorldProbe worldProbe,
                CountingAssetManager assetManager) {
            this.engine = engine;
            this.config = config;
            this.sceneA = sceneA;
            this.projectDir = projectDir;
            this.atlasService = atlasService;
            this.metricsBatch = metricsBatch;
            this.worldProbe = worldProbe;
            this.assetManager = assetManager;
        }
    }

    private static final class CountingAssetManager extends AssetManager {
        private int loadCalls;

        private CountingAssetManager(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        public synchronized <T> void load(
                String fileName, Class<T> type, AssetLoaderParameters<T> parameters) {
            loadCalls++;
            super.load(fileName, type, parameters);
        }
    }

    private static final class CandidateWorldProbe {
        private int buildCount;
        private int processCount;
        private World latestWorld;
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
            probe.processCount++;
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
        public void loadBorrowed(String tag, TextureAtlas atlas) {
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

    private static final class EmptyAtlasLoader extends SynchronousAssetLoader<
            TextureAtlas, TextureAtlasLoader.TextureAtlasParameter> {

        EmptyAtlasLoader(FileHandleResolver resolver) {
            super(resolver);
        }

        @Override
        public TextureAtlas load(AssetManager manager, String fileName, FileHandle file,
                                 TextureAtlasLoader.TextureAtlasParameter parameter) {
            return new TextureAtlas();
        }

        @Override
        public Array<AssetDescriptor> getDependencies(String fileName, FileHandle file,
                TextureAtlasLoader.TextureAtlasParameter parameter) {
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
