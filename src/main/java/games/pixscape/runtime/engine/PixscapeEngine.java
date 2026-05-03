package games.pixscape.runtime.engine;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;
import com.badlogic.gdx.utils.IntMap;
import games.pixscape.runtime.api.PixscapeAPI;
import games.pixscape.runtime.api.PixscapeApiImpl;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.configuration.PlatformTarget;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.*;
import games.pixscape.runtime.prefab.PrefabLoader;
import games.pixscape.runtime.prefab.PrefabSpawnService;
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
import games.pixscape.runtime.system.RenderSubmitSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledSoaAllocator;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;

import java.util.function.Consumer;

import static games.pixscape.runtime.loading.WorldConfigFactory.DEFAULT_TILED_BUDGET;

public final class PixscapeEngine {

    public static final String RUNTIME_DIR_NAME = RuntimeFs.DIR_RUNTIME_PROJECT;
    private static final String PHYSICS_LOG_TAG = "PreviewPhysics";

    private FileHandle userRootDir;
    private FileHandle runtimeProjectDir;
    private PlatformTarget platformTarget = PlatformTarget.AUTO;

    private RuntimeConfig cfg;
    private boolean loaded;
    private boolean sceneLoaded;

    // World + rendering
    private World world;
    private OrthographicCamera worldCamera;

    private RenderStateSOA renderState;
    private LayerStateSOA layerState;
    private DrawList drawList;
    private MetricsBatch metricsBatch;
    private float ambientMulR = 1f;
    private float ambientMulG = 1f;
    private float ambientMulB = 1f;

    private RenderStats stats;
    private RenderStatsSink statsSink;

    private AtlasRuntimeService atlasRuntimeService;
    private String defaultShaderName;

    private final IdentityRegistry identityRegistry = new IdentityRegistry();
    private final TagRegistry tagRegistry = new TagRegistry();
    private final TileAnimationRegistry animatedTileRegistry = new TileAnimationRegistry();
    private PixscapeAPI publicApi;
    private PrefabSpawnService prefabSpawnService;


    private Consumer<WorldConfigurationBuilder> configurationCustomizer;

    // Box2D (lazy)
    private Box2dWorldService box2dWorldService;
    private Box2dSyncSystem box2dSyncSystem;
    private boolean loggedFirstUpdate;
    private boolean loggedFirstRender;
    private int runtimeTiledStart;
    private int runtimeTiledEnd;


    public PixscapeEngine() {
    }

    public PixscapeEngine setConfigurationCustomizer(Consumer<WorldConfigurationBuilder> customizer) {
        this.configurationCustomizer = customizer;
        return this;
    }

    // ---------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------

    /** Loads {@code project.json} and initializes runtime state once. */
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
        RuntimeProjectIO.loadTileAnimations(runtimeProjectDir, animatedTileRegistry);

        if (cfg.runtimeRootDir == null || cfg.runtimeRootDir.isBlank()) {
            cfg.runtimeRootDir = runtimeProjectDir.path();
        }

        initRuntime(cfg, runtimeProjectDir);

        loaded = true;

