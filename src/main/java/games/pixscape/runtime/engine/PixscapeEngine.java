package games.pixscape.runtime.engine;

import com.artemis.*;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.api.PixscapeAPI;
import games.pixscape.runtime.api.PixscapeApiImpl;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.*;
import games.pixscape.runtime.prefab.RuntimePrefabFragmentSpawner;
import games.pixscape.runtime.prefab.SpawnResult;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.BatchFactory;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.AnimationSystem;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.PhysicsSpatialFootprintSyncSystem;
import games.pixscape.runtime.system.RenderSubmitSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;

import java.util.function.Consumer;


public final class PixscapeEngine {

    public static final String RUNTIME_DIR_NAME = RuntimeFs.DIR_RUNTIME_PROJECT;
    private static final String PHYSICS_LOG_TAG = "PreviewPhysics";

    private FileHandle userRootDir;
    private FileHandle runtimeProjectDir;
    private PlatformTarget platformTarget = PlatformTarget.AUTO;

    private RuntimeConfig cfg;
    private boolean loaded;
    private boolean sceneLoaded;
    private SceneMetaRuntime activeSceneMeta;
    private int configuredLogLevel = Application.LOG_INFO;

    // World + rendering
    private World world;
    private OrthographicCamera worldCamera;

    private DynamicEntityRenderState dynamicEntityState;
    private LayerStateSOA layerState;
    private DrawList drawList;
    private FrameRenderQueue frameQueue;
    private VfxRenderState vfxState;
    private TiledMapRenderState tiledState;
    private MetricsBatch metricsBatch;
    private float ambientMulR = 1f;
    private float ambientMulG = 1f;
    private float ambientMulB = 1f;

    private RenderStats stats;
    private RenderStatsSink statsSink;
    private SystemProfiler systemProfiler = SystemProfilers.DISABLED;

    private AtlasRuntimeService atlasRuntimeService;
    private String defaultShaderName;

    private final IdentityRegistry identityRegistry = new IdentityRegistry();
    private final TagRegistry tagRegistry = new TagRegistry();
    private final AnimationRegistry animationRegistry = new AnimationRegistry();
    private final TileAnimationRegistry animatedTileRegistry = new TileAnimationRegistry();
    private RuntimeTilesetProfiles tilesetProfiles = RuntimeTilesetProfiles.empty();
    private PixscapeAPI publicApi;


    private Consumer<WorldConfigurationBuilder> configurationCustomizer;

    // Box2D (lazy)
    private Box2dWorldService box2dWorldService;
    private Box2dSyncSystem box2dSyncSystem;


    public PixscapeEngine() {
    }

    public PixscapeEngine setConfigurationCustomizer(Consumer<WorldConfigurationBuilder> customizer) {
        this.configurationCustomizer = customizer;
        return this;
    }

    public PixscapeEngine setSystemProfiler(SystemProfiler profiler) {
        this.systemProfiler = SystemProfilers.orDisabled(profiler);
        return this;
    }

    public SystemProfiler getSystemProfiler() {
        return systemProfiler;
    }

    /**
     * Sets the LibGDX runtime log level used by this engine.
     *
     * <p>Accepted values are {@link Application#LOG_NONE}, {@link Application#LOG_ERROR},
     * {@link Application#LOG_INFO}, and {@link Application#LOG_DEBUG}. The configured
     * level is stored on the engine and applied immediately when {@link Gdx#app} is
     * available; otherwise it is applied during runtime initialization.</p>
     *
     * @param logLevel LibGDX log level constant
     * @return this engine for fluent configuration
     * @throws IllegalArgumentException if {@code logLevel} is not a standard LibGDX level
     */
    public PixscapeEngine setLogLevel(int logLevel) {
        validateLogLevel(logLevel);
        this.configuredLogLevel = logLevel;
        applyConfiguredLogLevel();
        return this;
    }

