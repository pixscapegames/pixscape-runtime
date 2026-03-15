// ------------------------------------------------------------
// PixscapeEngine.java (refactor: instance-based + loadScene() clears world)
// ------------------------------------------------------------
package games.pixscape.runtime.engine;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxRuntimeException;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.configuration.RuntimeConfig;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.loading.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.BatchFactory;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.MetricsBatch;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.system.AnimationSystem;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.RenderSubmitSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.TiledSoaAllocator;

import java.util.function.Consumer;

import static games.pixscape.runtime.loading.WorldConfigFactory.DEFAULT_TILED_BUDGET;

public final class PixscapeEngine {

    public static final String RUNTIME_DIR_NAME = RuntimeFs.DIR_RUNTIME_PROJECT;
    private static final String PHYSICS_LOG_TAG = "PreviewPhysics";

    private FileHandle userRootDir;
    private FileHandle runtimeProjectDir;

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

    /** Charge project.json et initialise le runtime (idempotent). */
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

        // pratique : si le json ne contient pas runtimeRootDir
        if (cfg.runtimeRootDir == null || cfg.runtimeRootDir.isBlank()) {
            cfg.runtimeRootDir = runtimeProjectDir.path();
        }

        // init runtime from scratch (no scene)
        initRuntime(cfg, runtimeProjectDir);

        loaded = true;