        return this;
    }

    /** Loads a scene and rebuilds world state for that scene. */
    public PixscapeEngine loadScene(String sceneName) {
        if (!loaded) loadProject(userRootDir);

        String resolved = resolveSceneName(sceneName);
        SceneMetaRuntime meta = cfg.getSceneMeta(resolved);
        if (meta == null)
            throw new IllegalArgumentException("Unknown scene: " + resolved);

        FileHandle sceneFile = runtimeProjectDir
                .child(cfg.scenesDir)
                .child(RuntimeFs.withExt(RuntimeConfig.sceneDirName(meta), RuntimeFs.EXT_JSON));

        int tiledLayerCount = SceneLoader.countTiledLayers(sceneFile);

        int tiledBudget = DEFAULT_TILED_BUDGET * tiledLayerCount;

        if (!meta.tiledEnabled) {
            tiledBudget = 0;
        }

        rebuildWorldWithBudget(cfg, runtimeProjectDir, meta, tiledBudget);

        loadSceneInternal(resolved);

        sceneLoaded = true;
        return this;
    }

    private void rebuildWorldWithBudget(RuntimeConfig config,
                                        FileHandle projectDir,
                                        SceneMetaRuntime meta,
                                        int tiledBudget) {

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

        renderState = new RenderStateSOA();
        drawList    = new DrawList();

        GLCaps caps = GLCaps.detect();
        new RenderContext(renderState, layerState, drawList, metricsBatch, caps);

        applyAmbientFromMeta(meta);
        int defaultShaderIdx = ShaderRegistry.indexOf(defaultShaderName);
        FileHandle effectsRoot = resolveEffectsRoot(projectDir, config);

        WorldBootstrapResult result =
                WorldConfigFactory.buildWorld(
                        worldCamera,
                        renderState,
                        layerState,
                        drawList,
                        stats,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        effectsRoot,
                        () -> new RenderSubmitSystem(
                                renderState,
                                layerState,
                                drawList,
                                worldCamera,
                                ambientMulR,
                                ambientMulG,
                                ambientMulB,
                                metricsBatch,
                                stats,
                                statsSink
                        ),
                        meta,
                        tiledBudget,
                        animatedTileRegistry,
                        configurationCustomizer
                );

        world = result.getWorld();
        prefabSpawnService = null;
        runtimeTiledStart = result.getTiledStart();
        runtimeTiledEnd = result.getTiledEnd();
        bindRuntimeRegistries();

        box2dSyncSystem = world.getSystem(Box2dSyncSystem.class);
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setEnabled(false);
            box2dSyncSystem.setStepEnabled(false);
        }

        loggedFirstUpdate = false;
        loggedFirstRender = false;
    }

    /** Updates ECS delta time; call once per frame before {@link #render()}. */
    public void update(float dt) {
        if (world == null) return;
        world.setDelta(dt);
        if (!loggedFirstUpdate) {
            Gdx.app.log(PHYSICS_LOG_TAG, "update dt=" + dt + " worldId=" + System.identityHashCode(world));
            loggedFirstUpdate = true;
        }
    }

    /** Processes the ECS world and flushes deferred atlas disposals. */
    public void render() {
        if (world == null) return;
        if (!loggedFirstRender) {
            Gdx.app.log(PHYSICS_LOG_TAG, "render worldId=" + System.identityHashCode(world));
            loggedFirstRender = true;
        }
        world.process();
        if (atlasRuntimeService != null) {
            atlasRuntimeService.flushDeferredDisposals();
        }
    }

    /** Resizes the runtime camera viewport. */
    public void resize(int w, int h) {
        if (worldCamera != null) {
            worldCamera.viewportWidth = w;
            worldCamera.viewportHeight = h;
            worldCamera.update();
        }
    }

    /** Disposes world and runtime resources; the instance must be reinitialized afterwards. */
    public void dispose() {
        disposeWorldAndRuntime();

        loaded = false;
        sceneLoaded = false;
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

    /**
     * Returns the high-level API facade for runtime gameplay access.
     *
     * <p>The returned instance is cached for this engine instance and
     * coexists with direct engine/ECS access methods.</p>
     */
    public PrefabSpawnService prefabs() {
        if (prefabSpawnService == null) {
            if (world == null || cfg == null || runtimeProjectDir == null) {
                throw new IllegalStateException("Engine must be loaded before using prefabs API.");
            }
            prefabSpawnService = new PrefabSpawnService(world, new PrefabLoader(), runtimeProjectDir, cfg, identityRegistry);
        }
        return prefabSpawnService;
    }

    public PixscapeAPI api() {
        if (publicApi == null) {
            publicApi = new PixscapeApiImpl(this);
        }
        return publicApi;
    }

    public int findEntityByStableId(long stableId) {
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
        if (name == null || name.isBlank()) {
            return;
        }
        IdentityRegistry registry = getIdentityRegistry();
        if (registry == null) {
            return;
        }
        var hits = registry.getByName(name);
        for (int i = 0; i < hits.size; i++) {
            out.add(hits.get(i));
        }
    }

    public int firstEntityByTag(String tag) {
        if (tag == null || tag.isBlank()) {
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
        if (tag == null || tag.isBlank()) {
            return;
        }
        TagRegistry registry = getTagRegistry();
        if (registry == null) {
            return;
        }
        var hits = registry.get(tag);
        for (int i = 0; i < hits.size; i++) {
            out.add(hits.get(i));
        }
    }

    // ---------------------------------------------------------------------
    // Getters
    // ---------------------------------------------------------------------

    public RuntimeConfig config() { return cfg; }
    public FileHandle userRootDir() { return userRootDir; }
    public FileHandle runtimeProjectDir() { return runtimeProjectDir; }

    public World getWorld() { return world; }
    public OrthographicCamera getCamera() { return worldCamera; }
    public RenderStateSOA getRenderState() { return renderState; }
    public LayerStateSOA getLayerState() { return layerState; }
    public DrawList getDrawList() { return drawList; }
    public MetricsBatch getMetricsBatch() { return metricsBatch; }
    public RenderStats getRenderStats() { return stats; }
    public RenderStatsSink getRenderStatsSink() { return statsSink; }
    public AtlasRuntimeService getAtlasRuntimeService() { return atlasRuntimeService; }
    public String getDefaultShaderName() { return defaultShaderName; }

    // ---------------------------------------------------------------------
    // Internal init / reset
    // ---------------------------------------------------------------------

    /** Disposes world and GPU-side runtime resources. */
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

        renderState = null;
        layerState = null;
        drawList = null;
        runtimeTiledStart = 0;
        runtimeTiledEnd = 0;
        stats = null;
        statsSink = null;
        defaultShaderName = null;
        identityRegistry.bind(null);
        tagRegistry.bind(null);
    }

    /** Fully initializes runtime resources and creates an empty world. */
    private void initRuntime(RuntimeConfig config, FileHandle projectDir) {
        disposeWorldAndRuntime();

        if (config == null) throw new IllegalArgumentException("config is null");
        if (projectDir == null) throw new IllegalArgumentException("projectDir is null");

        if (worldCamera == null) worldCamera = new OrthographicCamera();

        ShaderRegistry.initDefaults(platformTarget, projectDir, config.shadersDir);

        renderState = new RenderStateSOA();
        layerState  = new LayerStateSOA();
        drawList    = new DrawList();

        GLCaps caps             = GLCaps.detect();
        RenderSettings settings = RenderSettings.defaultEditor(caps);

        atlasRuntimeService = new AtlasRuntimeService();
        BatchFactory.Result r = BatchFactory.create(atlasRuntimeService, settings, caps);
        metricsBatch      = r.batch;
        defaultShaderName = r.defaultShaderName;

        stats     = new RenderStats();
        statsSink = new RenderStatsSink(0.5f);

        new RenderContext(renderState, layerState, drawList, metricsBatch, caps);

        layerState.setCapacity(32);
        SceneMetaRuntime meta = config.getCurrentSceneMeta();
        applyAmbientFromMeta(meta);

        int defaultShaderIdx = ShaderRegistry.indexOf(defaultShaderName);
        FileHandle effectsRoot = resolveEffectsRoot(projectDir, config);

        WorldBootstrapResult result =
                WorldConfigFactory.buildWorld(
                        worldCamera,
                        renderState,
                        layerState,
                        drawList,
                        stats,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        null,
                        () -> new RenderSubmitSystem(
                                renderState,
                                layerState,
                                drawList,
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
                        configurationCustomizer
                );

        world = result.getWorld();
        prefabSpawnService = null;
        runtimeTiledStart = result.getTiledStart();
        runtimeTiledEnd = result.getTiledEnd();
        bindRuntimeRegistries();
        rebuildRuntimeRegistries();

        box2dSyncSystem = world.getSystem(Box2dSyncSystem.class);
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setEnabled(false);
        }
        Gdx.app.log(
                PHYSICS_LOG_TAG,
                "initRuntime worldId=" + System.identityHashCode(world)
                        + " box2dSyncSystem=" + (box2dSyncSystem != null)
        );

        sceneLoaded = false;
    }


    /** Initializes a runtime with default configuration and no scene file. */
    public PixscapeEngine initEmptyRuntime() {
        this.cfg = new RuntimeConfig();

        ShaderRegistry.initDefaults(platformTarget, null, null);

        if (worldCamera == null) {
            worldCamera = new OrthographicCamera();
        }

        renderState = new RenderStateSOA();
        layerState  = new LayerStateSOA();
        drawList    = new DrawList();

        GLCaps caps = GLCaps.detect();
        RenderSettings settings = RenderSettings.defaultEditor(caps);

        atlasRuntimeService = new AtlasRuntimeService();

        BatchFactory.Result r = BatchFactory.create(atlasRuntimeService, settings, caps);
        metricsBatch = r.batch;
        defaultShaderName = r.defaultShaderName;

        stats     = new RenderStats();
        statsSink = new RenderStatsSink(0.5f);

        new RenderContext(renderState, layerState, drawList, metricsBatch, caps);

        layerState.setCapacity(32);
        applyAmbientFromMeta(null);

        int defaultShaderIdx = ShaderRegistry.indexOf(defaultShaderName);
        WorldBootstrapResult result =
                WorldConfigFactory.buildWorld(
                        worldCamera,
                        renderState,
                        layerState,
                        drawList,
                        stats,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        null,
                        () -> new RenderSubmitSystem(
                                renderState,
                                layerState,
                                drawList,
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
                        configurationCustomizer
                );

        world = result.getWorld();
        prefabSpawnService = null;
        runtimeTiledStart = result.getTiledStart();
        runtimeTiledEnd = result.getTiledEnd();
        bindRuntimeRegistries();
        rebuildRuntimeRegistries();

        return this;
    }


    /** Rebuilds the ECS world while keeping existing render resources. */
    public void rebuildWorldOnly(RuntimeConfig config, FileHandle projectDir) {
        if (config == null) throw new IllegalArgumentException("config is null");
        if (projectDir == null) throw new IllegalArgumentException("projectDir is null");
        if (worldCamera == null) worldCamera = new OrthographicCamera();
        if (renderState == null || layerState == null || drawList == null || metricsBatch == null || stats == null || statsSink == null) {
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
                        renderState,
                        layerState,
                        drawList,
                        stats,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        effectsRoot,
                        () -> new RenderSubmitSystem(
                                renderState,
                                layerState,
                                drawList,
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
                        configurationCustomizer
                );

        world = result.getWorld();
        prefabSpawnService = null;
        runtimeTiledStart = result.getTiledStart();
        runtimeTiledEnd = result.getTiledEnd();
        bindRuntimeRegistries();
        rebuildRuntimeRegistries();

        box2dSyncSystem = world.getSystem(Box2dSyncSystem.class);
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setEnabled(false);
            box2dSyncSystem.setStepEnabled(false);
        }
        Gdx.app.log(
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
        if (sceneTag == null || sceneTag.isBlank()) {
            throw new IllegalStateException("Cannot resolve logical scene name for: " + resolvedName);
        }
        applyPhysicsFromScene(meta);
        loggedFirstUpdate = false;
        loggedFirstRender = false;

        FileHandle sceneFile = runtimeProjectDir.child(cfg.scenesDir).child(RuntimeFs.withExt(sceneTag, RuntimeFs.EXT_JSON));

        SceneLoader.loadScene(world, sceneFile, false);
        world.process();

        rebuildRuntimeRegistries();
        rebuildTiledLayersRuntime(meta);

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
        TiledSoaAllocator allocator = new TiledSoaAllocator(runtimeTiledStart, runtimeTiledEnd);

        ComponentMapper<TiledLayerComponent> mTiled =
                world.getMapper(TiledLayerComponent.class);
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(TiledLayerComponent.class))
                .getEntities();

        int[] dataArr = bag.getData();

        for (int i = 0; i < bag.size(); i++) {

            int e = dataArr[i];
            TiledLayerComponent tiled = mTiled.get(e);
            if (tiled == null) continue;

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

            int required = tiled.mapWidthCells * tiled.mapHeightCells;
            TiledSoaAllocator.Range r = allocator.allocate(required);
            tiled.tiledStart = r.start;
            tiled.tiledEnd = r.end;
            tiled.data.initSlotRange(r.start, r.end);

            for (int t = 0; t < tiled.tileXs.size; t++) {
                int gx = tiled.tileXs.get(t);
                int gy = tiled.tileYs.get(t);
                int assetId = tiled.tileAssetIds.get(t);
                byte flags = tiled.tileTransformFlags.get(t);

                tiled.data.setTile(gx, gy, assetId, flags);

                int cx = gx / tiled.data.chunkSize;
                int cy = gy / tiled.data.chunkSize;

                TileChunk chunk = tiled.data.getChunk(cx, cy);

                if (chunk != null) {
                    int lx = gx - (cx * tiled.data.chunkSize);
                    int ly = gy - (cy * tiled.data.chunkSize);
                    TileAnimationStateSupport.syncWorldCell(chunk, lx, ly, animatedTileRegistry);
                }
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
        if (sceneTag == null || sceneTag.isBlank()) {
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
            maskTiledSlotsInvisible(tiled.data);
            tiled.data.markAllChunksContentDirty();
        }
    }

    private void maskTiledSlotsInvisible(TiledMapLayerData tiledData) {
        if (renderState == null || tiledData == null) return;

        for (IntMap.Values<TileChunk> chunks = tiledData.getChunks(); chunks.hasNext();) {
            TileChunk chunk = chunks.next();
            if (chunk == null || chunk.soaCount <= 0) continue;

            int slotStart = Math.max(0, chunk.soaStartIndex);
            int slotEnd = Math.min(renderState.visible.length, slotStart + chunk.soaCount);
            for (int slot = slotStart; slot < slotEnd; slot++) {
                renderState.visible[slot] = false;
            }
        }
    }

    private void rebuildSceneAssets(String sceneTag) {

        var mSrc = world.getMapper(AssetRefComponent.class);
        var mTR  = world.getMapper(TextureRegionComponent.class);
        var mMat = world.getMapper(RenderMaterialComponent.class);
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
            if (atlasTag == null || atlasTag.isBlank()) {
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

    private String resolveSceneName(String sceneName) {
        if (sceneName != null && !sceneName.isBlank()) return sceneName;

        String cur = cfg.currentSceneName;
        if (cur != null && cfg.getSceneMeta(cur) != null) return cur;

        var names = cfg.getSceneNamesSorted();
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

    private void applyPhysicsFromScene(SceneMetaRuntime meta) {
        if (box2dSyncSystem == null) {
            Gdx.app.log(PHYSICS_LOG_TAG, "applyPhysicsFromScene: box2dSyncSystem missing");
            return;
        }

        if (meta == null || !meta.physicsEnabled) {
            box2dSyncSystem.setEnabled(false);
            box2dSyncSystem.setStepEnabled(false);
            Gdx.app.log(PHYSICS_LOG_TAG, "applyPhysicsFromScene: physics disabled (meta=" + (meta != null) + ")");
            return;
        }

        if (box2dWorldService == null || box2dWorldService.isDisposed() || box2dWorldService.world == null) {
            float ppm = meta.pixelsPerMeter > 0f ? meta.pixelsPerMeter : 100f;
            box2dWorldService = new Box2dWorldService(
                    ppm,
                    new Vector2(meta.gravityX, meta.gravityY),
                    meta.doSleep
            );
            box2dSyncSystem.setBox2d(box2dWorldService);
        } else {
            float ppm = meta.pixelsPerMeter > 0f ? meta.pixelsPerMeter : 100f;
            box2dWorldService.setPpm(ppm);
            box2dWorldService.setGravity(meta.gravityX, meta.gravityY);
            box2dWorldService.setDoSleep(meta.doSleep);
        }

        box2dSyncSystem.setSceneMeta(meta);
        box2dSyncSystem.setEnabled(true);
        box2dSyncSystem.setStepEnabled(true);
        Gdx.app.log(
                PHYSICS_LOG_TAG,
                "applyPhysicsFromScene: enabled ppm=" + meta.pixelsPerMeter
                        + " gravity=(" + meta.gravityX + "," + meta.gravityY + ")"
                        + " doSleep=" + meta.doSleep
                        + " stepEnabled=" + box2dSyncSystem.isStepEnabled()
        );
    }

    private void bindRuntimeRegistries() {
        identityRegistry.bind(world);
        tagRegistry.bind(world);
    }

    private void rebuildRuntimeRegistries() {
        identityRegistry.rebuild();
        tagRegistry.rebuild();
    }

    private static FileHandle resolveEffectsRoot(FileHandle projectDir, RuntimeConfig config) {
        String effectsDir = (config != null && config.effectsDir != null && !config.effectsDir.isBlank())
                ? config.effectsDir
                : "effects";
        return (projectDir != null) ? projectDir.child(effectsDir) : null;
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
