package games.pixscape.runtime.profiling;

public final class SystemProfilePhases {
    public static final int ANIMATION = 0;
    public static final int TILED_ANIMATION = 1;
    public static final int RENDER_TILED_SYNC = 2;
    public static final int SPATIAL_RENDER_ORDER = 3;
    public static final int CULLING = 4;
    public static final int RENDER_BUILD_DRAW_LIST = 5;
    public static final int RENDER_SORT = 6;
    public static final int RENDER_SUBMIT = 7;
    public static final int BOX2D_SYNC = 8;
    public static final int DIRTY_FLUSH = 9;
    public static final int STUDIO_RENDER_SUBMIT = 10;
    public static final int TILED_FALLBACK = 11;
    public static final int TILED_GHOST_PREVIEW = 12;
    public static final int UI_REFRESH_DISPATCH = 13;
    public static final int UPDATE_WORLD_GEOMETRY = 14;
    public static final int LAYER_STATE_BUILD = 15;
    public static final int RENDER_SPRITE_SYNC = 16;
    public static final int PARALLAX_DISPLAY = 17;
    public static final int RENDER_PARTICLE_SYNC = 18;
    public static final int ANIMATION_FALLBACK = 19;
    public static final int STUDIO_PARTICLE_FALLBACK = 20;

    public static final int PHASE_COUNT = 21;

    private static final String[] NAMES = {
            "AnimationSystem",
            "TiledAnimationSystem",
            "RenderTiledSyncSystem",
            "SpatialRenderOrderSystem",
            "CullingSystem",
            "RenderBuildDrawListSystem",
            "RenderSortSystem",
            "RenderSubmitSystem",
            "Box2dSyncSystem",
            "DirtyFlushSystem",
            "StudioRenderSubmitSystem",
            "TiledFallbackSystem",
            "TiledGhostPreviewSystem",
            "UiRefreshDispatchSystem",
            "UpdateWorldGeometrySystem",
            "LayerStateBuildSystem",
            "RenderSpriteSyncSystem",
            "ParallaxDisplaySystem",
            "RenderParticleSyncSystem",
            "AnimationFallbackSystem",
            "StudioParticleFallbackSystem"
    };

    private SystemProfilePhases() {
    }

    public static String name(int phaseId) {
        return phaseId >= 0 && phaseId < NAMES.length ? NAMES[phaseId] : "UnknownSystem";
    }
}