    /**
     * Returns the LibGDX runtime log level configured for this engine.
     *
     * <p>The default is {@link Application#LOG_INFO}.</p>
     */
    public int getLogLevel() {
        return configuredLogLevel;
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /**
     * Loads {@code project.json} and initializes runtime state once.
     */
    public PixscapeEngine loadProject(FileHandle userRootDir) {
        if (userRootDir == null) throw new GdxRuntimeException("userRootDir is null");
        this.userRootDir = userRootDir;
        this.runtimeProjectDir = userRootDir.child(RUNTIME_DIR_NAME);

        if (loaded) return this;

        if (!runtimeProjectDir.exists()) {
            throw new GdxRuntimeException(
                    "Missing runtime project directory: " + runtimeProjectDir.path()
                            + " (expected '" + RUNTIME_DIR_NAME + "' under user root)"
            );
        }

        this.cfg = RuntimeProjectIO.loadProject(runtimeProjectDir);
        RuntimeProjectIO.loadAnimations(runtimeProjectDir, animationRegistry);
        RuntimeProjectIO.loadTileAnimations(runtimeProjectDir, animatedTileRegistry);
        tilesetProfiles = RuntimeProjectIO.loadTilesetProfiles(runtimeProjectDir);

        if (cfg.runtimeRootDir == null || isBlank(cfg.runtimeRootDir)) {
            cfg.runtimeRootDir = runtimeProjectDir.path();
        }

        initRuntime(cfg, runtimeProjectDir);

        loaded = true;

        return this;
    }

    /**
     * Loads a scene and rebuilds world state for that scene.
     */
    public PixscapeEngine loadScene(String sceneName) {
        if (!loaded) loadProject(userRootDir);

        String resolved = resolveSceneName(sceneName);
        SceneMetaRuntime meta = cfg.getSceneMeta(resolved);
        if (meta == null)
            throw new IllegalArgumentException("Unknown scene: " + resolved);

        FileHandle sceneFile = runtimeProjectDir
                .child(cfg.scenesDir)
                .child(RuntimeFs.withExt(RuntimeConfig.sceneDirName(meta), RuntimeFs.EXT_JSON));

        sceneLoaded = false;
        rebuildWorld(cfg, runtimeProjectDir, meta);
        loadSceneInternal(resolved);
        activeSceneMeta = meta;
        sceneLoaded = true;
        return this;
    }

    /**
     * Spawns an in-memory prefab fragment into the currently loaded scene.
     *
     * <p>The fragment is deserialized into the active Artemis world. All spawned
     * transforms are offset by {@code offsetX}/{@code offsetY}, spawned identities
     * receive fresh stable IDs, and asset references are resolved against the
     * currently loaded runtime atlases.</p>
     *
     * @param fragment prefab fragment to instantiate
     * @param offsetX  world-space X offset applied to spawned transforms
     * @param offsetY  world-space Y offset applied to spawned transforms
     * @return result containing all created entity IDs
     * @throws IllegalStateException if no world is initialized
     */
    public SpawnResult spawnPrefabFragment(SaveFileFormat fragment, float offsetX, float offsetY) {
        if (world == null || !sceneLoaded) {
            throw new IllegalStateException("No scene is active. Call loadScene() successfully first.");
        }
        if (activeSceneMeta == null) {
            throw new IllegalStateException(
                    "Active scene metadata is required to allocate physics shape IDs.");
        }
        RuntimePrefabFragmentSpawner spawner =
                new RuntimePrefabFragmentSpawner(identityRegistry, activeSceneMeta);
        SpawnResult result = spawner.spawn(world, fragment, offsetX, offsetY);
        resolveAssetRefsForEntities(world, atlasRuntimeService, result.createdEntityIds());
        return result;
    }

    /**
     * Loads and spawns an exported prefab fragment by name.
     *
     * <p>The prefab is resolved from {@code <runtimeProject>/<prefabsDir>/<name>.pixfragment.json}
     * and then spawned into the currently loaded scene.</p>
     *
     * @param name    prefab name without the {@code .pixfragment.json} extension
     * @param offsetX world-space X offset applied to spawned transforms
     * @param offsetY world-space Y offset applied to spawned transforms
     * @return result containing all created entity IDs
     * @throws IllegalStateException                      if the project or world is not initialized
     * @throws com.badlogic.gdx.utils.GdxRuntimeException if the prefab fragment file does not exist
     */
    public SpawnResult spawnPrefab(String name, float offsetX, float offsetY) {
        if (cfg == null) throw new IllegalStateException("Project is not loaded.");
        if (world == null) throw new IllegalStateException("World is not initialized. Call loadScene() first.");

        FileHandle fragmentFile = runtimeProjectDir
                .child(cfg.prefabsDir)
                .child(name + ".pixfragment.json");

        if (!fragmentFile.exists()) {
            throw new GdxRuntimeException("Prefab fragment not found: " + fragmentFile.path());
        }

        JsonValue root = new JsonReader().parse(fragmentFile);
        JsonArtemisSerializer serializer = new JsonArtemisSerializer(world);
        SaveFileFormat fragment = serializer.load(root, SaveFileFormat.class);

        return spawnPrefabFragment(fragment, offsetX, offsetY);
    }

    private void rebuildWorld(RuntimeConfig config,
                              FileHandle projectDir,
                              SceneMetaRuntime meta) {

        if (config == null) throw new IllegalArgumentException("config is null");
        if (projectDir == null) throw new IllegalArgumentException("projectDir is null");
        if (worldCamera == null) worldCamera = new OrthographicCamera();

        if (box2dSyncSystem != null) {
            box2dSyncSystem.setStepEnabled(false);
            box2dSyncSystem.setEnabled(false);
            box2dSyncSystem.setBox2d(null);
        }
        box2dSyncSystem = null;

        if (world != null) {
            world.dispose();
            world = null;
        }

        if (box2dWorldService != null) {
            box2dWorldService.dispose();
            box2dWorldService = null;
        }

        dynamicEntityState = new DynamicEntityRenderState();
        drawList = new DrawList();
        frameQueue = new FrameRenderQueue();
        vfxState = new VfxRenderState();
        tiledState = new TiledMapRenderState();

        GLCaps caps = GLCaps.detect();
        new RenderContext(dynamicEntityState, layerState, drawList, frameQueue, vfxState, tiledState, metricsBatch, caps);

        applyAmbientFromMeta(meta);
        int defaultShaderIdx = ShaderRegistry.indexOf(defaultShaderName);
        FileHandle effectsRoot = resolveEffectsRoot(projectDir, config);

        WorldBootstrapResult result =
                WorldConfigFactory.buildWorld(
                        worldCamera,
                        dynamicEntityState,
                        layerState,
                        drawList,
                        frameQueue,
                        vfxState,
                        tiledState,
                        stats,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        effectsRoot,
                        () -> new RenderSubmitSystem(
                                layerState,
                                frameQueue,
                                worldCamera,
                                ambientMulR,
                                ambientMulG,
                                ambientMulB,
                                metricsBatch,
                                stats,
                                statsSink
                        ),
                        meta,
                        0,
                        animatedTileRegistry,
                        tilesetProfiles,
                        systemProfiler,
                        null,
                        configurationCustomizer
                );

        world = result.getWorld();
        bindRuntimeRegistries(meta);

        box2dSyncSystem = world.getSystem(Box2dSyncSystem.class);
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setEnabled(false);
            box2dSyncSystem.setStepEnabled(false);
        }

    }

