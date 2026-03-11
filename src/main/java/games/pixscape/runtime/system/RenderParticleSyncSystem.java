package games.pixscape.runtime.system;

import com.artemis.Aspect;
import com.artemis.BaseSystem;
import com.artemis.ComponentMapper;
import com.artemis.EntitySubscription;
import com.artemis.annotations.All;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.ParticleEffect;
import com.badlogic.gdx.ParticleEmitter;
import com.badlogic.gdx.ParticleEmitter.Particle;
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
import com.badlogic.gdx.utils.IntSet;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.render.BlendMode;
import games.pixscape.runtime.render.RenderStateSOA;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.render.TextureRegistry;
import games.pixscape.runtime.service.AtlasRuntimeService;

/**
 * Version SOA des particules : met à jour les ParticleEffect et
 * injecte chaque Sprite de particule active dans RenderStateSOA.
 */
@All({ParticleEmitterComponent.class, TransformComponent.class})
public final class RenderParticleSyncSystem extends BaseSystem {

    private final OrthographicCamera camera;
    private final RenderStateSOA state;

    // cache perf: évite TextureRegistry.handleOf(tex) pour chaque particule
    private Texture lastTex = null;
    private int lastTexHandle = 0;

    // plage d’indices réservée aux VFX dans le SOA
    private final int vfxStartIndex;
    private final int vfxEndIndex;

    // cache : entityId -> ParticleEffect
    private final IntMap<ParticleEffect> effects = new IntMap<>();
    private final IntSet loggedEntities = new IntSet();
    private final IntSet waitingAtlasLoggedEntities = new IntSet();

    private ComponentMapper<ParticleEmitterComponent>  mEmitter;
    private ComponentMapper<TransformComponent>        mTransform;
    private ComponentMapper<VisibilityComponent>       mVis;
    private ComponentMapper<EntityIndexComponent>      mEntityIndex;
    private ComponentMapper<LayerComponent>       mLayerIndex;
    private ComponentMapper<ParticleOverridesComponent> mOverrides;

    private EntitySubscription subscription;
    private EntitySubscription layerSubscription;
    private final IntIntMap layerVisibility = new IntIntMap();

    // stats simples pour nettoyer les slots de la frame précédente
    private int lastUsedVfxSlots = 0;

    // params “matériau” par défaut
    private final int defaultShaderIdx;
    private final AtlasRuntimeService atlasRuntimeService;
    private FileHandle effectsRoot;

