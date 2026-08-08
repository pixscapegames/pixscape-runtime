package games.pixscape.runtime.engine;

import com.artemis.*;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
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
import games.pixscape.runtime.prefab.RuntimePrefabFragment;
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
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.PhysicsSpatialFootprintSyncSystem;
import games.pixscape.runtime.system.RenderSubmitSystem;
import games.pixscape.runtime.tiled.TileChunk;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.tiled.animation.TileAnimationStateSupport;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;

import java.util.function.Consumer;
import java.util.function.Supplier;


public final class PixscapeEngine {

    public static final String RUNTIME_DIR_NAME = RuntimeFs.DIR_RUNTIME_PROJECT;
    private static final String PHYSICS_LOG_TAG = "PreviewPhysics";

    private FileHandle userRootDir;
    private FileHandle runtimeProjectDir;
    private PlatformTarget platformTarget = PlatformTarget.AUTO;
    private AssetManager suppliedAssetManager;
    private FileAvailabilityService fileAvailability;
    private SceneAvailabilityPlan activeSceneAvailability;
    private SceneAvailabilityPlan pendingSceneAvailability;
    private boolean assetManagerConfigurationLocked;
    private final AssetManagerFactory assetManagerFactory;

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


    private Consumer<WorldConfigurationBuilder> preRenderSystemCustomizer;
    private Consumer<WorldConfigurationBuilder> postRenderSystemCustomizer;
    private Supplier<BaseSystem> renderSubmitSystemSupplier;

    // Box2D (lazy)
    private Box2dWorldService box2dWorldService;
    private Box2dSyncSystem box2dSyncSystem;


    public PixscapeEngine() {
        this(new AssetManagerFactory() {
            @Override
            public AssetManager create(FileHandleResolver resolver) {
                return new AssetManager(resolver);
            }
        });
    }

    PixscapeEngine(AssetManagerFactory assetManagerFactory) {
        if (assetManagerFactory == null) throw new IllegalArgumentException("assetManagerFactory is null");
        this.assetManagerFactory = assetManagerFactory;
    }

    interface AssetManagerFactory {
        AssetManager create(FileHandleResolver resolver);
    }

    /**
     * Selects the AssetManager used for Pixscape project/scene resources.
     *
     * <p>Configure this before {@link #loadProject(FileHandle)} or scene loading begins.
     * A supplied manager is borrowed: Pixscape queues, gets, and unloads only the
     * references it acquires, and never globally clears or disposes the manager. This
     * permits application, splash, and Pixscape assets to share one normal LibGDX loading
     * queue. Applications may instead keep independent managers. Passing {@code null}
     * before loading restores the default internally owned manager behavior.</p>
     *
     * @param assetManager borrowed application manager, or {@code null} for an internal one
     * @return this engine
     */
    public PixscapeEngine setAssetManager(AssetManager assetManager) {
        if (assetManagerConfigurationLocked) {
            throw new IllegalStateException(
                    "AssetManager must be configured before Pixscape project/scene loading begins.");
        }
        this.suppliedAssetManager = assetManager;
        return this;
    }

    /**
     * Configures the existing general system extension point after Pixscape rendering.
     *
     * <p>This source-compatible method has the same phase and replacement semantics as
     * {@link #setPostRenderSystemCustomizer(Consumer)}. Systems added by the most recent
     * call to either method run after render submission and {@link games.pixscape.runtime.system.DirtyFlushSystem},
     * but before the synchronous {@link World#process()} call returns.</p>
     *
     * @param customizer builder callback invoked while each candidate World is configured,
     *                   or {@code null} to clear the post-render callback
     * @return this engine
     */
    public PixscapeEngine setConfigurationCustomizer(Consumer<WorldConfigurationBuilder> customizer) {
        return setPostRenderSystemCustomizer(customizer);
    }