    /**
     * Updates ECS delta time; call once per frame before {@link #render()}.
     */
    public void update(float dt) {
        if (world == null) return;
        world.setDelta(dt);
    }

    /**
     * Processes the ECS world and flushes deferred atlas disposals.
     */
    public void render() {
        if (world == null) return;
        processWorld();
        if (atlasRuntimeService != null) {
            atlasRuntimeService.flushDeferredDisposals();
        }
    }

    /**
     * Resizes the runtime camera viewport.
     */
    public void resize(int w, int h) {
        if (worldCamera != null) {
            worldCamera.viewportWidth = w;
            worldCamera.viewportHeight = h;
            worldCamera.update();
        }
    }

    /**
     * Disposes world and runtime resources; the instance must be reinitialized afterwards.
     */
    public void dispose() {
        disposeWorldAndRuntime();

        loaded = false;
        sceneLoaded = false;
        activeSceneMeta = null;
    }

    public PixscapeEngine setWorldCamera(OrthographicCamera cam) {
        this.worldCamera = cam;
        return this;
    }

    public Box2dWorldService getBox2dWorldService() {
        return box2dWorldService;
    }

    public Box2dSyncSystem getBox2dSyncSystem() {
        return box2dSyncSystem;
    }

    public <T extends Component> ComponentMapper<T> mapper(Class<T> type) {
        if (world == null) throw new IllegalStateException("World is not initialized.");
        return world.getMapper(type);
    }

    public <T extends BaseSystem> T system(Class<T> type) {
        if (world == null) throw new IllegalStateException("World is not initialized.");
        return world.getSystem(type);
    }

    public IdentityRegistry getIdentityRegistry() {
        return identityRegistry;
    }

    public TagRegistry getTagRegistry() {
        return tagRegistry;
    }

    public ShaderRegistry getShaderRegistry() {
        return ShaderRegistry.getInstance();
    }

    public TileAnimationRegistry getAnimatedTileRegistry() {
        return animatedTileRegistry;
    }

    public AnimationRegistry getAnimationRegistry() {
        return animationRegistry;
    }

    public RuntimeTilesetProfiles getTilesetProfiles() {
        return tilesetProfiles;
    }

    /**
     * Returns the high-level API facade for runtime gameplay access.
     *
     * <p>The returned instance is cached for this engine instance and
     * coexists with direct engine/ECS access methods.</p>
     */
    public PixscapeAPI api() {
        if (publicApi == null) {
            publicApi = new PixscapeApiImpl(this);
        }
        return publicApi;
    }

    public int findEntityByStableId(int stableId) {
        IdentityRegistry registry = getIdentityRegistry();
        return registry.findByStableId(stableId);
    }

    public int firstEntityByName(String name) {
        IdentityRegistry registry = getIdentityRegistry();
        return registry.firstByName(name);
    }

    public void findEntitiesByName(String name, IntBag out) {
        if (out == null) {
            throw new IllegalArgumentException("out is null");
        }
        out.setSize(0);
        if (name == null || isBlank(name)) {
            return;
        }
        IdentityRegistry registry = getIdentityRegistry();
        if (registry == null) {
            return;
        }
        IntArray hits = registry.getByName(name);
        for (int i = 0; i < hits.size; i++) {
            out.add(hits.get(i));
        }
    }

    public int firstEntityByTag(String tag) {
        if (tag == null || isBlank(tag)) {
            return -1;
        }
        TagRegistry registry = getTagRegistry();
        return registry != null ? registry.first(tag) : -1;
    }

