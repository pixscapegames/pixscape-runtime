package games.pixscape.runtime.loading;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.system.*;

import java.util.function.Consumer;
import java.util.function.Supplier;

public final class WorldConfigFactory {

    public static final int DEFAULT_VFX_BUDGET = 16384;
    public static final int DEFAULT_TILED_BUDGET = 200_000;

    // ✅ ECS watermark fixe
    private static final int ECS_WATERMARK = 150_000;

    private WorldConfigFactory() {}

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
            Consumer<WorldConfigurationBuilder> customizer
    ) {

        // =========================
        // 1) FIXED ECS WATERMARK
        // =========================

        int ecsStart = 0;
        int ecsEnd   = ECS_WATERMARK;

        // =========================
        // 2) RESERVE BLOCKS
        // =========================

        int effectiveTiledBudget = tiledBudget;

        int tiledStart = ecsEnd;
        int tiledEnd   = tiledStart + effectiveTiledBudget;

        int vfxStart = tiledEnd;
        int totalCapacity = vfxStart + DEFAULT_VFX_BUDGET;

        renderState.setCapacity(totalCapacity);
        drawList.setCapacity(totalCapacity);

        // =========================
        // 3) FINAL BUILD
        // =========================

        WorldConfigurationBuilder builder = new WorldConfigurationBuilder();

        addCoreSystems(
                builder,
                camera,
                renderState,
                layerState,
                drawList,
                ecsEnd,
                meta,
                stats,
                atlasRuntimeService,
                defaultShaderIdx,
                tiledStart,
                tiledEnd
        );

        attachRenderParticleSyncSystem(
                builder,
                renderState,
                camera,
                vfxStart,
                totalCapacity,
                defaultShaderIdx,
                atlasRuntimeService,
                effectsRoot
        );

        addSubmitSystem(builder, submitSupplier);
        builder.with(new DirtyFlushSystem());

        if (customizer != null) {
            customizer.accept(builder);
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
                totalCapacity
        );
    }

    private static void addCoreSystems(
            WorldConfigurationBuilder builder,
            OrthographicCamera worldCamera,
            RenderStateSOA renderState,
            LayerStateSOA layerState,
            DrawList drawList,
            int entityCapacityHint,
            SceneMetaRuntime meta,
            RenderStats stats,
            AtlasRuntimeService atlasRuntimeService,
            int defaultShaderIdx,
            int tiledStart,
            int tiledEnd
    ) {

        builder.with(
                new WorldSerializationManager(),
                new DirtyTrackerSystem(entityCapacityHint),
                new Box2dSyncSystem(null),
                new UpdateWorldGeometrySystem(),
                new AnimationSystem(atlasRuntimeService),
                new LayerStateBuildSystem(layerState, meta),
                new RenderSpriteSyncSystem(renderState),
                new ParallaxDisplaySystem(renderState, layerState, worldCamera),
                new CullingSystem(worldCamera, renderState),
                new TiledRenderSyncSystem(
                        worldCamera,
                        renderState,
                        atlasRuntimeService,
                        defaultShaderIdx,
                        tiledStart,
                        tiledEnd
                ),
                new RenderBuildDrawListSystem(renderState, layerState, drawList, stats),
                new RenderSortSystem(renderState, drawList)
        );
    }

    private static void attachRenderParticleSyncSystem(
            WorldConfigurationBuilder builder,
            RenderStateSOA renderState,
            OrthographicCamera worldCamera,
            int vfxStartIndex,
            int vfxEndIndex,
            int defaultShaderIdx,
            AtlasRuntimeService atlasRuntimeService,
            FileHandle effectsRoot
    ) {
        builder.with(
                new RenderParticleSyncSystem(
                        renderState,
                        worldCamera,
                        vfxStartIndex,
                        vfxEndIndex,
                        defaultShaderIdx,
                        atlasRuntimeService,
                        effectsRoot
                )
        );
    }

    private static void addSubmitSystem(
            WorldConfigurationBuilder builder,
            Supplier<BaseSystem> submitSupplier
    ) {
        builder.with(submitSupplier.get());
    }
}