    /**
     * Adds advanced render-integration systems after Pixscape core synchronization and
     * before draw-list construction, sorting, Spatial composition, frame-queue extraction,
     * and default or custom submission.
     *
     * <pre>
     * core runtime sync
     *     -&gt; pre-render custom systems
     *     -&gt; draw-list build -&gt; sort -&gt; Spatial composition -&gt; queue extraction
     *     -&gt; submit -&gt; dirty flush -&gt; post-render custom systems
     * </pre>
     *
     * <p>The core synchronization already includes the normal sprite, tiled, and VFX
     * authored-ECS-to-derived-state work for this frame. A pre-render system can affect the
     * current frame when it intentionally produces data in the supported derived/frame
     * render source structures consumed by the later pipeline. Mutating an ordinary authored
     * component here does not rerun earlier synchronization systems and must not be assumed
     * to update every derived structure in the same frame. This is not a general gameplay
     * update hook.</p>
     *
     * <p>The callback is invoked while each candidate Artemis World is being configured.
     * That candidate is not yet published by {@link #getWorld()}, which may still return the
     * previous World or {@code null}; use the callback's {@link WorldConfigurationBuilder}
     * argument to register systems. Setting it does not insert systems into an already-built
     * World; it applies on the next
     * runtime/scene World build.
     * Added systems execute synchronously on the thread calling {@link #render()}, normally
     * the LibGDX render thread; no thread-safety guarantee is provided.</p>
     *
     * @param customizer builder callback for pre-render systems, or {@code null} to clear it
     * @return this engine
     */
    public PixscapeEngine setPreRenderSystemCustomizer(
            Consumer<WorldConfigurationBuilder> customizer) {
        this.preRenderSystemCustomizer = customizer;
        return this;
    }

    /**
     * Adds integration systems after render submission and dirty flushing, but before
     * {@link World#process()} returns.
     *
     * <p>This phase is appropriate for overlays, diagnostics, picking, gizmos,
     * application/editor integration, inspection, and mutations intended for a subsequent
     * frame. Current-frame Pixscape queue extraction and submission are already complete;
     * this hook must not be used to alter that submitted frame.</p>
     *
     * <p>The callback is invoked while each candidate Artemis World is being configured.
     * That candidate is not yet published by {@link #getWorld()}, which may still return the
     * previous World or {@code null}; use the callback's {@link WorldConfigurationBuilder}
     * argument to register systems. This method and
     * {@link #setConfigurationCustomizer(Consumer)} configure the same
     * post-render callback slot, so the most recent call replaces the previous callback.
     * Setting it does not insert systems into an already-built World; it applies on the next
     * runtime/scene World build.
     * Added systems execute synchronously on the thread calling {@link #render()}, normally
     * the LibGDX render thread; no thread-safety guarantee is provided.</p>
     *
     * @param customizer builder callback for post-render systems, or {@code null} to clear it
     * @return this engine
     */
    public PixscapeEngine setPostRenderSystemCustomizer(
            Consumer<WorldConfigurationBuilder> customizer) {
        this.postRenderSystemCustomizer = customizer;
        return this;
    }

