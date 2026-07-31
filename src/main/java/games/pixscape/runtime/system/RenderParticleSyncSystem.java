package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.All;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.graphics.g2d.Sprite;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntIntMap;
import com.badlogic.gdx.utils.IntMap;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEffectPool;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.particle.ParticleEmitter.Particle;
import games.pixscape.runtime.profiling.ProfiledSystem;
import games.pixscape.runtime.profiling.SystemProfilePhases;
import games.pixscape.runtime.profiling.SystemProfiler;
import games.pixscape.runtime.profiling.SystemProfilers;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.RenderRepeatFlags;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.TextureRegistry;

/**
 * SOA version of particles: updates ParticleEffect and
 * injects each active particle Sprite into VfxRenderState.
 */
@All({ParticleEmitterComponent.class, TransformComponent.class})
public final class RenderParticleSyncSystem extends BaseSystem implements ProfiledSystem {

    private final OrthographicCamera camera;
    private final VfxRenderState vfxState;

    // perf cache: avoids TextureRegistry.handleOf(tex) for each particle
    private Texture lastTex = null;
    private int lastTexHandle = 0;

    // cache : entityId -> ParticleEffect
    private final IntMap<ParticleEffectPool.PooledEffect> effects = new IntMap<>();
    private final ObjectMap<String, ParticleEffectPool> effectPools = new ObjectMap<>();

    private ComponentMapper<ParticleEmitterComponent> mEmitter;
    private ComponentMapper<TransformComponent> mTransform;
    private ComponentMapper<VisibilityComponent> mVis;
    private ComponentMapper<EntityIndexComponent> mEntityIndex;
    private ComponentMapper<LayerComponent> mLayerIndex;
    private ComponentMapper<ParticleOverridesComponent> mOverrides;

    private EntitySubscription subscription;
    private EntitySubscription layerSubscription;
    private final IntIntMap layerVisibility = new IntIntMap();

    // default “material” params
    private final int defaultShaderIdx;
    private final AtlasRuntimeService atlasRuntimeService;
    private FileHandle effectsRoot;
    private SystemProfiler profiler = SystemProfilers.DISABLED;

    public RenderParticleSyncSystem(VfxRenderState vfxState,
                                    OrthographicCamera camera,
                                    int defaultShaderIdx,
                                    AtlasRuntimeService atlasRuntimeService,
                                    FileHandle effectsRoot) {
        this.vfxState = vfxState;
        this.camera = camera;
        this.defaultShaderIdx = defaultShaderIdx;
        this.atlasRuntimeService = atlasRuntimeService;
        this.effectsRoot = effectsRoot;
    }

    private static String effectPoolKey(ParticleEmitterComponent emitter) {
        String atlasTag = (emitter.atlasTag != null) ? emitter.atlasTag : "";
        String effectPath = (emitter.effectPath != null) ? emitter.effectPath : "";
        return atlasTag + "|" + effectPath;
    }

    public void setEffectsRoot(FileHandle effectsRoot) {
        this.effectsRoot = effectsRoot;
    }