    public RenderParticleSyncSystem(RenderStateSOA state,
                                    OrthographicCamera camera,
                                    int vfxStartIndex,
                                    int vfxEndIndex,
                                    int defaultShaderIdx,
                                    AtlasRuntimeService atlasRuntimeService,
                                    FileHandle effectsRoot) {
        this.state               = state;
        this.camera              = camera;
        this.vfxStartIndex       = vfxStartIndex;
        this.vfxEndIndex         = vfxEndIndex;
        this.defaultShaderIdx    = defaultShaderIdx;
        this.atlasRuntimeService = atlasRuntimeService;
        this.effectsRoot         = effectsRoot;
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
            @Override public void inserted(IntBag entities) {
                // lazy-load des effets dans processSystem
            }

            @Override public void removed(IntBag entities) {
                int[] data = entities.getData();
                for (int i = 0, n = entities.size(); i < n; i++) {
                    int e = data[i];
                    ParticleEffect fx = effects.remove(e);
                    if (fx != null && fx.ownsTexture) {
                        fx.dispose();
                    }
                    loggedEntities.remove(e);
                    waitingAtlasLoggedEntities.remove(e);
                }
            }
        });
    }

    @Override
    protected void processSystem() {
        float dt = world.getDelta();

        // 1) nettoyer les slots VFX de la frame précédente
        clearPreviousVfxSlots();
        refreshLayerVisibility();

        IntBag bag = subscription.getEntities();
        int[] data = bag.getData();
        int count  = bag.size();
        if (count == 0) return;

        int cursor = vfxStartIndex;

        for (int i = 0; i < count; i++) {
            int e = data[i];

            if (mVis != null && mVis.has(e) && !mVis.get(e).isVisible()) {
                continue;
            }
            if (!isLayerVisible(e)) continue;

            ParticleEmitterComponent comp = mEmitter.get(e);
            TransformComponent t          = mTransform.get(e);
            if (comp == null || t == null) continue;

            ParticleOverridesComponent ov = (mOverrides != null) ? mOverrides.getSafe(e, null) : null;

            ParticleEffect fx = effects.get(e);
            if (fx == null) {
                fx = createEffect(e, comp);
                if (fx == null) continue;
                effects.put(e, fx);
                waitingAtlasLoggedEntities.remove(e);

                if (!loggedEntities.contains(e)) {
                    String log = "Created effect for entity " + e + " path=" + comp.effectPath;
                    Gdx.app.log("RenderParticleSyncSystem", log);
                    loggedEntities.add(e);
                }

                if (comp.autoStart) fx.start();
            }

            // suivre le Transform (localSpace)
            if (comp.localSpace) {
                float px = t.x + t.originX;
                float py = t.y + t.originY;
                fx.setPosition(px, py);
            }

            if (comp.restartRequested) {
                fx.reset(true, true);
                comp.restartRequested = false;
            }
            if (comp.playRequested) {
                fx.start();
                comp.playRequested = false;
            }
            if (comp.paused) {
                continue;
            }

            // simulation
            fx.update(dt);

            // culling effect-level : bounding box vs caméra
            if (!isEffectVisible(fx)) {
                continue;
            }

            // enabled=false => l'effet vit mais on ne rend rien
            if (ov != null && !ov.enabled) {
                continue;
            }

            // injecter toutes les particules actives dans le SOA,
            // tant qu’on a de la place dans [vfxStartIndex, vfxEndIndex)
            int layerIndex = resolveLayerIndex(e);
            int zIndex     = resolveZIndex(e);
            cursor = collectEffect(fx, cursor, layerIndex, zIndex, ov);
            if (cursor >= vfxEndIndex) break;
        }

        lastUsedVfxSlots = Math.max(0, cursor - vfxStartIndex);
    }

    // ------------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------------

    private void clearPreviousVfxSlots() {
        if (lastUsedVfxSlots <= 0) return;
        int end = Math.min(vfxStartIndex + lastUsedVfxSlots, vfxEndIndex);
        for (int i = vfxStartIndex; i < end; i++) {
            state.disable(i);
        }
        lastUsedVfxSlots = 0;
    }

    private ParticleEffect createEffect(int entityId, ParticleEmitterComponent emitter) {
        if (emitter.effectPath == null || emitter.effectPath.isEmpty()) return null;

        if (effectsRoot == null) {
            Gdx.app.error("RenderParticleSyncSystem", "effectsRoot is null");
            return null;
        }
        FileHandle effectFile  = effectsRoot.child(emitter.effectPath);

        if (!effectFile.exists()) {
            Gdx.app.error("RenderParticleSyncSystem",
                    "Effect file not found: " + effectFile.path()
                            + " (emitter.effectPath=" + emitter.effectPath + ")");
            return null;
        }

        ParticleEffect fx = new ParticleEffect();
        boolean loaded = false;

        if (atlasRuntimeService != null &&
                emitter.atlasTag != null &&
                !emitter.atlasTag.isEmpty()) {

            TextureAtlas atlas = atlasRuntimeService.getAtlas(emitter.atlasTag);
            if (atlas != null) {
                try {
                    fx.load(effectFile, atlas);
                    loaded = true;
                } catch (Exception ex) {
                    Gdx.app.error("RenderParticleSyncSystem",
                            "Failed to load particle effect from atlas '" + emitter.atlasTag
                                    + "': " + effectFile.path(), ex);
                }
            } else {
                if (!waitingAtlasLoggedEntities.contains(entityId)) {
                    Gdx.app.log("RenderParticleSyncSystem",
                            "Waiting atlas '" + emitter.atlasTag + "' before loading effect " + effectFile.path());
                    waitingAtlasLoggedEntities.add(entityId);
                }
            }
        }

        if (!loaded) {
            // IMPORTANT : ne retourne pas un fx non chargé => effet fantôme
            return null;
        }

        fx.setEmittersCleanUpBlendFunction(false);
        return fx;
    }

    private boolean isEffectVisible(ParticleEffect fx) {
        BoundingBox box = fx.getBoundingBox();
        if (!box.isValid()) return false;

        float halfW = camera.viewportWidth * 0.5f * camera.zoom;
        float halfH = camera.viewportHeight * 0.5f * camera.zoom;
        float camX  = camera.position.x;
        float camY  = camera.position.y;

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
        if (effects.size == 0) return;

        effects.forEach(entry -> {
            ParticleEffect fx = entry.value;
            if (fx != null && fx.ownsTexture) {
                fx.dispose();
            }
        });

        effects.clear();
        loggedEntities.clear();
        waitingAtlasLoggedEntities.clear();
        lastTex = null;
        lastTexHandle = 0;
    }

    private int collectEffect(ParticleEffect fx, int cursor, int layerIndex, int zIndex, ParticleOverridesComponent ov) {
        Array<ParticleEmitter> emitters = fx.getEmitters();
        for (int ei = 0, en = emitters.size; ei < en && cursor < vfxEndIndex; ei++) {
            ParticleEmitter emitter = emitters.get(ei);

            int blendId = emitter.isAdditive()
                    ? BlendMode.ADDITIVE_ALPHA.id
                    : BlendMode.ALPHA.id;

            Particle[] particles = emitter.particles;
            boolean[]  active    = emitter.getActiveArray();
            if (particles == null || active == null) continue;

            int cap = emitter.getCapacity();
            for (int pi = 0; pi < cap && cursor < vfxEndIndex; pi++) {
                if (!active[pi]) continue;
                Particle p = particles[pi];
                if (p == null) continue;

                cursor = pushParticle(p, cursor, blendId, layerIndex, zIndex, ov);
            }
        }
        return cursor;
    }

    private int pushParticle(Sprite sprite, int index, int blendId, int layerIndex, int zIndex, ParticleOverridesComponent ov) {
        state.touch(index);
        state.enabled[index]  = true;
        state.visible[index]  = true;
        state.kind[index]     = RenderStateSOA.KIND_SPRITE;
        state.entityId[index] = -1;

        float[] v = sprite.getVertices();

        float x1 = v[Batch.X1], y1 = v[Batch.Y1], u1 = v[Batch.U1], vv1 = v[Batch.V1];
        float x2 = v[Batch.X2], y2 = v[Batch.Y2], u2 = v[Batch.U2], vv2 = v[Batch.V2];
        float x3 = v[Batch.X3], y3 = v[Batch.Y3], u3 = v[Batch.U3], vv3 = v[Batch.V3];
        float x4 = v[Batch.X4], y4 = v[Batch.Y4], u4 = v[Batch.U4], vv4 = v[Batch.V4];

        float sizeMul = 1f;
        float alphaMul = 1f;
        int tintRgba = -1;

        if (ov != null) {
            sizeMul  = ov.sizeMul;
            alphaMul = ov.alphaMul;
            tintRgba = ov.tintRgba;
        }

        // positions (+ sizeMul autour du centre)
        if (sizeMul != 1f) {
            float cx = (x1 + x2 + x3 + x4) * 0.25f;
            float cy = (y1 + y2 + y3 + y4) * 0.25f;

            state.x1[index] = cx + (x1 - cx) * sizeMul; state.y1[index] = cy + (y1 - cy) * sizeMul;
            state.x2[index] = cx + (x2 - cx) * sizeMul; state.y2[index] = cy + (y2 - cy) * sizeMul;
            state.x3[index] = cx + (x3 - cx) * sizeMul; state.y3[index] = cy + (y3 - cy) * sizeMul;
            state.x4[index] = cx + (x4 - cx) * sizeMul; state.y4[index] = cy + (y4 - cy) * sizeMul;
        } else {
            state.x1[index] = x1; state.y1[index] = y1;
            state.x2[index] = x2; state.y2[index] = y2;
            state.x3[index] = x3; state.y3[index] = y3;
            state.x4[index] = x4; state.y4[index] = y4;
        }

        // UV rect
        float uMin = Math.min(Math.min(u1, u2), Math.min(u3, u4));
        float uMax = Math.max(Math.max(u1, u2), Math.max(u3, u4));
        float vMin = Math.min(Math.min(vv1, vv2), Math.min(vv3, vv4));
        float vMax = Math.max(Math.max(vv1, vv2), Math.max(vv3, vv4));

        state.u1[index] = uMin; state.v1[index] = vMin;
        state.u2[index] = uMax; state.v2[index] = vMax;

        // couleur (spriteColor * tintRgba) puis alphaMul
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
        if (r < 0f) r = 0f; else if (r > 1f) r = 1f;
        if (g < 0f) g = 0f; else if (g > 1f) g = 1f;
        if (b < 0f) b = 0f; else if (b > 1f) b = 1f;
        if (a < 0f) a = 0f; else if (a > 1f) a = 1f;

        state.colorPacked[index] = Color.toFloatBits(r, g, b, a);
        state.a[index] = a;

        // --- matériau / texture array ---
        Texture tex = sprite.getTexture();
        int texHandle;
        if (tex == lastTex) {
            texHandle = lastTexHandle;
        } else {
            texHandle = TextureRegistry.handleOf(tex);
            lastTex = tex;
            lastTexHandle = texHandle;
        }

        state.textureHandle[index] = texHandle;
        state.shader[index]        = defaultShaderIdx;
        state.blend[index]         = blendId;
        state.layerIndex[index]    = layerIndex;
        state.z[index]             = zIndex;

        // tri
        int runtimeOrder = (index - vfxStartIndex) & ((1 << SortKey64.TIE_BITS) - 1);
        state.paramsId[index]       = 0;
        state.customParamsId[index] = 0;

        state.sortKey[index] = SortKey64.packForBlend(
                state.shader[index],
                state.blend[index],   // blendModeId
                texHandle,
                state.layerIndex[index],
                state.z[index],
                runtimeOrder
        );

        return index + 1;
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
}