    public void findEntitiesByTag(String tag, IntBag out) {
        if (out == null) {
            throw new IllegalArgumentException("out is null");
        }
        out.setSize(0);
        if (tag == null || isBlank(tag)) {
            return;
        }
        TagRegistry registry = getTagRegistry();
        if (registry == null) {
            return;
        }
        IntArray hits = registry.get(tag);
        for (int i = 0; i < hits.size; i++) {
            out.add(hits.get(i));
        }
    }

    // ---------------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------------

    public RuntimeConfig config() {
        return cfg;
    }

    public SceneMetaRuntime getActiveSceneMeta() {
        return activeSceneMeta;
    }

    public FileHandle userRootDir() {
        return userRootDir;
    }

    public FileHandle runtimeProjectDir() {
        return runtimeProjectDir;
    }

    public World getWorld() {
        return world;
    }

    public OrthographicCamera getCamera() {
        return worldCamera;
    }

    public DynamicEntityRenderState getDynamicEntityRenderState() {
        return dynamicEntityState;
    }

    public LayerStateSOA getLayerState() {
        return layerState;
    }

    public DrawList getDrawList() {
        return drawList;
    }

    public FrameRenderQueue getFrameQueue() {
        return frameQueue;
    }

    public VfxRenderState getVfxState() {
        return vfxState;
    }

    public MetricsBatch getMetricsBatch() {
        return metricsBatch;
    }

    public RenderStats getRenderStats() {
        return stats;
    }

    public RenderStatsSink getRenderStatsSink() {
        return statsSink;
    }

    public AtlasRuntimeService getAtlasRuntimeService() {
        return atlasRuntimeService;
    }

    public String getCurrentSceneAtlasTag() {
        if (cfg == null) return "main";
        SceneMetaRuntime meta = cfg.getCurrentSceneMeta();
        String sceneTag = RuntimeConfig.sceneDirName(meta);
        return sceneTag == null || isBlank(sceneTag) ? "main" : sceneTag;
    }

    public String getDefaultShaderName() {
        return defaultShaderName;
    }

    // ---------------------------------------------------------------------
    // Internal init / reset
    // ---------------------------------------------------------------------

    /**
     * Disposes world and GPU-side runtime resources.
     */
    private void disposeWorldAndRuntime() {
        // World first (systems may touch services)
        if (world != null) {
            world.dispose();
            world = null;
        }

        // Box2D service after world dispose
        if (box2dWorldService != null) {
            box2dWorldService.dispose();
            box2dWorldService = null;
        }
        box2dSyncSystem = null;

        if (metricsBatch != null) {
            metricsBatch.close();
            metricsBatch = null;
        }

        if (atlasRuntimeService != null) {
            atlasRuntimeService.unloadAll();
            atlasRuntimeService = null;
        }

        dynamicEntityState = null;
        layerState = null;
        drawList = null;
        frameQueue = null;
        vfxState = null;
        stats = null;
        statsSink = null;
        defaultShaderName = null;
        identityRegistry.bind(null, null);
        tagRegistry.bind(null);
    }

    /**
     * Fully initializes runtime resources and creates an empty world.
     */
    private void initRuntime(RuntimeConfig config, FileHandle projectDir) {
        applyConfiguredLogLevel();
        disposeWorldAndRuntime();

        if (config == null) throw new IllegalArgumentException("config is null");
        if (projectDir == null) throw new IllegalArgumentException("projectDir is null");

        if (worldCamera == null) worldCamera = new OrthographicCamera();

        ShaderRegistry.initDefaults(projectDir, config.shadersDir);

        dynamicEntityState = new DynamicEntityRenderState();
        layerState = new LayerStateSOA();
        drawList = new DrawList();
        frameQueue = new FrameRenderQueue();
        vfxState = new VfxRenderState();
        tiledState = new TiledMapRenderState();

        GLCaps caps = GLCaps.detect();
        atlasRuntimeService = new AtlasRuntimeService();
        BatchFactory.Result r = BatchFactory.create(atlasRuntimeService, caps);
        metricsBatch = r.batch;
        defaultShaderName = r.defaultShaderName;

        stats = new RenderStats();
        statsSink = new RenderStatsSink(0.5f);

        new RenderContext(dynamicEntityState, layerState, drawList, frameQueue, vfxState, tiledState, metricsBatch, caps);

        layerState.setCapacity(32);
        SceneMetaRuntime meta = config.getCurrentSceneMeta();
        applyAmbientFromMeta(meta);

        int defaultShaderIdx = ShaderRegistry.indexOf(defaultShaderName);
        FileHandle effectsRoot = resolveEffectsRoot(projectDir, config);

        WorldBootstrapResult result =
                WorldConfigFactory.buildWorld(
                        worldCamera,
                        dynamicEntityState,
                        layerState,
                        drawList,
                        frameQueue,
                        vfxState,
                        tiledState,
                        stats,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        null,
                        () -> new RenderSubmitSystem(
                                layerState,
                                frameQueue,
                                worldCamera,
                                ambientMulR,
                                ambientMulG,
                                ambientMulB,
                                metricsBatch,
                                stats,
                                statsSink
                        ),
                        null,
                        0,
                        animatedTileRegistry,
                        tilesetProfiles,
                        systemProfiler,
                        null,
                        configurationCustomizer
                );

        world = result.getWorld();
        bindRuntimeRegistries(meta);
        rebuildRuntimeRegistries();

        box2dSyncSystem = world.getSystem(Box2dSyncSystem.class);
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setEnabled(false);
        }
        logRuntimeInitialized(caps);

