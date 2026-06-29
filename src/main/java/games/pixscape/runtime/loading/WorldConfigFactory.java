package games.pixscape.runtime.loading;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.render.DrawList;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TileAnimationRegistry;
import games.pixscape.runtime.system.*;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WorldConfigFactory {

    public static final int DEFAULT_VFX_BUDGET = 16384;
    public static final int DEFAULT_TILED_BUDGET = 200_000;
    private static final float DEFAULT_PIXELS_PER_METER = 100f;

    private static final int ECS_WATERMARK = 150_000;

    private WorldConfigFactory() {
    }

    /**
     * Backward-compatible overload.
     * <p>
     * The customizer is treated as post-render, matching the previous behavior.
     */
    public static WorldBootstrapResult buildWorld(
            OrthographicCamera camera,
            RenderStateSOA renderState,
            LayerStateSOA layerState,
            DrawList drawList,
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
                renderState,
                layerState,
                drawList,
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
     * <p>Use this hook only for systems that write into {@link RenderStateSOA} and must be
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
     * <p>If unsure: systems that mutate {@link RenderStateSOA} for rendering belong in
     * {@code preRenderCustomizer}; systems that inspect input/UI or draw editor overlays
     * belong in {@code postRenderCustomizer}.</p>
     */
    public static WorldBootstrapResult buildWorld(
            OrthographicCamera camera,
            RenderStateSOA renderState,
            LayerStateSOA layerState,
            DrawList drawList,
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
                renderState,
                layerState,
                drawList,
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
            RenderStateSOA renderState,
            LayerStateSOA layerState,
            DrawList drawList,
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
                renderState,
                layerState,
                drawList,
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
            RenderStateSOA renderState,
            LayerStateSOA layerState,
            DrawList drawList,
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
        int ecsEnd = ECS_WATERMARK;

        int effectiveTiledBudget = tiledBudget;

        int tiledStart = ecsEnd;
        int tiledEnd = tiledStart + effectiveTiledBudget;

        int vfxStart = tiledEnd;
        int totalCapacity = vfxStart + DEFAULT_VFX_BUDGET;

        renderState.setCapacity(totalCapacity);
        drawList.setCapacity(totalCapacity);

        WorldConfigurationBuilder builder = new WorldConfigurationBuilder();

        TileAnimationRegistry effectiveAnimatedTileRegistry =
                animatedTileRegistry != null ? animatedTileRegistry : new TileAnimationRegistry();
        addCoreSyncSystems(
                builder,
                camera,
                renderState,
                layerState,
                ecsEnd,
                meta,
                atlasRuntimeService,
                defaultShaderIdx,
                effectsRoot,
                tiledStart,
                tiledEnd,
                vfxStart,
                totalCapacity,
                effectiveAnimatedTileRegistry,
                tilesetProfiles,
                systemProfiler
        );

        if (preRenderCustomizer != null) {
            preRenderCustomizer.accept(builder);
        }

        addRenderPipelineSystems(
                builder,
                renderState,
                layerState,
                drawList,
                stats,
                ecsEnd,
                vfxStart,
                totalCapacity,
                meta,
                submitSupplier,
                systemProfiler
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
                tiledStart,
                tiledEnd,
                vfxStart,
                totalCapacity,
                totalCapacity,
                effectiveAnimatedTileRegistry
        );
    }

    private static void addCoreSyncSystems(
            WorldConfigurationBuilder builder,
            OrthographicCamera worldCamera,
            RenderStateSOA renderState,
            LayerStateSOA layerState,
            int entityCapacityHint,
            SceneMetaRuntime meta,
            AtlasRuntimeService atlasRuntimeService,
            int defaultShaderIdx,
            FileHandle effectsRoot,
            int tiledStart,
            int tiledEnd,
            int vfxStartIndex,
            int vfxEndIndex,
            TileAnimationRegistry animatedTileRegistry,
            RuntimeTilesetProfiles tilesetProfiles,
            SystemProfiler systemProfiler
    ) {
        builder.with(
                new WorldSerializationManager(),
                new DirtyTrackerSystem(entityCapacityHint),
                profiled(new Box2dSyncSystem(null), systemProfiler),
                profiled(new UpdateWorldGeometrySystem(), systemProfiler),
                profiled(new AnimationSystem(atlasRuntimeService), systemProfiler),
                profiled(new LayerStateBuildSystem(layerState, meta), systemProfiler),
                profiled(new RenderSpriteSyncSystem(renderState), systemProfiler),
                profiled(new ParallaxDisplaySystem(renderState, layerState, worldCamera), systemProfiler),
                profiled(new CullingSystem(worldCamera, renderState), systemProfiler),
                profiled(new TiledAnimationSystem(animatedTileRegistry), systemProfiler),
                profiled(new RenderTiledSyncSystem(
                        worldCamera,
                        renderState,
                        atlasRuntimeService,
                        defaultShaderIdx,
                        tiledStart,
                        tiledEnd,
                        animatedTileRegistry,
                        tilesetProfiles
                ), systemProfiler),
                profiled(new RenderParticleSyncSystem(
                        renderState,
                        worldCamera,
                        vfxStartIndex,
                        vfxEndIndex,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        effectsRoot
                ), systemProfiler)
        );
    }

    private static void addRenderPipelineSystems(
            WorldConfigurationBuilder builder,
            RenderStateSOA renderState,
            LayerStateSOA layerState,
            DrawList drawList,
            RenderStats stats,
            int entityCapacityHint,
            int vfxStartIndex,
            int vfxEndIndex,
            SceneMetaRuntime meta,
            Supplier<BaseSystem> submitSupplier,
            SystemProfiler systemProfiler
    ) {
        builder.with(
                profiled(new RenderBuildDrawListSystem(
                        renderState,
                        layerState,
                        drawList,
                        stats,
                        entityCapacityHint,
                        vfxStartIndex,
                        vfxEndIndex
                ), systemProfiler),
                profiled(new RenderSortSystem(renderState, drawList), systemProfiler),
                profiled(new SpatialRenderOrderSystem(
                        renderState,
                        drawList,
                        meta != null && meta.pixelsPerMeter > 0f
                                ? meta.pixelsPerMeter
                                : DEFAULT_PIXELS_PER_METER
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