    @Override
    protected void initialize() {
        subscription = world.getAspectSubscriptionManager().get(
                Aspect.all(ParticleEmitterComponent.class, TransformComponent.class)
        );
        layerSubscription = world.getAspectSubscriptionManager()
                .get(Aspect.all(LayerComponent.class));

        subscription.addSubscriptionListener(new EntitySubscription.SubscriptionListener() {
            @Override
            public void inserted(IntBag entities) {
                // lazy-load effects in processSystem
            }

            @Override
            public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    int e = data[i];
                    ParticleEffectPool.PooledEffect fx = effects.remove(e);
                    if (fx != null) {
                        fx.free();
                    }
                }
            }
        });
    }

    @Override
    protected void processSystem() {
        if (profiler.enabled()) {
            long startNs = profiler.begin(SystemProfilePhases.RENDER_PARTICLE_SYNC);
            try {
                processSystemInternal();
            } finally {
                profiler.end(SystemProfilePhases.RENDER_PARTICLE_SYNC, startNs);
            }
            return;
        }

        processSystemInternal();
    }

    private void processSystemInternal() {
        float dt = world.getDelta();

        vfxState.clearFrame();
        refreshLayerVisibility();

        IntBag bag = subscription.getEntities();
        int[] data = bag.getData();
        int count = bag.size();
        if (count == 0) return;

        for (int i = 0; i < count; i++) {
            int e = data[i];

            if (mVis != null && mVis.has(e) && !mVis.get(e).isVisible()) {
                continue;
            }
            if (!isLayerVisible(e)) continue;

            ParticleEmitterComponent comp = mEmitter.get(e);
            TransformComponent t = mTransform.get(e);
            if (comp == null || t == null) continue;

            ParticleOverridesComponent ov = (mOverrides != null) ? mOverrides.getSafe(e, null) : null;

            ParticleEffectPool.PooledEffect fx = effects.get(e);
            if (fx == null) {
                fx = createEffect(comp);
                if (fx == null) continue;
                effects.put(e, fx);
                applyLooping(fx, comp.looping);

                if (comp.autoStart) fx.start();
            }

            positionEffect(fx, t);

            if (comp.restartRequested) {
                applyLooping(fx, comp.looping);
                fx.reset(true, true);
                comp.restartRequested = false;
            }
            if (comp.playRequested) {
                applyLooping(fx, comp.looping);
                fx.start();
                comp.playRequested = false;
            }
            if (comp.paused) {
                continue;
            }

            // simulation
            fx.update(dt);

            if (comp.autoRemoveWhenComplete && fx.isComplete()) {
                effects.remove(e);
                fx.free();
                world.delete(e);
                continue;
            }

            // culling effect-level : bounding box vs camera
            if (!isEffectVisible(fx)) {
                continue;
            }

            // enabled=false => the effect lives but nothing is rendered
            if (ov != null && !ov.enabled) {
                continue;
            }

            // inject all active particles into the frame-local VFX SOA.
            int layerIndex = resolveLayerIndex(e);
            int zIndex = resolveZIndex(e);
            collectEffect(fx, layerIndex, zIndex, e, ov);
        }
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    static void positionEffect(ParticleEffect fx, TransformComponent transform) {
        fx.setPosition(transform.x, transform.y);
    }

    private ParticleEffectPool.PooledEffect createEffect(ParticleEmitterComponent emitter) {
        if (emitter.effectPath == null || emitter.effectPath.isEmpty()) return null;

        if (effectsRoot == null) {
            Gdx.app.error("RenderParticleSyncSystem", "effectsRoot is null");
            return null;
        }

        FileHandle effectFile = effectsRoot.child(emitter.effectPath);
        if (!effectFile.exists()) {
            Gdx.app.error("RenderParticleSyncSystem",
                    "Effect file not found: " + effectFile.path()
                            + " (emitter.effectPath=" + emitter.effectPath + ")");
            return null;
        }

        if (atlasRuntimeService == null ||
                emitter.atlasTag == null ||
                emitter.atlasTag.isEmpty()) {
            return null;
        }

        String key = effectPoolKey(emitter);
        ParticleEffectPool pool = effectPools.get(key);

        if (pool == null) {
            TextureAtlas atlas = atlasRuntimeService.getAtlas(emitter.atlasTag);
            if (atlas == null) {
                return null;
            }

            ParticleEffect template = new ParticleEffect();
            try {
                template.load(effectFile, atlas);
                template.setEmittersCleanUpBlendFunction(false);
            } catch (Exception ex) {
                template.dispose();
                return null;
            }

            pool = new ParticleEffectPool(template, 1, 16);
            effectPools.put(key, pool);
        }

        ParticleEffectPool.PooledEffect fx = pool.obtain();
        fx.setEmittersCleanUpBlendFunction(false);
        return fx;
    }

    private void applyLooping(ParticleEffect fx, boolean looping) {
        if (fx == null) return;
        Array<ParticleEmitter> emitters = fx.getEmitters();
        for (int i = 0, n = emitters.size; i < n; i++) {
            ParticleEmitter emitter = emitters.get(i);
            if (emitter != null) {
                emitter.setContinuous(looping);
            }
        }
    }

    private boolean isEffectVisible(ParticleEffect fx) {
        BoundingBox box = fx.getBoundingBox();
        if (!box.isValid()) return false;

        float halfW = camera.viewportWidth * 0.5f * camera.zoom;
        float halfH = camera.viewportHeight * 0.5f * camera.zoom;
        float camX = camera.position.x;
        float camY = camera.position.y;

        float viewMinX = camX - halfW;
        float viewMaxX = camX + halfW;
        float viewMinY = camY - halfH;
        float viewMaxY = camY + halfH;

        float minX = box.min.x;
        float maxX = box.max.x;
        float minY = box.min.y;
        float maxY = box.max.y;

        boolean overlapX = maxX >= viewMinX && minX <= viewMaxX;
        boolean overlapY = maxY >= viewMinY && minY <= viewMaxY;

        return overlapX && overlapY;
    }

    public void invalidateAllEffects() {
        for (IntMap.Entries<ParticleEffectPool.PooledEffect> it = effects.entries(); it.hasNext(); ) {
            IntMap.Entry<ParticleEffectPool.PooledEffect> entry = it.next();
            ParticleEffectPool.PooledEffect fx = entry.value;
            if (fx != null) {
                fx.free();
            }
        }

        effects.clear();
        effectPools.clear();
        lastTex = null;
        lastTexHandle = 0;
    }

    private void collectEffect(ParticleEffect fx,
                               int layerIndex,
                               int zIndex,
                               int sourceEmitter,
                               ParticleOverridesComponent ov) {
        Array<ParticleEmitter> emitters = fx.getEmitters();
        for (int ei = 0, en = emitters.size; ei < en; ei++) {
            ParticleEmitter emitter = emitters.get(ei);

            int blendId = emitter.isAdditive()
                    ? BlendMode.ADDITIVE_ALPHA.id
                    : BlendMode.ALPHA.id;

            Particle[] particles = emitter.particles;
            boolean[] active = emitter.getActiveArray();
            if (particles == null || active == null) continue;

            int cap = emitter.getCapacity();
            for (int pi = 0; pi < cap; pi++) {
                if (!active[pi]) continue;
                Particle p = particles[pi];
                if (p == null) continue;

                pushParticle(p, blendId, layerIndex, zIndex, sourceEmitter, ov);
            }
        }
    }

    private void pushParticle(Sprite sprite,
                              int blendId,
                              int layerIndex,
                              int zIndex,
                              int sourceEmitter,
                              ParticleOverridesComponent ov) {
        float[] v = sprite.getVertices();

        float x1 = v[Batch.X1], y1 = v[Batch.Y1], u1 = v[Batch.U1], vv1 = v[Batch.V1];
        float x2 = v[Batch.X2], y2 = v[Batch.Y2], u2 = v[Batch.U2], vv2 = v[Batch.V2];
        float x3 = v[Batch.X3], y3 = v[Batch.Y3], u3 = v[Batch.U3], vv3 = v[Batch.V3];
        float x4 = v[Batch.X4], y4 = v[Batch.Y4], u4 = v[Batch.U4], vv4 = v[Batch.V4];

        float sizeMul = 1f;
        float alphaMul = 1f;
        int tintRgba = -1;

        if (ov != null) {
            sizeMul = ov.sizeMul;
            alphaMul = ov.alphaMul;
            tintRgba = ov.tintRgba;
        }

        // positions (+ sizeMul around the center)
        if (sizeMul != 1f) {
            float cx = (x1 + x2 + x3 + x4) * 0.25f;
            float cy = (y1 + y2 + y3 + y4) * 0.25f;

            x1 = cx + (x1 - cx) * sizeMul;
            y1 = cy + (y1 - cy) * sizeMul;
            x2 = cx + (x2 - cx) * sizeMul;
            y2 = cy + (y2 - cy) * sizeMul;
            x3 = cx + (x3 - cx) * sizeMul;
            y3 = cy + (y3 - cy) * sizeMul;
            x4 = cx + (x4 - cx) * sizeMul;
            y4 = cy + (y4 - cy) * sizeMul;
        }

        // UV rect
        float uMin = Math.min(Math.min(u1, u2), Math.min(u3, u4));
        float uMax = Math.max(Math.max(u1, u2), Math.max(u3, u4));
        float vMin = Math.min(Math.min(vv1, vv2), Math.min(vv3, vv4));
        float vMax = Math.max(Math.max(vv1, vv2), Math.max(vv3, vv4));

        // color (spriteColor * tintRgba), then alphaMul
        Color col = sprite.getColor();
        float r = col.r, g = col.g, b = col.b, a = col.a;

        if (tintRgba != -1) {
            float tr = ((tintRgba >>> 24) & 0xff) / 255f;
            float tg = ((tintRgba >>> 16) & 0xff) / 255f;
            float tb = ((tintRgba >>> 8) & 0xff) / 255f;
            float ta = (tintRgba & 0xff) / 255f;
            r *= tr;
            g *= tg;
            b *= tb;
            a *= ta;
        }
        if (alphaMul != 1f) a *= alphaMul;

        // clamp
        if (r < 0f) r = 0f;
        else if (r > 1f) r = 1f;
        if (g < 0f) g = 0f;
        else if (g > 1f) g = 1f;
        if (b < 0f) b = 0f;
        else if (b > 1f) b = 1f;
        if (a < 0f) a = 0f;
        else if (a > 1f) a = 1f;

        float colorPacked = Color.toFloatBits(r, g, b, a);

        // --- material / texture array ---
        Texture tex = sprite.getTexture();
        int texHandle;
        if (tex == lastTex) {
            texHandle = lastTexHandle;
        } else {
            texHandle = TextureRegistry.handleOf(tex);
            lastTex = tex;
            lastTexHandle = texHandle;
        }

        // sort
        int runtimeOrder = vfxState.activeCount & ((1 << SortKey64.TIE_BITS) - 1);

        long sortKey = SortKey64.packForBlend(
                defaultShaderIdx,
                blendId,
                texHandle,
                layerIndex,
                zIndex,
                runtimeOrder
        );

        vfxState.addParticleQuad(
                texHandle,
                defaultShaderIdx,
                blendId,
                layerIndex,
                zIndex,
                0,
                0,
                sortKey,
                x1,
                y1,
                x2,
                y2,
                x3,
                y3,
                x4,
                y4,
                uMin,
                vMin,
                uMax,
                vMax,
                colorPacked,
                RenderRepeatFlags.NONE,
                sourceEmitter
        );
    }

    private int resolveLayerIndex(int entityId) {
        EntityIndexComponent index = mEntityIndex.getSafe(entityId, null);
        return index != null ? index.getLayerIndex() : 0;
    }

    private boolean isLayerVisible(int entityId) {
        EntityIndexComponent index = mEntityIndex.getSafe(entityId, null);
        if (index == null) return true;

        int layerIndex = index.getLayerIndex();
        return layerVisibility.get(layerIndex, 1) == 1;
    }

    private int resolveZIndex(int entityId) {
        EntityIndexComponent index = mEntityIndex.getSafe(entityId, null);
        return index != null ? index.getZIndex() : 0;
    }

    private void refreshLayerVisibility() {
        layerVisibility.clear();
        IntBag bag = layerSubscription.getEntities();
        int[] data = bag.getData();
        for (int i = 0, n = bag.size(); i < n; i++) {
            int e = data[i];
            LayerComponent li = mLayerIndex.getSafe(e, null);
            if (li == null) continue;
            VisibilityComponent vis = mVis.getSafe(e, null);
            boolean visible = (vis == null) || vis.isVisible();
            layerVisibility.put(li.layerIndex, visible ? 1 : 0);
        }
    }

    @Override
    public void setSystemProfiler(SystemProfiler profiler) {
        this.profiler = SystemProfilers.orDisabled(profiler);
    }
}
