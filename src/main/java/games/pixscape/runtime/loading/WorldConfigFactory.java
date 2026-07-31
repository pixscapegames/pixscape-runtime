package games.pixscape.runtime.loading;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.spatial.SpatialLayerRuntimeRegistry;
import games.pixscape.runtime.system.*;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WorldConfigFactory {

    public static final int DEFAULT_VFX_BUDGET = 16384;
    public static final int DEFAULT_ECS_ENTITY_CAPACITY_HINT = 150_000;
    public static final int DEFAULT_DYNAMIC_ECS_RENDER_CAPACITY = 4096;
    public static final int DEFAULT_FRAME_QUEUE_CAPACITY = 4096;
    public static final int DEFAULT_TILED_VISIBLE_SLOTS_CAPACITY = 4096;
    private static final float DEFAULT_PIXELS_PER_METER = 100f;

    private WorldConfigFactory() {
    }

    /**
     * Single-hook overload.
     * <p>
     * The customizer is treated as post-render, matching the previous behavior.
     */
    public static WorldBootstrapResult buildWorld(
            OrthographicCamera camera,
            DynamicEntityRenderState dynamicEntityState,
            LayerStateSOA layerState,
            DrawList drawList,
            FrameRenderQueue frameQueue,
            VfxRenderState vfxState,
            TiledMapRenderState tiledState,
            RenderStats stats,
            int defaultShaderIdx,
            AtlasRuntimeService atlasRuntimeService,
            FileHandle effectsRoot,
            Supplier<BaseSystem> submitSupplier,
            SceneMetaRuntime meta,
            int tiledBudget,
            TileAnimationRegistry animatedTileRegistry,
            Consumer<WorldConfigurationBuilder> customizer
    ) {
        return buildWorld(
                camera,
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
                submitSupplier,
                meta,
                tiledBudget,
                animatedTileRegistry,
                null,
                null,
                customizer
        );
    }

    /**
     * Builds a Pixscape ECS world and exposes two extension hooks for editor/runtime callers.
     *
     * <p><b>System order matters.</b> Artemis executes systems in the order they are added
     * to the {@link WorldConfigurationBuilder}. The two customizers are intentionally split
     * because some Studio systems must feed the current frame render state before the draw
     * list is built, while others are editor tools/overlays that must run after rendering.</p>
     *
     * <h3>preRenderCustomizer</h3>
     * <p>Runs after core sync systems and before:</p>
     * <ul>
     *     <li>{@link RenderBuildDrawListSystem}</li>
     *     <li>{@link RenderSortSystem}</li>
     *     <li>the submit/render system supplied by {@code submitSupplier}</li>
     * </ul>
     *
     * <p>Use this hook only for systems that write into frame render source state and must be
     * visible in the current frame draw list.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *     <li>Studio standalone fallback systems</li>
     *     <li>Animation fallback</li>
     *     <li>Tiled fallback</li>
     *     <li>Particle fallback</li>
     * </ul>
     *
     * <h3>postRenderCustomizer</h3>
     * <p>Runs after the submit/render system and after {@link DirtyFlushSystem}.</p>
     *
     * <p>Use this hook for editor tools, UI refresh, picking, gizmos, and overlays that do
     * not need to feed the current frame draw list.</p>
     *
     * <p>Examples:</p>
     * <ul>
     *     <li>Ghost previews</li>
     *     <li>UI refresh dispatch</li>
     *     <li>Light icon overlays</li>
     *     <li>Picking</li>
     *     <li>Gizmos</li>
     * </ul>
     *
     * <p>If unsure: systems that mutate frame render source state for rendering belong in
     * {@code preRenderCustomizer}; systems that inspect input/UI or draw editor overlays
     * belong in {@code postRenderCustomizer}.</p>
     */
    public static WorldBootstrapResult buildWorld(
            OrthographicCamera camera,
            DynamicEntityRenderState dynamicEntityState,
            LayerStateSOA layerState,
            DrawList drawList,
            FrameRenderQueue frameQueue,
            VfxRenderState vfxState,
            TiledMapRenderState tiledState,
            RenderStats stats,
            int defaultShaderIdx,
            AtlasRuntimeService atlasRuntimeService,
            FileHandle effectsRoot,
            Supplier<BaseSystem> submitSupplier,
            SceneMetaRuntime meta,
            int tiledBudget,
            TileAnimationRegistry animatedTileRegistry,
            Consumer<WorldConfigurationBuilder> preRenderCustomizer,
            Consumer<WorldConfigurationBuilder> postRenderCustomizer
    ) {
        return buildWorld(
                camera,
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
                submitSupplier,
                meta,
                tiledBudget,
                animatedTileRegistry,
                null,
                preRenderCustomizer,
                postRenderCustomizer
        );
    }

    public static WorldBootstrapResult buildWorld(
            OrthographicCamera camera,
            DynamicEntityRenderState dynamicEntityState,
            LayerStateSOA layerState,
            DrawList drawList,
            FrameRenderQueue frameQueue,
            VfxRenderState vfxState,
            TiledMapRenderState tiledState,
            RenderStats stats,
            int defaultShaderIdx,
            AtlasRuntimeService atlasRuntimeService,
            FileHandle effectsRoot,
            Supplier<BaseSystem> submitSupplier,
            SceneMetaRuntime meta,
            int tiledBudget,
            TileAnimationRegistry animatedTileRegistry,
            SystemProfiler systemProfiler,
            Consumer<WorldConfigurationBuilder> preRenderCustomizer,
            Consumer<WorldConfigurationBuilder> postRenderCustomizer
    ) {
        return buildWorld(
                camera,
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
                submitSupplier,
                meta,
                tiledBudget,
                animatedTileRegistry,
                null,
                systemProfiler,
                preRenderCustomizer,
                postRenderCustomizer
        );
    }

    public static WorldBootstrapResult buildWorld(
            OrthographicCamera camera,
            DynamicEntityRenderState dynamicEntityState,
            LayerStateSOA layerState,
            DrawList drawList,
            FrameRenderQueue frameQueue,
            VfxRenderState vfxState,
            TiledMapRenderState tiledState,
            RenderStats stats,
            int defaultShaderIdx,
            AtlasRuntimeService atlasRuntimeService,
            FileHandle effectsRoot,
            Supplier<BaseSystem> submitSupplier,
            SceneMetaRuntime meta,
            int tiledBudget,
            TileAnimationRegistry animatedTileRegistry,
            RuntimeTilesetProfiles tilesetProfiles,
            SystemProfiler systemProfiler,
            Consumer<WorldConfigurationBuilder> preRenderCustomizer,
            Consumer<WorldConfigurationBuilder> postRenderCustomizer
    ) {

        int ecsStart = 0;
        int ecsEnd = DEFAULT_DYNAMIC_ECS_RENDER_CAPACITY;
        int entityCapacityHint = DEFAULT_ECS_ENTITY_CAPACITY_HINT;
        int vfxStart = 0;
        int vfxEnd = vfxStart + DEFAULT_VFX_BUDGET;

        configureRenderStorageCapacities(dynamicEntityState, drawList, frameQueue, vfxState, tiledState, ecsEnd);

        WorldConfigurationBuilder builder = new WorldConfigurationBuilder();

        TileAnimationRegistry effectiveAnimatedTileRegistry =
                animatedTileRegistry != null ? animatedTileRegistry : new TileAnimationRegistry();
        SpatialLayerRuntimeRegistry spatialRuntimeRegistry = new SpatialLayerRuntimeRegistry();
        addCoreSyncSystems(
                builder,
                camera,
                dynamicEntityState,
                vfxState,
                tiledState,
                layerState,
                entityCapacityHint,
                meta,
                atlasRuntimeService,
                defaultShaderIdx,
                effectsRoot,
                vfxStart,
                vfxEnd,
                effectiveAnimatedTileRegistry,
                tilesetProfiles,
                systemProfiler,
                spatialRuntimeRegistry
        );

        if (preRenderCustomizer != null) {
            preRenderCustomizer.accept(builder);
        }

        addRenderPipelineSystems(
                builder,
                dynamicEntityState,
                vfxState,
                tiledState,
                layerState,
                drawList,
                frameQueue,
                stats,
                ecsEnd,
                vfxStart,
                vfxEnd,
                meta,
                submitSupplier,
                systemProfiler,
                spatialRuntimeRegistry
        );

        builder.with(profiled(new DirtyFlushSystem(), systemProfiler));

        if (postRenderCustomizer != null) {
            postRenderCustomizer.accept(builder);
        }

        World world = new World(builder.build());

        return new WorldBootstrapResult(
                world,
                ecsStart,
                ecsEnd,
                vfxStart,
                vfxEnd,
                ecsEnd,
                effectiveAnimatedTileRegistry
        );
    }

    private static void addCoreSyncSystems(
            WorldConfigurationBuilder builder,
            OrthographicCamera worldCamera,
            DynamicEntityRenderState dynamicEntityState,
            VfxRenderState vfxState,
            TiledMapRenderState tiledState,
            LayerStateSOA layerState,
            int entityCapacityHint,
            SceneMetaRuntime meta,
            AtlasRuntimeService atlasRuntimeService,
            int defaultShaderIdx,
            FileHandle effectsRoot,
            int vfxStartIndex,
            int vfxEndIndex,
            TileAnimationRegistry animatedTileRegistry,
            RuntimeTilesetProfiles tilesetProfiles,
            SystemProfiler systemProfiler,
            SpatialLayerRuntimeRegistry spatialRuntimeRegistry
    ) {
        builder.with(
                new WorldSerializationManager(),
                new DirtyTrackerSystem(entityCapacityHint),
                profiled(new Box2dSyncSystem(null), systemProfiler),
                profiled(new PhysicsSpatialFootprintSyncSystem(
                        meta != null && meta.pixelsPerMeter > 0f
                                ? meta.pixelsPerMeter
                                : DEFAULT_PIXELS_PER_METER), systemProfiler),
                profiled(new UpdateWorldGeometrySystem(), systemProfiler),
                profiled(new AnimationSystem(atlasRuntimeService), systemProfiler),
                profiled(new LayerStateBuildSystem(layerState, meta), systemProfiler),
                profiled(new RenderSpriteSyncSystem(dynamicEntityState), systemProfiler),
                profiled(new ParallaxDisplaySystem(dynamicEntityState, layerState, worldCamera), systemProfiler),
                profiled(new CullingSystem(worldCamera, dynamicEntityState), systemProfiler),
                profiled(new TiledAnimationSystem(animatedTileRegistry), systemProfiler),
                profiled(new RenderTiledSyncSystem(
                        worldCamera,
                        tiledState,
                        atlasRuntimeService,
                        defaultShaderIdx,
                        animatedTileRegistry,
                        tilesetProfiles,
                        spatialRuntimeRegistry
                ), systemProfiler),
                profiled(new RenderParticleSyncSystem(
                        vfxState,
                        worldCamera,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        effectsRoot
                ), systemProfiler)
        );
    }

    static void configureRenderStorageCapacities(DynamicEntityRenderState dynamicEntityState,
                                                 DrawList drawList,
                                                 FrameRenderQueue frameQueue,
                                                 VfxRenderState vfxState,
                                                 TiledMapRenderState tiledState,
                                                 int ecsCapacity) {
        dynamicEntityState.setRenderCapacity(ecsCapacity);
        dynamicEntityState.setEntityCapacity(Math.min(DEFAULT_ECS_ENTITY_CAPACITY_HINT, Math.max(1024, ecsCapacity)));
        drawList.setCapacity(ecsCapacity);
        frameQueue.setCapacity(DEFAULT_FRAME_QUEUE_CAPACITY);
        vfxState.setCapacity(DEFAULT_VFX_BUDGET);
        tiledState.setCapacity(DEFAULT_TILED_VISIBLE_SLOTS_CAPACITY);
    }

    private static void addRenderPipelineSystems(
            WorldConfigurationBuilder builder,
            DynamicEntityRenderState dynamicEntityState,
            VfxRenderState vfxState,
            TiledMapRenderState tiledState,
            LayerStateSOA layerState,
            DrawList drawList,
            FrameRenderQueue frameQueue,
            RenderStats stats,
            int entityCapacityHint,
            int vfxStartIndex,
            int vfxEndIndex,
            SceneMetaRuntime meta,
            Supplier<BaseSystem> submitSupplier,
            SystemProfiler systemProfiler,
            SpatialLayerRuntimeRegistry spatialRuntimeRegistry
    ) {
        builder.with(
                profiled(new RenderBuildDrawListSystem(
                        dynamicEntityState,
                        tiledState,
                        vfxState,
                        layerState,
                        drawList,
                        stats,
                        entityCapacityHint,
                        vfxStartIndex,
                        vfxEndIndex
                ), systemProfiler),
                profiled(new RenderSortSystem(
                        dynamicEntityState,
                        tiledState,
                        vfxState,
                        drawList,
                        vfxStartIndex,
                        vfxEndIndex
                ), systemProfiler),
                profiled(new SpatialRenderOrderSystem(
                        dynamicEntityState,
                        tiledState,
                        drawList,
                        spatialRuntimeRegistry
                ), systemProfiler),
                profiled(new RenderExtractFrameQueueSystem(
                        dynamicEntityState,
                        tiledState,
                        vfxState,
                        drawList,
                        frameQueue,
                        stats,
                        entityCapacityHint,
                        vfxStartIndex,
                        vfxEndIndex
                ), systemProfiler)
        );

        if (submitSupplier != null) {
            builder.with(profiled(submitSupplier.get(), systemProfiler));
        }
    }

    private static <T extends BaseSystem> T profiled(T system, SystemProfiler profiler) {
        if (system instanceof ProfiledSystem) {
            ((ProfiledSystem) system).setSystemProfiler(profiler);
        }
        return system;
    }
}