        sceneLoaded = false;
    }


    /**
     * Initializes a runtime with default configuration and no scene file.
     */
    public PixscapeEngine initEmptyRuntime() {
        applyConfiguredLogLevel();
        this.cfg = new RuntimeConfig();

        ShaderRegistry.initDefaults(null, null);

        if (worldCamera == null) {
            worldCamera = new OrthographicCamera();
        }

        dynamicEntityState = new DynamicEntityRenderState();
        layerState = new LayerStateSOA();
        drawList = new DrawList();
        frameQueue = new FrameRenderQueue();
        vfxState = new VfxRenderState();
        tiledState = new TiledMapRenderState();

        GLCaps caps = GLCaps.detect();
        atlasRuntimeService = new AtlasRuntimeService();
        BatchFactory.Result r = BatchFactory.create(atlasRuntimeService, caps);
        metricsBatch = r.batch;
        defaultShaderName = r.defaultShaderName;

        stats = new RenderStats();
        statsSink = new RenderStatsSink(0.5f);

        new RenderContext(dynamicEntityState, layerState, drawList, frameQueue, vfxState, tiledState, metricsBatch, caps);

        layerState.setCapacity(32);
        applyAmbientFromMeta(null);

        int defaultShaderIdx = ShaderRegistry.indexOf(defaultShaderName);
        WorldBootstrapResult result =
                WorldConfigFactory.buildWorld(
                        worldCamera,
                        dynamicEntityState,
                        layerState,
                        drawList,
                        frameQueue,
                        vfxState,
                        tiledState,
                        stats,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        null,
                        () -> new RenderSubmitSystem(
                                layerState,
                                frameQueue,
                                worldCamera,
                                ambientMulR,
                                ambientMulG,
                                ambientMulB,
                                metricsBatch,
                                stats,
                                statsSink
                        ),
                        null,
                        0,
                        animatedTileRegistry,
                        tilesetProfiles,
                        systemProfiler,
                        null,
                        configurationCustomizer
                );

        world = result.getWorld();
        bindRuntimeRegistries(null);
        rebuildRuntimeRegistries();

        logRuntimeInitialized(caps);

        return this;
    }


    /**
     * Rebuilds the ECS world while keeping existing render resources.
     */
    public void rebuildWorldOnly(RuntimeConfig config, FileHandle projectDir) {
        if (config == null) throw new IllegalArgumentException("config is null");
        if (projectDir == null) throw new IllegalArgumentException("projectDir is null");
        if (worldCamera == null) worldCamera = new OrthographicCamera();
        if (dynamicEntityState == null || layerState == null || drawList == null || frameQueue == null || vfxState == null
                || tiledState == null
                || metricsBatch == null || stats == null || statsSink == null) {
            initRuntime(config, projectDir);
            return;
        }

        SceneMetaRuntime meta = config.getCurrentSceneMeta();
        applyAmbientFromMeta(meta);
        int defaultShaderIdx = (defaultShaderName != null) ? ShaderRegistry.indexOf(defaultShaderName) : 0;
        FileHandle effectsRoot = resolveEffectsRoot(projectDir, config);

        WorldBootstrapResult result =
                WorldConfigFactory.buildWorld(
                        worldCamera,
                        dynamicEntityState,
                        layerState,
                        drawList,
                        frameQueue,
                        vfxState,
                        tiledState,
                        stats,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        effectsRoot,
                        () -> new RenderSubmitSystem(
                                layerState,
                                frameQueue,
                                worldCamera,
                                ambientMulR,
                                ambientMulG,
                                ambientMulB,
                                metricsBatch,
                                stats,
                                statsSink
                        ),
                        null,
                        0,
                        animatedTileRegistry,
                        tilesetProfiles,
                        systemProfiler,
                        null,
                        configurationCustomizer
                );

        world = result.getWorld();
        bindRuntimeRegistries(meta);
        rebuildRuntimeRegistries();

        box2dSyncSystem = world.getSystem(Box2dSyncSystem.class);
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setEnabled(false);
            box2dSyncSystem.setStepEnabled(false);
        }
        Gdx.app.debug(
                PHYSICS_LOG_TAG,
                "rebuildWorldOnly worldId=" + System.identityHashCode(world)
                        + " box2dSyncSystem=" + (box2dSyncSystem != null)
        );
    }

    // ---------------------------------------------------------------------
    // Scene loading
    // ---------------------------------------------------------------------

    private void loadSceneInternal(String sceneName) {
        if (world == null) return;
        if (cfg == null) throw new IllegalStateException("loadProject() must be called before loadScene().");

        String resolvedName = resolveSceneName(sceneName);
        SceneMetaRuntime meta = cfg.getSceneMeta(resolvedName);
        if (meta == null) throw new IllegalArgumentException("Unknown scene: " + resolvedName);

        String sceneTag = RuntimeConfig.sceneDirName(meta);
        if (sceneTag == null || isBlank(sceneTag)) {
            throw new IllegalStateException("Cannot resolve logical scene name for: " + resolvedName);
        }
        applyPhysicsFromScene(meta, false);

        FileHandle sceneFile = runtimeProjectDir.child(cfg.scenesDir).child(RuntimeFs.withExt(sceneTag, RuntimeFs.EXT_JSON));

        SceneLoader.loadScene(world, sceneFile, false, meta);
        processWorld();

        rebuildRuntimeRegistries();
        rebuildTiledLayersRuntime(meta);
        PhysicsService.rebuildPreparedBodyCaches(world);
        applyPhysicsFromScene(meta, true);

        RuntimeSceneAtlasLoader.loadSceneAtlas(
                cfg,
                resolvedName,
                runtimeProjectDir,
                atlasRuntimeService
        );
        rebindAtlas(sceneTag);
        forceFullDirtyAfterLoad();
    }

    private void rebuildTiledLayersRuntime(SceneMetaRuntime meta) {
        ComponentMapper<TiledLayerComponent> mTiled =
                world.getMapper(TiledLayerComponent.class);
        ComponentMapper<LayerComponent> mLayer =
                world.getMapper(LayerComponent.class);
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] dataArr = bag.getData();

        for (int i = 0; i < bag.size(); i++) {

            int e = dataArr[i];
            TiledLayerComponent tiled = mTiled.get(e);
            if (tiled == null) continue;
            LayerComponent layer = mLayer.getSafe(e, null);

            tiled.ensureSparseTileStorageConsistency();

            tiled.data = new TiledMapLayerData(
                    tiled.mapWidthCells,
                    tiled.mapHeightCells,
                    (int) meta.tileWidth,
                    (int) meta.tileHeight,
                    meta.chunkSize,
                    meta.tiledProjection
            );
            tiled.data.originX = tiled.originX;
            tiled.data.originY = tiled.originY;
            tiled.data.spatialEnabled = tiled.spatialEnabled || (layer != null && layer.spatialEnabled);
            tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
            tiled.data.defaultTileHeight = tiled.defaultTileHeight;

            tiled.data.beginContentMutation();
            try {
            for (int t = 0; t < tiled.tileXs.size; t++) {
                int gx = tiled.tileXs.get(t);
                int gy = tiled.tileYs.get(t);
                int assetId = tiled.tileAssetIds.get(t);
                byte flags = tiled.tileTransformFlags.get(t);
                float altitude = tiled.sparseTileAltitude(t);
                float height = tiled.sparseTileHeight(t);
                int spatialFlags = tiled.sparseTileSpatialFlags(t);

                tiled.data.setTile(gx, gy, assetId, flags);
                if (tiled.hasSparseSpatialOverride(t)) {
                    tiled.data.setTileSpatialOverride(gx, gy, altitude, height, spatialFlags);
                }

                int cx = gx / tiled.data.chunkSize;
                int cy = gy / tiled.data.chunkSize;

                TileChunk chunk = tiled.data.getChunk(cx, cy);

                if (chunk != null) {
                    int lx = gx - (cx * tiled.data.chunkSize);
                    int ly = gy - (cy * tiled.data.chunkSize);
                    TileAnimationStateSupport.syncWorldCell(chunk, lx, ly, animatedTileRegistry);
                }
            }
            } finally {
                tiled.data.endContentMutation();
            }

            tiled.data.markAllChunksContentDirty();
        }
    }

    private void forceFullDirtyAfterLoad() {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty == null) return;

        IntBag sprites = world.getAspectSubscriptionManager()
                .get(Aspect.all(
                        OrientedBoundsComponent.class,
                        TextureRegionComponent.class,
                        RenderMaterialComponent.class,
                        EntityIndexComponent.class
                ))
                .getEntities();

        int[] data = sprites.getData();
        for (int i = 0, n = sprites.size(); i < n; i++) {
            int e = data[i];
            dirty.mark(e, DirtyBits.EVERYTHING);
            dirty.geometry(e, GeometryDirty.ALL);
        }
    }

    private void rebindAtlas(String sceneTag) {
        if (world == null) return;
        if (atlasRuntimeService == null) {
            throw new IllegalStateException("Atlas runtime service is not initialized.");
        }
        if (sceneTag == null || isBlank(sceneTag)) {
            throw new IllegalStateException("Scene tag must not be null or blank.");
        }
        TextureAtlas atlas = atlasRuntimeService.getAtlas(sceneTag);

        if (atlas == null) {
            throw new IllegalStateException("No atlas loaded for scene: " + sceneTag);
        }
        atlasRuntimeService.clearRegionCache();
        AtlasRuntimeService.TextureArrayBundle previous = atlasRuntimeService.bundle(sceneTag);
        AtlasRuntimeService.TextureArrayBundle bundle = atlasRuntimeService.rebuildBundle(sceneTag);

        if (metricsBatch != null) {
            metricsBatch.setTextureArrayBundle(bundle);
        }

        if (atlasRuntimeService != null && previous != null && previous != bundle) {
            atlasRuntimeService.deferDispose(previous);
        }

        AnimationSystem animationSystem = world.getSystem(AnimationSystem.class);
        if (animationSystem != null) {
            animationSystem.clearBindingCache();
        }

        rebuildSceneAssets(sceneTag);
        markAllTiledChunksContentDirty();

        SceneLoader.forceFullRenderDirty(world);
    }

    private void markAllTiledChunksContentDirty() {
        if (world == null) return;

        ComponentMapper<TiledLayerComponent> mTiled = world.getMapper(TiledLayerComponent.class);
        IntBag tiledBag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] data = tiledBag.getData();
        for (int i = 0, n = tiledBag.size(); i < n; i++) {
            TiledLayerComponent tiled = mTiled.get(data[i]);
            if (tiled == null || tiled.data == null) continue;
            tiled.data.markAllChunksContentDirty();
        }
    }

    private void rebuildSceneAssets(String sceneTag) {

        ComponentMapper<AssetRefComponent> mSrc = world.getMapper(AssetRefComponent.class);
        ComponentMapper<TextureRegionComponent> mTR = world.getMapper(TextureRegionComponent.class);
        ComponentMapper<RenderMaterialComponent> mMat = world.getMapper(RenderMaterialComponent.class);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(
                        AssetRefComponent.class,
                        TextureRegionComponent.class,
                        RenderMaterialComponent.class))
                .getEntities();

        int[] data = bag.getData();

        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];

            AssetRefComponent src = mSrc.get(e);
            TextureRegionComponent tr = mTR.get(e);
            RenderMaterialComponent mat = mMat.get(e);

            if (src.assetId < 0)
                throw new IllegalStateException(
                        "AssetRef entity without assetId during rebuild: e=" + e);

            String atlasTag = src.atlasTag;
            if (isBlank(atlasTag)) {
                throw new IllegalStateException("AssetRef atlasTag not set for entity " + e);
            }

            AtlasRuntimeService.CachedRegion region = atlasRuntimeService.resolveCached(src.assetId, atlasTag);

            if (region == null) {
                tr.valid = false;
                mat.textureHandle = 0;
                continue;
            }

            tr.u1 = region.u1;
            tr.v1 = region.v1;
            tr.u2 = region.u2;
            tr.v2 = region.v2;
            tr.pixW = region.pixW;
            tr.pixH = region.pixH;
            tr.valid = true;

            mat.textureHandle = region.textureHandle;

            if (dirty != null) {
                dirty.material(e);
            }
        }
    }

    static void resolveAssetRefsForEntities(World world, AtlasRuntimeService atlasRuntimeService, IntBag entityIds) {
        if (world == null || atlasRuntimeService == null || entityIds == null || entityIds.isEmpty()) return;

        ComponentMapper<AssetRefComponent> mSrc = world.getMapper(AssetRefComponent.class);
        ComponentMapper<TextureRegionComponent> mTR = world.getMapper(TextureRegionComponent.class);
        ComponentMapper<RenderMaterialComponent> mMat = world.getMapper(RenderMaterialComponent.class);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        for (int i = 0; i < entityIds.size(); i++) {
            int e = entityIds.get(i);

            AssetRefComponent src = mSrc.getSafe(e, null);
            TextureRegionComponent tr = mTR.getSafe(e, null);
            RenderMaterialComponent mat = mMat.getSafe(e, null);
            if (src == null || tr == null || mat == null) continue;

            if (src.assetId < 0) {
                throw new IllegalStateException("AssetRef entity without assetId during prefab resolve: e=" + e);
            }
            String atlasTag = src.atlasTag;
            if (isBlank(atlasTag)) {
                throw new IllegalStateException("AssetRef atlasTag not set for entity " + e);
            }

            AtlasRuntimeService.CachedRegion region = atlasRuntimeService.resolveCached(src.assetId, atlasTag);
            if (region == null) {
                tr.valid = false;
                mat.textureHandle = 0;
            } else {
                tr.u1 = region.u1;
                tr.v1 = region.v1;
                tr.u2 = region.u2;
                tr.v2 = region.v2;
                tr.pixW = region.pixW;
                tr.pixH = region.pixH;
                tr.valid = true;
                mat.textureHandle = region.textureHandle;
            }

            if (dirty != null) {
                dirty.mark(e, DirtyBits.MATERIAL | DirtyBits.GEOMETRY | DirtyBits.LAYER | DirtyBits.ORDER);
            }
        }
    }

    private String resolveSceneName(String sceneName) {
        if (sceneName != null && !isBlank(sceneName)) return sceneName;

        String cur = cfg.currentSceneName;
        if (cur != null && cfg.getSceneMeta(cur) != null) return cur;

        Array<String> names = cfg.getSceneNamesSorted();
        if (names != null && names.size > 0) return names.first();

        throw new IllegalStateException("RuntimeConfig has no scenes.");
    }

    private void applyAmbientFromMeta(SceneMetaRuntime meta) {
        if (meta == null) {
            ambientMulR = 1f;
            ambientMulG = 1f;
            ambientMulB = 1f;
            return;
        }
        ambientMulR = meta.ambientMulR;
        ambientMulG = meta.ambientMulG;
        ambientMulB = meta.ambientMulB;
    }

    private void applyPhysicsFromScene(SceneMetaRuntime meta, boolean activate) {
        if (box2dSyncSystem == null) {
            Gdx.app.debug(PHYSICS_LOG_TAG, "applyPhysicsFromScene: box2dSyncSystem missing");
            return;
        }

        if (meta == null || !meta.physicsEnabled) {
            box2dSyncSystem.setEnabled(false);
            box2dSyncSystem.setStepEnabled(false);
            Gdx.app.debug(PHYSICS_LOG_TAG, "applyPhysicsFromScene: physics disabled (meta=" + (meta != null) + ")");
            return;
        }

        float ppm = meta.pixelsPerMeter > 0f ? meta.pixelsPerMeter : 100f;
        if (box2dWorldService == null || box2dWorldService.isDisposed() || box2dWorldService.world == null) {
            box2dWorldService = new Box2dWorldService(
                    ppm,
                    new Vector2(meta.gravityX, meta.gravityY),
                    meta.doSleep
            );
            box2dSyncSystem.setBox2d(box2dWorldService);
        } else {
            box2dWorldService.setPpm(ppm);
            box2dWorldService.setGravity(meta.gravityX, meta.gravityY);
            box2dWorldService.setDoSleep(meta.doSleep);
        }
        PhysicsSpatialFootprintSyncSystem footprintSync =
                world.getSystem(PhysicsSpatialFootprintSyncSystem.class);
        if (footprintSync == null) {
            throw new IllegalStateException(
                    "PhysicsSpatialFootprintSyncSystem is required "
                            + "to apply scene pixelsPerMeter.");
        }
        footprintSync.setPixelsPerMeter(ppm);

        box2dSyncSystem.setSceneMeta(meta);
        box2dSyncSystem.setEnabled(activate);
        box2dSyncSystem.setStepEnabled(activate);
        Gdx.app.debug(
                PHYSICS_LOG_TAG,
                "applyPhysicsFromScene: enabled ppm=" + meta.pixelsPerMeter
                        + " gravity=(" + meta.gravityX + "," + meta.gravityY + ")"
                        + " doSleep=" + meta.doSleep
                        + " stepEnabled=" + box2dSyncSystem.isStepEnabled()
        );
    }

    private void applyConfiguredLogLevel() {
        if (Gdx.app != null) {
            Gdx.app.setLogLevel(configuredLogLevel);
        }
    }

    private static void validateLogLevel(int logLevel) {
        if (logLevel == Application.LOG_NONE
                || logLevel == Application.LOG_ERROR
                || logLevel == Application.LOG_INFO
                || logLevel == Application.LOG_DEBUG) {
            return;
        }
        throw new IllegalArgumentException("Unsupported LibGDX log level: " + logLevel);
    }

    private void logRuntimeInitialized(GLCaps caps) {
        if (Gdx.app == null) return;
        Gdx.app.log(
                "PixscapeRuntime",
                "Runtime initialized: target=" + ShaderRegistry.getResolvedPlatformTarget()
                        + ", shaderVariant=" + ShaderRegistry.getCurrentShaderVariant()
                        + ", defaultShader=" + defaultShaderName
                        + ", caps=" + caps
        );
    }

    private void bindRuntimeRegistries(SceneMetaRuntime sceneMeta) {
        identityRegistry.bind(world, sceneMeta);
        tagRegistry.bind(world);
    }

    private void processWorld() {
        if (systemProfiler.enabled()) {
            systemProfiler.beginFrame();
        }
        world.process();
    }

    private void rebuildRuntimeRegistries() {
        identityRegistry.rebuild();
        tagRegistry.rebuild();
    }

    private static FileHandle resolveEffectsRoot(FileHandle projectDir, RuntimeConfig config) {
        String effectsDir = (config != null && config.effectsDir != null && !isBlank(config.effectsDir))
                ? config.effectsDir
                : "effects";
        return (projectDir != null) ? projectDir.child(effectsDir) : null;
    }


    private static boolean isBlank(String s) {
        if (s == null || s.length() == 0) return true;

        for (int i = 0; i < s.length(); i++) {
            if (!Character.isWhitespace(s.charAt(i))) {
                return false;
            }
        }

        return true;
    }

    public boolean isLoaded() {
        return loaded;
    }

    public PixscapeEngine setPlatformTarget(PlatformTarget target) {
        if (loaded) {
            throw new IllegalStateException("PlatformTarget must be set before loadProject().");
        }

        this.platformTarget = target != null ? target : PlatformTarget.AUTO;
        return this;
    }

    public PlatformTarget getPlatformTarget() {
        return platformTarget;
    }
}
