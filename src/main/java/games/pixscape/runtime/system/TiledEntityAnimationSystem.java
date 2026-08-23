package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.TiledAnimationComponent;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasRegionMetadata;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.tiled.animation.TileAnimationDef;
import games.pixscape.runtime.tiled.animation.TileAnimationLookup;
import games.pixscape.runtime.tiled.animation.TileAnimationPlayback;
import games.pixscape.runtime.tiled.animation.TileAnimationPlaybackStepper;

/** Advances Tiled animations attached to regular entities and updates their sprite visual state. */
public final class TiledEntityAnimationSystem extends IteratingSystem implements ProfiledSystem {

    private ComponentMapper<TiledAnimationComponent> mAnimation;
    private ComponentMapper<AssetRefComponent> mAssetRef;
    private ComponentMapper<TextureRegionComponent> mTextureRegion;
    private ComponentMapper<RenderMaterialComponent> mMaterial;
    private DirtyTrackerSystem dirty;

    private final TileAnimationLookup animationLookup;
    private final AtlasRuntimeService atlasRuntimeService;
    private final TileAnimationPlaybackStepper.Result stepResult =
            new TileAnimationPlaybackStepper.Result();

    private float deltaRemainderMs;
    private int frameDeltaMs;
    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private boolean profiling;
    private long profileStartNs;

    public TiledEntityAnimationSystem(TileAnimationLookup animationLookup,
                                      AtlasRuntimeService atlasRuntimeService) {
        super(Aspect.all(
                TiledAnimationComponent.class,
                AssetRefComponent.class,
                TextureRegionComponent.class,
                RenderMaterialComponent.class));
        if (animationLookup == null) {
            throw new IllegalArgumentException("animationLookup must not be null");
        }
        if (atlasRuntimeService == null) {
            throw new IllegalArgumentException("atlasRuntimeService must not be null");
        }
        this.animationLookup = animationLookup;
        this.atlasRuntimeService = atlasRuntimeService;
    }

    @Override
    protected void begin() {
        profiling = profiler.enabled();
        if (profiling) {
            profileStartNs = profiler.begin(SystemProfilePhases.TILED_ANIMATION);
        }
        float deltaMs = world.getDelta() * 1000f + deltaRemainderMs;
        frameDeltaMs = (int) deltaMs;
        deltaRemainderMs = deltaMs - frameDeltaMs;
    }

    @Override
    protected void process(int entityId) {
        TiledAnimationComponent animation = mAnimation.get(entityId);
        TileAnimationDef def = animationLookup.get(animation.animationId);
        if (def == null || def.frameCount() <= 0) {
            restoreBaseVisualIfNeeded(entityId, animation);
            return;
        }

        applyFrameIfNeeded(entityId, animation, def.frameAssetId(
                clampFrame(animation.frameIndex, def.frameCount())));

        TileAnimationPlaybackStepper.advance(
                def,
                TileAnimationPlayback.PLAYING,
                TileAnimationPlayback.MODE_LOOPING,
                false,
                false,
                animation.frameIndex,
                animation.frameElapsedMs,
                frameDeltaMs,
                stepResult);

        animation.frameIndex = stepResult.frameIndex;
        animation.frameElapsedMs = stepResult.frameElapsedMs;
        applyFrameIfNeeded(entityId, animation, def.frameAssetId(animation.frameIndex));
    }

    private void restoreBaseVisualIfNeeded(int entityId, TiledAnimationComponent animation) {
        if (animation.appliedFrameAssetId < 0) return;

        AssetRefComponent source = mAssetRef.get(entityId);
        if (source.assetId > 0) {
            if (!applyFrameIfNeeded(entityId, animation, source.assetId)) return;
        }
        animation.frameIndex = 0;
        animation.frameElapsedMs = 0;
        animation.appliedFrameAssetId = -1;
    }

    private boolean applyFrameIfNeeded(int entityId,
                                       TiledAnimationComponent animation,
                                       int frameAssetId) {
        if (frameAssetId <= 0) return false;
        if (frameAssetId == animation.appliedFrameAssetId) return true;

        AssetRefComponent source = mAssetRef.get(entityId);
        String atlasTag = source.atlasTag != null ? source.atlasTag : "";
        if (atlasTag.length() == 0) return false;

        AtlasAssetBinding binding = atlasRuntimeService.resolveBinding(frameAssetId, atlasTag);
        if (binding == null) return false;
        AtlasRegionMetadata metadata = binding.metadata();
        if (metadata == null) return false;

        TextureRegionComponent region = mTextureRegion.get(entityId);
        region.u1 = metadata.u1();
        region.v1 = metadata.v1();
        region.u2 = metadata.u2();
        region.v2 = metadata.v2();
        region.pixW = metadata.pixelWidth();
        region.pixH = metadata.pixelHeight();
        region.valid = true;

        RenderMaterialComponent material = mMaterial.get(entityId);
        material.textureHandle = metadata.textureHandle();
        material.debugAtlasTag = atlasTag;

        animation.appliedFrameAssetId = frameAssetId;
        if (dirty != null) dirty.material(entityId);
        return true;
    }

    private static int clampFrame(int frameIndex, int frameCount) {
        if (frameIndex < 0) return 0;
        return frameIndex >= frameCount ? frameCount - 1 : frameIndex;
    }

    @Override
    protected void end() {
        if (profiling) {
            profiler.end(SystemProfilePhases.TILED_ANIMATION, profileStartNs);
            profiling = false;
        }
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
