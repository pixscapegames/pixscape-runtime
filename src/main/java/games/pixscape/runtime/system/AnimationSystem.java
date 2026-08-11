package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.animation.AnimationClipDef;
import games.pixscape.runtime.animation.AnimationDef;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.service.AtlasAssetBinding;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.AnimationRegistry;
import games.pixscape.runtime.service.TextureRegistry;

/**
 * Updates animated sprite UVs using pre-indexed atlas frame groups.
 */
public final class AnimationSystem extends IteratingSystem implements ProfiledSystem {

    private ComponentMapper<AnimationComponent> mAnim;
    private ComponentMapper<TextureRegionComponent> mTR;
    private ComponentMapper<RenderMaterialComponent> mMat;
    private ComponentMapper<AssetRefComponent> mSrc;

    private DirtyTrackerSystem dirty;

    private final AtlasRuntimeService atlasRuntimeService;
    private final AnimationRegistry animationRegistry;

    private SystemProfiler profiler = SystemProfilers.DISABLED;
    private boolean profiling;
    private long profileStartNs;

    public AnimationSystem(
            AnimationRegistry animationRegistry,
            AtlasRuntimeService atlasRuntimeService) {
        super(Aspect.all(AnimationComponent.class,
                TextureRegionComponent.class,
                RenderMaterialComponent.class,
                AssetRefComponent.class));

        this.atlasRuntimeService = atlasRuntimeService;
        if (animationRegistry == null) {
            throw new IllegalArgumentException("animationRegistry must not be null");
        }
        this.animationRegistry = animationRegistry;
    }

    @Override
    protected void begin() {
        profiling = profiler.enabled();
        if (profiling) {
            profileStartNs = profiler.begin(SystemProfilePhases.ANIMATION);
        }
    }

    @Override
    protected void process(int e) {
        AnimationComponent a = mAnim.get(e);
        if (a.fps <= 0f || (!a.playing && a.frame >= 0)) return;

        AssetRefComponent src = mSrc.get(e);
        AnimationDef def = animationRegistry.getByAssetId(src.assetId);
        AnimationClipDef clip = def != null ? def.clip(a.currentClip) : null;
        if (clip == null) return;


        AtlasAssetBinding binding = resolveBinding(e);
        if (binding == null) return;
        int regionCount = binding.regionCount();
        if (regionCount == 0) return;

        int start = Math.max(0, clip.start());
        int end = Math.max(0, clip.end());
        int dir = (end >= start) ? 1 : -1;
        int count = frameCount(clip);
        if (count <= 0) return;

        if (a.playing) a.stateTime += world.getDelta();

        float frameDur = 1f / a.fps;
        int local = (int) (a.stateTime / frameDur);
        local = a.loop ? (local % count) : Math.min(local, count - 1);

        int frameIndex = start + local * dir;
        if (frameIndex < 0) frameIndex = 0;
        if (frameIndex >= regionCount) frameIndex = regionCount - 1;

        if (frameIndex != a.frame) {
            a.frame = frameIndex;
            applyFrame(e, clip, binding.regionAt(frameIndex));
        }
    }

    private AtlasAssetBinding resolveBinding(int e) {

        AssetRefComponent src = mSrc.get(e);

        if (src.assetId <= 0)
            throw new IllegalStateException(
                    "AssetRefComponent.assetId must be > 0 for entity " + e
                            + ", got " + src.assetId + ".");

        String atlasTag = (src.atlasTag != null) ? src.atlasTag : "";
        if (atlasTag.isEmpty())
            return null;

        return atlasRuntimeService.resolveBinding(src.assetId, atlasTag);
    }

    private static int frameCount(AnimationClipDef clip) {
        if (clip == null) return 0;
        return Math.abs(Math.max(0, clip.end()) - Math.max(0, clip.start())) + 1;
    }

    private void applyFrame(int e, AnimationClipDef clip, TextureAtlas.AtlasRegion region) {
        TextureRegionComponent tr = mTR.get(e);
        RenderMaterialComponent mat = mMat.get(e);
        if (region == null) return;

        float u1 = region.getU();
        float v1 = region.getV();
        float u2 = region.getU2();
        float v2 = region.getV2();

        if (clip != null && clip.flipX()) {
            float tmp = u1;
            u1 = u2;
            u2 = tmp;
        }

        tr.u1 = u1;
        tr.v1 = v1;
        tr.u2 = u2;
        tr.v2 = v2;
        tr.pixW = region.getRegionWidth();
        tr.pixH = region.getRegionHeight();
        tr.valid = true;

        Texture pageTex = region.getTexture();
        mat.textureHandle = TextureRegistry.handleOf(pageTex);

        if (dirty != null) dirty.mark(e, DirtyBits.MATERIAL);
    }

    @Override
    protected void end() {
        if (profiling) {
            profiler.end(SystemProfilePhases.ANIMATION, profileStartNs);
            profiling = false;
        }
    }

    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
