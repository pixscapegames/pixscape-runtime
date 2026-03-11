package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.ComponentMapper;
import com.artemis.systems.IteratingSystem;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.render.DirtyBits;
import games.pixscape.runtime.render.TextureRegistry;
import games.pixscape.runtime.service.AtlasRuntimeService;

/**
 * Updates animated sprite UVs using atlas frame groups (atlas.findRegions(animation)).
 * Resolution is cached (binding cache), never looked up in draw loop.
 */
public final class AnimationSystem extends IteratingSystem {

    private ComponentMapper<AnimationComponent> mAnim;
    private ComponentMapper<TextureRegionComponent> mTR;
    private ComponentMapper<RenderMaterialComponent> mMat;
    private ComponentMapper<AssetRefComponent> mSrc;

    private DirtyTrackerSystem dirty;

    private final AtlasRuntimeService atlasRuntimeService;

    private static final class AnimationBinding {
        final Array<TextureAtlas.AtlasRegion> frames = new Array<>();
    }

    private final ObjectMap<String, AnimationBinding> bindingCache = new ObjectMap<>();

    public AnimationSystem(AtlasRuntimeService atlasRuntimeService) {
        super(Aspect.all(AnimationComponent.class,
                TextureRegionComponent.class,
                RenderMaterialComponent.class,
                AssetRefComponent.class));

        this.atlasRuntimeService = atlasRuntimeService;
    }

    @Override
    protected void process(int e) {
        AnimationComponent a = mAnim.get(e);
        if (!a.playing || a.fps <= 0f) return;

        AnimationComponent.Clip clip = a.getClip();
        if (clip == null) return;



        AnimationBinding binding = resolveBinding(e);
        if (binding == null || binding.frames.size == 0) return;

        int start = Math.max(0, clip.start);
        int end = Math.max(0, clip.end);
        int dir = (end >= start) ? 1 : -1;
        int count = Math.abs(end - start) + 1;
        if (count <= 0) return;

        a.stateTime += world.getDelta();

        float frameDur = 1f / a.fps;
        int local = (int) (a.stateTime / frameDur);
        local = a.loop ? (local % count) : Math.min(local, count - 1);

        int frameIndex = start + local * dir;
        if (frameIndex < 0) frameIndex = 0;
        if (frameIndex >= binding.frames.size) frameIndex = binding.frames.size - 1;

        if (frameIndex != a.frame) {
            a.frame = frameIndex;
            applyFrame(e, clip, binding.frames.get(frameIndex));
        }
    }

    private AnimationBinding resolveBinding(int e) {

        AssetRefComponent src = mSrc.get(e);

        if (src.assetId < 0)
            throw new IllegalStateException(
                    "AssetRefComponent.assetId not set for entity " + e);

        String atlasTag = (src.atlasTag != null) ? src.atlasTag : "";
        if (atlasTag.isEmpty())
            return null;

        String cacheKey = atlasTag + "|__a" + src.assetId;
        AnimationBinding cached = bindingCache.get(cacheKey);
        if (cached != null)
            return cached;

        AtlasRuntimeService.CachedRegion cachedRegion =
                atlasRuntimeService.resolveCached(src.assetId, atlasTag);

        if (cachedRegion == null || cachedRegion.regionName == null || cachedRegion.regionName.isEmpty())
            return null;

        TextureAtlas atlas = atlasRuntimeService.getAtlas(atlasTag);
        if (atlas == null) return null;

        Array<TextureAtlas.AtlasRegion> regions = atlas.findRegions(cachedRegion.regionName);

        if (regions == null || regions.size == 0)
            return null;

        AnimationBinding created = new AnimationBinding();
        created.frames.addAll(regions);

        bindingCache.put(cacheKey, created);
        return created;
    }

    private void applyFrame(int e, AnimationComponent.Clip clip, TextureAtlas.AtlasRegion region) {
        TextureRegionComponent tr = mTR.get(e);
        RenderMaterialComponent mat = mMat.get(e);
        if (region == null) return;

        float u1 = region.getU();
        float v1 = region.getV();
        float u2 = region.getU2();
        float v2 = region.getV2();

        if (clip != null && clip.flipX) {
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

    public void clearBindingCache() {
        bindingCache.clear();
    }
}