        return this;
    }

    /**
     * Charge une scène (public, nouveau point d'entrée).
     * CONTRAT : clear COMPLET du world avant de charger la scène.
     *
     * Usage:
     *   PixscapeEngine engine = new PixscapeEngine(userRootDir);
     *   engine.loadScene(sceneName);
     */
    public PixscapeEngine loadScene(String sceneName) {
        if (!loaded) loadProject(userRootDir);

        String resolved = resolveSceneName(sceneName);
        SceneMetaRuntime meta = cfg.getSceneMeta(resolved);

        FileHandle sceneFile = runtimeProjectDir
                .child(cfg.scenesDir)
                .child(RuntimeFs.withExt(RuntimeConfig.sceneDirName(meta), RuntimeFs.EXT_JSON));

        int tiledLayerCount = SceneLoader.countTiledLayers(sceneFile);

        int tiledBudget = DEFAULT_TILED_BUDGET * tiledLayerCount;

        if (meta == null)
            throw new IllegalArgumentException("Unknown scene: " + resolved);

        clearWorldForSceneSwitch();

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

        // -------------------------------------------------
        // 1️⃣ Stop old world safely
        // -------------------------------------------------

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

        // -------------------------------------------------
        // 2️⃣ Recreate SOA memory stack
        // -------------------------------------------------

        renderState = new RenderStateSOA();
        drawList    = new DrawList();

        // IMPORTANT: RenderContext must be recreated
        GLCaps caps = GLCaps.detect();
        new RenderContext(renderState, layerState, drawList, metricsBatch, caps);

        // -------------------------------------------------
        // 3️⃣ Rebuild world with correct budget
        // -------------------------------------------------

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
                        configurationCustomizer
                );

        world = result.getWorld();
        runtimeTiledStart = result.getTiledStart();
        runtimeTiledEnd = result.getTiledEnd();

        // -------------------------------------------------
        // 4️⃣ Rebind Box2D system safely
        // -------------------------------------------------

        box2dSyncSystem = world.getSystem(Box2dSyncSystem.class);
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setEnabled(false);
            box2dSyncSystem.setStepEnabled(false);
        }

        loggedFirstUpdate = false;
        loggedFirstRender = false;
    }

    private void clearWorldForSceneSwitch() {

        if (world == null) return;

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

        if (atlasRuntimeService != null) {
            atlasRuntimeService.unloadAll();
        }
    }

    public void update(float dt) {
        if (world == null) return;
        world.setDelta(dt);
        if (!loggedFirstUpdate) {
            Gdx.app.log(PHYSICS_LOG_TAG, "update dt=" + dt + " worldId=" + System.identityHashCode(world));
            loggedFirstUpdate = true;
        }
    }

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

    public void resize(int w, int h) {
        if (worldCamera != null) {
            worldCamera.viewportWidth = w;
            worldCamera.viewportHeight = h;
            worldCamera.update();
        }
    }

    /**
     * Dispose complet de l'engine.
     * Après ça, il faut recréer une instance.
     */
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

    /**
     * Clear complet du World "runtime" pour changer de scène proprement.
     *
     * IMPORTANT:
     * - On NE réinstancie pas toute la stack GL (batch/shaders) ici.
     * - On recrée le World + services runtime dépendants du World (Box2D sync system refs, subscriptions, etc.)
     * - On reset Box2D (service) pour éviter de garder des bodies du monde précédent.
     * - On garde metricsBatch + shader registry + atlasRuntimeService (mais on unloadAll l'atlas).
     *
     * Si tu veux un "hard reset" encore plus strict, remplace ce clear par:
     *   initRuntime(cfg, runtimeProjectDir);
     * (ça recrée aussi renderState/drawList/targets etc.)
     */
    private void clearWorldCompletely() {
        if (cfg == null) throw new IllegalStateException("loadProject() must be called before loadScene().");

        // 1) Stop/detach Box2D sync FIRST (avoid native calls during world.dispose)
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setStepEnabled(false);
            box2dSyncSystem.setEnabled(false);

            // CRUCIAL: prevent any further native calls during world.dispose()
            box2dSyncSystem.setBox2d(null);
        }
        box2dSyncSystem = null;

        // 2) Dispose ECS world (drops entities, subscriptions, systems state, etc.)
        if (world != null) {
            world.dispose();
            world = null;
        }

        // 3) Now it's safe to dispose native Box2D world/service
        if (box2dWorldService != null) {
            box2dWorldService.dispose();
            box2dWorldService = null;
        }

        // 4) Unload atlas assets so next scene rebind is clean
        if (atlasRuntimeService != null) {
            atlasRuntimeService.unloadAll();
        }

        // 5) Rebuild a fresh World with the same runtime config (no scene loaded yet)
        rebuildWorldOnly(cfg, runtimeProjectDir);

        // Optional but recommended: reset one-shot logs for the new world id
        loggedFirstUpdate = false;
        loggedFirstRender = false;

        sceneLoaded = false;
    }


    /**
     * Dispose complet world + runtime resources GPU-side, used by dispose().
     * (hard shutdown)
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

        renderState = null;
        layerState = null;
        drawList = null;
        runtimeTiledStart = 0;
        runtimeTiledEnd = 0;
        stats = null;
        statsSink = null;
        defaultShaderName = null;
        // worldCamera kept (cheap), but you can null it too if you want:
        // worldCamera = null;
    }

    /**
     * Initialisation runtime "hard": reconstruit tout (GL batch, SOA, targets, World).
     * Appelé une fois au chargement projet.
     */
    private void initRuntime(RuntimeConfig config, FileHandle projectDir) {
        // HARD reset of everything
        disposeWorldAndRuntime();

        if (config == null) throw new IllegalArgumentException("config is null");
        if (projectDir == null) throw new IllegalArgumentException("projectDir is null");

        if (worldCamera == null) worldCamera = new OrthographicCamera();

        ShaderRegistry.initDefaults(config.glProfile, projectDir, config.shadersDir);

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
                        null,      // meta
                        0,         // tiledBudget runtime (pas utilisé pour l’instant)
                        configurationCustomizer
                );

        world = result.getWorld();

        // Grab Box2D system from the world, disable by default
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


    public PixscapeEngine initEmptyRuntime() {
        this.cfg = new RuntimeConfig();

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
                        null,      // meta
                        0,         // tiledBudget runtime (pas utilisé pour l’instant)
                        configurationCustomizer
                );

        world = result.getWorld();

        return this;
    }


    /**
     * Rebuild seulement le World ECS (pour changement de scène),
     * en conservant renderState/drawList/batch/targets existants.
     */
    public void rebuildWorldOnly(RuntimeConfig config, FileHandle projectDir) {
        if (config == null) throw new IllegalArgumentException("config is null");
        if (projectDir == null) throw new IllegalArgumentException("projectDir is null");
        if (worldCamera == null) worldCamera = new OrthographicCamera();
        if (renderState == null || layerState == null || drawList == null || metricsBatch == null || stats == null || statsSink == null) {
            // Safety: if something is missing, fallback to full init.
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
                        null,      // meta
                        0,         // tiledBudget runtime (pas utilisé pour l’instant)
                        configurationCustomizer
                );

        world = result.getWorld();

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
        clearWorldBeforeSceneLoad();

        // Physics: lazy init + enable/disable
        applyPhysicsFromScene(meta);
        loggedFirstUpdate = false;
        loggedFirstRender = false;

        // Scene load
        FileHandle sceneFile = runtimeProjectDir.child(cfg.scenesDir).child(RuntimeFs.withExt(sceneTag, RuntimeFs.EXT_JSON));

        SceneLoader.loadScene(world, sceneFile, true);

        rebuildTiledLayersRuntime(meta);

        // Atlas load + rebind
        RuntimeSceneAtlasLoader.loadSceneAtlas(
                cfg,
                resolvedName,
                runtimeProjectDir,
                atlasRuntimeService
        );
        rebindAtlas(sceneTag);
        forceFullDirtyAfterLoad();

        // Debug info (optional)
        ComponentMapper<AssetRefComponent> mSrc = world.getMapper(AssetRefComponent.class);
        IntBag bag = world.getAspectSubscriptionManager()
                .get(Aspect.all(AssetRefComponent.class, TextureRegionComponent.class))
                .getEntities();

        int[] data = bag.getData();
        for (int i = 0; i < bag.size(); i++) {
            int e = data[i];
            AssetRefComponent src = mSrc.get(e);
            Gdx.app.log("DBG",
                    "e=" + e
                            + " assetId=" + src.assetId
                            + " atlasTag=" + src.atlasTag
            );
        }

        // First ECS tick after atlas bind (important for particles init)
        world.process();
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

            // Create dense map
            tiled.data = new TiledMapLayerData(
                    tiled.mapWidthCells,
                    tiled.mapHeightCells,
                    (int) meta.tileWidth,
                    (int) meta.tileHeight,
                    meta.chunkSize
            );
            tiled.data.originX = tiled.originX;
            tiled.data.originY = tiled.originY;

            // Allocate SOA
            int required = tiled.mapWidthCells * tiled.mapHeightCells;
            TiledSoaAllocator.Range r = allocator.allocate(required);
            tiled.tiledStart = r.start;
            tiled.tiledEnd = r.end;
            tiled.data.initSlotRange(r.start, r.end);

            // Reinject sparse
            for (int t = 0; t < tiled.tileXs.size; t++) {

                int gx = tiled.tileXs.get(t);
                int gy = tiled.tileYs.get(t);
                int assetId = tiled.tileAssetIds.get(t);

                tiled.data.setTile(gx, gy, assetId);
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
            // full rebuild render-side
            dirty.mark(e, DirtyBits.EVERYTHING);
            // et côté géométrie logique, si tu veux recalculer OBB/AABB proprement
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
        // 1) Rebind bundle texture-array pour le batch
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

        // 2) Rebuild runtime scene assets (STRICT assetId-only)
        rebuildSceneAssets(sceneTag);

        // 3) Dirty global
        SceneLoader.forceFullRenderDirty(world);
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

            // --- UV ---
            tr.u1 = region.u1;
            tr.v1 = region.v1;
            tr.u2 = region.u2;
            tr.v2 = region.v2;
            tr.pixW = region.pixW;
            tr.pixH = region.pixH;
            tr.valid = true;

            // --- Material ---
            mat.textureHandle = region.textureHandle;

            if (dirty != null) {
                dirty.material(e);
            }
        }
    }

    private void clearWorldBeforeSceneLoad() {
        if (world == null) return;

        // 1) IMPORTANT Box2D : si tu deletes des entités avec fixtures/bodies,
        // il faut d’abord stopper le sync et/ou vider Box2D proprement
        if (box2dSyncSystem != null) {
            box2dSyncSystem.setStepEnabled(false);
            box2dSyncSystem.setEnabled(false);
        }
        if (box2dWorldService != null) {
            box2dWorldService.dispose();
            box2dWorldService = null;
            if (box2dSyncSystem != null) {
                box2dSyncSystem.setBox2d(null);
            }
        }

        // 2) Supprime toutes les entités Artemis
        IntBag all = world.getAspectSubscriptionManager()
                .get(Aspect.all())
                .getEntities();

        int[] data = all.getData();
        // delete() pendant qu’on itère est ok si on lit le snapshot IntBag
        for (int i = 0, n = all.size(); i < n; i++) {
            int e = data[i];
            world.delete(e);
        }

        // 3) Flush deletions (souvent 2 ticks pour purger subscriptions/systèmes)
        world.process();
        world.process();

        // 4) Optionnel mais sain: reset des “tickets” dirty, drawlists, etc.
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) dirty.clearAll();

        if (drawList != null) drawList.clear();
        if (renderState != null) renderState.clearAll();
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

    private static FileHandle resolveEffectsRoot(FileHandle projectDir, RuntimeConfig config) {
        String effectsDir = (config != null && config.effectsDir != null && !config.effectsDir.isBlank())
                ? config.effectsDir
                : "effects";
        return (projectDir != null) ? projectDir.child(effectsDir) : null;
    }

    public boolean isLoaded() {
        return loaded;
    }
}