    /**
     * Replaces Pixscape's default GPU submission system with an expert Artemis system.
     *
     * <p>Pixscape still performs synchronization, culling, draw-list construction, sorting,
     * Spatial composition, and {@link games.pixscape.runtime.system.RenderExtractFrameQueueSystem}
     * before the supplied system runs. The custom system runs after queue extraction and
     * before dirty flushing. It owns submission of that frame's engine-owned
     * {@link FrameRenderQueue}; Pixscape does not also run {@link RenderSubmitSystem}.</p>
     *
     * <p>The custom system is responsible for its GPU calls, any batch begin/end lifecycle,
     * and any render-stat reporting it requires. Engine getters provide borrowed access to
     * the queue, camera, layer state, internal {@link MetricsBatch}, and metrics; ownership
     * remains with Pixscape, so the custom system must not dispose those objects.</p>
     *
     * <p>The supplier is invoked while each candidate World is configured and may be invoked
     * again whenever scene/runtime lifecycle rebuilds the Artemis World. It must return a
     * non-null system suitable for the new World; callers must not assume that one system
     * instance can be reused across Worlds. The candidate World is not yet published through
     * {@link #getWorld()} when configuration occurs. Systems execute synchronously during
     * {@link #render()}, normally on the LibGDX render thread.</p>
     *
     * <p>Changing the supplier does not replace the submit system in an already-built World;
     * the selection applies on the next runtime/scene World build.</p>
     *
     * <p>Passing {@code null} restores the default {@link RenderSubmitSystem} for subsequent
     * World builds.</p>
     *
     * @param supplier supplier of a fresh custom submit system, or {@code null} for default submission
     * @return this engine
     */
    public PixscapeEngine setRenderSubmitSystemSupplier(Supplier<BaseSystem> supplier) {
        this.renderSubmitSystemSupplier = supplier;
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
        ensureFileAvailability();

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
     *
     * <p>If scene replacement fails after rebuilding begins, the engine discards
     * the candidate scene and remains project-loaded with no active scene. The
     * previous scene is not restored; callers may invoke {@code loadScene} again.</p>
     */
    public PixscapeEngine loadScene(String sceneName) {
        if (!loaded) loadProject(userRootDir);
        String resolved = resolveSceneName(sceneName);
        try {
            internalBeginSceneLoad(resolved);
            pendingSceneAvailability.finishOnNative();
            return internalCompleteSceneLoad();
        } catch (RuntimeException failure) {
            releasePendingSceneAvailability();
            throw failure;
        }
    }

    /** Unsupported HTML bridge: begins exact Runtime-owned scene availability. */
    public PixscapeEngine internalBeginSceneLoad(String sceneName) {
        if (!loaded) throw new IllegalStateException("loadProject() must be called before scene loading.");
        String resolved = resolveSceneName(sceneName);
        if (cfg.getSceneMeta(resolved) == null) {
            throw new IllegalArgumentException("Unknown scene: " + resolved);
        }
        releasePendingSceneAvailability();
        pendingSceneAvailability = new SceneAvailabilityPlan(
                fileAvailability, cfg, runtimeProjectDir, resolved);
        return this;
    }

    /** Unsupported HTML bridge: advances the shared queue once. */
    public boolean internalUpdateSceneAvailability() {
        if (pendingSceneAvailability == null) {
            throw new IllegalStateException("No scene availability operation is pending.");
        }
        try {
            return pendingSceneAvailability.update();
        } catch (RuntimeException failure) {
            releasePendingSceneAvailability();
            throw failure;
        }
    }

    /** Unsupported HTML bridge: returns deterministic Pixscape-scoped item progress. */
    public float internalSceneAvailabilityProgress() {
        return pendingSceneAvailability != null ? pendingSceneAvailability.progress() : 1f;
    }

    /** Unsupported HTML bridge: constructs the scene from the completed availability plan. */
    public PixscapeEngine internalCompleteSceneLoad() {
        if (pendingSceneAvailability == null || !pendingSceneAvailability.isComplete()) {
            throw new IllegalStateException("Scene file availability is not complete.");
        }

        SceneAvailabilityPlan candidate = pendingSceneAvailability;
        SceneMetaRuntime meta = cfg.getSceneMeta(candidate.sceneName());
        sceneLoaded = false;
        activeSceneMeta = null;
        boolean constructionStarted = false;
        try {
            constructionStarted = true;
            rebuildWorld(cfg, runtimeProjectDir, meta);
            retireActiveSceneAvailability();
            loadSceneInternal(candidate.sceneName(), candidate.atlas());
            activeSceneAvailability = candidate;
            pendingSceneAvailability = null;
            activeSceneMeta = meta;
            sceneLoaded = true;
            return this;
        } catch (RuntimeException failure) {
            discardFailedSceneLoad();
            if (atlasRuntimeService != null) atlasRuntimeService.unload(candidate.sceneTag());
            releasePendingSceneAvailability();
            if (constructionStarted) retireActiveSceneAvailability();
            throw failure;
        }
    }

    /**
     * Spawns an in-memory prefab fragment into the currently loaded scene.
     *
     * <p>The fragment is staged and validated before its prepared entities are
     * published into the active Artemis world.</p>
     *
     * @param fragment prefab fragment to instantiate
     * @param offsetX  world-space X offset applied to spawned transforms
     * @param offsetY  world-space Y offset applied to spawned transforms
     * @return result containing all created entity IDs
     * @throws IllegalStateException if no world is initialized
     */
    public SpawnResult spawnPrefabFragment(
            RuntimePrefabFragment fragment, float offsetX, float offsetY) {
        if (world == null || !sceneLoaded) {
            throw new IllegalStateException("No scene is active. Call loadScene() successfully first.");
        }
        if (activeSceneMeta == null) {
            throw new IllegalStateException(
                    "Active scene metadata is required to allocate physics shape IDs.");
        }
        RuntimePrefabFragmentSpawner spawner =
                new RuntimePrefabFragmentSpawner(
                        identityRegistry, activeSceneMeta, atlasRuntimeService);
        return spawner.spawn(world, fragment, offsetX, offsetY);
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
        RuntimePrefabFragmentSpawner spawner =
                new RuntimePrefabFragmentSpawner(
                        identityRegistry, activeSceneMeta, atlasRuntimeService);
        return spawner.spawn(world, root, offsetX, offsetY);
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

        identityRegistry.bind(null, null);
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
                        createRenderSubmitSystemSupplier(),
                        meta,
                        0,
                        animatedTileRegistry,
                        tilesetProfiles,
                        systemProfiler,
                        preRenderSystemCustomizer,
                        postRenderSystemCustomizer
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
     * Sets the active Artemis World's delta time for the next processing pass.
     *
     * <p>This method does not call {@link World#process()} and does not by itself advance
     * the complete Runtime simulation. Normal usage calls it once before {@link #render()}.</p>
     *
     * @param dt frame delta time in seconds
     */
    public void update(float dt) {
        if (world == null) return;
        world.setDelta(dt);
    }

    /**
     * Synchronously processes the configured Artemis World and then flushes deferred atlas
     * disposals.
     *
     * <p>Despite its name, this is more than a stateless GPU draw call. Depending on Runtime
     * configuration, the {@link World#process()} pass includes physics/runtime systems,
     * animation, geometry and render synchronization, culling, custom pre-render systems,
     * draw-list construction, sorting, Spatial composition, frame-queue extraction,
     * default or custom submission, dirty flushing, and custom post-render systems. All
     * systems run synchronously on the calling thread, normally the LibGDX render thread;
     * the engine and its render integration objects are not thread-safe.</p>
     *
     * <p>Ordinary LibGDX rendering does not require a system hook:</p>
     * <pre>{@code
     * engine.update(delta);
     *
     * // Optional application rendering before Pixscape.
     * // Begin and end the application's own Batch normally.
     *
     * engine.render();
     *
     * // Optional application rendering after Pixscape.
     * // Begin and end the application's own Batch normally.
     * }</pre>
     *
     * <p>With default submission, Pixscape calls {@link MetricsBatch#begin} and
     * {@link MetricsBatch#end} and the internal batch is ended before this method returns
     * normally. Pixscape owns and closes that batch; callers must not dispose it. External
     * callers own their separate LibGDX batches and may use them before or after Pixscape once
     * each batch's own begin/end lifecycle is respected.</p>
     *
     * <p>Pixscape does not capture and restore the caller's complete OpenGL state. Submission
     * binds shader programs and textures and changes blending; concrete internal batches may
     * also select texture unit zero and change depth-mask state. Framebuffer, viewport,
     * scissor, culling, depth state, and color mask are not preserved as a caller-state
     * snapshot. External rendering after this call must establish every GL state it relies
     * on rather than assuming the pre-Pixscape state was restored.</p>
     */
    public void render() {
        if (world == null) return;
        processWorld();
        if (atlasRuntimeService != null) {
            atlasRuntimeService.flushDeferredDisposals();
        }
    }

    /**
     * Sets the borrowed Runtime camera's viewport dimensions and immediately calls
     * {@link OrthographicCamera#update()}.
     *
     * <p>Pixscape does not own or dispose a camera supplied through
     * {@link #setWorldCamera(OrthographicCamera)}.</p>
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
        releasePendingSceneAvailability();
        releaseActiveSceneAvailability();
        if (fileAvailability != null) {
            fileAvailability.dispose();
            fileAvailability = null;
        }

        loaded = false;
        sceneLoaded = false;
        activeSceneMeta = null;
        assetManagerConfigurationLocked = false;
    }

    /**
     * Supplies the camera borrowed by subsequently built Runtime Worlds.
     *
     * <p>The configured engine field changes immediately, but systems in an already-built
     * World retain the camera reference captured when that World was constructed. Configure
     * the camera before runtime initialization or a World rebuild when possible; a late
     * replacement is used by systems after the next World build and does not retroactively
     * rewire existing systems. Pixscape keeps
     * the reference rather than cloning it, does not dispose it, reads it during culling and
     * rendering, and default submission calls {@link OrthographicCamera#update()}. The
     * application may mutate the camera between frames; {@link #resize(int, int)} changes its
     * viewport and updates it. Passing {@code null} allows a later build to create the default
     * camera.</p>
     *
     * @param cam borrowed application camera, or {@code null} for a later default
     * @return this engine
     */
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

    /**
     * Returns the camera borrowed by the current Runtime configuration.
     *
     * <p>This is the camera currently configured on the engine. After a late
     * {@link #setWorldCamera(OrthographicCamera)} call, it can differ from the reference held
     * by systems in an already-built World until the next World build. The application owns a
     * supplied camera; Pixscape never disposes it. Pixscape reads captured camera references
     * during core rendering and default submission updates them; callers must not assume
     * exclusive mutation during {@link #render()}.</p>
     */
    public OrthographicCamera getCamera() {
        return worldCamera;
    }

    /**
     * Returns the engine-owned derived SOA for synchronized dynamic ECS render sources.
     *
     * <p>Core synchronization writes it before the pre-render phase. Expert mutation is
     * phase-sensitive and must preserve its invariants; it is not authored scene state.
     * Reacquire it after scene/runtime World rebuilds, and never dispose it.</p>
     */
    public DynamicEntityRenderState getDynamicEntityRenderState() {
        return dynamicEntityState;
    }

    /**
     * Returns engine-owned derived layer render state built from authored layer components.
     *
     * <p>It is current after core synchronization and is consumed by later render phases.
     * Expert mutation is phase-sensitive. Reacquire it after lifecycle rebuilds and do not
     * dispose it.</p>
     */
    public LayerStateSOA getLayerState() {
        return layerState;
    }

    /**
     * Returns the engine-owned frame-local draw-list workspace.
     *
     * <p>Pixscape builds, sorts, and Spatially composes this list after pre-render systems,
     * then extracts the frame queue from it. It is derived data, is reset each frame, and
     * must not be retained as persistent scene state or disposed. Direct mutation outside
     * the owning pipeline phases is unsupported.</p>
     */
    public DrawList getDrawList() {
        return drawList;
    }

    /**
     * Returns the engine-owned, frame-local submit queue.
     *
     * <p>{@link games.pixscape.runtime.system.RenderExtractFrameQueueSystem} populates this
     * derived queue after build, sort, and Spatial composition. A default or custom submit
     * system sees it immediately after extraction. Its entries are not authored or persistent
     * scene state and may change on every frame. Mutation is an expert, phase-sensitive
     * operation; callers must not dispose the queue or retain entry assumptions across
     * scene/runtime World rebuilds.</p>
     */
    public FrameRenderQueue getFrameQueue() {
        return frameQueue;
    }

    /**
     * Returns the engine-owned frame-local VFX render-source SOA.
     *
     * <p>Core VFX synchronization clears and populates it before pre-render systems, and the
     * draw-list pipeline consumes it afterward. Expert current-frame production is
     * phase-sensitive. It is derived data; do not retain entries across frames/rebuilds or
     * dispose it.</p>
     */
    public VfxRenderState getVfxState() {
        return vfxState;
    }

    /**
     * Returns Pixscape's borrowed expert view of the engine-owned internal batch.
     *
     * <p>Default submission owns its begin/end lifecycle and the engine closes it during
     * disposal. A custom submitter that elects to use it assumes responsibility for a correct
     * per-frame begin/end sequence and must leave it ended before returning. Callers must not
     * close or dispose it and should reacquire it after runtime reinitialization.</p>
     */
    public MetricsBatch getMetricsBatch() {
        return metricsBatch;
    }

    /**
     * Returns engine-owned mutable metrics for the current rendering lifecycle.
     *
     * <p>Render systems update this derived diagnostic object. It is not scene state, is not
     * thread-safe, may be replaced during runtime reinitialization, and must not be disposed.</p>
     */
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

    private Supplier<BaseSystem> createRenderSubmitSystemSupplier() {
        if (renderSubmitSystemSupplier != null) {
            return renderSubmitSystemSupplier;
        }
        return () -> new RenderSubmitSystem(
                layerState,
                frameQueue,
                worldCamera,
                ambientMulR,
                ambientMulG,
                ambientMulB,
                metricsBatch,
                stats,
                statsSink
        );
    }

    /**
     * Disposes world and GPU-side runtime resources.
     */
    private void disposeWorldAndRuntime() {
        identityRegistry.bind(null, null);

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
        tagRegistry.bind(null);
        ShaderRegistry.disposeAll();
    }

    private void discardFailedSceneLoad() {
        sceneLoaded = false;
        activeSceneMeta = null;
        identityRegistry.bind(null, null);

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

        tagRegistry.bind(null);

        if (layerState != null) {
            layerState.clear();
            layerState.physicsParallaxX = Float.NaN;
            layerState.physicsParallaxY = Float.NaN;
        }

        dynamicEntityState = new DynamicEntityRenderState();
        drawList = new DrawList();
        frameQueue = new FrameRenderQueue();
        vfxState = new VfxRenderState();
        tiledState = new TiledMapRenderState();

        applyAmbientFromMeta(null);
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

        ShaderRegistry.initDefaults(platformTarget, projectDir, config.shadersDir);

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
                        createRenderSubmitSystemSupplier(),
                        null,
                        0,
                        animatedTileRegistry,
                        tilesetProfiles,
                        systemProfiler,
                        preRenderSystemCustomizer,
                        postRenderSystemCustomizer
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
        disposeWorldAndRuntime();
        this.cfg = new RuntimeConfig();

        ShaderRegistry.initDefaults(platformTarget, null, null);

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
                        createRenderSubmitSystemSupplier(),
                        null,
                        0,
                        animatedTileRegistry,
                        tilesetProfiles,
                        systemProfiler,
                        preRenderSystemCustomizer,
                        postRenderSystemCustomizer
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

        identityRegistry.bind(null, null);

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
                        createRenderSubmitSystemSupplier(),
                        null,
                        0,
                        animatedTileRegistry,
                        tilesetProfiles,
                        systemProfiler,
                        preRenderSystemCustomizer,
                        postRenderSystemCustomizer
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

    private void loadSceneInternal(String sceneName, TextureAtlas availableAtlas) {
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
        PhysicsService.rebuildPreparedBodyCaches(world, meta.pixelsPerMeter);
        applyPhysicsFromScene(meta, true);

        RuntimeSceneAtlasLoader.loadSceneAtlas(
                cfg,
                resolvedName,
                runtimeProjectDir,
                atlasRuntimeService,
                availableAtlas
        );
        rebindAtlas(sceneTag);
        forceFullDirtyAfterLoad();
    }

    private void ensureFileAvailability() {
        assetManagerConfigurationLocked = true;
        if (fileAvailability != null) return;
        if (suppliedAssetManager != null) {
            fileAvailability = new FileAvailabilityService(suppliedAssetManager, false);
        } else {
            final FileHandle projectRoot = runtimeProjectDir;
            FileHandleResolver resolver = new FileHandleResolver() {
                @Override
                public FileHandle resolve(String fileName) {
                    String normalized = FileAvailabilityService.normalizePath(fileName);
                    String rootPath = FileAvailabilityService.normalizePath(projectRoot.path());
                    if (normalized.equals(rootPath)) return projectRoot;
                    String prefix = rootPath.endsWith("/") ? rootPath : rootPath + "/";
                    if (normalized.startsWith(prefix)) {
                        return projectRoot.child(normalized.substring(prefix.length()));
                    }
                    return Gdx.files.internal(normalized);
                }
            };
            fileAvailability = new FileAvailabilityService(assetManagerFactory.create(resolver), true);
        }
    }

    private void retireActiveSceneAvailability() {
        if (activeSceneAvailability == null) return;
        if (atlasRuntimeService != null) {
            atlasRuntimeService.unload(activeSceneAvailability.sceneTag());
        }
        releaseActiveSceneAvailability();
    }

    private void releaseActiveSceneAvailability() {
        if (activeSceneAvailability == null) return;
        activeSceneAvailability.release();
        activeSceneAvailability = null;
    }

    private void releasePendingSceneAvailability() {
        if (pendingSceneAvailability == null) return;
        pendingSceneAvailability.release();
        pendingSceneAvailability = null;
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
        AtlasRuntimeService.TextureArrayBundle previous = atlasRuntimeService.bundle(sceneTag);
        AtlasRuntimeService.TextureArrayBundle bundle = atlasRuntimeService.rebuildBundle(sceneTag);

        if (metricsBatch != null) {
            metricsBatch.setTextureArrayBundle(bundle);
        }

        if (atlasRuntimeService != null && previous != null && previous != bundle) {
            atlasRuntimeService.deferDispose(previous);
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

            if (src.assetId <= 0)
                throw new IllegalStateException(
                        "AssetRef assetId must be > 0 during rebuild: e=" + e
                                + ", got " + src.assetId);

            String atlasTag = src.atlasTag;
            if (isBlank(atlasTag)) {
                throw new IllegalStateException("AssetRef atlasTag not set for entity " + e);
            }

            AtlasAssetBinding binding =
                    atlasRuntimeService.resolveBinding(src.assetId, atlasTag);

            if (binding == null) {
                tr.valid = false;
                mat.textureHandle = 0;
                continue;
            }
            AtlasRegionMetadata region = binding.metadata();

            tr.u1 = region.u1();
            tr.v1 = region.v1();
            tr.u2 = region.u2();
            tr.v2 = region.v2();
            tr.pixW = region.pixelWidth();
            tr.pixH = region.pixelHeight();
            tr.valid = true;

            mat.textureHandle = region.textureHandle();

            if (dirty != null) {
                dirty.material(e);
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
            if (activate && box2dWorldService != null) {
                if (box2dWorldService.world != null
                        && (box2dWorldService.world.getBodyCount() != 0
                        || box2dWorldService.world.getJointCount() != 0)) {
                    throw new IllegalStateException(
                            "Cannot dispose Box2D before native physics teardown.");
                }
                box2dSyncSystem.setBox2d(null);
                box2dWorldService.dispose();
                box2dWorldService = null;
            }
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
